# Architecture

```text
Instagram / TikTok / YouTube / X / Facebook
                    |
                    | Android ACTION_SEND (text/plain)
                    v
            Reel Transcribe app
                    |
                    | POST /transcribe
                    | X-App-Token (optional)
                    v
             Cloudflare Worker
                    |
                    | x-api-key (secret, server-side only)
                    v
              Supadata API
                    |
                    v
             Actual transcript
                    |
          +---------+----------+
          |                    |
     local SQLite         Copy / ChatGPT
```

## Security model

- `SUPADATA_API_KEY` is stored only as a Cloudflare Worker secret.
- The Android APK never contains the Supadata key.
- `APP_TOKEN` is optional but recommended for a personal deployment so random callers cannot spend Supadata credits through the Worker URL.
- The Worker only accepts known social-video hosts and cannot be used as a generic HTTP proxy.

## Android share flow

The launch activity declares an `ACTION_SEND` + `text/plain` intent filter. Android therefore lists **Transcribe video** in the system share sheet for apps that share text/URLs.

When a supported URL is received, the app immediately starts transcription. Successful transcripts are stored automatically in the app's SQLite database before being shown.
