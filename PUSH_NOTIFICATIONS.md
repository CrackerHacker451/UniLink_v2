# Background push notifications
Deploy rules and backend before distributing the new Android build. Test in a staging project first.

1. Run `cd functions && npm install && npm test`.
2. From the repo root, select the correct Firebase project, then deploy `firebase deploy --only firestore:rules,functions:push`. Cloud Functions requires a Blaze billing plan; review costs and region first.
3. Distribute the updated app. Each user must open it once and grant notification permission. One active token per account is supported; legacy public profile tokens are no longer used and should be removed from production separately.
4. Test foreground, background, lock screen, swipe from recents, notification tap, permission denial, logout and token rotation on two real devices. Android Settings Force stop prevents delivery until manual reopening; OEM restrictions may delay it.

The backend sends generic high-priority data pushes; private chat text stays in Firestore. The client filters recipient UIDs and opens the notification inbox. No persistent foreground service or battery optimization bypass is required. Call signaling and actual delivery need device testing. Function deployment, rules and Android build are not verified by this change. Server-side rate limiting and stronger authorization for non-chat activity producers remain follow-up work.
