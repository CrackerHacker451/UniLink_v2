# ETHGlobal Mumbai — Arkiv Track Judging Criteria

This document addresses each judging criterion for the **Arkiv Track** at ETHGlobal Mumbai.

---

## 1. Web3 Database Integration

- **SDK Used**: `@arkiv-network/sdk@1.0.0`
- **RPC Endpoint**: `https://rpc.arkiv.network`
- **Implementation File**: [`app/src/main/java/com/example/unilink/repository/ArkivDatabaseService.kt`](../app/src/main/java/com/example/unilink/repository/ArkivDatabaseService.kt)
- **Writes On-Chain**:
  - `createEntityOnArkivChain("posts", title, bodyOrUrl, 30)` is invoked in `VaultActivity.kt` when saving scanned AI study notes and in `ProjectDetailsActivity.kt` when publishing project showcases.
- **Reads From-Chain**:
  - `queryArkivEntities("posts", ownerAddress)` fetches on-chain entities directly in `VaultActivity.kt` and `ProjectDetailsActivity.kt` to render permanent Web3 verification badges.

---

## 2. Wallet Address & Runnable Query

- **Writer Wallet Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`
- **Runnable Query**:
  ```typescript
  import { publicClient, eq } from "@arkiv-network/sdk";
  import { Posts } from "./schema";

  const results = await publicClient
      .select({ author: true, title: true, body: true, publishedAt: true })
      .from(Posts)
      .where(eq("title", "Newton's Lobby Smart Agent Engine"))
      .ownedBy("0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D")
      .fetch();
  ```

---

## 3. Work Done During ETHGlobal Mumbai vs. Existing Baseline

- **Existing Continuity Baseline**:
  - Mobile layout frame, basic Firebase Auth & Firestore chat primitives.
- **Added at ETHGlobal Mumbai**:
  - Full **Arkiv DB-Chain Integration** (`ArkivDatabaseService.kt`, `arkiv/schema.md`, `arkiv/friction.md`).
  - **World ID 4.0 IDKit Liveness Trust Package** (`com.example.unilink.trust`, `proofOfHuman` account verification, `selfieCheck` team creation gate).
  - **0G Private Computer** verifiable AI inference (`0gm-1.0` / `qwen-omni`) & daily iAI compute credits.
  - **Chainlink CRE** workflow trigger.
  - **Atomic Firestore transaction-based SpeedLink peer selection** (`db.runTransaction`).
  - **Android 15/16 16 KB Page-Size Device Compatibility** (`useLegacyPackaging = false`, `android:extractNativeLibs="false"`).

---

## 4. AI Disclosure

In compliance with ETHGlobal rules:
- **AI Models & Frameworks Used**: UniAI integrated with **0G Private Computer** (`0gm-1.0` / `qwen-omni`) for decentralized zero-knowledge verified AI inference, and Google Gemini API for multimodal image pixel sampling and lecture note summarization.
- **Role of AI**: AI assisted in generating smart campus copilot responses, analyzing lecture note images in the Vault, and suggesting code refactorings. All architecture design, Web3 integrations, and Kotlin/Compose implementations were authored by the team.
