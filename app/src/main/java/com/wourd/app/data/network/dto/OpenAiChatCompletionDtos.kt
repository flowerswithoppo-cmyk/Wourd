package com.wourd.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
sealed interface ContentPart

@Serializable
@SerialName("text")
data class TextPart(
    val text: String,
) : ContentPart

@Serializable
@SerialName("image_url")
data class ImageUrlPart(
    @SerialName("image_url")
    val imageUrl: ImageUrl,
) : ContentPart

@Serializable
data class ImageUrl(
    val url: String,
) 

@Serializable
data class ChatCompletionsStreamChunk(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val delta: Delta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
)

