package com.wourd.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Attachment {
    val id: String

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: String,
        val uri: String,
        val mimeType: String = "image/jpeg",
    ) : Attachment

    @Serializable
    @SerialName("file")
    data class File(
        override val id: String,
        val uri: String,
        val displayName: String,
        val mimeType: String,
        val textContent: String? = null,
    ) : Attachment
}

