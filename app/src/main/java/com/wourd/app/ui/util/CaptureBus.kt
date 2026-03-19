package com.wourd.app.ui.util

import androidx.compose.runtime.mutableStateOf

object CaptureBus {
    val lastCapturedUri = mutableStateOf<String?>(null)
}

