# RemoteControl

AirDroid-style Android app: screen mirroring + remote tap/swipe control,
working over the **internet** (not just local WiFi) via a small relay server.

```
[Phone app] --outbound--> [Relay server on Render] <--outbound-- [Viewer (any browser)]
```

Phone and viewer both connect *out* to the relay, so neither needs a public
IP, port-forwarding, or to be on the same network. They find each other
using a 6-digit pairing code.

## Project layout

```
server/       Node.js relay server (deploy this to Render)
android-app/  Android Studio project (the app that runs on the phone)
viewer/       viewer.html — open this in any browser to watch/control the phone
```

## 1. Deploy the relay server to Render (free)

1. Push this repo to your own GitHub account (see below).
2. Go to https://render.com → sign up/login → **New +** → **Web Service**.
3. Connect your GitHub repo.
4. Set:
   - **Root Directory:** `server`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Instance Type:** Free
5. Deploy. Render gives you a URL like `https://remotecontrol-xxxx.onrender.com`.
6. Your WebSocket URL is the same host with `wss://` instead of `https://`,
   e.g. `wss://remotecontrol-xxxx.onrender.com`.

**Free tier note:** Render's free web services sleep after ~15 minutes of
inactivity and take 30–50 seconds to wake up on the next connection. That's
fine for personal use — just expect a short delay the first time you connect
after a while.

## 2. Build and run the Android app

1. Open the `android-app` folder in Android Studio.
2. Let Gradle sync (needs internet to fetch dependencies).
3. Connect a real phone (USB debugging on) — MediaProjection is unreliable
   on emulators.
4. Run ▶.
5. In the app:
   - Paste your relay's `wss://...` URL into the text field.
   - Tap **"1. Enable Accessibility Service"** → find "RemoteControl" in the
     list that opens → turn it ON. (Android requires this to be done
     manually — no app can enable it automatically, for your own security.)
   - Tap **"2. Start Screen Sharing"** → approve the screen-record dialog.
   - Note the 6-digit **pairing code** shown on screen — it stays the same
     across app restarts.

## 3. Open the viewer

1. Open `viewer/viewer.html` in any browser, on any device, anywhere.
2. Enter the same `wss://...` relay URL and the phone's pairing code.
3. Click **Connect**. The phone's screen should appear within a few seconds.
4. Click = tap. Click-and-drag = swipe. Buttons for Back/Home/Recents.

## How to push this repo to your own GitHub

```bash
cd RemoteControl-repo
git init
git add .
git commit -m "Initial commit: RemoteControl app + relay server + viewer"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

(Create the empty repo on github.com first, then run the commands above.)

## Limitations to know about

- **One phone per pairing code, and the relay currently supports multiple
  viewers per code** — but there's no authentication beyond the 6-digit
  code. Anyone who guesses/knows the code can connect. Fine for personal
  use; add a proper login if you ever share this more widely.
- Video is JPEG-frame based, not real H.264 video — noticeably less smooth
  than AirDroid's actual video codec. Good enough to see and tap around.
- Free Render tier sleeps when idle (see above).
- Google Play policy is strict about apps combining `MediaProjection` +
  `AccessibilityService` (it looks like spyware to automated review) —
  this is meant for personal/sideloaded use, not Play Store distribution.

## Possible next steps

- Real video encoding via `MediaCodec` (H.264) instead of JPEG frames
- File transfer between phone and viewer
- A proper pairing flow (QR code) and per-session authentication
- Persisting multiple device pairings for managing more than one phone
