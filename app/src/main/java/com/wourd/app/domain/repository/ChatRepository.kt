package com.wourd.app.domain.repository

import com.wourd.app.domain.model.Chat
import com.wourd.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    suspend fun createChat(initialTitle: String): Chat
    suspend fun renameChat(chatId: String, title: String)
    suspend fun deleteChat(chatId: String)
    suspend fun clearAll()

    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun getMessages(chatId: String): List<Message>
    suspend fun addMessage(message: Message)
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteMessagesForChat(chatId: String)
}

