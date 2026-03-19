package com.wourd.app.domain.model

import java.time.Instant

data class Message(
    val id: String,
    val chatId: String,
    val role: Role,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Instant,
) {
    enum class Role { System, User, Assistant }
}

