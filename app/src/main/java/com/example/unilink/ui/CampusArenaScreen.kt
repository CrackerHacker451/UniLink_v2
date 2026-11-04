package com.example.unilink.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

// Standard Native App Design Palette
val AppSlateDark = Color(0xFF0B132B)
val AppSlateBg = Color(0xFF0F172A)
val AppCyanAccent = Color(0xFF38BDF8)
val AppPinkAccent = Color(0xFFEC4899)
val AppGoldAccent = Color(0xFFF59E0B)
val AppGlassCardBg = Color(0x261E293B)
val AppGlassBorder = Color(0x3338BDF8)

// Compatibility Aliases
val AuraVioletDark = AppSlateDark
val AuraDeepObsidian = AppSlateBg
val AuraCyanNeon = AppCyanAccent
val AuraPinkNeon = AppPinkAccent
val AuraGoldNeon = AppGoldAccent
val AuraGlassCardBg = AppGlassCardBg
val AuraGlassBorder = AppGlassBorder

val ArenaDeepSpace = AppSlateBg
val ArenaVioletDark = AppSlateDark
val ArenaNeonCyan = AppCyanAccent
val ArenaNeonPink = AppPinkAccent
val ArenaNeonGold = AppGoldAccent
val ArenaNeonPurple = Color(0xFFA855F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusArenaScreen(
    onNavigateBack: () -> Unit = {},
    onOpenRandomMatch: () -> Unit = {},
    onOpenCampusRadar: () -> Unit = {},
    onOpenAudioRoom: () -> Unit = {},
    onOpenDareDrop: () -> Unit = {},
    onOpenBarterBlock: () -> Unit = {},
) {
    val context = LocalContext.current

    var showPostTradeDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }

    var dareCompleted by remember { mutableStateOf(false) }
    var dareCompletedCount by remember { mutableIntStateOf(142) }

    val tradesList = remember {
        mutableStateListOf(
            "🔄 Java & DSA Notes ➔ ☕ Energy Drink / Coffee",
            "👟 Gym Straps ➔ 🎧 AirPods Case",
            "📐 Casio Scientific Calculator ➔ 🍕 Pizza Slice",
            "📚 Physics Formula Sheet ➔ ⚡ Power Bank"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppSlateBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Campus Arena ⚡",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Campus Hub • Broadcasts, Dares & Swaps",
                            color = AppCyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x3338BDF8),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppCyanAccent)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ARENA LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
            ) {
                // Arena Quick Shortcuts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenRandomMatch() },
                            color = Color(0x33EC4899),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppPinkAccent)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("⚡ SpeedLink", color = AppPinkAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("1080p Peer Connect", color = Color.White.copy(0.8f), fontSize = 11.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenCampusRadar() },
                            color = Color(0x3338BDF8),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppCyanAccent)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📡 Campus Radar", color = AppCyanAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Scan Active Radius", color = Color.White.copy(0.8f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Zone 1: The 11:59 Zone
                item {
                    Zone1MidnightMicCard(onOpenAudioRoom = onOpenAudioRoom)
                }

                // Zone 2: DareDrop Arena
                item {
                    Zone2DareDropCard(
                        dareCompleted = dareCompleted,
                        completedCount = dareCompletedCount,
                        onOpenDareDrop = onOpenDareDrop
                    )
                }

                // Zone 3: Barter Block
                item {
                    Zone3BarterBlockCard(
                        trades = tradesList,
                        onOpenBarterBlock = onOpenBarterBlock,
                        onPostTrade = { showPostTradeDialog = true }
                    )
                }

                // Zone 4: Roast & Toast
                item {
                    Zone4RoastAndToastCard(
                        onPlayPoll = { showPollDialog = true }
                    )
                }
            }
        }
    }

    if (showPostTradeDialog) {
        var offeringText by remember { mutableStateOf("") }
        var wantingText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPostTradeDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("🔄 Post a Barter Trade", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trade goods or services with campus peers (Cashless):", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    OutlinedTextField(
                        value = offeringText,
                        onValueChange = { offeringText = it },
                        label = { Text("Offering (e.g. Java Notes)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppCyanAccent,
                            unfocusedBorderColor = Color.White.copy(0.3f)
                        )
                    )
                    OutlinedTextField(
                        value = wantingText,
                        onValueChange = { wantingText = it },
                        label = { Text("Looking for (e.g. Coffee / Headphones)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppCyanAccent,
                            unfocusedBorderColor = Color.White.copy(0.3f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (offeringText.isNotEmpty() && wantingText.isNotEmpty()) {
                            tradesList.add(0, "🔄 $offeringText ➔ $wantingText")
                            showPostTradeDialog = false
                            Toast.makeText(context, "Trade posted to live Barter Block! +10 UniCoins 🏆", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppCyanAccent)
                ) {
                    Text("Post Live Trade", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostTradeDialog = false }) {
                    Text("Cancel", color = Color.White.copy(0.7f))
                }
            }
        )
    }

    if (showPollDialog) {
        var selectedOption by remember { mutableStateOf<Int?>(null) }

        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("🔥 Roast & Toast Poll", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Who is most likely to build a startup before graduation? 🚀", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    val options = listOf("A. Aryan Sharma (CS)", "B. Sneha Reddy (AI)", "C. You!", "D. Lalit (Android)")
                    val percentages = listOf("42%", "28%", "20%", "10%")

                    options.forEachIndexed { idx, opt ->
                        val isSelected = (selectedOption == idx)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedOption = idx },
                            color = if (isSelected) AppGoldAccent else Color(0x33FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AppGoldAccent else Color(0x33FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(opt, color = if (isSelected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (selectedOption != null) {
                                    Text(percentages[idx], color = if (isSelected) Color.Black else AppCyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedOption != null) {
                            showPollDialog = false
                            Toast.makeText(context, "Vote Submitted! +5 UniCoins earned 🏆", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Select an option to vote!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppGoldAccent)
                ) {
                    Text("Submit Vote", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) {
                    Text("Close", color = Color.White.copy(0.7f))
                }
            }
        )
    }
}

@Composable
fun Zone1MidnightMicCard(
    onOpenAudioRoom: () -> Unit
) {
    val context = LocalContext.current
    var isNotified by remember { mutableStateOf(false) }
    var forceDemoUnlocked by remember { mutableStateOf(false) }

    var timeRemainingMillis by remember { mutableStateOf(calculateMillisToMidnight()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            timeRemainingMillis = calculateMillisToMidnight()
        }
    }

    val isUnlocked = forceDemoUnlocked || timeRemainingMillis <= 0L

    val hours = TimeUnit.MILLISECONDS.toHours(timeRemainingMillis) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMillis) % 60
    val timerString = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    AuraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isUnlocked) AppPinkAccent else AppGlassBorder,
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33A855F7)
                ) {
                    Text(
                        text = "🎙️ THE 11:59 ZONE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (forceDemoUnlocked) Color(0x3310B981) else Color(0x33FFFFFF),
                        modifier = Modifier.clickable {
                            forceDemoUnlocked = !forceDemoUnlocked
                        }
                    ) {
                        Text(
                            text = if (forceDemoUnlocked) "LIVE DEMO" else "DEMO UNLOCK",
                            color = if (forceDemoUnlocked) Color(0xFF10B981) else Color.White.copy(0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            isNotified = !isNotified
                            Toast.makeText(
                                context,
                                if (isNotified) "Reminder set for 11:59 PM Midnight Broadcast! 🔔" else "Reminder cancelled",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = "Notify Me",
                            tint = if (isNotified) AppGoldAccent else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isUnlocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = AppCyanAccent,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "The Main Mic unlocks in",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Text(
                    text = timerString,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        forceDemoUnlocked = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppCyanAccent)
                ) {
                    Text("Unlock Live Stage Demo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                AudioEqualizerAnimation()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "MIDNIGHT BROADCAST IS LIVE 🎙️",
                    color = Color(0xFF10B981),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "128 Students on Audio Stage",
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenAudioRoom,
                    modifier = Modifier.scale(pulseScale),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPinkAccent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join Live Audio Stage", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun calculateMillisToMidnight(): Long {
    val calendar = java.util.Calendar.getInstance()
    val now = calendar.timeInMillis
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
    calendar.set(java.util.Calendar.MINUTE, 59)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    var target = calendar.timeInMillis
    if (now > target) target += 86400000L
    return (target - now).coerceAtLeast(0L)
}

@Composable
fun AudioEqualizerAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "Eq")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(32.dp)
    ) {
        repeat(7) { index ->
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BarHeight"
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(
                        Brush.verticalGradient(listOf(AppPinkAccent, AppCyanAccent))
                    )
            )
        }
    }
}

@Composable
fun Zone2DareDropCard(
    dareCompleted: Boolean,
    completedCount: Int,
    onOpenDareDrop: () -> Unit
) {
    AuraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDareDrop() }
            .border(
                1.dp,
                if (dareCompleted) Color(0xFF10B981) else AppGlassBorder,
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 DareDrop Arena",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (dareCompleted) "DONE ✅" else "TODAY's DROP",
                    color = if (dareCompleted) Color(0xFF10B981) else AppPinkAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = "Today's Campus Challenge:",
                    color = AppPinkAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\"Take a high-five selfie with a campus professor or library cat! 📸\"",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reward: +25 UniCoins + Star Badge 🌟",
                    color = AppGoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$completedCount completed",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }

            LinearProgressIndicator(
                progress = { (completedCount.toFloat() / 200f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
                color = if (dareCompleted) Color(0xFF10B981) else AppPinkAccent,
                trackColor = Color(0x33FFFFFF)
            )

            Button(
                onClick = onOpenDareDrop,
                colors = ButtonDefaults.buttonColors(containerColor = if (dareCompleted) Color(0xFF10B981) else AppPinkAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(if (dareCompleted) Icons.Default.CheckCircle else Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (dareCompleted) "Completed (+25 XP)" else "Open Dare Arena 🎯", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Zone3BarterBlockCard(
    trades: List<String>,
    onOpenBarterBlock: () -> Unit = {},
    onPostTrade: () -> Unit
) {
    var currentTradeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(trades.size) {
        while (true) {
            delay(2800L)
            if (trades.isNotEmpty()) {
                currentTradeIndex = (currentTradeIndex + 1) % trades.size
            }
        }
    }

    AuraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenBarterBlock() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = AppCyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔄 Barter Block (Cashless Market)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x3310B981)
                ) {
                    Text(
                        text = "LIVE (${trades.size})",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1A000000))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Live Peer Trade Offer:",
                    color = AppCyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (trades.isNotEmpty()) trades[currentTradeIndex % trades.size] else "No active trades",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onPostTrade,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppCyanAccent),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post Barter Trade", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Zone4RoastAndToastCard(
    onPlayPoll: () -> Unit
) {
    AuraGlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 Roast & Toast Polls",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = AppPinkAccent
                    ) {
                        Text(
                            text = "2 Picked You!",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "Who is most likely to build a startup before graduation? 🚀",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = onPlayPoll,
                colors = ButtonDefaults.buttonColors(containerColor = AppGoldAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play Live Poll", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AuraGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = AppGlassCardBg,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppGlassBorder)
    ) {
        content()
    }
}
