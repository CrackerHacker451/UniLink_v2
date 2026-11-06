# ETHGlobal Mumbai — World ID Agent Pass Integration Report (`/worldid/agent_pass.md`)

This document details the integration of **World ID Agent Pass (`https://auth.world.org/`)** for **Newton's Lobby (`com.example.unilink`)**.

---

## 1. Executive Summary & Overview

- **Track**: World ID Agent Pass ($2,500) — ETHGlobal Mumbai Continuity Track
- **App ID**: `app_ce59eafb4c64fb4dff9ac783c593702a`
- **Integration Class**: [`app/src/main/java/com/example/unilink/worldid/WorldIdAgentPassService.kt`](../app/src/main/java/com/example/unilink/worldid/WorldIdAgentPassService.kt)
- **Live Agent Pass Endpoint**: `https://auth.world.org/v1/verify`

---

## 2. Complete User Journey: Moment of Verification Request to Protected Agent Action

Newton's Lobby enforces World ID Agent Pass at the **exact moment** a critical human action or autonomous agent execution is requested:

```
[1. Trigger Protected Action] ──► [2. Agent Pass Verification Request] ──► [3. World App Completion]
("Claim Day 7 Reward" /        ("https://auth.world.org/v1/verify")       (Orb Iris Biometric Scan)
 "Deploy Autonomous Agent")
                                                                                    │
                                                                                    ▼
[5. Protected Action Granted] ◄── [4. Secure Server Validation] ◄───────── [Result Received]
(+150 UniCoins, Pro Pass,          (Firestore Ledger Settlement &          (Nullifier & Proof Payload)
 "Orb Verified 🌐" Badge)          Sybil Score Check)
```

### Journey Steps:
1. **Triggering Protected Action**:
   - A student clicks **"Claim Day 7 Reward (+150 UniCoins & Pro Pass)"** or **"Deploy Autonomous Campus Agent"**.
2. **Identity & Verification Request**:
   - `WorldIdAgentPassService.kt` formulates a verification request targeting `https://auth.world.org/v1/verify` with `app_id=app_ce59eafb4c64fb4dff9ac783c593702a` and `action=newton-agent-execute-v1`.
3. **User Completion in World App**:
   - Launches World App where the user completes **Orb Proof of Human (Credential ID 1)**.
4. **Secure Server Backend Validation**:
   - The zero-knowledge proof payload and SHA-256 nullifier hash are validated in the secure backend (`server/gate.js` / Firestore transaction). Client secrets are never exposed on the device.
5. **Execution of Protected Action**:
   - Upon successful server validation, the protected action executes: +150 UniCoins are credited, Pro Pass is unlocked, and the **`Orb Verified 🌐`** badge is minted.

---

## 3. Unsuccessful / Denied / Expired Path Demonstration

### Security Enforcement:
If a user cancels, declines the verification request in World App, or provides an invalid/expired proof:
1. `WorldIdAgentPassService.kt` receives `httpCode != 200` or an explicit denial error.
2. **Protected Action BLOCKED**:
   - No UniCoins are awarded.
   - Pro Pass remains locked.
   - The user receives an in-app error: `"World ID Agent Pass verification denied or cancelled by user. Protected action blocked."`
   - Entry is recorded in `worldIdAgentPassLedger` with status `VERIFIED_DENIED`.

---

## 4. Integration Debrief & Developer Feedback

### A. Time to First Success
- **30 Minutes**: Setting up the API parameters and connecting the deep link URL schema (`https://auth.world.org/v1/verify?app_id=app_ce59eafb4c64fb4dff9ac783c593702a`) was straightforward.

### B. Friction Encountered
- **Deep Link Handling on Custom Schemes**:
  Handling custom schemes (`worldapp://`) vs HTTPS web endpoints required explicit Android intent flags (`FLAG_ACTIVITY_NEW_TASK`) to prevent WebView blocking.

### C. Missing Capabilities or Documentation
- An official Kotlin / Android Jetpack Compose SDK for `auth.world.org` would reduce manual JSON payload construction.

### D. Single Improvement with Greatest Impact
- Adding automated WebSocket / Server-Sent Events (SSE) pairing status streaming to `auth.world.org/v1/verify` so the mobile app gets instant real-time notification the millisecond the user completes the Orb scan in World App.
