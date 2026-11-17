# KEA Prep — complete offline Master Book Android app

A native Kotlin + Jetpack Compose Android study app for Karnataka FDA, SDA, VAO and Group-C preparation. Its active study library is built **only** from the supplied master source:

```text
app/src/main/assets/source/KEA_Complete_Study_Material.txt
```

> **Independent preparation material — not an official KEA or Government of Karnataka product.** Always confirm the current recruitment notification, corrigenda, pattern, eligibility, office holders, schemes, statistics and current affairs from official/current sources before an exam.

## Complete material, topic-wise and folder-wise

The source book is retained verbatim for provenance. A deterministic generator converts it into a searchable offline library arranged as folders rather than title-only links:

- **13 Master Book topic folders** with **every numbered chapter 001–272** as a separate readable offline JSON reader asset.
- All chapter bodies are preserved from the source, with source-line provenance and reading-time estimates.
- Each reader page includes a clearly labelled **source-derived study aid**: its extracted section headings, quick recall cues, and active-recall prompts. These aids provide structure without inventing new facts or representing material as official.
- A **Rapid Revision** supplement keeps all **242 source question–answer** prompts both as a complete reader and offline SRS cards.
- A **Solved Maths Workbook** keeps all ten supplied sections and **111** step-by-step source examples (the source’s actual count; it exceeds the “100” headline claim).
- **514 offline flashcards**: one source-derived recall card for every numbered chapter plus all 242 rapid-revision Q&As.
- **24 source-derived quick checks**, built only from answers in the supplied rapid-revision section and explicitly labelled as **not previous-year questions**.

The app loads the local index and every chapter asset from APK assets, then searches their full content on-device. It does not need an account, a network connection, or a server to read/search/revise the master material.

```text
app/src/main/assets/
├── source/
│   └── KEA_Complete_Study_Material.txt             # supplied text, verbatim
└── content/master-book/
    ├── index.json                                  # navigation and integrity metadata
    ├── topics/
    │   ├── topic-01-history/001.json … 043.json
    │   ├── topic-02-constitution-public-administration/044.json … 063.json
    │   └── … topic-13-exam-strategy/257.json … 272.json
    └── supplements/
        ├── rapid-revision/rapid-revision-242.json
        └── solved-maths/section-01.json … section-10.json
```

## Study features

- Native Android navigation: **Home**, **Study**, **Practice**, **Ask Gemini**, **Profile**.
- Master Book folder → numbered chapter hierarchy, selected-text reader, local keyword search and source-line provenance.
- Bookmarks, completed-topic progress, local daily planner, optional exam-date field, dark theme and reader-size control.
- Offline spaced repetition with Again / Hard / Good / Easy scheduling stored on the device.
- Persistent **English / ಕನ್ನಡ / Both** UI preferences. The supplied content stays source-faithful; it is never silently machine-translated.
- Every topic can launch **Ask Gemini** with bounded selected-topic context, and the central AI tab always provides local-note search.

## Ask Gemini: secure and optional

The committed build ships with no AI key and no backend URL. It clearly says that Gemini is unavailable and offers offline note search instead. It never fabricates an online answer.

When a production owner deploys an authenticated secure backend, the route is:

```text
Android app → secure backend proxy → versioned local-note retrieval/RAG → Gemini → source-chip-backed answer
```

The Android app can send a selected chapter ID/title, up to 12,000 characters of its content as a retrieval hint, verification status, question and requested Kannada/English answer language. It calls only the configured HTTPS backend at `/v1/gemini/ask`; it does **not** call Gemini directly and must never contain a provider credential. See [secure Gemini integration](docs/GEMINI_BACKEND_INTEGRATION.md).

## Regenerate and validate content

Edit only the canonical source, then rebuild the generated folder tree:

```bash
node scripts/build-content-pack.js
node scripts/validate-learning-content.js
```

The validator checks source hashing, all 13 source-topic folders, the complete sequential 001–272 chapter range, reader-asset provenance, all rapid-revision and maths supplements, flashcard/quick-check counts, native feature wiring, and the absence of direct provider calls or client-side secrets.

## Build

Requirements: JDK 17 and Android SDK platform 35.

```bash
./gradlew :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the generator and validator, fails if the generated master-book folders are stale, and builds/uploads the debug APK.

## Autonomous Tutor: live enrichment without paid keys

The app now includes an **Always-Teaching** layer while retaining the complete offline Master Book as the primary source.

- **Auto-Teach 3-Minute Capsule:** Home prioritises missed concepts, then rotates unread chapters. It combines the local chapter recall cues, saved public-reference context, a source-heading memory ladder and two available source checks.
- **Auto Content Sync:** `AutoContentSync` refreshes a public raw JSON feed when connected and `LiveKnowledgeSyncWorker` schedules a network-constrained background refresh. Every successful result is stored locally in `kea_live_knowledge`; a failure never removes the offline cache.
- **One-tap/auto topic enrichment:** Opening a chapter with no cache starts a best-effort public-reference lookup. The chapter also exposes **Fetch More Exam Facts & PYQ Angles**. It uses key-free Wikipedia REST summaries (Kannada and English) plus Wikidata and stores source URLs with concise extracts.
- **Live current affairs:** `data/live/kea-live-feed.json` is the public raw cache. After merge, `.github/workflows/daily-kea-feed.yml` runs the transparent key-free feed builder against configured PIB/Karnataka Government public sources, writes source-linked headlines and source-derived recognition checks, and commits a change only when data changed.
- **Live practice + concept recovery:** Generated public-feed checks join Practice automatically. Every wrong answer receives **−0.25** net marks, a “Why you missed this” explanation and local 1-day → 3-day → 7-day review priority until two consecutive correct answers.
- **Hands-free audio:** A persistent **Listen & Learn** bar uses Android TextToSpeech (`en-IN` / `kn-IN` if installed) to speak core notes, cached extra facts, options, a recall pause and the explanation. Playback begins only after a learner taps Listen.
- **Busy-schedule nudges:** Profile can enable local reminders and set up to three device-local `HH:mm` times (default `08:00,13:30,21:30`). Android 13+ asks for notification permission; the notification uses a cached live headline or a safe local revision prompt.

No paid API key is used or stored. The public live-feed URL is build-configurable with `-PLIVE_CONTENT_URL=https://…/kea-live-feed.json`. The live layer stores only public topic titles and public source responses—never learner answers, account data or provider credentials. Full technical behavior and the GitHub publishing path are in [Autonomous Tutor design](docs/AUTONOMOUS_TUTOR.md).

## Verification policy

This source includes changing material through **August 2026**. Current affairs, present office holders, rankings, counts, dates, schemes, recruitment notices and corrigenda are not presented as current truth in November 2026. The app flags the current-affairs topic and selected changing material with **VERIFY** notices. Consult official KEA and primary government sources before relying on any changing claim.
