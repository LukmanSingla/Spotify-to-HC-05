# Spotify HC-05 Bridge

Android app starter that receives Spotify Device Broadcast Status metadata and sends `Track - Artist\n` to a paired HC-05 using Bluetooth Classic SPP.

## Before testing
1. Pair HC-05 in Android Settings.
2. In Spotify, enable **Device Broadcast Status** (Spotify Settings → Devices; menu wording can vary).
3. Open the app, select the HC-05, and tap **Connect to HC-05**.
4. Play/change a Spotify track while keeping the app open.

The first version is a starter for testing, not a verified release. Some Spotify/Android versions may not deliver metadata broadcasts to third-party apps. If connection fails, close Serial Bluetooth Terminal first because a serial device generally accepts one active connection at a time.
