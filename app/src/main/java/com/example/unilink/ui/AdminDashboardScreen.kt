package com.example.unilink.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unilink.models.Post
import com.example.unilink.models.User
import com.example.unilink.repository.AdminAuditLog
import com.example.unilink.repository.AdminRepository
import com.example.unilink.repository.AppConfig
import com.example.unilink.repository.NotificationRepository
import com.example.unilink.repository.PostRepository
import com.example.unilink.repository.SystemStats
import com.example.unilink.repository.UserRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val adminRepository = remember { AdminRepository() }
    val userRepository = remember { UserRepository() }
    val postRepository = remember { PostRepository() }

    var isAdminAuthenticated by remember { mutableStateOf(false) }
    var securityPinInput by remember { mutableStateOf("") }

    var stats by remember { mutableStateOf(SystemStats()) }
    var appConfig by remember { mutableStateOf(AppConfig()) }
    var auditLogs by remember { mutableStateOf<List<AdminAuditLog>>(emptyList()) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("📊 Overview & Logs", "👥 Users & Economy", "📝 Moderation", "⚙️ Maintenance & Broadcast")

    var userList by remember { mutableStateOf<List<User>>(emptyList()) }
    var postList by remember { mutableStateOf<List<Post>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedUserForCoins by remember { mutableStateOf<User?>(null) }
    var selectedUserForVip by remember { mutableStateOf<User?>(null) }
    var showBroadcastDialog by remember { mutableStateOf(false) }

    // Live Observers
    DisposableEffect(isAdminAuthenticated) {
        if (!isAdminAuthenticated) return@DisposableEffect onDispose {}

        val statsListener = adminRepository.observeSystemStats { newStats -> stats = newStats }
        val configListener = adminRepository.observeAppConfig { newConfig -> appConfig = newConfig }
        val logsListener = adminRepository.observeAuditLogs { newLogs -> auditLogs = newLogs }
        val postsListener = postRepository.getFeed { posts -> postList = posts }
        userRepository.getAllUsers { users -> userList = users }

        onDispose {
            statsListener.remove()
            configListener.remove()
            logsListener.remove()
            postsListener.remove()
        }
    }

    if (!isAdminAuthenticated) {
        // Admin Security PIN Lock Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FACC15))
                        .border(2.dp, Color(0xFFFACC15), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(40.dp))
                }

                Text("👑 Royal Admin Security Gate", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Enter the 6-digit Admin Passcode to access system controls & database management:", color = Color.White.copy(0.7f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                OutlinedTextField(
                    value = securityPinInput,
                    onValueChange = { securityPinInput = it },
                    label = { Text("Enter Security PIN") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFACC15), unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (securityPinInput == "051008") {
                            isAdminAuthenticated = true
                            Toast.makeText(context, "👑 Authenticated as Royal Admin!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect Security PIN!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Unlock Admin Portal 🔓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                TextButton(onClick = onNavigateBack) {
                    Text("Cancel", color = Color.White.copy(0.6f))
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👑 Royal Admin Portal", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBroadcastDialog = true }) {
                            Icon(Icons.Default.Campaign, contentDescription = "Broadcast", tint = Color(0xFF06B6D4))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF06B6D4),
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color(0xFF06B6D4)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    title,
                                    color = if (selectedTabIndex == index) Color(0xFF06B6D4) else Color.White.copy(0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> AdminOverviewTab(
                        stats = stats,
                        auditLogs = auditLogs,
                        onGrantLakhToAll = {
                            adminRepository.grantCoinsToAllUsers(100000) { success ->
                                if (success) {
                                    Toast.makeText(context, "🎉 1 Lakh (100,000) UniCoins granted to ALL users!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to grant UniCoins to all users", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPurgeStreams = {
                            adminRepository.purgeEndedStreams { count ->
                                Toast.makeText(context, "Purged $count ended streams 🧹", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    1 -> AdminUsersTab(
                        users = userList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.email.contains(searchQuery, ignoreCase = true) ||
                            it.username.contains(searchQuery, ignoreCase = true)
                        },
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSelectUserForCoins = { selectedUserForCoins = it },
                        onSelectUserForVip = { selectedUserForVip = it },
                        onToggleAdmin = { targetUser ->
                            val newStatus = !targetUser.isAdmin
                            adminRepository.toggleAdminStatus(targetUser.uid, newStatus) { success ->
                                if (success) {
                                    Toast.makeText(context, "${targetUser.name} Admin Status: $newStatus", Toast.LENGTH_SHORT).show()
                                    userRepository.getAllUsers { users -> userList = users }
                                }
                            }
                        },
                        onToggleBan = { targetUser ->
                            adminRepository.toggleBanUser(targetUser.uid, true) { success ->
                                if (success) {
                                    Toast.makeText(context, "${targetUser.name} banned!", Toast.LENGTH_SHORT).show()
                                    userRepository.getAllUsers { users -> userList = users }
                                }
                            }
                        }
                    )

                    2 -> AdminPostsTab(
                        posts = postList,
                        onDeletePost = { postId ->
                            adminRepository.deletePost(postId) { success ->
                                if (success) {
                                    Toast.makeText(context, "Post deleted permanently 🗑️", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    3 -> AdminMaintenanceTab(
                        appConfig = appConfig,
                        onToggleMaintenance = { isEnabled, msg ->
                            adminRepository.toggleMaintenanceMode(isEnabled, msg) { success ->
                                if (success) {
                                    Toast.makeText(context, "Maintenance Mode set to $isEnabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSendBroadcast = { title, message ->
                            NotificationRepository().sendGlobalUpdateNotification(title, message) { success ->
                                if (success) {
                                    Toast.makeText(context, "Global Push Notification Broadcasted! 🚀", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Broadcast failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // Grant UniCoins Modal Dialog
            selectedUserForCoins?.let { targetUser ->
                var coinInput by remember { mutableStateOf("100000") }

                AlertDialog(
                    onDismissRequest = { selectedUserForCoins = null },
                    containerColor = Color(0xFF1E1B4B),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFACC15))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant UniCoins to ${targetUser.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Current Balance: ${targetUser.uniCoins} UniCoins", color = Color.White.copy(0.7f), fontSize = 12.sp)

                            OutlinedTextField(
                                value = coinInput,
                                onValueChange = { coinInput = it },
                                label = { Text("UniCoins Amount (Default: 100,000 / 1 Lakh)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF06B6D4), unfocusedBorderColor = Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { coinInput = "10000" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3306B6D4)),
                                    modifier = Modifier.weight(1f)
                                ) { Text("10K 🪙", fontSize = 11.sp, color = Color.White) }

                                Button(
                                    onClick = { coinInput = "100000" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FACC15)),
                                    modifier = Modifier.weight(1f)
                                ) { Text("1 Lakh 🪙", fontSize = 11.sp, color = Color.Yellow) }

                                Button(
                                    onClick = { coinInput = "500000" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EC4899)),
                                    modifier = Modifier.weight(1f)
                                ) { Text("5 Lakhs 🪙", fontSize = 11.sp, color = Color.White) }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val amount = coinInput.toIntOrNull() ?: 100000
                                adminRepository.grantCoinsToUser(targetUser.uid, amount) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Successfully granted $amount UniCoins to ${targetUser.name}! 💰", Toast.LENGTH_LONG).show()
                                        userRepository.getAllUsers { users -> userList = users }
                                        selectedUserForCoins = null
                                    } else {
                                        Toast.makeText(context, "Failed to grant UniCoins", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15))
                        ) {
                            Text("Grant UniCoins 💰", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedUserForCoins = null }) {
                            Text("Cancel", color = Color.White.copy(0.7f))
                        }
                    }
                )
            }

            // Grant VIP Level & Badges Modal Dialog
            selectedUserForVip?.let { targetUser ->
                var selectedLevel by remember { mutableIntStateOf(5) }

                AlertDialog(
                    onDismissRequest = { selectedUserForVip = null },
                    containerColor = Color(0xFF160F38),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFFFACC15))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Royal VIP Level 5 to ${targetUser.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Select VIP Level & Verification Badges:", color = Color.White.copy(0.7f), fontSize = 12.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..5).forEach { level ->
                                    Button(
                                        onClick = { selectedLevel = level },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedLevel == level) Color(0xFFFACC15) else Color(0x33FFFFFF)),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Lvl $level", fontSize = 10.sp, color = if (selectedLevel == level) Color.Black else Color.White) }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                adminRepository.grantVipAndBadges(targetUser.uid, selectedLevel, "👑 Royal Diamond VIP") { success ->
                                    if (success) {
                                        Toast.makeText(context, "Granted VIP Level $selectedLevel to ${targetUser.name}!", Toast.LENGTH_SHORT).show()
                                        userRepository.getAllUsers { users -> userList = users }
                                        selectedUserForVip = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15))
                        ) {
                            Text("Grant VIP Pass 👑", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedUserForVip = null }) {
                            Text("Cancel", color = Color.White.copy(0.7f))
                        }
                    }
                )
            }

            // Global Broadcast Modal Dialog
            if (showBroadcastDialog) {
                var bTitle by remember { mutableStateOf("🚀 Mega Campus Update!") }
                var bMsg by remember { mutableStateOf("Welcome to Newton's Lobby! Special UniCoins rewards dropped today!") }

                AlertDialog(
                    onDismissRequest = { showBroadcastDialog = false },
                    containerColor = Color(0xFF160F38),
                    title = {
                        Text("📢 Send Global Broadcast Notification", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = bTitle,
                                onValueChange = { bTitle = it },
                                label = { Text("Title") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = bMsg,
                                onValueChange = { bMsg = it },
                                label = { Text("Message Body") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBroadcastDialog = false
                                NotificationRepository().sendGlobalUpdateNotification(bTitle, bMsg) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Notification Broadcasted to All Users! 🚀", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                        ) {
                            Text("Send Broadcast 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBroadcastDialog = false }) {
                            Text("Cancel", color = Color.White.copy(0.7f))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AdminOverviewTab(
    stats: SystemStats,
    auditLogs: List<AdminAuditLog>,
    onGrantLakhToAll: () -> Unit,
    onPurgeStreams: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // One Lakh UniCoins to ALL Users Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FACC15)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFACC15))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("💰 Campus Economy Controller", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Airdrop UniCoins directly to all student wallets", color = Color.White.copy(0.7f), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onGrantLakhToAll,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Airdrop 1 Lakh to ALL 🚀", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onPurgeStreams,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EC4899)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899))
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item {
            Text("📊 System Stats & Live Monitor", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("👥 Students", "${stats.totalUsers}", Color(0xFF06B6D4), Modifier.weight(1f))
                StatTile("🟢 Active Online", "${stats.onlineUsers}", Color(0xFF10B981), Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("📝 Feed Posts", "${stats.totalPosts}", Color(0xFFEC4899), Modifier.weight(1f))
                StatTile("❓ Doubts", "${stats.totalDoubts}", Color(0xFF3B82F6), Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("🔄 Barter Items", "${stats.totalBarterItems}", Color(0xFF10B981), Modifier.weight(1f))
                StatTile("🎥 Live Streams", "${stats.totalLiveStreams}", Color(0xFFF59E0B), Modifier.weight(1f))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF06B6D4))
                Spacer(modifier = Modifier.width(8.dp))
                Text("🛡️ Real-Time Admin Security Audit Log", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        items(auditLogs, key = { it.logId }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Action: ${log.actionType}", color = Color(0xFF06B6D4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(log.adminName, color = Color.White.copy(0.6f), fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(log.details, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatTile(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0x1F1E293B), shape = RoundedCornerShape(16.dp))
            .border(1.dp, accentColor.copy(0.3f), shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, color = accentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AdminUsersTab(
    users: List<User>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectUserForCoins: (User) -> Unit,
    onSelectUserForVip: (User) -> Unit,
    onToggleAdmin: (User) -> Unit,
    onToggleBan: (User) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search student by name, username, email...", color = Color.White.copy(0.6f), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF06B6D4)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0x990F172A),
                unfocusedContainerColor = Color(0x800F172A),
                focusedBorderColor = Color(0xFF06B6D4),
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(users, key = { it.uid }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x261E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF06B6D4).copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF06B6D4))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name.ifEmpty { "Student" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (user.isAdmin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Verified, contentDescription = "Admin", tint = Color(0xFFFACC15), modifier = Modifier.size(16.dp))
                                }
                            }

                            Text("${user.email} • 🪙 ${user.uniCoins}", color = Color.White.copy(0.7f), fontSize = 11.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Grant UniCoins Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FACC15))
                                    .clickable { onSelectUserForCoins(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = "Grant Coins", tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
                            }

                            // Grant VIP Pass
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33EC4899))
                                    .clickable { onSelectUserForVip(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "VIP Pass", tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                            }

                            // Toggle Admin
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (user.isAdmin) Color(0x3310B981) else Color(0x333B82F6))
                                    .clickable { onToggleAdmin(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, contentDescription = "Toggle Admin", tint = if (user.isAdmin) Color(0xFF10B981) else Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                            }

                            // Ban User
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33EF4444))
                                    .clickable { onToggleBan(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Block, contentDescription = "Ban User", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPostsTab(
    posts: List<Post>,
    onDeletePost: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(posts, key = { it.postId }) { post ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x261E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Author: ${post.authorName}", color = Color(0xFF06B6D4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(post.content, color = Color.White, fontSize = 13.sp)
                    }

                    IconButton(onClick = { onDeletePost(post.postId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Post", tint = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}

@Composable
fun AdminMaintenanceTab(
    appConfig: AppConfig,
    onToggleMaintenance: (Boolean, String) -> Unit,
    onSendBroadcast: (title: String, message: String) -> Unit
) {
    var titleText by remember { mutableStateOf("🚀 Important Campus Announcement") }
    var messageText by remember { mutableStateOf("UniLink Admin has released new rewards and feature updates for all students!") }
    var maintMsg by remember { mutableStateOf(appConfig.maintenanceMessage) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (appConfig.isMaintenanceMode) Color(0x4D371B1B) else Color(0x261E293B)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (appConfig.isMaintenanceMode) Color(0xFFEF4444) else Color(0xFF10B981))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = if (appConfig.isMaintenanceMode) Color(0xFFEF4444) else Color(0xFF10B981), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("⚙️ Scheduled Maintenance Controller", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Status: ${if (appConfig.isMaintenanceMode) "ACTIVE (App Locked for Non-Admins)" else "NORMAL (Operational)"}", color = Color.White.copy(0.7f), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = maintMsg,
                        onValueChange = { maintMsg = it },
                        label = { Text("Maintenance Notice Message") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onToggleMaintenance(!appConfig.isMaintenanceMode, maintMsg) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (appConfig.isMaintenanceMode) Color(0xFF10B981) else Color(0xFFEF4444)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (appConfig.isMaintenanceMode) "Disable Maintenance Mode ✅" else "Enable Maintenance Mode 🔒", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("🚀 Global Push Notification Broadcast", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        item {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Broadcast Title") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Broadcast Message") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = { onSendBroadcast(titleText, messageText) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Global Push Notification 🚀", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
