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

## Verification policy

This source includes changing material through **August 2026**. Current affairs, present office holders, rankings, counts, dates, schemes, recruitment notices and corrigenda are not presented as current truth in November 2026. The app flags the current-affairs topic and selected changing material with **VERIFY** notices. Consult official KEA and primary government sources before relying on any changing claim.
