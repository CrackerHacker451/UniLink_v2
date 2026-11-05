# Arkiv Network Developer Friction & Feedback Report

This document records developer feedback, developer experience observations, and suggestions for the Arkiv Network team based on our integration during **ETHGlobal Mumbai**.

---

## 🌟 What Worked Phenomenally Well

1. **Intuitive Schema Definition (`defineSchema`)**:
   - The TypeScript SDK's `defineSchema` method made modeling queryable Web3 database entities as effortless as a traditional Web2 ORM (such as Prisma or Drizzle).

2. **Time-Scoped Lifecycle Management (`ExpirationTime.fromDays`)**:
   - The built-in expiration policy allows setting automated data pruning, ensuring cost efficiency for temporary campus announcements while allowing permanent extension for lecture notes.

3. **Familiar RPC Query Engine (`publicClient.select().from()`)**:
   - Querying indexed attributes over standard RPC endpoints without writing complex GraphQL subgraphs or custom indexers drastically accelerated development speed.

---

## 💡 Constructive Feedback & Friction Points

1. **Kotlin / Android Native SDK Bindings**:
   - Currently, the primary SDK is for TypeScript (`@arkiv-network/sdk`). Having an official Kotlin/Android Multiplatform SDK or UniFFI mobile binding would streamline direct native Android integration without requiring RPC wrapper mapping.

2. **Explorer Deep-Linking**:
   - Standardizing direct block explorer URLs for testnet entities (`https://explorer.arkiv.network/tx/{hash}`) with instant status feedback helps non-crypto users verify entity writes in real-time.

3. **Multi-Sig Wallet Ownership**:
   - Adding native support for multi-sig wallet ownership in `walletClient.createEntity` for team hackathon submissions where multiple students co-own an entity schema.
