# Autonomous Tutor and live-knowledge design

## What runs on the Android device

`AutonomousTutor.kt` adds a key-free, offline-first enrichment layer to the native Compose app.

1. **`AutoContentSync`** downloads the public raw JSON feed configured by `LIVE_CONTENT_URL` whenever connectivity is available. The default is:

   ```text
   https://raw.githubusercontent.com/modernnavi-star/kea-prep/main/data/live/kea-live-feed.json
   ```

   The feed and all fetched topic notes are cached in a separate device-local `SharedPreferences` store named `kea_live_knowledge`. A failed request never deletes the last successful cache.

2. **Topic enrichment and deep dives** run automatically on opening a reader page when no cache exists, and can always be retried through **Refresh saved facts**. Every chapter, source-heading subtopic chip, Home capsule and quiz question also offers **Get More Details from Internet**. Its native dialog accepts a custom follow-up term and requests the public English/Kannada Wikipedia `generator=search` plaintext-extract endpoints plus Wikidata with `origin=*`; it does not send account, progress, answers or personal data. Results stay transient until the learner taps **Save to My Offline Notes**, after which concise cited extracts and public URLs are cached locally and become available to audio. The UI labels this material as external reference and **VERIFY**.

3. **`LiveKnowledgeSyncWorker`** is scheduled by `KeaPrepApplication` with an Android network constraint. It refreshes the public feed every 12 hours when Android allows background work. WorkManager is deliberately best-effort rather than a guarantee of an exact clock time.

4. **`TutorAudioPlayer`** uses Android `TextToSpeech`, with `en-IN` and `kn-IN` when installed. It builds an audio sequence from local core notes, cached external facts and two available checks. Audio starts only after the learner taps Listen, as Android should not begin speech unexpectedly at app launch.

5. **`TutorReminderWorker`** posts an optional local notification using the cached feed or a safe local revision prompt. The learner can enable it and customise up to three `HH:mm` local times in Profile. Android 13+ asks for notification permission; without permission, the worker safely does nothing.

## Current-affairs publishing path

```text
Official PIB / Karnataka Government public pages
            ↓
.github/workflows/daily-kea-feed.yml (scheduled, key-free)
            ↓
scripts/build-live-feed.py
            ↓
data/live/kea-live-feed.json on main
            ↓
Android AutoContentSync → device-local offline cache
```

The feed generator keeps source headline/title, short source description, timestamp and original URL. It does **not** scrape a paid API, use an API key, call an LLM, paraphrase facts, or claim that a headline is final exam truth. Its four-option checks are recognition checks built from separate published headlines and are marked VERIFY.

## Teaching and scoring rules

- Home selects a missed/weak topic first; otherwise it rotates unread master-book content. The capsule contains core recall points, any saved extra facts, a source-heading memory ladder and up to two live source checks.
- A wrong answer changes the local net quiz score by **−0.25** while a correct answer earns **+1**.
- A wrong answer enters the concept into a local 1-day, then 3-day, then 7-day queue. Two consecutive correct answers mark that concept mastered and remove its weak-topic priority.
- The “Why you missed this” card shows the original explanation and cached reference context when available.

## No-provider-key policy

`LIVE_CONTENT_URL` is public content metadata, not a credential. The existing optional Gemini feature remains backend-mediated; this tutor feature does not add an AI provider key or direct Gemini call. All changing public-reference/current-affairs material remains subject to official verification before an exam.
