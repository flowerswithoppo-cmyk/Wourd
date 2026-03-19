package com.wourd.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wourd.app.domain.model.Attachment
import com.wourd.app.domain.model.Chat
import com.wourd.app.domain.model.Message
import com.wourd.app.domain.repository.AiRepository
import com.wourd.app.domain.repository.AiStreamEvent
import com.wourd.app.domain.repository.ChatRepository
import com.wourd.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatScreenState(
    val chats: List<Chat> = emptyList(),
    val activeChatId: String? = null,
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val activeChatId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val streaming = kotlinx.coroutines.flow.MutableStateFlow(false)

    private var streamJob: Job? = null

    val state: StateFlow<ChatScreenState> = combine(
        chatRepository.observeChats(),
        activeChatId,
        streaming,
    ) { chats, activeId, isStreaming ->
        Triple(chats, activeId ?: chats.firstOrNull()?.id, isStreaming)
    }.flatMapLatest { (chats, resolvedChatId, isStreaming) ->
        if (resolvedChatId == null) {
            kotlinx.coroutines.flow.flowOf(ChatScreenState(chats = chats, activeChatId = null, messages = emptyList(), isStreaming = isStreaming))
        } else {
            chatRepository.observeMessages(resolvedChatId).combine(
                kotlinx.coroutines.flow.flowOf(chats),
            ) { msgs, chatList ->
                ChatScreenState(
                    chats = chatList,
                    activeChatId = resolvedChatId,
                    messages = msgs,
                    isStreaming = isStreaming,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatScreenState())

    fun setActiveChat(chatId: String) {
        activeChatId.value = chatId
        cancelStreaming()
    }

    fun newChat() {
        activeChatId.value = null
        cancelStreaming()
    }

    fun deleteChat(chatId: String) = viewModelScope.launch {
        cancelStreaming()
        chatRepository.deleteChat(chatId)
        if (activeChatId.value == chatId) activeChatId.value = null
    }

    fun renameChat(chatId: String, title: String) = viewModelScope.launch {
        chatRepository.renameChat(chatId, title)
    }

    fun clearAllHistory() = viewModelScope.launch {
        cancelStreaming()
        chatRepository.clearAll()
    }

    fun deleteMessage(messageId: String) = viewModelScope.launch {
        chatRepository.deleteMessage(messageId)
    }

    fun resendLastUserMessage() {
        val s = state.value
        val lastUser = s.messages.lastOrNull { it.role == Message.Role.User } ?: return
        sendMessage(lastUser.content, lastUser.attachments, thinkingOverride = null)
    }

    fun sendMessage(
        text: String,
        attachments: List<Attachment>,
        thinkingOverride: Boolean?,
    ) = viewModelScope.launch {
        if (text.isBlank() && attachments.isEmpty()) return@launch
        cancelStreaming()

        val settings = settingsRepository.apiSettings.first()

        val resolvedChatId = activeChatId.value ?: run {
            val title = text.trim().take(40).ifBlank { "New chat" }
            val chat = chatRepository.createChat(title)
            activeChatId.value = chat.id
            chat.id
        }

        val now = Instant.now()

        // Ensure system prompt exists as first message (stored once)
        val existing = chatRepository.getMessages(resolvedChatId)

        if (existing.none { it.role == Message.Role.System } && settings.systemPrompt.isNotBlank()) {
            chatRepository.addMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    chatId = resolvedChatId,
                    role = Message.Role.System,
                    content = settings.systemPrompt,
                    attachments = emptyList(),
                    timestamp = now,
                ),
            )
        }

        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = resolvedChatId,
            role = Message.Role.User,
            content = text,
            attachments = attachments,
            timestamp = now,
        )
        chatRepository.addMessage(userMsg)

        val assistantId = UUID.randomUUID().toString()
        chatRepository.addMessage(
            Message(
                id = assistantId,
                chatId = resolvedChatId,
                role = Message.Role.Assistant,
                content = "",
                attachments = emptyList(),
                timestamp = Instant.now(),
            ),
        )

        val thinkingMode = thinkingOverride ?: settings.defaultThinkingMode
        streaming.value = true

        streamJob = viewModelScope.launch {
            var acc = ""
            try {
                val allForApi = chatRepository.getMessages(resolvedChatId)
                    .filter { it.id != assistantId } // avoid placeholder being sent

                aiRepository.streamChatCompletion(
                    messages = allForApi,
                    settings = settings,
                    thinkingMode = thinkingMode,
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> {
                            acc += event.text
                            chatRepository.addMessage(
                                Message(
                                    id = assistantId,
                                    chatId = resolvedChatId,
                                    role = Message.Role.Assistant,
                                    content = acc,
                                    attachments = emptyList(),
                                    timestamp = Instant.now(),
                                ),
                            )
                        }
                        AiStreamEvent.Done -> {
                            // no-op
                        }
                    }
                }
            } finally {
                streaming.value = false
            }
        }
    }

    private fun cancelStreaming() {
        streamJob?.cancel()
        streamJob = null
        streaming.value = false
    }
}

