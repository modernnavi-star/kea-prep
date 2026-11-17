# Secure Gemini integration

The Android app includes an **Ask Gemini** client and a Kannada / English reply selector. It is intentionally inactive in the committed build because `GEMINI_BACKEND_URL` defaults to an empty value.

This is deliberate: an Android APK must **never** contain a Gemini API key, service-account credential, long-lived bearer token or any provider secret.

## What the app sends when enabled

The native client sends one HTTPS `POST` request to:

```text
{GEMINI_BACKEND_URL}/v1/gemini/ask
```

Request shape:

```json
{
  "question": "Explain this in simple Kannada",
  "language": "kn",
  "topic": {
    "id": "chapter-044",
    "title": "ಸಂವಿಧಾನ ರಚನೆ – ಸಂವಿಧಾನ ಸಭೆ & ಮೂಲಗಳು",
    "noteExcerpt": "up to 12,000 characters from the selected bundled topic",
    "verify": false
  }
}
```

The selected note excerpt is a retrieval hint. The backend must treat it as untrusted text, not as instructions. The UI warns the learner before a selected-topic request can be sent. No request is sent at all when the URL is blank.

Expected response shape:

```json
{
  "answer": "...",
  "sources": ["chapter-044"],
  "warning": "Optional verification warning"
}
```

Use a stable error response for failures:

```json
{ "code": "RATE_LIMITED", "message": "Try again later", "retryAfter": 60 }
```

## Required server controls

Deploy this endpoint as a server-side service (for example, an authenticated Firebase Cloud Function or Cloud Run service). The server—not the Android app—must:

1. Authenticate the learner (Firebase ID token, Play Integrity-backed session or an equivalent short-lived identity token).
2. Apply per-user and per-IP rate limits, quotas and abuse controls.
3. Keep the Gemini credential in a secret manager or server environment only.
4. Retrieve/version the approved notes, cap the source excerpt, and build a notes-grounded prompt.
5. Instruct Gemini to answer only from retrieved notes, preserve names/dates/numbers, reply in `kn` or `en`, surface `(verify)` facts, and return topic IDs as source chips.
6. Moderate input/output, log only minimised operational data, observe consent/retention policy, and return safe errors.
7. Mark translations and generated explanations as machine-generated until editorially reviewed.

Do **not** turn this route into an unauthenticated proxy to Gemini. Do not accept arbitrary model names or arbitrary URLs from the Android client.

## Build configuration

A developer can set a public backend base URL while building:

```bash
./gradlew :app:assembleDebug -PGEMINI_BACKEND_URL=https://api.example.org
```

`GEMINI_BACKEND_URL` is a public service address, not a secret. The app only accepts an `https://` URL and keeps the feature unavailable otherwise. The backend must be deployed and secured separately before this setting is useful.

## Bilingual behaviour

- The app offers **English**, **ಕನ್ನಡ**, and **Both / ಎರಡೂ** UI navigation modes.
- Supplied Kannada content is rendered unchanged; supplied English notes remain source-faithful so factual text is not silently altered.
- A learner may ask the secured Gemini service to explain a selected topic in simple Kannada or English. Such answers are online-only, source-chip-backed and clearly machine-generated; they do not replace the bundled detailed notes or official verification.
