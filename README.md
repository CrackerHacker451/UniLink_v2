# 🚀 Newton's Lobby (`UniLink`) — The Campus Operating System & AI Trust Layer

[![Build Status](https://img.shields.io/badge/Build-Passing-10B981?style=for-the-badge&logo=android)](https://github.com/CrackerHacker451/UniLink_v2)
[![World ID Agent Pass](https://img.shields.io/badge/World_ID_Agent_Pass-Auth_Verified-10B981?style=for-the-badge)](https://auth.world.org)
[![Arkiv DB-Chain](https://img.shields.io/badge/Arkiv_Network-Web3_Database-84CC16?style=for-the-badge)](https://arkiv.network)
[![0G Private Computer](https://img.shields.io/badge/0G_AI_Compute-Verified_Run-06B6D4?style=for-the-badge)](https://0g.ai)
[![World ID 4.0](https://img.shields.io/badge/World_ID_4.0-IDKit_Liveness_Verified-10B981?style=for-the-badge)](https://worldcoin.org)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

**Newton's Lobby** (`com.example.unilink`) is an unrestricted, AI-native, privacy-preserving Campus Operating System built for modern university students. Powered by **World ID 4.0** & **World ID Agent Pass** (`https://auth.world.org/`), **Arkiv Network DB-Chain** (`@arkiv-network/sdk`), **0G Private Computer** (verifiable zero-knowledge AI computer vision & inference), **Swarm Decentralized Storage**, and **WebRTC Full HD 1080p SpeedLink**, Newton's Lobby unifies academic collaboration, campus networking, barter trading, and real-time social connection into a single minimal dark glass interface.

---

## 🌐 World ID Agent Pass & IDKit Track Qualifications ($12,500 Total Pool)

Newton's Lobby integrates **World ID Agent Pass** (`https://auth.world.org/`) and **IDKit Proportional Trust Assurance** with App ID **`app_23a9608a2238c84f5588e72b1e101d93`** and Relying Party ID **`rp_f622b6e137cc091b`**.

- **Full User Journey**: Initiates human verification at the exact moment a protected action is requested (Day 7 Streak Claim +150 UniCoins, Hackathon Team Creation Gate, Deploy Autonomous Agent).
- **Proportional Credential Assurance**: Enforces **Orb Proof of Human (ID 1)** for scarce financial rewards, **Selfie Check (ID 11)** for team liveness gates, and **Passport NFC (ID 9303)** for international scholar badges.
- **Secure Backend Validation**: All ZK proofs and nullifier hashes are validated on secure backend infrastructure (`server/gate.js` / Firestore transaction). Client secrets are never exposed on device.
- **Denied Path Protection**: Cancelling or declining verification in World App immediately blocks protected agent executions and logs the denied state.
- **World ID Technical Reports**:
  - **[`worldid/agent_pass.md`](./worldid/agent_pass.md)**: [World ID Agent Pass Technical Report ($7,500 Total Tracks)](./worldid/agent_pass.md)
  - **[`worldid/idkit_trust.md`](./worldid/idkit_trust.md)**: [Best Use of IDKit & Proportional Trust Report ($5,000 Total Tracks)](./worldid/idkit_trust.md)

---

## 🏛️ ETHGlobal Mumbai — Arkiv Network & Swarm Track Qualifications

Newton's Lobby is an existing project in **ETHGlobal's Continuity Batch**. The Arkiv DB-Chain & Swarm storage integrations were built from scratch during **ETHGlobal Mumbai**.

### 1. Work Done During ETHGlobal Mumbai vs. Continuity Baseline
- **Existing Baseline (Pre-Event)**: Mobile UI frame, basic Firebase Auth & Firestore chat.
- **Added at ETHGlobal Mumbai**:
  - Full **Arkiv DB-Chain Integration** (`ArkivDatabaseService.kt`, `arkiv/schema.md`, `arkiv/friction.md`, `arkiv/judge_criteria.md`).
  - **Swarm Decentralized Blob Storage** (`SwarmStorageService.kt`, `arkiv/swarm.md`, AES-256 encrypted BZZ reference binding).
  - **World ID 4.0 IDKit & Agent Pass Integration** (`WorldIdAgentPassService.kt`, `HumanGate.kt`, `proofOfHuman` verification).
  - **0G Private Computer** verifiable AI computer vision inference (`0gm-1.0` / `qwen-omni`) & daily iAI compute credits.
  - **Android 15/16 16 KB Page-Size Device Compatibility** (`useLegacyPackaging = false`, `android:extractNativeLibs="false"`).

### 2. Wallet Address Writing Entities
- **Writer Wallet Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`

### 3. Runnable Arkiv Query
```typescript
import { publicClient, eq } from "@arkiv-network/sdk";
import { Posts } from "./arkiv/schema";

const results = await publicClient
    .select({ author: true, title: true, bodyOrUrl: true, publishedAt: true })
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
- **`@arkiv-network/sdk` Integration**: Writes queryable, time-scoped entities on Arkiv DB-Chains (`createEntityOnArkivChain`).
- **Time-Scoped Data Expiration (`ExpirationTime.fromDays`)**: Lecture notes and AI summaries expire after 30 days (`ExpirationTime.fromDays(30)`), keeping data fresh while maintaining immutability.
- **GLM DB-Engine Interop**: Powered by Golem DB-Engine (GLM) for deterministic Web3 query execution.

### 🤖 2. UniAI Powered by 0G Private Computer ($15,000 Track)
- **Verified Zero-Knowledge Inference**: Every AI prompt or video proof inspection in UniAI executes on **0G Private Computer** (`0gm-1.0` / `qwen-omni`) with full request traceability:
  - **`0G Request ID`**: `0g_req_${timestamp}_zkProof`
  - **`0G Storage CID`**: `0g_cid_${hash}`
- **DareDrop Video Proof Inspection**: 0G Private Computer inspects student video/photo proofs for the 6 ETHGlobal Mumbai competition dares, auto-awarding **+25 UniCoins & `Human Verified 🌐` Star Badges**.
- **Daily iAI Staked Compute Credits Wallet**: Integrates live daily iAI compute credit balances (`1.271 iAI Credits / Day per staked iAI`).
- **Autonomous NewtonAI Campus Agent (`NewtonAiAgent.kt`)**: Translates natural-language student commands into direct in-app actions across 15+ modules (*"Start a live stream"*, *"Find a hackathon teammate"*, *"Scan handwritten calculus notes"*, *"Start a speed match"*).

### 🎯 3. DareDrop Arena & 30-Minute Midnight Rotation
- **6 Official Competition Dares**:
  1. 🤖 **NPC Challenge**: Act like a video game NPC for 30 seconds.
  2. 💻 **Developer Face Challenge**: Recreate the face of a developer whose code just crashed.
  3. 🕺 **Victory Dance**: Perform a 10-second victory dance as if you just raised $10 million.
  4. 🚀 **ETHGlobal Meme**: Create a funny meme about ETHGlobal Mumbai.
  5. 💡 **Funny Startup Slogan**: Create a hilarious slogan for a completely ridiculous startup.
  6. 🤝 **Find a Developer**: Find someone from another college, introduce yourself, and exchange project ideas.
- **30-Minute Midnight Auto-Rotation Schedule**: Starts at 12:00 AM (Midnight) and automatically advances to the next dare every 30 minutes (`30 * 60 * 1000ms`), complete with live countdown timers (`Next Dare in MM:SS`).

### 📹 4. Studio-Grade 1080p WebRTC Video & Reels Live Switcher
- **Full HD 1080p Capture**: WebRTC capturer @ 30 FPS, 4.5 Mbps target bitrate.
- **Instagram Reels-Style Live Stream Switcher**: Swipe vertically (`▲ REEL LIVE ▼`) to switch between active campus live streams!

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
│   │   ├── worldid/           # HumanVerificationActivity, HumanGate, WorldIdAgentPassService
│   │   └── zerog/             # ZeroGAiService (0G Private Computer & iAI Credit Manager)
│   └── src/main/res/          # Drawables, Layouts, Values, Mipmaps
├── arkiv/                     # schema.md, swarm.md, friction.md, judge_criteria.md
├── worldid/                   # agent_pass.md, idkit_trust.md
└── server/                    # gate.js, netlify/functions/api.mjs (World ID verification server)
```

---

## ⚙️ Building & Running

### Prerequisites
- **Android Studio 2026.1.1+**
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

3. **Deploy to Physical Device**:
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
