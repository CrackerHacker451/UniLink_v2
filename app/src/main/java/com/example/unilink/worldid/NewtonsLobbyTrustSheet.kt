package com.example.unilink.worldid

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun LiveCameraPreview(
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                    } catch (e: Exception) {
                        Log.e("IDKitCamera", "Camera preview failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
    )
}

@Composable
fun NewtonsLobbyTrustSheet(
    projectId: String = "project_newton_ai",
    projectTitle: String = "NewtonAI Smart Agent Engine",
    tier: CredentialTier = CredentialTier.PROOF_OF_HUMAN,
    viewModel: NewtonsLobbyTrustViewModel,
    onVerifiedSuccess: () -> Unit = {},
    onClose: () -> Unit = {}
) {
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
                    val headerTitle = if (tier == CredentialTier.PROOF_OF_HUMAN) "World ID Proof of Human Account Gate 🌐" else "World ID Selfie Check Team Gate 📸"
                    val headerSub = if (tier == CredentialTier.PROOF_OF_HUMAN) "Proof of Human (Credential ID 1) • Verified Account Tag" else "Selfie Check (Credential ID 11) • Team Creation Liveness"
                    Text(headerTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(headerSub, color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                            val cardTitle = if (tier == CredentialTier.PROOF_OF_HUMAN) "Verify Proof of Human (Account Tag) 🌐" else "Verify Selfie Check Liveness (Team Gate) 📸"
                            val cardDesc = if (tier == CredentialTier.PROOF_OF_HUMAN) "Proof of Human verifies unique human identity to grant the Orbit Verified Student tag on your profile & posts." else "Selfie Check verifies live human presence via camera face frame alignment before publishing your squad."

                            Text(cardTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(cardDesc, color = Color.White.copy(0.7f), fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.startLiveLivenessScan(projectId, tier, onVerifiedSuccess)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                val btnText = if (tier == CredentialTier.PROOF_OF_HUMAN) "Start Proof of Human Scan 🌐" else "Start Selfie Check Team Gate 📸"
                                Text(btnText, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

                is TrustUiState.ScanningFace -> {
                    val infiniteTransition = rememberInfiniteTransition()
                    val laserAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2B06B6D4),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF06B6D4).copy(alpha = laserAlpha))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(3.dp, Color(0xFF06B6D4).copy(alpha = laserAlpha), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                LiveCameraPreview(modifier = Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(2.dp, Color(0xFF06B6D4), CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(state.promptMessage, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { state.progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF06B6D4),
                                trackColor = Color(0x3306B6D4)
                            )
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
                                Text("Badge Unlocked: ${state.badge} 🌐", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
            HorizontalDivider(color = Color(0x33FFFFFF))
            Spacer(modifier = Modifier.height(16.dp))

            // Re-verify / Reset Action
            OutlinedButton(
                onClick = { viewModel.resetState() },
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Reset Verification (Start Liveness Scan Over)", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
