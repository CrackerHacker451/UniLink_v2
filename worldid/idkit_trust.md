# ETHGlobal Mumbai — Best Use of IDKit & Proportional Trust Report (`/worldid/idkit_trust.md`)

This document outlines the **Best Use of IDKit** integration for **Newton's Lobby (`com.example.unilink`)**, competing in both General and Continuity track categories ($5,000 total prize pool).

---

## 1. Product Events Requiring Trust & Proportional Credential Choices

Newton's Lobby uses **Proportional Trust Assurance**: selecting the minimum sufficient World ID credential required for each specific product event rather than over-requiring credentials.

| Product Event | Trust Problem Solved | Chosen Credential & Schema ID | Why Minimum Sufficient Assurance |
| :--- | :--- | :--- | :--- |
| **Day 7 Streak Reward (+150 UniCoins & Pro Pass)** | Prevents Sybil double-claiming of scarce financial UniCoins & Pro Pass benefits across multiple accounts | **Orb Proof of Human (Credential ID 1)** | Scarce financial reward requires maximum Sybil resistance (Orb iris biometric proof). |
| **Hackathon Team Creation & SpeedLink Gate** | Ensures genuine live student presence and prevents automated spam bot team creation | **Selfie Check (Credential ID 11)** | Liveness & face match provides fast, mobile-first liveness verification without requiring an Orb. |
| **International Scholar Badge** | Verifies university enrollment & international student status while preserving complete privacy | **Passport / NFC (Credential ID 9303)** | ICAO 9303 NFC ZK proof confirms encrypted passport validity without exposing real name or passport number. |

---

## 2. Server-Side & On-Chain Verification Architecture

- **App ID**: `app_ce59eafb4c64fb4dff9ac783c593702a`
- **Server Verification**:
  All IDKit ZK proof responses (`proof`, `merkle_root`, `nullifier_hash`, `credential_type`) are verified in a secure server backend ([`server/gate.js`](../server/gate.js) and [`WorldTrustRepository.kt`](../app/src/main/java/com/example/unilink/trust/WorldTrustRepository.kt)).
- **On-Chain Settlement**:
  - Nullifiers are settled on-chain via **Chainlink CRE (Runtime Environment)** smart contracts ([`server/netlify/functions/api.mjs`](../server/netlify/functions/api.mjs)).
  - Mints verified **`Orb Verified 🌐`** badges on Cloud Firestore and EVM ledgers.

---

## 3. Successful Verification vs. Alternative / Denied Path Demonstration

### A. Successful Verification Path
1. Student clicks **"Claim Day 7 Streak Reward (+150 UniCoins)"**.
2. Completes World ID Orb verification (`WorldTrustManager.kt`).
3. Secure backend validates ZK proof against `app_ce59eafb4c64fb4dff9ac783c593702a`.
4. +150 UniCoins credited, Pro Pass unlocked, and **`Orb Verified 🌐`** badge displayed on profile.

### B. Alternative / Denied Path (Cancellation / Unavailable Credential / Rejection)
1. If a student cancels IDKit verification, declines permissions, or attempts to reuse a previously claimed nullifier hash:
2. **Server Rejection**: `WorldTrustRepository.kt` catches the duplicate nullifier or HTTP non-200 status.
3. **Action Blocked**:
   - UniCoins remain unchanged.
   - Pro Pass remains locked.
   - App displays clear feedback: `"Verification cancelled or identity already linked to another account. Action blocked."`

---

## 4. Integration Debrief & Developer Feedback

### A. Time to First Success
- **25 Minutes**: Configuring IDKit JS and `WorldTrustManager.kt` using `app_ce59eafb4c64fb4dff9ac783c593702a` was seamless.

### B. Friction Encountered
- Setting up custom URI scheme handlers (`unilink://worldid-callback`) required configuring `singleTask` launchModes in `AndroidManifest.xml` to prevent activity stack duplication.

### C. Missing Capability or Documentation
- Adding pre-built Android Jetpack Compose IDKit UI components would speed up native Kotlin development.

### D. Single Improvement with Greatest Impact
- Real-time SSE / WebSocket event streaming on `https://developer.world.org/api/v4/verify/` for instant mobile push alerts when an Orb scan completes.
