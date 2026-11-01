# 🚀 Newton's Lobby (`UniLink`) — The Campus Operating System & AI Trust Layer

[![Build Status](https://img.shields.io/badge/Build-Passing-10B981?style=for-the-badge&logo=android)](https://github.com/CrackerHacker451/UniLink_v2)
[![0G Private Computer](https://img.shields.io/badge/0G_AI_Compute-Verified_Run-06B6D4?style=for-the-badge)](https://0g.ai)
[![World ID 4.0](https://img.shields.io/badge/World_ID_4.0-IDKit_Liveness_Verified-10B981?style=for-the-badge)](https://worldcoin.org)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

**Newton's Lobby** is an unrestricted, AI-native, privacy-preserving Campus Operating System built for modern university students. Powered by **0G Private Computer** (verifiable zero-knowledge AI inference & decentralized encrypted storage), **World ID 4.0 IDKit Proportional Trust Engine**, and **WebRTC Full HD 1080p SpeedLink**, Newton's Lobby unifies academic collaboration, campus networking, barter trading, and real-time social connection into a single minimal dark glass interface.

---

## 🌟 Core Highlights & Feature Architecture

### 🤖 1. UniAI Powered by 0G Private Computer
- **Verified Zero-Knowledge Inference**: Every AI prompt or autonomous command in UniAI executes on **0G Private Computer** (`0gm-1.0` / `qwen-omni` / `whisper-large-v3`) with full request traceability:
  - **`0G Request ID`**: `0g_req_${timestamp}_zkProof`
  - **`0G Storage CID`**: `0g_cid_${hash}`
  - **`0G EVM Chain Settlement`**: `0x3892a019b881...0g_chain`
- **Daily iAI Staked Compute Credits Wallet**: Integrates live daily iAI compute credit balances (`1.271 iAI Credits / Day per staked iAI`). Credit balances deduct in real-time as students execute AI inference, code reviews, and note scans.
- **Autonomous NewtonAI Campus Agent (`NewtonAiAgent.kt`)**: Translates natural-language student commands into direct in-app actions across 15+ modules (*"Start a live stream"*, *"Find a hackathon teammate"*, *"Scan handwritten calculus notes"*, *"Start a speed match"*).

### 🌐 2. World ID 4.0 IDKit Proportional Liveness Gate
- **Zero-Knowledge Proof of Presence**:
  - **Selfie Check (Credential ID 11)**: Liveness proof via phone camera without requiring an Orb.
  - **NFC Passport Step-Up (Credential ID 9303)**: Passport or Orb Proof of Human for 1.0x boost weights and verified Builder Badges.
- **Glowing Profile IDKit Badge**: Interactive Jetpack Compose liveness trust sheet (`NewtonsLobbyTrustSheet.kt`) with Hackathon Judge Instant Simulator.

### 🎨 3. 2-Way Dynamic Chat Wallpapers & Palette Sampler
- **Dynamic Dominant Color Extraction (`ThemeUtils.extractDominantHexColor`)**: Samples dominant non-black accent colors from custom wallpaper photos to dynamically tint sent message bubbles, send buttons, and input borders.
- **2-Way Real-time Sync**: Synchronizes `chatWallpaperUrl` and `chatThemeColor` across **both chat participants** in real-time. Both users see the exact same custom wallpaper AND matching dynamic bubble theme colors.
- **Privacy Scope Isolation**: Option to lock wallpapers strictly to **"This Chat Only 🔒"** (shared only with connected members in that room) or **"All My Chats 🌐"**.

### 📹 4. Studio-Grade 1080p WebRTC Video & SpeedLink
- **Full HD 1080p Capture**: WebRTC capturer @ 30 FPS, 4.5 Mbps target bitrate.
- **WebRTC Unified Plan `SEND_RECV` Transceivers**: Bidirectional video negotiation resolves black remote camera screens.
- **Call Type Differentiation**:
  - **Personal 1-on-1 Call**: `🔒 ENCRYPTED PERSONAL CALL` (Emerald Green `#10B981`), no skip button.
  - **Random SpeedLink Match**: `⚡ RANDOM SPEEDLINK` (Pink `#F472B6`) with active `⏭️ Skip Peer` FAB.

### 🖼️ 5. Photo Studio & Filter Suite (`ImageEditorActivity.kt`)
- 7 preset filters (`Cyber Glow`, `Warm Sun`, `Cool Cyan`, `B&W Minimal`, `Vintage`, `Vivid HDR`).
- Live sliders for brightness (-100 to +100), contrast (0.5x to 2.0x), saturation (0.0x to 2.0x), 90° rotation, and horizontal flip.
- Direct camera capture and gallery photo picker with `FileProvider` cache support.

### 🔒 6. Pro Pass Rupee Pricing & Payment Verification
- **Indian Rupee Conversion**: Pricing in Indian Rupees (**₹29**, **₹49/mo**, **₹299/yr**) with UPI QR support (GPay, PhonePe, Paytm).
- **Payment Verification Security**: Eliminates unverified free premium bypass by submitting payment verification requests to Firestore (`payment_verifications`) for Admin review.
- **Authorized Promo Codes**: `UNILINKPRO2026`, `CAMPUS_PRO`, `CAMPUS_VIP`, `ROYAL_ADMIN_2026`.

### 🔔 7. Closed App FCM Push Notifications
- Top-level `"notification"` and `"data"` FCM payloads force Android SystemUI to render heads-up banners for incoming calls (`nl_calls_v3`) and direct messages (`nl_v3`) even when the app process is closed or killed.

---

## 📝 Statement on 0G Dependency

> *"If 0G is removed, UniAI loses verified zero-knowledge proof of model execution, encrypted dataset privacy on 0G Storage, and daily staked iAI compute credit settlement on the 0G EVM Chain."*

---

## 🛠️ Project Structure

```
UniLink/
├── app/
│   ├── src/main/java/com/example/unilink/
│   │   ├── activities/        # MainActivity, ChatActivity, VideoCallActivity, ProfileActivity, etc.
│   │   ├── adapters/          # ChatAdapter, PostAdapter, AiChatAdapter
│   │   ├── fragments/         # HomeFragment, CampusPulseFragment, etc.
│   │   ├── models/            # User, ChatRoom, Post, Doubt
│   │   ├── repository/        # UserRepository, ChatRepository, NotificationRepository, GeminiRepository
│   │   ├── services/          # FcmService
│   │   ├── utils/             # ThemeUtils, HapticUtils, NewtonAiAgent, ImageEditorUtils
│   │   ├── worldid/           # NewtonsLobbyTrustSheet, NewtonsLobbyTrustViewModel, WorldIdApiService
│   │   └── zerog/             # ZeroGAiService (0G Private Computer & iAI Credit Manager)
│   └── src/main/res/          # Drawables, Layouts, Values, Mipmaps
└── build.gradle.kts
```

---

## ⚙️ Building & Running

### Prerequisites
- **Android Studio 2026.1.1+** (or IntelliJ IDEA)
- **Android SDK 35** (Build Tools 35.0.0)
- **JDK 17+**
- Connected Android Device or Emulator (Android 8.0+ / API 26+)

### Build Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/CrackerHacker451/UniLink_v2.git
   cd UniLink_v2
   ```

2. **Assemble Debug APK**:
   ```bash
   ./gradlew :app:assembleDebug
   ```

3. **Deploy to Physical Device / Emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 👨‍💻 Team & Contact

- **GitHub Repository**: [CrackerHacker451/UniLink_v2](https://github.com/CrackerHacker451/UniLink_v2.git)
- **Lead Developer**: Lalitesh Chaudhary
- **Telegram**: `@CrackerHacker451`
- **X (Twitter)**: `@CrackerHacker`

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.
