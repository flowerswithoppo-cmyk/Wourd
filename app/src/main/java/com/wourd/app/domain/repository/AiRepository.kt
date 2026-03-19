package com.wourd.app.domain.repository

import com.wourd.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

sealed class AiStreamEvent {
    data class Delta(val text: String) : AiStreamEvent()
    data object Done : AiStreamEvent()
}

interface AiRepository {
    fun streamChatCompletion(
        messages: List<Message>,
        settings: ApiSettings,
        thinkingMode: Boolean,
    ): Flow<AiStreamEvent>
}

