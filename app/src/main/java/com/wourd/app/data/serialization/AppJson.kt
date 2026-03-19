package com.wourd.app.data.serialization

import com.wourd.app.domain.model.Attachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object AppJson {
    private val module = SerializersModule {
        polymorphic(Attachment::class) {
            subclass(Attachment.Image::class)
            subclass(Attachment.File::class)
        }
    }

    val json: Json = Json {
        serializersModule = module
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }
}

