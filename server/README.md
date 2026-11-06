# UniLink human gate server (free, no credit card)

Runs on Netlify's free plan. It signs the World ID request, asks World ID itself whether the proof is real,
stores the nullifier (one human = one account) and then sets the `humanVerified` flag on the user.
Every error, timeout or missing setting means "not verified". Nothing here ever fails open.

## One-time setup
1. **World ID Developer Portal** (https://developer.world.org): create an app, enable World ID 4.0, keep
   `app_id`, `rp_id` and the `signing_key` (shown once; never commit it).
2. **Firebase service account** (free): Firebase console -> Project settings -> Service accounts ->
   Generate new private key. Open the downloaded JSON, copy ALL of it, and make it a single line.
   Never commit this file: it has full admin access to your project.
3. **Netlify** (https://netlify.com, sign up with GitHub, no card): Add new site -> Import from Git ->
   pick this repo and branch. Set **Base directory** to `server`, leave the build command empty, and
   set **Publish directory** to `public`.
4. In Site configuration -> Environment variables, add these (mark the last two as secret):
   `WORLD_APP_ID`, `WORLD_RP_ID`, `WORLD_RP_SIGNING_KEY`, `FIREBASE_SERVICE_ACCOUNT`.
   For simulator testing also add `WORLD_ENVIRONMENT=staging`. Then trigger a deploy.
5. Put your Netlify address (for example `https://your-site.netlify.app`) in
   `app/src/main/res/values/human_gate.xml`.
6. Deploy the rules with the Firebase CLI (free): `firebase deploy --only firestore:rules,storage`.
   Do this last: it locks out everyone without the verified flag.

Check it works: `curl -s -X POST https://your-site.netlify.app/api/startVerification` should answer
`{"success":false,"error":"unauthenticated"}`.

Tests: `cd server && npm install && npm test`.
