# Whisper Player Firebase Functions

The chat UI and Android FCM token registration are included in the app source. Background push delivery requires deploying this Cloud Function to the same Firebase project used by the Android app.

## Deploy

1. Install Firebase CLI and authenticate with an account that has Firebase Functions deployment permission.
2. From the repository root, select the existing Firebase project that matches `AuroraPlayer/app/google-services.json`.
3. Run `npm --prefix functions install`.
4. Run `firebase deploy --only functions:notifyChatParticipants`.
5. Publish the current Firestore and Storage rules with `firebase deploy --only firestore:rules,storage`.

The function sends a push notification to the other participants whenever a chat message is created. It uses the Firebase Admin SDK's default service identity; do not put service-account private keys or service-role secrets in the repository.

Android 13 and later require the user to grant notification permission. The app requests it when opening chat. FCM delivery also requires Cloud Messaging to be enabled for the Firebase project and a valid Android `google-services.json`.
