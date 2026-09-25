# Reel Transcribe

A personal Android share-target app that turns public social-video links into the **actual transcript**, saves them locally, and makes them easy to send to ChatGPT for deeper research.

## What it does

- Appears in the Android share sheet as **Transcribe video**.
- Receives shared `text/plain` URLs from Instagram, TikTok, YouTube, X/Twitter, and Facebook.
- Starts transcription automatically — there is no second "Transcribe" tap after sharing.
- Calls Supadata through a Cloudflare Worker, keeping the Supadata API key off the phone.
- Automatically stores successful transcripts locally in SQLite.
- Lets you:
  - copy only the transcript;
  - copy a ready-made ChatGPT research prompt + transcript;
  - copy the prompt and immediately open ChatGPT;
  - revisit or delete previous transcripts.
- Also accepts a pasted URL manually for testing.

## User flow

```text
Instagram / TikTok
       |
      Share
       |
       v
Transcribe video
       |
       v
App opens -> "Getting the actual transcript..."
       |
       v
Cloudflare Worker -> Supadata
       |
       v
Transcript saved + displayed
       |
       +--> Copy
       +--> Copy + GPT prompt
       +--> Copy prompt & open ChatGPT
```

## Project layout

```text
app/       Native Android app (Kotlin + Jetpack Compose)
worker/    Cloudflare Worker proxy for Supadata
docs/      Architecture notes
.github/   CI that builds the APK and dry-runs the Worker
```

## 1. Get a Supadata API key

Create a Supadata account and copy your API key. Supadata's `/v1/transcript` endpoint accepts public Instagram/TikTok/etc. URLs directly; this project requests `text=true` so the Worker receives plain transcript text.

Do **not** put the Supadata key into the Android source code.

## 2. Deploy the Cloudflare Worker

Requirements: Node.js 20+ and a Cloudflare account.

```bash
cd worker
npm install
npx wrangler login
npx wrangler secret put SUPADATA_API_KEY
```

Recommended: protect your personal Worker with a second token:

```bash
npx wrangler secret put APP_TOKEN
```

Use a long random value and remember it; you will enter the same value in the Android app settings.

Deploy:

```bash
npm run deploy
```

Wrangler prints a URL similar to:

```text
https://reel-transcribe-api.<your-subdomain>.workers.dev
```

Health check:

```bash
curl https://reel-transcribe-api.<your-subdomain>.workers.dev/health
```

Expected:

```json
{"ok":true}
```

For local Worker development, copy `worker/.dev.vars.example` to `worker/.dev.vars` and fill in the values. `.dev.vars` is gitignored.

## 3. Build/install the Android app

### Android Studio

1. Clone/open this repository in Android Studio.
2. Let Gradle sync.
3. Connect your Android phone with USB debugging enabled (or use an emulator).
4. Run the `app` configuration.

The project targets Android API 37 and requires JDK 17.

### GitHub Actions APK

The `CI` workflow builds a debug APK on pushes and pull requests. Open a successful workflow run and download the `reel-transcribe-debug-apk` artifact, then install `app-debug.apk` on your phone.

## 4. Configure the app

Open **Reel Transcribe -> Settings** and enter:

- **Worker URL**: the `https://...workers.dev` URL from the deployment.
- **App access token**: only if you configured the Worker's `APP_TOKEN` secret.

No Supadata key is entered on the phone.

## 5. Use it from Instagram/TikTok

1. Open a public Reel/TikTok.
2. Tap **Share**.
3. Choose **Transcribe video**.
4. Reel Transcribe opens and immediately starts fetching the transcript.
5. When complete, the transcript is automatically saved and displayed.
6. Tap **Copy prompt & open ChatGPT** to put the full research prompt on your clipboard and launch ChatGPT. Paste and send.

If Android initially hides the app farther down the share sheet, use the share-sheet edit/pin controls available on your phone to keep it near the top.

## Supported hosts

- Instagram
- TikTok (including `vm.tiktok.com` links)
- YouTube / youtu.be
- X / Twitter
- Facebook / fb.watch

The source video must be publicly accessible to the transcription provider.

## Privacy and cost notes

- Transcripts are stored locally on the Android device in SQLite.
- Video URLs are sent to your Cloudflare Worker and then Supadata for transcription.
- No OpenAI API is used by this project.
- Opening ChatGPT only launches your existing ChatGPT app/browser after copying text to the clipboard.
- Supadata usage is billed/limited according to your Supadata account plan.

## Troubleshooting

### App does not appear in Share

The app declares Android `ACTION_SEND` for `text/plain`. Install the APK, then try sharing a link again. Some source apps cache their share sheet; reopening the source app can refresh it.

### `401 Invalid app access token`

The token in Android Settings must exactly match the Worker's `APP_TOKEN` secret. If you do not want this protection, remove the Worker secret and leave the app field empty.

### `SUPADATA_API_KEY is not configured`

Run:

```bash
cd worker
npx wrangler secret put SUPADATA_API_KEY
npm run deploy
```

### Private/unsupported video

Supadata must be able to access the public video URL. Private, follower-only, deleted, or region-restricted content can fail.

## Development

Worker syntax/deploy dry run:

```bash
cd worker
npm install
npm run check
```

Android debug build (with Gradle 9.6 installed):

```bash
gradle :app:assembleDebug
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the data flow and security model.
