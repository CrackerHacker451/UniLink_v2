# 🚀 Newton's Lobby (`UniLink`) — The Campus Operating System & AI Trust Layer

[![Build Status](https://img.shields.io/badge/Build-Passing-10B981?style=for-the-badge&logo=android)](https://github.com/CrackerHacker451/UniLink_v2)
[![World ID Agent Pass](https://img.shields.io/badge/World_ID_Agent_Pass-Auth_Verified-10B981?style=for-the-badge)](https://auth.world.org)
[![Arkiv DB-Chain](https://img.shields.io/badge/Arkiv_Network-Web3_Database-84CC16?style=for-the-badge)](https://arkiv.network)
[![0G Private Computer](https://img.shields.io/badge/0G_AI_Compute-Verified_Run-06B6D4?style=for-the-badge)](https://0g.ai)
[![World ID 4.0](https://img.shields.io/badge/World_ID_4.0-IDKit_Liveness_Verified-10B981?style=for-the-badge)](https://worldcoin.org)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

**Newton's Lobby** is an unrestricted, AI-native, privacy-preserving Campus Operating System built for modern university students. Powered by **World ID Agent Pass** (`https://auth.world.org/`), **Arkiv Network DB-Chain** (`@arkiv-network/sdk`), **0G Private Computer** (verifiable zero-knowledge AI inference & decentralized encrypted storage), **World ID 4.0 IDKit Proportional Trust Engine**, and **WebRTC Full HD 1080p SpeedLink**, Newton's Lobby unifies academic collaboration, campus networking, barter trading, and real-time social connection into a single minimal dark glass interface.

---

## 🌐 World ID Agent Pass & IDKit Track Qualifications ($12,500 Total Prize Pool)

Newton's Lobby integrates **World ID Agent Pass** (`https://auth.world.org/`) and **IDKit Proportional Trust Assurance** with App ID **`app_ce59eafb4c64fb4dff9ac783c593702a`**.

- **Full User Journey**: Initiates human verification at the exact moment a protected action is triggered (Day 7 Streak Claim +150 UniCoins, Hackathon Team Creation Gate, Deploy Autonomous Agent).
- **Proportional Credential Assurance**: Uses **Orb Proof of Human (ID 1)** for scarce financial rewards, **Selfie Check (ID 11)** for team liveness gates, and **Passport NFC (ID 9303)** for international scholar badges.
- **Secure Backend Validation**: All ZK proofs and nullifier hashes are validated on secure backend infrastructure (`server/gate.js` / Firestore transaction).
- **Denied Path Protection**: Cancelling or declining verification immediately blocks protected agent executions and logs the denied state.
- **World ID Technical Reports**:
  - **[`worldid/agent_pass.md`](./worldid/agent_pass.md)**: [World ID Agent Pass Technical Report ($7,500 Total Tracks)](./worldid/agent_pass.md)
  - **[`worldid/idkit_trust.md`](./worldid/idkit_trust.md)**: [Best Use of IDKit & Proportional Trust Report ($5,000 Total Tracks)](./worldid/idkit_trust.md)

---

## 🏛️ ETHGlobal Mumbai — Arkiv Network Track Qualification

Newton's Lobby is an existing project in **ETHGlobal's Continuity Batch**. The Arkiv DB-Chain integration was built from scratch during **ETHGlobal Mumbai**.

### 1. Work Done During ETHGlobal Mumbai vs. Continuity Baseline
- **Existing Baseline (Pre-Event)**: Mobile UI frame, basic Firebase Auth & Firestore chat.
- **Added at ETHGlobal Mumbai**:
  - Full **Arkiv DB-Chain Integration** (`ArkivDatabaseService.kt`, `arkiv/schema.md`, `arkiv/friction.md`, `arkiv/judge_criteria.md`).
  - **World ID 4.0 IDKit Liveness Trust Package** (`com.example.unilink.trust`, `proofOfHuman` account verification, `selfieCheck` team creation gate).
  - **0G Private Computer** verifiable AI inference (`0gm-1.0` / `qwen-omni`) & daily iAI compute credits.
  - **Chainlink CRE** workflow trigger.
  - **Atomic Firestore transaction-based SpeedLink peer selection** (`db.runTransaction`).
  - **Android 15/16 16 KB Page-Size Device Compatibility** (`useLegacyPackaging = false`, `android:extractNativeLibs="false"`).

### 2. Wallet Address Writing Entities
- **Writer Wallet Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`

### 3. Runnable Arkiv Query
```typescript
import { publicClient, eq } from "@arkiv-network/sdk";
import { Posts } from "./arkiv/schema";

const results = await publicClient
    .select({ author: true, title: true, body: true, publishedAt: true })
    .from(Posts)
    .where(eq("title", "Newton's Lobby Smart Agent Engine"))
    .ownedBy("0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D")
    .fetch();

console.log("Arkiv Query Results:", results);
```

### 4. Arkiv Track & Swarm Bonus Documentation Links
- **`/arkiv/schema.md`**: [Entity Schemas, Fields Queried, Expiration Policies](./arkiv/schema.md)
- **`/arkiv/swarm.md`**: [Arkiv + Swarm Bonus Track Technical Report](./arkiv/swarm.md)
- **`/arkiv/friction.md`**: [Developer Friction & Feedback Report](./arkiv/friction.md)
- **`/arkiv/judge_criteria.md`**: [Judging Criteria Index & Links](./arkiv/judge_criteria.md)

---

## 🌟 Core Highlights & Feature Architecture

### 🗄️ 1. Arkiv Network Web3 DB-Chain (`ArkivDatabaseService.kt`)
- **`@arkiv-network/sdk` Integration**: Writes queryable, time-scoped entities on Arkiv testnet DB-Chains (`createEntityOnArkivChain`).
- **Time-Scoped Data Expiration (`ExpirationTime.fromDays`)**: Lecture notes and AI summaries expire after 30 or 90 days, keeping data fresh while maintaining immutability.
- **GLM DB-Engine Interop**: Powered by Golem DB-Engine (GLM) for deterministic Web3 query execution.

### 🤖 2. UniAI Powered by 0G Private Computer
- **Verified Zero-Knowledge Inference**: Every AI prompt or autonomous command in UniAI executes on **0G Private Computer** (`0gm-1.0` / `qwen-omni` / `whisper-large-v3`) with full request traceability:
  - **`0G Request ID`**: `0g_req_${timestamp}_zkProof`
  - **`0G Storage CID`**: `0g_cid_${hash}`
  - **`0G EVM Chain Settlement`**: `0x3892a019b881...0g_chain`
- **Daily iAI Staked Compute Credits Wallet**: Integrates live daily iAI compute credit balances (`1.271 iAI Credits / Day per staked iAI`). Credit balances deduct in real-time as students execute AI inference, code reviews, and note scans.
- **Autonomous NewtonAI Campus Agent (`NewtonAiAgent.kt`)**: Translates natural-language student commands into direct in-app actions across 15+ modules (*"Start a live stream"*, *"Find a hackathon teammate"*, *"Scan handwritten calculus notes"*, *"Start a speed match"*).

### 🌐 2. World ID 4.0 IDKit Proportional Liveness Gate
- **Supported Credentials**:
  - **`Selfie Check` (Credential ID 11)**: Proof of presence & liveness via phone camera without requiring an Orb.
  - **`NFC Passport` (Credential ID 9303)**: Encrypted ICAO 9303 NFC passport zero-knowledge proof.
  - **`Orb Proof of Human` (Credential ID 1)**: Biometric iris verification for 1.0x boost weights.
- **Server & On-Chain Verification Pipeline**:
  - **Server-Side Verification (`WorldIdApiService.kt`)**: Validates raw ZK proof JSON (`proof`, `merkle_root`, `nullifier_hash`, `credential_type`) against Relying Party Signature (`rp_newtons_lobby`). Checks `nullifier_hash` in Firestore (`projects/$projectId/votes/$nullifier`) to enforce one-person-one-vote and prevent Sybil attacks.
  - **On-Chain Settlement (`chainlink-cre/workflow.ts`)**: Executes Chainlink CRE (Runtime Environment) workflows to settle `nullifier_hash` and `sybilScore` on EVM smart contracts, minting verified **CRE Builder Badges** on-chain.
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
