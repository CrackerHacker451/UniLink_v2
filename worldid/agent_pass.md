# ETHGlobal Mumbai — World ID Agent Pass Integration Report (`/worldid/agent_pass.md`)

This document outlines the **fresh World ID Agent Pass integration** (`https://auth.world.org/`) for **Newton's Lobby (`com.example.unilink`)**.

---

## 1. Executive Summary & Overview

- **Track**: World ID Agent Pass ($2,500 Continuity Track & $5,000 General Track)
- **App ID**: `app_ce59eafb4c64fb4dff9ac783c593702a`
- **Official Agent Pass Service**: [`app/src/main/java/com/example/unilink/worldid/WorldIdAgentPassService.kt`](../app/src/main/java/com/example/unilink/worldid/WorldIdAgentPassService.kt)
- **Official World ID Agent Pass Endpoints**:
  - Auth Request: `https://auth.world.org/api/v1/request`
  - Verification: `https://auth.world.org/v1/verify`
  - Portal: `https://auth.world.org/portal`

---

## 2. Complete User Journey: Moment-of-Action Verification

Newton's Lobby does NOT treat World ID as a simple login screen. Instead, World ID Agent Pass is requested at the **exact moment** a student attempts a protected, high-value agentic action:

```
[1. Student Action] ────────► [2. Agent Pass Verification] ────────► [3. World App Completion]
("Claim Day 7 Reward" /        ("https://auth.world.org/v1/verify")    (Orb Proof of Human / ZK)
 "Deploy NewtonAI Agent")
                                                                                    │
                                                                                    ▼
[5. Protected Action Granted] ◄── [4. Secure Backend Validation] ◄───────── [Result Received]
(+150 UniCoins, Pro Pass,          (Firestore Transaction Ledger &          (Nullifier & Proof Payload)
 "Orb Verified 🌐" Badge)          Sybil Score Verification)
```

### Complete Journey Execution (`WorldIdAgentPassService.kt`):
1. **Triggering Protected Action**:
   A student clicks **"Claim Day 7 Reward (+150 UniCoins & Pro Pass)"** or **"Deploy Autonomous Campus Agent"**.
2. **Verification Request**:
   `WorldIdAgentPassService.executeAgentPassProtectedAction()` initiates a verification request targeting `https://auth.world.org/v1/verify` with `app_id=app_ce59eafb4c64fb4dff9ac783c593702a` and `action=newton-streak-day7-v1`.
3. **User Completion**:
   The user completes Orb Proof of Human in World App.
4. **Secure Server Backend Validation**:
   The zero-knowledge proof payload and SHA-256 nullifier hash (`sha256("$uid:$action:$appId")`) are validated on secure server infrastructure ([`server/gate.js`](../server/gate.js) and Firestore transaction). Client secrets are never exposed on device.
5. **Execution of Protected Action**:
   Upon server validation, +150 UniCoins are credited, Pro Pass is unlocked, and the **`Orb Verified 🌐`** badge is displayed on their profile.

---

## 3. Unsuccessful / Denied / Cancelled / Expired Path

### Protection & Security Guarantee:
If a user cancels the verification in World App, declines permission, or provides an invalid/expired proof:
1. `WorldIdAgentPassService.kt` catches the non-200 HTTP response code or cancellation flag.
2. **Protected Action BLOCKED**:
   - UniCoins remain unchanged (+0 coins).
   - Pro Pass remains locked.
   - Entry is recorded in `worldIdAgentPassLedger` with status `VERIFIED_DENIED`.
   - The user receives explicit feedback: `"DENIED: World ID Agent Pass verification cancelled or rejected. Protected action BLOCKED."`

---

## 4. Integration Debrief & Developer Feedback Report

### A. Time to First Success
- **20 Minutes**: Integrating `https://auth.world.org/v1/verify` with `app_id=app_ce59eafb4c64fb4dff9ac783c593702a` was straightforward.

### B. Friction Encountered
- Mapping custom scheme callbacks (`unilink://worldid-callback`) vs. HTTPS web redirects required configuring `launchMode="singleTask"` in `AndroidManifest.xml` to avoid duplicate activity instances.

### C. Missing Capability or Documentation
- An official Kotlin / Android Jetpack Compose SDK for `auth.world.org` would eliminate manual HTTP JSON request formatting on mobile platforms.

### D. Single Improvement with Greatest Impact
- Real-time WebSocket or Server-Sent Events (SSE) stream on `https://auth.world.org/v1/verify` so native Android apps receive instant push notifications the millisecond an Orb scan completes in World App.
