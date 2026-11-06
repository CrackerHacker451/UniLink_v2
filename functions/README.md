# UniLink human gate (World ID, fail-closed)

Only users whose Firebase token has the `humanVerified` claim can use the app. Only this server can set that
claim, and only after the World ID Developer Portal confirmed a real proof. Errors, timeouts and unreachable
services all mean "not verified".

## One-time setup
1. **World ID Developer Portal** (https://developer.world.org): create an app, enable World ID 4.0, and keep
   `app_id`, `rp_id` and the `signing_key` (it is shown once; never put it in the app or in git).
2. **Firebase**: upgrade to the Blaze plan (Cloud Functions need it), then from the repo root:
   ```bash
   firebase functions:secrets:set WORLD_RP_SIGNING_KEY      # paste the signing key
   printf 'WORLD_APP_ID=app_xxx\nWORLD_RP_ID=rp_xxx\n' > functions/.env.<your-firebase-project-id>
   cd functions && npm install && npm test && cd ..
   firebase deploy --only functions,hosting,firestore:rules,storage
   ```
   To test with the World ID simulator, also add `WORLD_ENVIRONMENT=staging` to that env file.
3. **Android**: set `human_gate_base_url` in `app/src/main/res/values/human_gate.xml` to your hosting origin
   (for example `https://your-project.web.app`).

## What happens
App -> `/api/startVerification` (Firebase ID token) -> one-time session -> `hosting/verify.html` in a WebView ->
signed request -> World App (selfie / liveness check) -> proof -> `/api/verifyProof` -> World ID Developer Portal
-> nullifier stored (one human = one account) -> `humanVerified` claim -> Firestore/Storage rules open up.

Existing users must verify once on their next launch.
