package com.wourd.app.domain.model

import java.time.Instant

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

