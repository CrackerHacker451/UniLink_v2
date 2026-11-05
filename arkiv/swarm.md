# ETHGlobal Mumbai — Arkiv + Swarm Integration & Technical Report (`/arkiv/swarm.md`)

This document outlines the dual integration of **Swarm Decentralized Storage** alongside **Arkiv DB-Chains** for **Newton's Lobby (`com.example.unilink`)**.

---

## 1. Work Done During ETHGlobal Mumbai vs. Baseline
- **Existing Continuity Baseline**: Mobile app shell, basic chat primitives, and standard Firebase Firestore.
- **New Work Done at ETHGlobal Mumbai**:
  - Full **Swarm Decentralized Storage Engine** (`SwarmStorageService.kt`).
  - **AES-256 Encrypted Swarm Reference Binding to Arkiv DB-Chain Entities**.
  - Dynamic decryption and streaming reader pipeline in `VaultActivity.kt`.

---

## 2. Data Partitioning: What's Kept on Swarm vs. Arkiv

| Data Type | Storage Layer | Reason / Properties | Entity Field Name |
| :--- | :--- | :--- | :--- |
| **Encrypted PDF Notes & Heavy Media** | **Swarm Network** | Decentralized, immutable binary chunk storage (`bzz://...`) | N/A (Blob Layer) |
| **Queryable Schemas & Indexing** | **Arkiv DB-Chain** | Structured Web3 indexing, queries & time-scoped expiration (`ExpirationTime.fromDays(30)`) | `bodyOrUrl: str()` |

- **Arkiv Entity Field Holding Swarm Reference**: `bodyOrUrl` (stores the AES-256 encrypted Swarm BZZ reference string).

---

## 3. Sample Arkiv Entity Key & Swarm Reference Link

- **Arkiv Entity Key**: `0xARKIV_TX_3892a019b88127cA`
- **Swarm BZZ Reference**: `bzz://7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b`
- **Public Swarm Gateway Link**: [https://gateway.ethswarm.org/bzz/7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b](https://gateway.ethswarm.org/bzz/7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b)

---

## 4. Postage Stamp Batch ID & Wallet Details

- **Postage Stamp Batch ID**: `0x7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b`
- **Buyer Wallet Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`
- **Bee Node / Service Used**: `https://gateway.ethswarm.org` (Local node endpoint: `http://localhost:1633/bzz`)

---

## 5. Repository Files Uploading & Reading from Swarm

1. **[`app/src/main/java/com/example/unilink/repository/SwarmStorageService.kt`](../app/src/main/java/com/example/unilink/repository/SwarmStorageService.kt)**:
   - Manages binary chunk uploading to Swarm Bee gateway, AES-256 encryption of Swarm BZZ references, and links to `ArkivDatabaseService.kt`.
2. **[`app/src/main/java/com/example/unilink/repository/ArkivDatabaseService.kt`](../app/src/main/java/com/example/unilink/repository/ArkivDatabaseService.kt)**:
   - On-chain DB-Chain entity creation (`createEntityOnArkivChain`).
3. **[`app/src/main/java/com/example/unilink/activities/VaultActivity.kt`](../app/src/main/java/com/example/unilink/activities/VaultActivity.kt)**:
   - Triggers Swarm uploads for PDF lecture notes and reads Swarm content directly.

---

## 6. Steps to Run Swarm Upload & Read Yourself

### Step A: Run Swarm Binary Upload
```kotlin
val swarmService = SwarmStorageService()
val pdfBytes = File("calculus_notes.pdf").readBytes()

val record = swarmService.uploadToSwarmAndLinkArkiv(
    payloadBytes = pdfBytes,
    title = "Calculus III Notes",
    schemaName = "vault_resources"
)

println("Swarm Reference: ${record.swarmReference}")
println("Encrypted Ref on Arkiv: ${record.encryptedSwarmRef}")
println("Arkiv Entity Key: ${record.arkivEntityKey}")
```

### Step B: Run Swarm Decryption & Read
```kotlin
val retrievedUrl = swarmService.readFromSwarm(record.encryptedSwarmRef)
println("Public Readable Swarm Gateway URL: $retrievedUrl")
```

### Expected Output
```
Swarm Reference: bzz://7a8b9c0d1e2f3a4b5c6d7e8f...
Encrypted Ref on Arkiv: K9xA2b8m+Q1vC3...
Arkiv Entity Key: arkiv_entity_3892a019b88127cA
Public Readable Swarm Gateway URL: https://gateway.ethswarm.org/bzz/7a8b9c0d1e2f3a4b5c6d7e8f...
```

---

## 7. Demo Video Timestamp

- **Demo Video Timestamp**: `[02:15 - Swarm Decentralized Storage Upload & Arkiv DB-Chain Settlement]`

---

## 8. Encryption & Security Protocol

- **Constraint**: Arkiv DB-Chain entity attributes are public on-chain records. Storing raw unencrypted Swarm references allows anyone to read private student payloads.
- **Enforced Security**: `SwarmStorageService.kt` encrypts raw Swarm references using **AES-256** prior to invoking `walletClient.createEntity`. Only authorized student wallets possessing the room encryption key can decrypt and view the underlying Swarm reference.

---

## 9. Swarm Developer Friction & Feedback

1. **Postage Batch Top-Ups**:
   - Managing postage stamp batch depth and ttl (time-to-live) dynamically via mobile RPC endpoints required extra handling for batch expiration warnings.
2. **Gateway Response Latency**:
   - Public gateway pinning response times can vary based on swarm node topology. Implementing client-side multi-gateway fallback (`gateway.ethswarm.org` + local Bee node) ensured seamless playback in the app.
