package com.example.unilink.worldid

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NewtonsLobbyTrustSheet(
    projectId: String = "project_newton_ai",
    projectTitle: String = "NewtonAI Smart Agent Engine",
    viewModel: NewtonsLobbyTrustViewModel,
    onTakeSelfie: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30363D))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x3306B6D4),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("World ID 4.0 Proportional Trust Gate 🌐", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Boosting '$projectTitle' & Claiming Compute Credits", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // UI State Machine Cards
            when (val state = uiState) {
                is TrustUiState.Idle -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x1F161B22),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3306B6D4))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("World ID Selfie Check (Credential ID 11) 📸", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Selfie Check verifies liveness and proof-of-presence via phone camera without requiring an Orb. Sybil score < 3 grants 1.0x boost weight & unlocks Live Student Badge.", color = Color.White.copy(0.7f), fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.startVerification(projectId)
                                    onTakeSelfie()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verify Selfie Check Liveness 📸", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.triggerFallbackPath(projectId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Use Guest Queue (0.2x Weight Fallback)", color = Color.White.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                is TrustUiState.LoadingSignature -> {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF06B6D4), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching RP Signature from Backend...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Validating nonce & isolating RP_SIGNING_KEY", color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                }

                is TrustUiState.AwaitingWorldApp -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2B06B6D4),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Selfie Check Liveness Ready 📸", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Take a camera selfie to verify proof-of-presence and liveness.", color = Color.White.copy(0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { onTakeSelfie() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📸 Take Camera Selfie & Verify", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                is TrustUiState.VerifyingLiveness -> {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF06B6D4), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Verifying Zero-Knowledge Liveness Proof...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Validating Merkle Root & Nullifier Hash on-chain", color = Color(0xFF06B6D4), fontSize = 11.sp)
                    }
                }

                is TrustUiState.Verified -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2B10B981),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("100% Liveness Verified! 🚀", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sybil Score: ${state.sybilScore}/10 • Boost Weight: ${state.weight}", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x3310B981)
                            ) {
                                Text("Badge Unlocked: ${state.badge} 🏅", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                is TrustUiState.FallbackActive -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2B8B5CF6),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Quadratic Guest Queue Active (0.2x Weight)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(state.reason, color = Color.White.copy(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = Color(0x33FFFFFF))
            Spacer(modifier = Modifier.height(16.dp))

            // Judge Demo Switcher Panel (Instant Testing without physical World App)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1F161B22))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👨‍⚖️ Hackathon Judge Instant Simulator", color = Color(0xFF06B6D4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.simulateJudgeState(projectId, 1) },
                        label = { Text("Simulate Low Risk (Score: 1)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0x3310B981), labelColor = Color.White),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = false,
                        onClick = { viewModel.simulateJudgeState(projectId, 5) },
                        label = { Text("Simulate High Risk (5 -> Step-Up)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0x33F59E0B), labelColor = Color.White),
                        modifier = Modifier.weight(1f)
                    )
                }

                FilterChip(
                    selected = false,
                    onClick = { viewModel.simulateJudgeState(projectId, -1) },
                    label = { Text("Simulate Cancelled / Fallback (Guest Queue 0.2x)", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0x338B5CF6), labelColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { viewModel.resetState() },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Reset Verification State (Start Liveness Over)", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
