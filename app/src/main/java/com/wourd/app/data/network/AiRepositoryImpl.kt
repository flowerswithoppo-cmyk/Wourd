package com.wourd.app.data.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.wourd.app.data.network.dto.ChatCompletionsRequest
import com.wourd.app.data.network.dto.ChatMessage
import com.wourd.app.data.network.dto.ChatCompletionsStreamChunk
import com.wourd.app.data.network.dto.ImageUrl
import com.wourd.app.data.network.dto.ImageUrlPart
import com.wourd.app.data.network.dto.TextPart
import com.wourd.app.data.serialization.AppJson
import com.wourd.app.domain.model.Attachment
import com.wourd.app.domain.model.Message
import com.wourd.app.domain.repository.AiRepository
import com.wourd.app.domain.repository.AiStreamEvent
import com.wourd.app.domain.repository.ApiSettings
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiRepositoryImpl(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : AiRepository {

    override fun streamChatCompletion(
        messages: List<Message>,
        settings: ApiSettings,
        thinkingMode: Boolean,
    ): Flow<AiStreamEvent> = callbackFlow {
        val baseUrl = settings.baseUrl.trimEnd('/')
        val url = "$baseUrl/chat/completions"

        val supportsReasoning = settings.model.startsWith("o1") || settings.model.startsWith("o3")
        val includeSystem = !(supportsReasoning && thinkingMode)

        val requestDto = ChatCompletionsRequest(
            model = settings.model,
            messages = messages
                .asSequence()
                .filter { includeSystem || it.role != Message.Role.System }
                .map { it.toChatMessage(contentResolver = appContext.contentResolver) }
                .toList(),
            stream = true,
            reasoningEffort = if (supportsReasoning && thinkingMode) "high" else null,
        )

        val jsonBody = AppJson.json.encodeToString(ChatCompletionsRequest.serializer(), requestDto)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(request)

        val response = try {
            call.execute()
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }

        if (!response.isSuccessful) {
            val msg = response.body?.string().orEmpty()
            close(IllegalStateException("HTTP ${response.code}: ${msg.ifBlank { response.message }}"))
            return@callbackFlow
        }

        val stream = response.body?.byteStream()
        if (stream == null) {
            close(IllegalStateException("Empty response body"))
            return@callbackFlow
        }

        val reader = BufferedReader(InputStreamReader(stream))
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    trySend(AiStreamEvent.Done)
                    break
                }
                val chunk = runCatching {
                    AppJson.json.decodeFromString(ChatCompletionsStreamChunk.serializer(), data)
                }.getOrNull() ?: continue

                val delta = chunk.choices.firstOrNull()?.delta?.content
                if (!delta.isNullOrEmpty()) {
                    trySend(AiStreamEvent.Delta(delta))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close(t)
        } finally {
            runCatching { reader.close() }
            runCatching { response.close() }
        }

        awaitClose {
            runCatching { call.cancel() }
        }
    }

    private fun Message.toChatMessage(contentResolver: ContentResolver): ChatMessage {
        val roleStr = when (role) {
            Message.Role.System -> "system"
            Message.Role.User -> "user"
            Message.Role.Assistant -> "assistant"
        }

        val parts = buildList {
            if (content.isNotBlank()) add(TextPart(text = content))

            attachments.forEach { att ->
                when (att) {
                    is Attachment.Image -> {
                        val dataUrl = contentResolver.readImageAsDataUrl(att.uri, att.mimeType)
                        if (dataUrl != null) {
                            add(ImageUrlPart(imageUrl = ImageUrl(url = dataUrl)))
                        }
                    }
                    is Attachment.File -> {
                        // File text is expected to be merged into message content earlier.
                    }
                }
            }
        }

        return ChatMessage(role = roleStr, content = parts)
    }

    private fun ContentResolver.readImageAsDataUrl(uriString: String, mimeType: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val bytes = runCatching { openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$b64"
    }
}

