package com.example.unilink.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.unilink.repository.CampusLiveStream
import com.example.unilink.repository.LiveStreamRepository
import com.example.unilink.repository.StreamChatMessage
import com.example.unilink.webrtc.WebRtcClient
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

fun shareLiveStreamLink(context: Context, streamTitle: String, broadcasterName: String, streamId: String) {
    val shareText = "🔴 Join my live broadcast on Newton's Lobby! \"$streamTitle\" hosted by $broadcasterName.\n\nWatch live now: https://newtonslobby.app/stream/$streamId"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Newton's Lobby Live Stream 📡")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Live Stream via..."))
}

data class FloatingReaction(
    val id: Long,
    val emoji: String,
    val startX: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamScreen(
    streamTitle: String = "Campus Broadcast 🎙️",
    broadcasterName: String = "Host",
    webRtcClient: WebRtcClient? = null,
    remoteVideoTrack: org.webrtc.VideoTrack? = null,
    isHost: Boolean = true,
    initialStream: CampusLiveStream? = null,
    initialShowCreateDialog: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onStartBroadcasting: (title: String, description: String, isPrivate: Boolean, streamId: String) -> Unit = { _, _, _, _ -> },
    onLocalVideoReady: () -> Unit = {},
    onJoinStream: (stream: CampusLiveStream) -> Unit = {},
    onWatchReplays: () -> Unit = {},
    onEndStream: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { LiveStreamRepository() }

    var showCreateStreamDialog by remember { mutableStateOf(initialShowCreateDialog) }
    var activeStreams by remember { mutableStateOf<List<CampusLiveStream>>(emptyList()) }
    var currentActiveStream by remember { mutableStateOf<CampusLiveStream?>(initialStream) }

    // Observe Active Campus Streams in Real-Time
    DisposableEffect(Unit) {
        val listener = repository.observeActiveStreams { streams ->
            activeStreams = streams
        }
        onDispose {
            listener.remove()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasCameraPermission = cameraGranted && audioGranted
        if (hasCameraPermission) {
            showCreateStreamDialog = true
        } else {
            Toast.makeText(context, "Camera and Microphone permissions are required to go live!", Toast.LENGTH_LONG).show()
        }
    }

    var isLiveStreamingActive by remember(currentActiveStream, isHost, webRtcClient, remoteVideoTrack) {
        mutableStateOf(currentActiveStream != null || (isHost && webRtcClient != null) || (!isHost && remoteVideoTrack != null))
    }
    var isMicMuted by remember { mutableStateOf(false) }

    var chatInputText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<StreamChatMessage>() }
    val activeStreamId = currentActiveStream?.streamId.orEmpty()

    // Observe Live Chat if in an active stream
    DisposableEffect(activeStreamId) {
        if (activeStreamId.isNotEmpty()) {
            val listener = repository.observeStreamChat(activeStreamId) { msgs ->
                chatMessages.clear()
                chatMessages.addAll(msgs)
            }
            onDispose { listener?.remove() }
        } else {
            onDispose { }
        }
    }

    val floatingReactions = remember { mutableStateListOf<FloatingReaction>() }
    val chatListState = rememberLazyListState()

    // Observe Real-Time Stream Reactions from Firestore
    DisposableEffect(activeStreamId) {
        if (activeStreamId.isNotEmpty()) {
            val listener = repository.observeStreamReactions(activeStreamId) { reaction ->
                floatingReactions.add(
                    FloatingReaction(
                        id = System.currentTimeMillis() + (1..10000).random(),
                        emoji = reaction.emoji,
                        startX = reaction.startX
                    )
                )
            }
            onDispose { listener?.remove() }
        } else {
            onDispose { }
        }
    }

    // Automatically notify viewer if stream ended by host
    LaunchedEffect(activeStreams, currentActiveStream) {
        if (!isHost && currentActiveStream != null) {
            val streamStillActive = activeStreams.any { it.streamId == currentActiveStream?.streamId }
            if (activeStreams.isNotEmpty() && !streamStillActive) {
                Toast.makeText(context, "Stream has ended 📡", Toast.LENGTH_SHORT).show()
                currentActiveStream = null
                isLiveStreamingActive = false
                onEndStream()
            }
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
    ) {
        if (!isLiveStreamingActive && currentActiveStream == null) {
            // Live Gateway Screen with 2 Choice Option Boxes
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Campus Live Stage 📡", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Real-Time Campus Video Broadcasting", color = Color(0xFF06B6D4), fontSize = 11.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Option 1: Start Streaming Box Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasCameraPermission) {
                                        showCreateStreamDialog = true
                                    } else {
                                        permissionLauncher.launch(
                                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0x332C0A22),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEC4899))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("🎥 Option 1: Start Streaming", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEC4899)) {
                                        Text("STREAMER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Broadcast your live camera & stereo microphone feed to campus peers in real-time!", color = Color.White.copy(0.8f), fontSize = 13.sp)

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (hasCameraPermission) {
                                            showCreateStreamDialog = true
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Streaming 🎥", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Option 2: Start Watching Box Card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0x330A2E2A),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF06B6D4))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("👁️ Option 2: Start Watching", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0x3310B981)) {
                                        Text("${activeStreams.size} LIVE NOW", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Browse active campus broadcasts and join live stream video feeds as a watcher!", color = Color.White.copy(0.8f), fontSize = 13.sp)

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = onWatchReplays,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3306B6D4)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Watch Replays & Vault Files 📚", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Active Live Streams Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔴 Active Streams Available", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x3310B981)
                            ) {
                                Text("${activeStreams.size} ACTIVE", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (activeStreams.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0x1A000000),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(44.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No Live Streams Currently Active 📡", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No one is broadcasting right now. Tap 'Start Streaming' above to go live!", color = Color.White.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    } else {
                        items(activeStreams, key = { it.streamId }) { stream ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentActiveStream = stream
                                        onJoinStream(stream)
                                    },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0x26160F38),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFEF4444)
                                            ) {
                                                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stream.hostName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text("👀 ${stream.viewerCount} Viewers", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stream.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                                    if (stream.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(stream.description, color = Color.White.copy(0.7f), fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                currentActiveStream = stream
                                                onJoinStream(stream)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Join Stream 👁️", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        IconButton(
                                            onClick = {
                                                shareLiveStreamLink(context, stream.title, stream.hostName, stream.streamId)
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x3306B6D4))
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share Stream", tint = Color(0xFF06B6D4), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Active Live Video Broadcast Stage View
            Box(modifier = Modifier.fillMaxSize()) {
                if (isHost && webRtcClient != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(webRtcClient.getEglContext(), null)
                                setEnableHardwareScaler(true)
                                setMirror(true)
                                setZOrderMediaOverlay(false)
                                setZOrderOnTop(false)
                                webRtcClient.startLocalVideo(this) {
                                    onLocalVideoReady()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!isHost && remoteVideoTrack != null) {
                    androidx.compose.runtime.DisposableEffect(remoteVideoTrack) {
                        onDispose {
                            try {
                                remoteVideoTrack.removeSink(null)
                            } catch (_: Exception) {}
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                val eglContext = webRtcClient?.getEglContext() ?: org.webrtc.EglBase.create().eglBaseContext
                                init(eglContext, null)
                                setEnableHardwareScaler(false)
                                setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                setMirror(false)
                                setZOrderMediaOverlay(true)
                                remoteVideoTrack.addSink(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF030712))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x3306B6D4),
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF06B6D4)),
                                modifier = Modifier.size(90.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(44.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(currentActiveStream?.hostName ?: broadcasterName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("1080p60 • Connecting Live Video Stream...", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                        .zIndex(10f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isHost) {
                                    currentActiveStream?.streamId?.let { repository.endLiveStream(it) }
                                }
                                currentActiveStream = null
                                isLiveStreamingActive = false
                                onEndStream()
                            },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0x4D000000))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x99EF4444),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(currentActiveStream?.title ?: streamTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x66000000),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Text("👀 ${currentActiveStream?.viewerCount ?: 1} Viewers", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val title = currentActiveStream?.title ?: streamTitle
                                val host = currentActiveStream?.hostName ?: broadcasterName
                                val sid = activeStreamId
                                shareLiveStreamLink(context, title, host, sid)
                            },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0x66000000))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Stream", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Floating Reactions
                floatingReactions.forEach { reaction ->
                    key(reaction.id) {
                        FloatingEmoji(
                            emoji = reaction.emoji,
                            startX = reaction.startX,
                            onAnimationEnd = { floatingReactions.remove(reaction) }
                        )
                    }
                }

                // Bottom Controls & Live Chat Overlay
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(20f)
                ) {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(chatMessages, key = { it.id }) { chat ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0x800F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${chat.senderName}: ",
                                        color = if (chat.isVip) Color(0xFFFACC15) else Color(0xFF06B6D4),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(chat.messageText, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Emoji Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val emojis = listOf("❤️", "🔥", "👏", "🚀", "👑")
                        emojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66000000))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                    .clickable {
                                        floatingReactions.add(
                                            FloatingReaction(
                                                id = System.currentTimeMillis() + (1..1000).random(),
                                                emoji = emoji,
                                                startX = (100..800).random().toFloat()
                                            )
                                        )
                                        if (activeStreamId.isNotEmpty()) {
                                            repository.sendStreamReaction(activeStreamId, emoji)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    // Chat Input + Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Say something...", color = Color.White.copy(0.6f), fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0x990F172A),
                                unfocusedContainerColor = Color(0x800F172A),
                                focusedBorderColor = Color(0xFF06B6D4),
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (chatInputText.isNotBlank()) {
                                            if (activeStreamId.isNotEmpty()) {
                                                repository.sendStreamChatMessage(activeStreamId, chatInputText)
                                            }
                                            chatInputText = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF06B6D4))
                                }
                            }
                        )

                        if (isHost) {
                            // Mic Mute
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isMicMuted) Color(0xFFEF4444) else Color(0x80000000))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                    .clickable {
                                        isMicMuted = !isMicMuted
                                        webRtcClient?.toggleAudio()
                                        Toast.makeText(context, if (isMicMuted) "Muted 🔇" else "Unmuted 🎙️", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            // Switch Camera
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x80000000))
                                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                    .clickable {
                                        webRtcClient?.switchCamera()
                                        Toast.makeText(context, "Camera Switched 🔄", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cameraswitch, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }

                        // Exit Stream
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .clickable {
                                    if (isHost) {
                                        currentActiveStream?.streamId?.let { repository.endLiveStream(it) }
                                    }
                                    isLiveStreamingActive = false
                                    currentActiveStream = null
                                    onEndStream()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }

    // Create Live Stream Dialog (Form for Title, Description & Privacy)
    if (showCreateStreamDialog) {
        var inputTitle by remember { mutableStateOf("") }
        var inputDescription by remember { mutableStateOf("") }
        var isPrivateSelected by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateStreamDialog = false },
            containerColor = Color(0xFF160F38),
            title = {
                Text("🎥 Start Campus Live Stream", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter stream title, description, and privacy setting:", color = Color.White.copy(0.7f), fontSize = 12.sp)

                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Stream Title (e.g. Late Night Coding 🚀)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4), unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputDescription,
                        onValueChange = { inputDescription = it },
                        label = { Text("Description / Topic") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF06B6D4), unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Audience Privacy:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { isPrivateSelected = false }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isPrivateSelected,
                            onClick = { isPrivateSelected = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF06B6D4))
                        )
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Public Campus Stream", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Visible to everyone on campus feed", color = Color.White.copy(0.6f), fontSize = 10.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { isPrivateSelected = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isPrivateSelected,
                            onClick = { isPrivateSelected = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEC4899))
                        )
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Private Class / Team Stream", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Accessible only via direct invite", color = Color.White.copy(0.6f), fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTitle.isNotBlank()) {
                            showCreateStreamDialog = false
                            repository.publishLiveStream(inputTitle, inputDescription, isPrivateSelected) { success, newStreamId ->
                                if (success && newStreamId != null) {
                                    currentActiveStream = CampusLiveStream(
                                        streamId = newStreamId,
                                        title = inputTitle,
                                        description = inputDescription,
                                        hostName = broadcasterName,
                                        isLive = true
                                    )
                                    isLiveStreamingActive = true
                                    onStartBroadcasting(inputTitle, inputDescription, isPrivateSelected, newStreamId)
                                    Toast.makeText(context, "You are now LIVE on Campus! 🎥", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to start live stream", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter a stream title", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                ) {
                    Text("Go Live Now 🚀", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateStreamDialog = false }) {
                    Text("Cancel", color = Color.White.copy(0.7f))
                }
            }
        )
    }
}

@Composable
fun FloatingEmoji(
    emoji: String,
    startX: Float,
    onAnimationEnd: () -> Unit
) {
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            offsetY.animateTo(
                targetValue = -600f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
            onAnimationEnd()
        }
    }

    Text(
        text = emoji,
        fontSize = 28.sp,
        modifier = Modifier
            .offset(x = startX.dp, y = (1400f + offsetY.value).dp)
    )
}
