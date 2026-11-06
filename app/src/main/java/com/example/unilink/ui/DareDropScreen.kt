package com.example.unilink.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

// Data models for DareDrop private page
data class DareProofItem(
    val id: String,
    val authorName: String,
    val authorDept: String,
    val timeAgo: String,
    val caption: String,
    val imageUrl: Any? = null,
    val videoUrl: String? = null,
    val isVideo: Boolean = false,
    var likesCount: Int,
    var isLiked: Boolean = false,
    var cheersCount: Int = 0,
    var isCheered: Boolean = false,
    val isCurrentUser: Boolean = false,
)

data class ProposedDareItem(
    val id: String,
    val title: String,
    val description: String,
    val proposedBy: String,
    var votesCount: Int,
    var hasVoted: Boolean = false,
)

data class DareLeaderboardUser(
    val rank: Int,
    val name: String,
    val dept: String,
    val streakDays: Int,
    val totalCoins: Int,
    val badge: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DareDropScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Attached proof URI launchers (Photo & Video)
    var attachedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var attachedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isVideoSelected by remember { mutableStateOf(false) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachedImageUri = it
            attachedVideoUri = null
            isVideoSelected = false
            Toast.makeText(context, "Photo attached! 📸", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachedVideoUri = it
            attachedImageUri = null
            isVideoSelected = true
            Toast.makeText(context, "Video proof attached! 🎥", Toast.LENGTH_SHORT).show()
        }
    }

    // Dare States
    var isDareCompleted by remember { mutableStateOf(false) }
    var completedCount by remember { mutableIntStateOf(0) }
    var showSubmitProofDialog by remember { mutableStateOf(false) }
    var showProposeDareDialog by remember { mutableStateOf(false) }

    // Tab state (0 = Proofs Feed, 1 = Vote Tomorrow's Dare, 2 = Hall of Fame)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFeedFilter by remember { mutableStateOf("All") }

    // Community Proofs list (Clean Reset - List starts empty until cloud proof submissions arrive)
    val proofList = remember {
        mutableStateListOf<DareProofItem>()
    }

    // Countdown Timer & Real-time Firestore Proofs Sync
    var timeRemainingMillis by remember { mutableLongStateOf(28_800_000L) } // 8 hours countdown
    LaunchedEffect(Unit) {
        val repo = com.example.unilink.repository.DareDropRepository()
        // Automatically execute a clean cloud reset on launch
        repo.resetAllDareDropData { }

        repo.observeProofs { cloudProofs ->
            proofList.clear()
            proofList.addAll(cloudProofs)
            completedCount = cloudProofs.size
        }

        while (timeRemainingMillis > 0) {
            delay(1000)
            timeRemainingMillis -= 1000L
        }
    }

    // Proposed Dares list (Clean Reset - List starts empty)
    val proposedDaresList = remember {
        mutableStateListOf<ProposedDareItem>()
    }

    // Hall of Fame Leaderboard list (Clean Reset - List starts empty until earned)
    val leaderboardUsers = remember {
        mutableStateListOf<DareLeaderboardUser>()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraDeepObsidian
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🎯 DareDrop Arena",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Daily Campus Thrills & Rewards",
                            color = AuraCyanNeon,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraVioletDark)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
            ) {
                // Featured Active Dare Card
                item {
                    FeaturedDareHeaderCard(
                        isCompleted = isDareCompleted,
                        completedCount = completedCount,
                        timeRemainingMillis = timeRemainingMillis,
                        onCompleteClick = {
                            if (isDareCompleted) {
                                Toast.makeText(context, "You've already completed today's dare! 🎉", Toast.LENGTH_SHORT).show()
                            } else {
                                showSubmitProofDialog = true
                            }
                        }
                    )
                }

                // Interactive Navigation Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = AuraPinkNeon,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = AuraPinkNeon
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Community Proofs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 0) AuraPinkNeon else Color.White.copy(alpha = 0.6f)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Vote Next Dare 💡", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 1) AuraPinkNeon else Color.White.copy(alpha = 0.6f)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Hall of Fame 🏆", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedTab == 2) AuraPinkNeon else Color.White.copy(alpha = 0.6f)) }
                        )
                    }
                }

                // TAB 0: Community Proofs Feed (Instagram Post Style)
                if (selectedTab == 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Campus Submissions",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("All", "Trending 🔥", "My Proof").forEach { filter ->
                                    FilterChip(
                                        selected = selectedFeedFilter == filter,
                                        onClick = { selectedFeedFilter = filter },
                                        label = { Text(filter, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AuraPinkNeon,
                                            selectedLabelColor = Color.White,
                                            containerColor = AuraGlassCardBg,
                                            labelColor = Color.White.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val filteredProofs = when (selectedFeedFilter) {
                        "Trending 🔥" -> proofList.sortedByDescending { it.likesCount }
                        "My Proof" -> proofList.filter { it.isCurrentUser }
                        else -> proofList
                    }

                    if (filteredProofs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No proofs yet under '$selectedFeedFilter'. Be the first to complete today's dare! 🚀",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(filteredProofs, key = { it.id }) { proof ->
                            DareProofCard(
                                proof = proof,
                                onLikeClick = {
                                    proof.isLiked = !proof.isLiked
                                    if (proof.isLiked) proof.likesCount++ else proof.likesCount--
                                },
                                onCheerClick = {
                                    proof.isCheered = !proof.isCheered
                                    if (proof.isCheered) proof.cheersCount++ else proof.cheersCount--
                                }
                            )
                        }
                    }
                }

                // TAB 1: Vote Tomorrow's Dare & Propose
                if (selectedTab == 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Proposed Dares for Tomorrow",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { showProposeDareDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AuraCyanNeon),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Propose Dare", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(proposedDaresList, key = { it.id }) { proposed ->
                        ProposedDareCard(
                            item = proposed,
                            onVoteClick = {
                                proposed.hasVoted = !proposed.hasVoted
                                if (proposed.hasVoted) {
                                    proposed.votesCount++
                                    Toast.makeText(context, "Voted for '${proposed.title}'! 🗳️", Toast.LENGTH_SHORT).show()
                                } else {
                                    proposed.votesCount--
                                }
                            }
                        )
                    }
                }

                // TAB 2: Hall of Fame Leaderboard
                if (selectedTab == 2) {
                    item {
                        Text(
                            text = "Top Campus Daredevils 🏆",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(leaderboardUsers) { user ->
                        LeaderboardUserRow(user = user)
                    }
                }
            }
        }
    }

    // Submit Proof Dialog
    if (showSubmitProofDialog) {
        var captionText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSubmitProofDialog = false },
            containerColor = AuraVioletDark,
            title = {
                Text("🎥 Submit Dare Proof (Photo / Video)", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Upload video or photo proof to earn +25 UniCoins & Campus Star Badge!",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (!isVideoSelected && attachedImageUri != null) AuraPinkNeon else AuraGlassCardBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📸 Photo Proof", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isVideoSelected && attachedVideoUri != null) AuraCyanNeon else AuraGlassCardBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🎥 Video Proof", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraGlassCardBg)
                            .border(1.dp, AuraGlassBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (attachedImageUri != null) {
                            AsyncImage(
                                model = attachedImageUri,
                                contentDescription = "Attached Photo Proof",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (attachedVideoUri != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎥 Video Proof Ready!", color = AuraCyanNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(attachedVideoUri?.lastPathSegment ?: "video_proof.mp4", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AuraPinkNeon, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Select Photo or Video Proof Above", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = { Text("Write a quick caption...", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraPinkNeon,
                            unfocusedBorderColor = AuraGlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitProofDialog = false
                        isDareCompleted = true
                        completedCount++

                        val finalCaption = captionText.ifEmpty { if (isVideoSelected) "Completed today's DareDrop video challenge! 🎥🔥" else "Completed today's DareDrop challenge! 📸 high five!" }

                        if (isVideoSelected && attachedVideoUri != null) {
                            Toast.makeText(context, "Uploading video proof to cloud... 🎥", Toast.LENGTH_SHORT).show()
                            com.example.unilink.repository.StorageRepository().uploadDareVideo(context, attachedVideoUri!!) { cloudVideoUrl ->
                                com.example.unilink.repository.DareDropRepository().publishProof(
                                    caption = finalCaption,
                                    imageUrl = "",
                                    videoUrl = cloudVideoUrl,
                                    isVideo = true
                                ) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Video Proof Saved to Cloud! +25 🪙", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else if (attachedImageUri != null) {
                            Toast.makeText(context, "Uploading photo proof to cloud... 📸", Toast.LENGTH_SHORT).show()
                            com.example.unilink.repository.StorageRepository().uploadDarePhoto(attachedImageUri!!) { cloudPhotoUrl ->
                                com.example.unilink.repository.DareDropRepository().publishProof(
                                    caption = finalCaption,
                                    imageUrl = cloudPhotoUrl,
                                    videoUrl = null,
                                    isVideo = false
                                ) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Photo Proof Saved to Cloud! +25 🪙", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        // Local immediate feedback
                        proofList.add(
                            0,
                            DareProofItem(
                                id = "my_p_${System.currentTimeMillis()}",
                                authorName = "You (Verified Student)",
                                authorDept = "Campus Hero",
                                timeAgo = "Just now",
                                caption = finalCaption,
                                imageUrl = if (!isVideoSelected) attachedImageUri else null,
                                videoUrl = if (isVideoSelected) attachedVideoUri?.toString() else null,
                                isVideo = isVideoSelected,
                                likesCount = 1,
                                isLiked = true,
                                isCurrentUser = true
                            )
                        )

                        Toast.makeText(context, "Dare Completed! Earned +25 UniCoins 🏆", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPinkNeon)
                ) {
                    Text("Publish Proof (+25 Coins)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitProofDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Propose Dare Dialog
    if (showProposeDareDialog) {
        var proposedTitle by remember { mutableStateOf("") }
        var proposedDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showProposeDareDialog = false },
            containerColor = AuraVioletDark,
            title = {
                Text("💡 Propose Tomorrow's Dare", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = proposedTitle,
                        onValueChange = { proposedTitle = it },
                        label = { Text("Dare Title", color = Color.White.copy(alpha = 0.7f)) },
                        placeholder = { Text("e.g. Compliment a librarian", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyanNeon,
                            unfocusedBorderColor = AuraGlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = proposedDesc,
                        onValueChange = { proposedDesc = it },
                        label = { Text("Description & Instructions", color = Color.White.copy(alpha = 0.7f)) },
                        placeholder = { Text("Explain how students complete this dare...", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyanNeon,
                            unfocusedBorderColor = AuraGlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (proposedTitle.isNotEmpty()) {
                            proposedDaresList.add(
                                0,
                                ProposedDareItem(
                                    id = "prop_${System.currentTimeMillis()}",
                                    title = proposedTitle,
                                    description = proposedDesc.ifEmpty { "Fun campus activity" },
                                    proposedBy = "You",
                                    votesCount = 1,
                                    hasVoted = true
                                )
                            )
                            showProposeDareDialog = false
                            Toast.makeText(context, "Proposed dare submitted for campus voting! 🗳️", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyanNeon)
                ) {
                    Text("Submit Proposal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProposeDareDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Header Card for Active Dare
// ---------------------------------------------------------------------------
@Composable
fun FeaturedDareHeaderCard(
    isCompleted: Boolean,
    completedCount: Int,
    timeRemainingMillis: Long,
    onCompleteClick: () -> Unit
) {
    val hours = TimeUnit.MILLISECONDS.toHours(timeRemainingMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMillis) % 60
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF831843),
                        Color(0xFF31103E),
                        AuraVioletDark
                    )
                )
            )
            .border(
                1.dp,
                if (isCompleted) Color(0xFF10B981) else AuraPinkNeon,
                RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AuraPinkNeon, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TODAY'S FEATURED DROP", color = AuraPinkNeon, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33000000)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = AuraGoldNeon, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Resets in $formattedTime", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "\"Take a high-five selfie with a campus professor or library cat! 📸\"",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reward: +25 UniCoins + Star Badge 🌟",
                    color = AuraGoldNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$completedCount completed",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            LinearProgressIndicator(
                progress = { (completedCount.toFloat() / 200f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isCompleted) Color(0xFF10B981) else AuraPinkNeon,
                trackColor = Color(0x33FFFFFF)
            )

            Button(
                onClick = onCompleteClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Color(0xFF10B981) else AuraPinkNeon
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    if (isCompleted) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCompleted) "Completed (+25 UniCoins) ✅" else "Complete Dare & Upload Proof",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Card for Community Proof Submission (Instagram Post Style)
// ---------------------------------------------------------------------------
@Composable
fun DareProofCard(
    proof: DareProofItem,
    onLikeClick: () -> Unit,
    onCheerClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AuraGlassCardBg)
            .border(1.dp, AuraGlassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Instagram Header: Avatar + Author Info + Verified Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (proof.isCurrentUser) AuraPinkNeon else AuraCyanNeon),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = proof.authorName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = proof.authorName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${proof.authorDept} • ${proof.timeAgo}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x3310B981)
                ) {
                    Text(
                        text = "VERIFIED PROOF ✅",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Main Media Part (Instagram Post Style Photo or Inline Native Video Player)
            if (proof.isVideo && !proof.videoUrl.isNullOrEmpty()) {
                var isPlaying by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoURI(android.net.Uri.parse(proof.videoUrl))
                                    val controller = android.widget.MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { isPlaying = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = AuraPinkNeon
                                ) {
                                    Text(
                                        text = "▶",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "🎥 Tap to Stream Video Proof",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (proof.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF111827))
                ) {
                    AsyncImage(
                        model = proof.imageUrl,
                        contentDescription = "Dare Proof Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Instagram Action Bar (Like, Cheer, Reward Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.clickable { onLikeClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (proof.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (proof.isLiked) AuraPinkNeon else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = proof.likesCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.clickable { onCheerClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (proof.isCheered) "👏 Cheered" else "👏 Cheer",
                            color = if (proof.isCheered) AuraGoldNeon else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (proof.cheersCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${proof.cheersCount})",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33F59E0B)
                ) {
                    Text(
                        text = "💰 +25 UniCoins",
                        color = AuraGoldNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Instagram Style Caption (Author Name Bold + Caption Text)
            Text(
                text = buildAnnotatedString {
                    append(proof.authorName)
                    addStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        start = 0,
                        end = proof.authorName.length
                    )
                    append("  ")
                    append(proof.caption)
                    addStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        start = proof.authorName.length + 2,
                        end = length
                    )
                },
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Card for Proposed Dare
// ---------------------------------------------------------------------------
@Composable
fun ProposedDareCard(
    item: ProposedDareItem,
    onVoteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AuraGlassCardBg)
            .border(1.dp, AuraGlassBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Proposed by ${item.proposedBy}",
                    color = AuraCyanNeon,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onVoteClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.hasVoted) AuraCyanNeon else Color(0x3306B6D4)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "▲ ${item.votesCount}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (item.hasVoted) "Voted" else "Vote",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Leaderboard Row
// ---------------------------------------------------------------------------
@Composable
fun LeaderboardUserRow(user: DareLeaderboardUser) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AuraGlassCardBg)
            .border(1.dp, AuraGlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${user.rank}",
                    color = when (user.rank) {
                        1 -> AuraGoldNeon
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color.White.copy(alpha = 0.5f)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(28.dp)
                )

                Column {
                    Text(
                        text = user.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${user.dept} • ${user.badge}",
                        color = AuraCyanNeon,
                        fontSize = 10.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "🔥 ${user.streakDays}-Day Streak",
                    color = AuraPinkNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "💰 ${user.totalCoins} Coins",
                    color = AuraGoldNeon,
                    fontSize = 10.sp
                )
            }
        }
    }
}
