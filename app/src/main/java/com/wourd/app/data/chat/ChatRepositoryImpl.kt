package com.wourd.app.data.chat

import com.wourd.app.data.db.ChatDao
import com.wourd.app.data.db.ChatEntity
import com.wourd.app.data.db.MessageDao
import com.wourd.app.data.db.MessageEntity
import com.wourd.app.data.serialization.AppJson
import com.wourd.app.domain.model.Attachment
import com.wourd.app.domain.model.Chat
import com.wourd.app.domain.model.Message
import com.wourd.app.domain.repository.ChatRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

class ChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
) : ChatRepository {

    override fun observeChats(): Flow<List<Chat>> =
        chatDao.observeChats().map { list -> list.map { it.toDomain() } }

    override suspend fun createChat(initialTitle: String): Chat {
        val now = Instant.now()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = initialTitle.ifBlank { "New chat" },
            createdAtEpochMs = now.toEpochMilli(),
            updatedAtEpochMs = now.toEpochMilli(),
        )
        chatDao.upsert(chat)
        return chat.toDomain()
    }

    override suspend fun renameChat(chatId: String, title: String) {
        val existing = chatDao.getChat(chatId) ?: return
        chatDao.upsert(
            existing.copy(
                title = title.ifBlank { existing.title },
                updatedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
    }

    override suspend fun deleteChat(chatId: String) {
        messageDao.deleteForChat(chatId)
        chatDao.delete(chatId)
    }

    override suspend fun clearAll() {
        messageDao.deleteAll()
        chatDao.deleteAll()
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> =
        messageDao.observeMessages(chatId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessages(chatId: String): List<Message> =
        messageDao.getMessages(chatId).map { it.toDomain() }

    override suspend fun addMessage(message: Message) {
        messageDao.insert(message.toEntity())
        val chat = chatDao.getChat(message.chatId) ?: return
        chatDao.upsert(chat.copy(updatedAtEpochMs = Instant.now().toEpochMilli()))
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    override suspend fun deleteMessagesForChat(chatId: String) {
        messageDao.deleteForChat(chatId)
        val chat = chatDao.getChat(chatId) ?: return
        chatDao.upsert(chat.copy(updatedAtEpochMs = Instant.now().toEpochMilli()))
    }

    private fun ChatEntity.toDomain(): Chat = Chat(
        id = id,
        title = title,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )

    private fun MessageEntity.toDomain(): Message {
        val attachments = runCatching {
            AppJson.json.decodeFromString(
                ListSerializer(kotlinx.serialization.PolymorphicSerializer(Attachment::class)),
                attachmentsJson,
            )
        }.getOrDefault(emptyList())

        return Message(
            id = id,
            chatId = chatId,
            role = when (role) {
                "system" -> Message.Role.System
                "assistant" -> Message.Role.Assistant
                else -> Message.Role.User
            },
            content = content,
            attachments = attachments,
            timestamp = Instant.ofEpochMilli(timestampEpochMs),
        )
    }

    private fun Message.toEntity(): MessageEntity {
        val roleStr = when (role) {
            Message.Role.System -> "system"
            Message.Role.User -> "user"
            Message.Role.Assistant -> "assistant"
        }
        val attachmentsJson = AppJson.json.encodeToString(
            ListSerializer(kotlinx.serialization.PolymorphicSerializer(Attachment::class)),
            attachments,
        )
        return MessageEntity(
            id = id,
            chatId = chatId,
            role = roleStr,
            content = content,
            attachmentsJson = attachmentsJson,
            timestampEpochMs = timestamp.toEpochMilli(),
        )
    }
}

