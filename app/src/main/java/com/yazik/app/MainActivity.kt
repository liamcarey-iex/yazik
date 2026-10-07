package com.yazik.app

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yazik.app.data.Conversation
import com.yazik.app.data.PracticeMessage
import com.yazik.app.data.PracticeMode
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val Ink = Color(0xFF19352D)
private val Leaf = Color(0xFF397761)
private val Cream = Color(0xFFF6F4EC)
private val PaleGreen = Color(0xFFE5EFE8)
private val Muted = Color(0xFF65756D)
private val Coral = Color(0xFFD36F57)

class MainActivity : ComponentActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[YazikViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YazikTheme { YazikApp(viewModel) } }
    }
}

@Composable
private fun YazikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Leaf,
            onPrimary = Color.White,
            secondary = Coral,
            background = Cream,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink,
            outline = Color(0xFFD5DDD5)
        ),
        content = content
    )
}

@Composable
private fun YazikApp(viewModel: YazikViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when (state.screen) {
            AppScreen.HOME -> HomeScreen(
                state = state,
                onSettings = viewModel::openSettings,
                onStart = viewModel::startConversation,
                onResume = viewModel::resumeConversation,
                modifier = Modifier.padding(padding)
            )
            AppScreen.SETTINGS -> SettingsScreen(
                hasKey = state.hasApiKey,
                onBack = viewModel::openHome,
                onSave = viewModel::saveApiKey,
                onDelete = viewModel::deleteApiKey,
                modifier = Modifier.padding(padding)
            )
            AppScreen.CHAT -> ChatScreen(
                state = state,
                onBack = viewModel::finishConversation,
                onSend = viewModel::sendText,
                onVoice = viewModel::sendVoice,
                onSpeak = viewModel::createSpeech,
                onError = viewModel::showError,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: YazikUiState,
    onSettings: () -> Unit,
    onStart: (PracticeMode) -> Unit,
    onResume: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BrandMark()
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Ink)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("A little Russian,\nevery day.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink)
            Text("Friendly practice, at your pace.", style = MaterialTheme.typography.bodyLarge, color = Muted)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = PaleGreen),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Your progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${state.progress.practiceTurns} practice turns", color = Muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.progress.completedLessons.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Leaf)
                    Text("lessons", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Choose how to practice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PracticeModeCard(
                title = "Guided lesson",
                description = "Build from the basics with short explanations and gentle corrections.",
                eyebrow = "START HERE",
                onClick = { onStart(PracticeMode.GUIDED) }
            )
            PracticeModeCard(
                title = "Free conversation",
                description = "Pick a topic and chat in simple Russian, with help when you need it.",
                eyebrow = "YOUR TOPIC, YOUR PACE",
                onClick = { onStart(PracticeMode.FREE) }
            )
        }

        if (state.conversations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Leaf, modifier = Modifier.size(19.dp))
                    Text("Recent practice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                state.conversations.take(8).forEach { conversation ->
                    HistoryRow(conversation = conversation, onClick = { onResume(conversation) })
                }
            }
        }

        Text("Powered by OpenAI · API usage is billed to your API account", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(bottom = 20.dp))
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(38.dp).background(Leaf, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("я", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        Text("yazik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun PracticeModeCard(title: String, description: String, eyebrow: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(eyebrow, color = Leaf, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Text("Start practicing  →", style = MaterialTheme.typography.labelLarge, color = Leaf, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HistoryRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(conversation.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(if (conversation.completedAt == null) "In progress" else "Completed", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Leaf)
    }
}

@Composable
private fun SettingsScreen(
    hasKey: Boolean,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var key by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        ScreenHeader(title = "Settings", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Leaf)
                        Text("OpenAI API key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (hasKey) "A key is saved securely on this phone. Enter a new one to replace it."
                        else "Add your API key to let Yazik talk directly to OpenAI.",
                        color = Muted
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (hasKey) "Replace API key" else "API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    Button(
                        onClick = { onSave(key); key = "" },
                        enabled = key.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save key") }
                    if (hasKey) {
                        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("Remove saved key", color = Coral) }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("A note about keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Your key is encrypted on this phone and is never included in the app build. A key used by a mobile app can still be extracted from a compromised device. Use a key for personal use, set API spending limits in your OpenAI account, and revoke it if you lose your phone.",
                        color = Muted
                    )
                    TextButton(onClick = { uriHandler.openUri("https://platform.openai.com/api-keys") }) {
                        Text("Manage API keys and usage  ↗")
                    }
                }
            }
            Text("Tutor model: GPT-6 Luna", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, action: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        action?.invoke()
    }
}

@Composable
private fun ChatScreen(
    state: YazikUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onVoice: (File) -> Unit,
    onSpeak: suspend (String) -> File,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(state.conversationId) { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var speakingMessage by remember { mutableStateOf<String?>(null) }
    var autoPlayReplies by rememberSaveable(state.conversationId) { mutableStateOf(false) }
    var lastAutoPlayedAssistantId by remember(state.conversationId) {
        mutableStateOf(state.messages.lastOrNull { it.role == "assistant" }?.id)
    }

    LaunchedEffect(state.messages.lastOrNull()?.id, state.busy, autoPlayReplies) {
        val latestAssistant = state.messages.lastOrNull { it.role == "assistant" }
        if (!state.busy && latestAssistant != null && latestAssistant.id != lastAutoPlayedAssistantId) {
            lastAutoPlayedAssistantId = latestAssistant.id
            if (autoPlayReplies) {
                speakingMessage = latestAssistant.id
                try {
                    val audio = onSpeak(latestAssistant.content)
                    playAudio(context, audio) { speakingMessage = null }
                } catch (error: Exception) {
                    speakingMessage = null
                    onError(error.message ?: "Couldn't play this reply.")
                }
            }
        }
    }

    fun finishRecording() {
        try {
            val file = recorder?.stop()
            recorder = null
            recording = false
            if (file != null && file.length() > 0L) onVoice(file)
            else onError("No audio was recorded. Try again.")
        } catch (_: RuntimeException) {
            recorder?.cancel()
            recorder = null
            recording = false
            onError("The recording was too short. Try again.")
        }
    }

    LaunchedEffect(recording, recorder) {
        val activeRecorder = recorder ?: return@LaunchedEffect
        var heardSpeech = false
        var silenceMs = 0
        while (recording) {
            delay(100)
            if (activeRecorder.hasVoiceActivity()) {
                heardSpeech = true
                silenceMs = 0
            } else if (heardSpeech) {
                silenceMs += 100
                if (silenceMs >= 3_000) {
                    finishRecording()
                    break
                }
            }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording(context, onError) { newRecorder -> recorder = newRecorder; recording = true }
        else onError("Microphone permission is needed for voice practice. You can still type instead.")
    }

    Column(modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = state.mode.label,
            onBack = onBack,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { autoPlayReplies = !autoPlayReplies }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (autoPlayReplies) Leaf else Muted)
                        Text(if (autoPlayReplies) "Auto-play on" else "Auto-play", color = if (autoPlayReplies) Leaf else Muted)
                    }
                    TextButton(onClick = onBack) { Text("Finish", color = Leaf) }
                }
            }
        )
        Divider(color = Color(0xFFE5E8E1))

        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.busy) CircularProgressIndicator(color = Leaf)
                    else Text("Say привет or type a message to begin.", color = Muted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = PracticeMessage::id) { message ->
                    MessageBubble(
                        message = message,
                        speaking = speakingMessage == message.id,
                        onSpeak = {
                            speakingMessage = message.id
                            scope.launch {
                                try {
                                    val audio = onSpeak(message.content)
                                    playAudio(context, audio) { speakingMessage = null }
                                } catch (error: Exception) {
                                    speakingMessage = null
                                    onError(error.message ?: "Couldn't play this reply.")
                                }
                            }
                        }
                    )
                }
                if (state.busy) item(key = "thinking") { ThinkingIndicator() }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write in Russian or English…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!state.busy && draft.isNotBlank()) { onSend(draft); draft = "" } })
            )
            IconButton(
                onClick = {
                    if (recording) {
                        finishRecording()
                    } else if (!state.busy) {
                        permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.size(48.dp).background(if (recording) Coral else PaleGreen, CircleShape)
            ) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = if (recording) "Stop recording and send" else "Record a Russian reply", tint = if (recording) Color.White else Leaf)
            }
            IconButton(
                onClick = { onSend(draft); draft = "" },
                enabled = draft.isNotBlank() && !state.busy,
                modifier = Modifier.size(48.dp).background(Leaf, CircleShape)
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White) }
        }
        Text("Recording stops after 3 seconds of silence · audio is sent to OpenAI for transcription", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.fillMaxWidth().background(Color.White).padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun MessageBubble(message: PracticeMessage, speaking: Boolean, onSpeak: () -> Unit) {
    val isTutor = message.role == "assistant"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isTutor) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier.fillMaxWidth(0.88f)
                .background(if (isTutor) Color.White else PaleGreen, RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(if (isTutor) "YAZIK" else "YOU", color = if (isTutor) Leaf else Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(message.content, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (isTutor) {
                TextButton(onClick = onSpeak, enabled = !speaking, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text(if (speaking) "  Preparing audio…" else "  Listen")
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Leaf)
        Text("Thinking…", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun startRecording(context: Context, onError: (String) -> Unit, onStarted: (AudioRecorder) -> Unit) {
    val file = File(context.cacheDir, "yazik-${System.currentTimeMillis()}.m4a")
    val audioRecorder = AudioRecorder(context.applicationContext, file)
    runCatching { audioRecorder.start() }
        .onSuccess { onStarted(audioRecorder) }
        .onFailure {
            audioRecorder.cancel()
            onError("Couldn't start recording. Check microphone access and try again.")
        }
}

private fun playAudio(context: Context, file: File, onFinished: () -> Unit) {
    val player = MediaPlayer()
    player.setOnCompletionListener { it.release(); file.delete(); onFinished() }
    player.setOnErrorListener { mp, _, _ -> mp.release(); file.delete(); onFinished(); true }
    player.setOnPreparedListener { it.start() }
    runCatching {
        player.setDataSource(file.absolutePath)
        player.prepareAsync()
    }.onFailure {
        player.release()
        file.delete()
        onFinished()
        throw it
    }
}
