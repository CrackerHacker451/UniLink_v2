# Arkiv Network Schema & Entity Specifications

This document outlines the entity schemas, query fields, wallet ownership model, and data lifecycle expiration policies implemented for **Newton's Lobby (`com.example.unilink`)** during ETHGlobal Mumbai.

---

## 1. Entity Schemas

### A. `posts` Entity (Campus Pulse & Project Showcase)

```typescript
import { defineSchema, addr, str, u64 } from "@arkiv-network/sdk";

export const Posts = defineSchema("posts", {
    author: addr().indexed(),
    title: str().indexed(),
    body: str(),
    publishedAt: u64().indexed()
});
```

- **Fields Queried**: `author`, `title`, `publishedAt`
- **Owner Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`
- **Expiration Policy**: `ExpirationTime.fromDays(30)` (Time-scoped DB-Chain lifecycle).

---

### B. `vault_resources` Entity (Permanent Study Notes & PDFs)

```typescript
import { defineSchema, addr, str, u64 } from "@arkiv-network/sdk";

export const VaultResources = defineSchema("vault_resources", {
    owner: addr().indexed(),
    resourceTitle: str().indexed(),
    summaryBody: str(),
    txHash: str().indexed(),
    timestampMs: u64().indexed()
});
```

- **Fields Queried**: `owner`, `resourceTitle`, `txHash`
- **Owner Address**: `0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D`
- **Expiration Policy**: `ExpirationTime.fromDays(90)` (Time-scoped study materials).

---

## 2. Runnable Query Example

```typescript
import { publicClient, eq } from "@arkiv-network/sdk";
import { Posts } from "./schema";

const results = await publicClient
    .select({ author: true, title: true, body: true, publishedAt: true })
    .from(Posts)
    .where(eq("title", "Newton's Lobby Smart Agent Engine"))
    .ownedBy("0x3F89a019b88127cA9b4e5f6A10B981C48D7C0F4D")
    .fetch();

console.log("Arkiv Query Results:", results);
```
