# Secure release signing and human gate changes

## Android signing

The previous revision tracked Android keystores and signing passwords in the public repository. Treat those keys as compromised. Removing files from the current tree does not remove Git history. Keep a secure backup before merging and check which keys are registered with Google Play. If Play App Signing is enabled and an upload key was exposed, request an upload key reset; contact Play support for the appropriate app-signing-key rotation options if the app-signing key was exposed. Do not simply switch keys for an existing installed app without checking its signing lineage.

Configure all four environment variables locally or through protected CI secrets:

- `UNILINK_KEYSTORE_PATH` (absolute path recommended)
- `UNILINK_STORE_PASSWORD`
- `UNILINK_KEY_ALIAS`
- `UNILINK_KEY_PASSWORD`

Debug builds keep their normal debug signing. With all four release variables unset, release output is unsigned; never publish it. Partial configuration fails early. With all four set, run `./gradlew :app:bundleRelease` and verify the signing certificate before upload.

## Human gate

The backend revalidates the pairing's expiry, owner and nonce inside the transaction before recording a successful proof. World ID requests use an 8-second timeout signal. Provider response bodies are no longer logged, to avoid retaining proof-related data in operational logs.

Run `cd server && npm ci && npm test`. Before deployment, test fresh, expired, replayed and concurrent verification sessions against a staging Firebase project. Existing production data and deployment settings are unchanged by this branch.
