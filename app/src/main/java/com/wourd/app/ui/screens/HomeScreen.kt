package com.wourd.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.wourd.app.domain.model.Attachment
import com.wourd.app.domain.model.Message
import com.wourd.app.ui.util.CaptureBus
import com.wourd.app.ui.viewmodel.ChatViewModel
import com.wourd.app.ui.viewmodel.SettingsViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    chatVm: ChatViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val chatState by chatVm.state.collectAsState()
    val settingsState by settingsVm.state.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    var input by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameChatId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }

    // Receive camera capture
    LaunchedEffect(CaptureBus.lastCapturedUri.value) {
        val uri = CaptureBus.lastCapturedUri.value ?: return@LaunchedEffect
        attachments.add(
            Attachment.Image(
                id = UUID.randomUUID().toString(),
                uri = uri,
            ),
        )
        CaptureBus.lastCapturedUri.value = null
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                attachments.add(
                    Attachment.Image(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                    ),
                )
            }
        },
    )

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val (name, mime, text) = context.readDocumentAsText(uri)
                attachments.add(
                    Attachment.File(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        displayName = name ?: "file",
                        mimeType = mime ?: "application/octet-stream",
                        textContent = text,
                    ),
                )
                if (!text.isNullOrBlank()) {
                    input = buildString {
                        append(input.trimEnd())
                        if (isNotEmpty()) append("\n\n")
                        append("File: ${name ?: "document"}\n\n")
                        append(text.take(8000))
                    }
                }
            }
        },
    )

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename chat") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameChatId
                    if (id != null) chatVm.renameChat(id, renameValue.trim())
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Attach", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        showAttachSheet = false
                        pickPhotoLauncher.launch(ActivityResultContracts.PickVisualMedia.Request(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose photo from gallery") }

                Button(
                    onClick = {
                        showAttachSheet = false
                        onOpenCamera()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Take a photo") }

                Button(
                    onClick = {
                        showAttachSheet = false
                        pickFileLauncher.launch(arrayOf("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Attach a file") }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("History", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { chatVm.newChat(); scope.launch { drawerState.close() } }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                }

                Spacer(Modifier.height(6.dp))

                chatState.chats.forEach { chat ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    chat.updatedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm")),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                )
                            }
                        },
                        selected = chat.id == chatState.activeChatId,
                        onClick = {
                            chatVm.setActiveChat(chat.id)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.History, contentDescription = "History")
                        }
                    },
                    title = {
                        Text(
                            "Wourd",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                AnimatedVisibility(
                    visible = chatState.messages.none { it.role != Message.Role.System },
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "What would you like to do?",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 24.sp,
                        )
                    }
                }

                if (chatState.messages.any { it.role != Message.Role.System }) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(chatState.messages.filter { it.role != Message.Role.System }, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                isUser = msg.role == Message.Role.User,
                                onCopy = {
                                    context.copyToClipboard(msg.content)
                                    scope.launch { snackbarHostState.showSnackbar("Copied") }
                                },
                                onDelete = { chatVm.deleteMessage(msg.id) },
                                onRetry = {
                                    if (msg == chatState.messages.lastOrNull()) chatVm.resendLastUserMessage()
                                },
                            )
                        }
                        if (chatState.isStreaming && (chatState.messages.lastOrNull()?.role == Message.Role.Assistant) && chatState.messages.last().content.isBlank()) {
                            item {
                                TypingBubble()
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Composer(
                    input = input,
                    onInputChange = { input = it },
                    attachments = attachments,
                    onRemoveAttachment = { id -> attachments.removeAll { it.id == id } },
                    thinking = settingsState.api.defaultThinkingMode,
                    onThinkingChange = onThinkingChange@{ enabled ->
                        val supports = settingsState.api.model.startsWith("o1") || settingsState.api.model.startsWith("o3")
                        if (!supports && enabled) {
                            scope.launch { snackbarHostState.showSnackbar("Thinking Mode is not supported by this model.") }
                            return@onThinkingChange
                        }
                        settingsVm.setDefaultThinkingMode(enabled)
                    },
                    onAttachClick = { showAttachSheet = true },
                    onSend = onSend@{
                        val supports = settingsState.api.model.startsWith("o1") || settingsState.api.model.startsWith("o3")
                        val thinking = settingsState.api.defaultThinkingMode
                        if (thinking && !supports) {
                            scope.launch { snackbarHostState.showSnackbar("Thinking Mode is not supported by this model.") }
                            return@onSend
                        }
                        chatVm.sendMessage(input, attachments.toList(), thinkingOverride = thinking)
                        input = ""
                        attachments.clear()
                        keyboard?.hide()
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    sendEnabled = input.isNotBlank() || attachments.isNotEmpty(),
                    streaming = chatState.isStreaming,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isUser: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .combinedClickable(
                    onClick = { /* noop */ },
                    onLongClick = { menu = true },
                )
                .padding(12.dp)
                .fillMaxWidth(0.85f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isUser) {
                    message.attachments.filterIsInstance<Attachment.Image>().forEach { img ->
                        AsyncImage(
                            model = img.uri,
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Text(message.content, color = fg)
                } else {
                    if (message.content.isNotBlank()) {
                        RichText(color = fg) {
                            Markdown(message.content)
                        }
                    } else {
                        Text("...", color = fg.copy(alpha = 0.6f))
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = { menu = false; onCopy() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Retry") },
                        onClick = { menu = false; onRetry() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card {
            Text(
                "Typing…",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (String) -> Unit,
    thinking: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    onAttachClick: () -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    streaming: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { att ->
                    when (att) {
                        is Attachment.Image -> {
                            Box {
                                AsyncImage(
                                    model = att.uri,
                                    contentDescription = "Attachment",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                IconButton(
                                    onClick = { onRemoveAttachment(att.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                ) {
                                    Text("×", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        is Attachment.File -> {
                            Card(modifier = Modifier.height(56.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(att.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { onRemoveAttachment(att.id) }, modifier = Modifier.size(24.dp)) {
                                        Text("×")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onAttachClick, enabled = !streaming) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
            }

            androidx.compose.material3.OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                singleLine = false,
            )

            IconButton(
                onClick = onSend,
                enabled = sendEnabled && !streaming,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (sendEnabled && !streaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Thinking", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.width(8.dp))
            Switch(checked = thinking, onCheckedChange = onThinkingChange, enabled = !streaming)
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Wourd", text))
}

private fun Context.readDocumentAsText(uri: Uri): Triple<String?, String?, String?> {
    val cr = contentResolver
    val mime = cr.getType(uri)
    val name = runCatching {
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    val isText = mime?.startsWith("text/") == true || name?.endsWith(".txt", true) == true || name?.endsWith(".md", true) == true
    val text = if (isText) {
        runCatching {
            cr.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }
        }.getOrNull()
    } else null

    return Triple(name, mime, text)
}

