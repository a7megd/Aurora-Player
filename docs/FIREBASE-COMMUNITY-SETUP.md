# Whisper Player Firebase community setup

The Android app uses Firebase Authentication (email/password) and Cloud Firestore.

## Console checklist
1. In Firebase Authentication > Sign-in method, enable **Email/Password** (not Email link).
2. Create the default Firestore database in Production mode.
3. Open Firestore > Rules, replace the rules with the contents of `overrides/firestore.rules`, then Publish.
4. Keep the Firebase project on Spark while testing. Watch Authentication and Firestore usage quotas before inviting a large audience.

## Account flow
- New users register with display name, email and password.
- The app sends an email verification link. Access to the player and community is granted only after verification.
- Password reset is sent by email.
- Firestore stores only public display name and aggregate play/listening counters. It does not store email addresses, local filenames, track titles, or audio files.

## Community statistics
- The leaderboard ranks the top 25 profiles by aggregate listening time.
- Counts are approximate client-reported usage statistics, not tamper-proof competitive scores. Strong anti-cheat validation would require trusted server-side processing.
- No profile photo upload is included in this free-tier MVP; the UI uses the display name.
