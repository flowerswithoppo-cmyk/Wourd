package com.wourd.chat

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.CheckBox
import android.widget.TextView
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Android views
    private lateinit var ggufTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var thinkingModeCb: CheckBox
    private lateinit var internetSearchCb: CheckBox
    private lateinit var modelSpinner: Spinner
    private lateinit var userActionFab: FloatingActionButton

    // Arm AI Chat inference engine
    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null

    // Conversation states
    private var isModelReady = false
    private var isDownloadingModel = false
    private val messages = mutableListOf<Message>()
    private val lastAssistantMsg = StringBuilder()
    private var generationCancelledByUser = false
    private val messageAdapter = MessageAdapter(messages)

    private data class ModelPreset(val displayName: String, val fileName: String, val url: String)

    private val modelPresets = listOf(
        // Default: smaller download but still ~1.5B quality.
        ModelPreset(
            displayName = "Qwen2.5-1.5B (Q4_K_M)",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
        ),
        ModelPreset(
            displayName = "TinyLlama-1.1B (Q4_K_M)",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            url = "https://huggingface.co/pbatra/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf?download=true"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // View model boilerplate and state management is out of this basic sample's scope
        onBackPressedDispatcher.addCallback { Log.w(TAG, "Ignore back press for simplicity") }

        // Find views
        ggufTv = findViewById(R.id.gguf)
        messagesRv = findViewById(R.id.messages)
        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter
        userInputEt = findViewById(R.id.user_input)
        thinkingModeCb = findViewById(R.id.thinking_mode)
        internetSearchCb = findViewById(R.id.internet_search)
        modelSpinner = findViewById(R.id.model_spinner)
        userActionFab = findViewById(R.id.fab)

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (!::engine.isInitialized) return
                if (position !in modelPresets.indices) return
                if (isDownloadingModel) return
                if (generationJob?.isActive == true) {
                    generationCancelledByUser = true
                    engine.cancelGeneration()
                    generationJob?.cancel()
                    generationJob = null
                }

                // Trigger model swap.
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        withContext(Dispatchers.Main) {
                            isModelReady = false
                            isDownloadingModel = true
                            userInputEt.isEnabled = false
                            userActionFab.isEnabled = false
                            thinkingModeCb.isEnabled = false
                            internetSearchCb.isEnabled = false
                        }

                        downloadAndLoadModel(modelPresets[position])

                        withContext(Dispatchers.Main) {
                            isDownloadingModel = false
                            isModelReady = true
                            userInputEt.isEnabled = true
                            thinkingModeCb.isEnabled = true
                            internetSearchCb.isEnabled = true
                            userActionFab.isEnabled = true
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to swap model", t)
                        withContext(Dispatchers.Main) {
                            isDownloadingModel = false
                            isModelReady = false
                            userInputEt.isEnabled = false
                            thinkingModeCb.isEnabled = false
                            internetSearchCb.isEnabled = false
                            userActionFab.isEnabled = false
                            ggufTv.text = "Ошибка загрузки модели: ${t.message ?: "unknown error"}"
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Arm AI Chat initialization
        // Load default model automatically (offline generation).
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                val preset = modelPresets.getOrElse(0) { modelPresets.first() }
                withContext(Dispatchers.Main) {
                    userInputEt.isEnabled = false
                    userInputEt.hint = "Скачиваю модель..."
                    isModelReady = false
                    isDownloadingModel = true
                    modelSpinner.isEnabled = false
                    thinkingModeCb.isEnabled = false
                    internetSearchCb.isEnabled = false
                    userActionFab.isEnabled = false
                }
                downloadAndLoadModel(preset)
                withContext(Dispatchers.Main) {
                    isDownloadingModel = false
                    isModelReady = true
                    modelSpinner.isEnabled = true
                    userInputEt.isEnabled = true
                    userInputEt.hint = "Введите сообщение и нажмите отправить"
                    thinkingModeCb.isEnabled = true
                    internetSearchCb.isEnabled = true
                    userActionFab.isEnabled = true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to init/download default model", t)
                withContext(Dispatchers.Main) {
                    isDownloadingModel = false
                    isModelReady = false
                    modelSpinner.isEnabled = true
                    userInputEt.isEnabled = false
                    userInputEt.hint = "Ошибка инициализации (см. Logcat)"
                    thinkingModeCb.isEnabled = false
                    internetSearchCb.isEnabled = false
                    userActionFab.isEnabled = false
                    ggufTv.text = "Ошибка: не удалось инициализировать модель: ${t.message}"
                }
            }
        }

        // Upon CTA button tapped
        userActionFab.setOnClickListener {
            if (generationJob?.isActive == true) {
                generationCancelledByUser = true
                engine.cancelGeneration()
                generationJob?.cancel()
                generationJob = null
                // Mark last assistant message as cancelled (best effort).
                if (messages.isNotEmpty() && !messages.last().isUser) {
                    messages[messages.lastIndex] = messages.last().copy(content = "Отменено")
                    messageAdapter.notifyItemChanged(messages.size - 1)
                }
                return@setOnClickListener
            }

            if (isModelReady && !isDownloadingModel) handleUserInput()
        }
    }

    /**
     * Download a default GGUF model to app private storage (so APK works on first run).
     */
    private suspend fun downloadAndLoadModel(preset: ModelPreset) {
        generationCancelledByUser = false
        isDownloadingModel = true

        val modelsDir = ensureModelsDirectory()
        val modelFile = File(modelsDir, preset.fileName)

        withContext(Dispatchers.Main) {
            ggufTv.text = "Скачиваю модель: ${preset.displayName}\n${preset.fileName}"
        }

        if (!modelFile.exists() || modelFile.length() == 0L) {
            downloadFileWithProgress(
                url = preset.url,
                destFile = modelFile,
                onProgress = { downloaded, total ->
                    withContext(Dispatchers.Main) {
                        val pct = if (total > 0) (downloaded * 100 / total) else -1
                        ggufTv.text = buildString {
                            append("Скачиваю модель: ${preset.displayName}\n")
                            if (pct >= 0) append("Прогресс: $pct% \n") else append("Прогресс: $downloaded байт \n")
                            append("Размер: ${formatBytes(total)}")
                        }
                    }
                }
            )
        }

        withContext(Dispatchers.Main) {
            ggufTv.text = "Загружаю модель: ${preset.displayName}"
        }

        // If a previous load failed, the engine is likely in State.Error.
        // The library only allows `loadModel()` when the state is Initialized,
        // so we reset to avoid "Cannot load model in Error!" crashes.
        if (engine.state.value is InferenceEngine.State.Error) {
            Log.w(TAG, "Inference engine is in Error state; resetting via cleanUp()")
            engine.cleanUp()
        }

        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw IllegalStateException("Model file is missing or empty: ${modelFile.absolutePath}")
        }

        engine.loadModel(modelFile.path)
        // System prompt must be set right after model load (per AiChat's InferenceEngine).
        engine.setSystemPrompt(BASE_SYSTEM_PROMPT)

        withContext(Dispatchers.Main) {
            userInputEt.hint = "Введите сообщение и нажмите отправить"
            userInputEt.isEnabled = true
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        destFile: File,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                requestMethod = "GET"
            }

            val total = connection.contentLengthLong
            var downloaded: Long = 0L

            connection.inputStream.use { input ->
                destFile.outputStream().use { out ->
                    val buffer = ByteArray(1024 * 128)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        downloaded += read.toLong()
                        onProgress(downloaded, total)
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "?"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return String.format("%.2f %s", v, units[i])
    }

    private val getContent = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        Log.i(TAG, "Selected file uri:\n $uri")
        uri?.let { handleSelectedModel(it) }
    }

    /**
     * Handles the file Uri from [getContent] result
     */
    private fun handleSelectedModel(uri: Uri) {
        // Update UI states
        userActionFab.isEnabled = false
        userInputEt.hint = "Parsing GGUF..."
        ggufTv.text = "Parsing metadata from selected file \n$uri"

        lifecycleScope.launch(Dispatchers.IO) {
            // Parse GGUF metadata
            Log.i(TAG, "Parsing GGUF metadata...")
            contentResolver.openInputStream(uri)?.use {
                GgufMetadataReader.create().readStructuredMetadata(it)
            }?.let { metadata ->
                // Update UI to show GGUF metadata to user
                Log.i(TAG, "GGUF parsed: \n$metadata")
                withContext(Dispatchers.Main) {
                    ggufTv.text = metadata.toString()
                }

                // Ensure the model file is available
                val modelName = metadata.filename() + FILE_EXTENSION_GGUF
                contentResolver.openInputStream(uri)?.use { input ->
                    ensureModelFile(modelName, input)
                }?.let { modelFile ->
                    loadModel(modelName, modelFile)

                    withContext(Dispatchers.Main) {
                        isModelReady = true
                        userInputEt.hint = "Type and send a message!"
                        userInputEt.isEnabled = true
                        userActionFab.setImageResource(R.drawable.outline_send_24)
                        userActionFab.isEnabled = true
                    }
                }
            }
        }
    }

    /**
     * Prepare the model file within app's private storage
     */
    private suspend fun ensureModelFile(modelName: String, input: InputStream) =
        withContext(Dispatchers.IO) {
            File(ensureModelsDirectory(), modelName).also { file ->
                // Copy the file into local storage if not yet done
                if (!file.exists()) {
                    Log.i(TAG, "Start copying file to $modelName")
                    withContext(Dispatchers.Main) {
                        userInputEt.hint = "Copying file..."
                    }

                    FileOutputStream(file).use { input.copyTo(it) }
                    Log.i(TAG, "Finished copying file to $modelName")
                } else {
                    Log.i(TAG, "File already exists $modelName")
                }
            }
        }

    /**
     * Load the model file from the app private storage
     */
    private suspend fun loadModel(modelName: String, modelFile: File) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Loading model $modelName")
            withContext(Dispatchers.Main) {
                userInputEt.hint = "Loading model..."
            }
            engine.loadModel(modelFile.path)
        }

    /**
     * Validate and send the user message into [InferenceEngine]
     */
    private fun handleUserInput() {
        val userMsg = userInputEt.text?.toString()?.trim().orEmpty()
        if (userMsg.isEmpty()) {
            Toast.makeText(this, "Input message is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        generationCancelledByUser = false
        userInputEt.text = null
        userInputEt.isEnabled = false
        userActionFab.isEnabled = false

        // Snapshot history BEFORE we add the current user message.
        val historySnapshot = messages.toList()

        // Update message states
        messages.add(Message(UUID.randomUUID().toString(), userMsg, true))
        lastAssistantMsg.clear()
        messages.add(Message(UUID.randomUUID().toString(), "", false))
        messageAdapter.notifyItemInserted(messages.size - 1)

        val thinkingMode = thinkingModeCb.isChecked
        val internetSearch = internetSearchCb.isChecked

        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            val internetContext = if (internetSearch) {
                duckDuckGoSearch(userMsg)
            } else {
                ""
            }

            val prompt = buildUserPrompt(
                history = historySnapshot,
                lastUser = userMsg,
                thinkingMode = thinkingMode,
                internetContext = internetContext
            )

            val rawAssistant = StringBuilder()
            try {
                engine.sendUserPrompt(prompt).collect { token ->
                    rawAssistant.append(token)
                    lastAssistantMsg.setLength(0)
                    lastAssistantMsg.append(rawAssistant.toString())

                    withContext(Dispatchers.Main) {
                        val messageCount = messages.size
                        check(messageCount > 0 && !messages[messageCount - 1].isUser)
                        messages.removeAt(messageCount - 1).copy(
                            content = rawAssistant.toString()
                        ).let { messages.add(it) }
                        messageAdapter.notifyItemChanged(messages.size - 1)
                    }
                }
            } catch (_: CancellationException) {
                // ignore
            } finally {
                withContext(Dispatchers.Main) {
                    if (generationCancelledByUser) {
                        messages.removeAt(messages.size - 1).copy(content = "Отменено").also {
                            messages.add(it)
                        }
                        messageAdapter.notifyItemChanged(messages.size - 1)
                    } else if (thinkingMode) {
                        val raw = rawAssistant.toString()
                        val parsed = parseThinkingTags(raw)
                        val display = if (parsed.notes.isNotBlank()) {
                            "Заметки:\n${parsed.notes}\n\nОтвет:\n${parsed.answer}"
                        } else {
                            "Ответ:\n${parsed.answer}"
                        }
                        messages.removeAt(messages.size - 1).copy(content = display).also {
                            messages.add(it)
                        }
                        messageAdapter.notifyItemChanged(messages.size - 1)
                    }

                    userInputEt.isEnabled = true
                    userActionFab.isEnabled = true
                }
            }
        }
    }

    private fun parseThinkingTags(raw: String): ThinkingParsed {
        val text = raw.trim()
        if (text.isEmpty()) return ThinkingParsed("", "")

        val notesMatch = Regex("(?is)<notes>(.*?)</notes>").find(text)
        val answerMatch = Regex("(?is)<answer>(.*?)</answer>").find(text)

        val notes = notesMatch?.groupValues?.get(1)?.trim().orEmpty()
        val answerInside = answerMatch?.groupValues?.get(1)?.trim().orEmpty()
        val cleaned = text
            .replace(Regex("(?is)<notes>.*?</notes>"), "")
            .replace(Regex("(?is)</?answer>"), "")
            .trim()

        return ThinkingParsed(
            notes = notes,
            answer = if (answerInside.isNotBlank()) answerInside else cleaned
        )
    }

    private data class ThinkingParsed(val notes: String, val answer: String)

    private fun buildUserPrompt(
        history: List<Message>,
        lastUser: String,
        thinkingMode: Boolean,
        internetContext: String
    ): String {
        val sb = StringBuilder()

        if (thinkingMode) {
            sb.append(
                """
                Режим "Думающий".
                Сначала дай краткие заметки, что собираешься сделать, внутри <notes>...</notes> (до 400 символов).
                Затем дай окончательный ответ внутри <answer>...</answer>.
                Не раскрывай пошаговую внутреннюю цепочку рассуждений.
                
                """.trimIndent()
            )
        }

        if (internetContext.isNotBlank()) {
            sb.append("Интернет контекст:\n")
            sb.append(internetContext.trim())
            sb.append("\n\n")
        }

        sb.append("История диалога:\n")
        for (m in history) {
            val role = if (m.isUser) "User" else "Assistant"
            sb.append(role).append(": ").append(m.content).append('\n')
        }
        sb.append("User: ").append(lastUser).append('\n')
        sb.append("Assistant:")

        return sb.toString()
    }

    private suspend fun duckDuckGoSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return ""

        val url = "https://api.duckduckgo.com/?q=${Uri.encode(q)}&format=json&no_redirect=1&no_html=1"
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "WourdChatAndroid")
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                if (conn.responseCode != 200) return@withContext ""

                val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
                val json = JSONObject(body)

                val abs = json.optString("AbstractText", "").trim()
                // RelatedTopics can be array of objects or {Text,FirstURL,...}
                val relatedTopics = json.optJSONArray("RelatedTopics")
                val related = mutableListOf<String>()
                if (relatedTopics != null) {
                    fun walk(arr: org.json.JSONArray?) {
                        if (arr == null) return
                        for (i in 0 until arr.length()) {
                            val item = arr.opt(i)
                            if (item is JSONObject) {
                                val text = item.optString("Text", "").trim()
                                if (text.isNotEmpty()) related.add(text)
                                val topics = item.optJSONArray("Topics")
                                if (topics != null && related.size < 4) walk(topics)
                            }
                            if (related.size >= 4) return
                        }
                    }
                    walk(relatedTopics)
                }

                val parts = mutableListOf<String>()
                if (abs.isNotBlank()) parts.add(abs)
                if (related.isNotEmpty()) {
                    parts.add("Похожие темы:\n" + related.map { "- $it" }.joinToString("\n"))
                }
                parts.joinToString("\n\n").trim()
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Run a benchmark with the model file
     */
    @Deprecated("This benchmark doesn't accurately indicate GUI performance expected by app developers")
    private suspend fun runBenchmark(modelName: String, modelFile: File) =
        withContext(Dispatchers.Default) {
            Log.i(TAG, "Starts benchmarking $modelName")
            withContext(Dispatchers.Main) {
                userInputEt.hint = "Running benchmark..."
            }
            engine.bench(
                pp=BENCH_PROMPT_PROCESSING_TOKENS,
                tg=BENCH_TOKEN_GENERATION_TOKENS,
                pl=BENCH_SEQUENCE,
                nr=BENCH_REPETITION
            ).let { result ->
                messages.add(Message(UUID.randomUUID().toString(), result, false))
                withContext(Dispatchers.Main) {
                    messageAdapter.notifyItemChanged(messages.size - 1)
                }
            }
        }

    /**
     * Create the `models` directory if not exist.
     */
    private fun ensureModelsDirectory() =
        File(filesDir, DIRECTORY_MODELS).also {
            if (it.exists() && !it.isDirectory) { it.delete() }
            if (!it.exists()) { it.mkdir() }
        }

    override fun onStop() {
        generationJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        // `engine` may fail to initialize; avoid crash on Activity teardown.
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val DIRECTORY_MODELS = "models"
        private const val FILE_EXTENSION_GGUF = ".gguf"
        private const val BASE_SYSTEM_PROMPT = "Ты полезный ассистент Wourd. Отвечай по делу и аккуратно."

        private const val BENCH_PROMPT_PROCESSING_TOKENS = 512
        private const val BENCH_TOKEN_GENERATION_TOKENS = 128
        private const val BENCH_SEQUENCE = 1
        private const val BENCH_REPETITION = 3
    }
}

fun GgufMetadata.filename() = when {
    basic.name != null -> {
        basic.name?.let { name ->
            basic.sizeLabel?.let { size ->
                "$name-$size"
            } ?: name
        }
    }
    architecture?.architecture != null -> {
        architecture?.architecture?.let { arch ->
            basic.uuid?.let { uuid ->
                "$arch-$uuid"
            } ?: "$arch-${System.currentTimeMillis()}"
        }
    }
    else -> {
        "model-${System.currentTimeMillis().toHexString()}"
    }
}
