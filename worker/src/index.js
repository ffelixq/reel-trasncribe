const SUPPORTED_HOSTS = [
  "instagram.com",
  "tiktok.com",
  "youtube.com",
  "youtu.be",
  "x.com",
  "twitter.com",
  "facebook.com",
  "fb.watch",
];

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function isSupportedVideoUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" && url.protocol !== "http:") return false;
    const host = url.hostname.toLowerCase();
    return SUPPORTED_HOSTS.some((base) => host === base || host.endsWith("." + base));
  } catch {
    return false;
  }
}

function normalizeTranscript(content) {
  if (typeof content === "string") return content.trim();
  if (Array.isArray(content)) {
    return content
      .map((part) => (part && typeof part.text === "string" ? part.text.trim() : ""))
      .filter(Boolean)
      .join(" ")
      .trim();
  }
  return "";
}

export default {
  async fetch(request, env) {
    const requestUrl = new URL(request.url);

    if (request.method === "GET" && requestUrl.pathname === "/health") {
      return json({ ok: true });
    }

    if (request.method !== "POST" || requestUrl.pathname !== "/transcribe") {
      return json({ error: "not_found", message: "Use POST /transcribe." }, 404);
    }

    if (!env.SUPADATA_API_KEY) {
      return json({ error: "server_config", message: "SUPADATA_API_KEY is not configured." }, 500);
    }

    if (env.APP_TOKEN) {
      const suppliedToken = request.headers.get("X-App-Token") || "";
      if (suppliedToken !== env.APP_TOKEN) {
        return json({ error: "unauthorized", message: "Invalid app access token." }, 401);
      }
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "bad_request", message: "Request body must be JSON." }, 400);
    }

    const sourceUrl = typeof body.url === "string" ? body.url.trim() : "";
    if (!isSupportedVideoUrl(sourceUrl)) {
      return json(
        {
          error: "unsupported_url",
          message: "Use a public Instagram, TikTok, YouTube, X, or Facebook video URL.",
        },
        400,
      );
    }

    const supadataUrl = new URL("https://api.supadata.ai/v1/transcript");
    supadataUrl.searchParams.set("url", sourceUrl);
    supadataUrl.searchParams.set("text", "true");

    let upstream;
    try {
      upstream = await fetch(supadataUrl.toString(), {
        headers: {
          "x-api-key": env.SUPADATA_API_KEY,
          accept: "application/json",
        },
      });
    } catch (error) {
      return json(
        {
          error: "upstream_unreachable",
          message: error instanceof Error ? error.message : "Could not reach Supadata.",
        },
        502,
      );
    }

    const raw = await upstream.text();
    let payload = {};
    try {
      payload = raw ? JSON.parse(raw) : {};
    } catch {
      payload = {};
    }

    if (!upstream.ok || payload.error) {
      const message = payload.message || payload.details || payload.error || "Supadata could not transcribe this video.";
      return json(
        {
          error: payload.error || "supadata_error",
          message,
          upstreamStatus: upstream.status,
        },
        upstream.status >= 400 ? upstream.status : 502,
      );
    }

    const transcript = normalizeTranscript(payload.content);
    if (!transcript) {
      return json({ error: "empty_transcript", message: "Supadata returned no transcript text." }, 502);
    }

    return json({
      transcript,
      language: payload.lang || null,
      sourceUrl,
    });
  },
};
