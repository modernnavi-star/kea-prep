#!/usr/bin/env node
/* Validates the complete folder-wise KEA/KPSC master-book bundle. */
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const assets = path.join(root, "app", "src", "main", "assets");
const sourcePath = path.join(assets, "source", "KEA_Complete_Study_Material.txt");
const retiredSourcePath = path.join(assets, "source", "KEA_FDA_All_6_Notes_Merged.txt");
const contentRoot = path.join(assets, "content", "master-book");
const packPath = path.join(contentRoot, "index.json");
const retiredPackPath = path.join(assets, "content", "kea-fda-pack.json");
const activityPath = path.join(root, "app", "src", "main", "java", "com", "modernnavi", "keaprep", "MainActivity.kt");
const appBuildPath = path.join(root, "app", "build.gradle.kts");
const geminiGuidePath = path.join(root, "docs", "GEMINI_BACKEND_INTEGRATION.md");
const tutorPath = path.join(root, "app", "src", "main", "java", "com", "modernnavi", "keaprep", "AutonomousTutor.kt");
const applicationPath = path.join(root, "app", "src", "main", "java", "com", "modernnavi", "keaprep", "KeaPrepApplication.kt");
const manifestPath = path.join(root, "app", "src", "main", "AndroidManifest.xml");
const liveFeedPath = path.join(root, "data", "live", "kea-live-feed.json");
const liveBuilderPath = path.join(root, "scripts", "build-live-feed.py");
const dailyFeedWorkflowPath = path.join(root, ".github", "workflows", "daily-kea-feed.yml");
const legacyAssets = ["index.html", "lesson-notes.js", "syllabus-branches.js"];

assert.ok(fs.existsSync(sourcePath), "The canonical supplied master book must be bundled.");
assert.ok(fs.existsSync(packPath), "The folder-wise master-book index must be bundled.");
assert.equal(fs.existsSync(retiredSourcePath), false, "The retired six-file notes source must not remain active.");
assert.equal(fs.existsSync(retiredPackPath), false, "The retired flat six-file pack must not remain active.");
legacyAssets.forEach((file) => assert.equal(fs.existsSync(path.join(assets, file)), false, `Old active asset must be deleted: ${file}`));

const source = fs.readFileSync(sourcePath, "utf8").replace(/\r\n/g, "\n");
const sourceLines = source.endsWith("\n") ? source.slice(0, -1).split("\n") : source.split("\n");
const sourceHash = crypto.createHash("sha256").update(source).digest("hex");
const pack = JSON.parse(fs.readFileSync(packPath, "utf8"));
const partTwoLine = sourceLines.findIndex((line) => /◆\s*ಭಾಗ\s*2\s*:/u.test(line));
const mathLine = sourceLines.findIndex((line) => /π\s*ಭಾಗ\s*3\s*:/u.test(line));
const masterMarkers = sourceLines
  .slice(0, partTwoLine)
  .flatMap((line, index) => /^\d{3}$/.test(line.trim()) ? [{ number: Number(line.trim()), sourceLine: index + 1 }] : []);
const tocTopicCounts = sourceLines
  .slice(0, partTwoLine)
  .flatMap((line) => {
    const match = line.match(/^ಟಾಪಿಕ್\s+(\d{2})\s*:\s*(.+?)\s+—\s*ಪುಟ\s*ರಿಂದ\s*•\s*(\d+)\s+ಅಧ್ಯಾಯ/u);
    return match ? [{ number: Number(match[1]), title: match[2].trim(), count: Number(match[3]) }] : [];
  });

assert.equal(pack.packVersion, 2, "Unexpected master-book content-pack version.");
assert.equal(pack.sourceFile, "KEA_Complete_Study_Material.txt", "Pack must name the supplied master-book source.");
assert.equal(pack.sourceSha256, sourceHash, "Generated pack must match the bundled canonical source.");
assert.equal(pack.sourceLineCount, sourceLines.length, "Source line count must be honest.");
assert.equal(pack.masterTopicCount, 13, "The pack must expose the 13 supplied top-level topics.");
assert.equal(pack.masterChapterCount, 272, "The pack must expose all 272 numbered source chapters.");
assert.equal(pack.supplementCount, 11, "The rapid revision plus ten maths sections must remain accessible.");
assert.equal(pack.rapidRevisionQuestionCount, 242, "All 242 supplied rapid-revision Q&As must remain counted.");
assert.ok(pack.solvedMathsExampleCount >= 100, "The solved maths supplement must keep at least the claimed 100 examples.");
assert.equal(tocTopicCounts.length, 13, "The source TOC must declare 13 master topics.");
assert.equal(masterMarkers.length, 272, "The source body must contain all 272 chapter markers.");
assert.deepEqual(masterMarkers.map((marker) => marker.number), Array.from({ length: 272 }, (_, index) => index + 1), "Master chapters must be sequential 001–272.");
assert.equal(tocTopicCounts.reduce((total, topic) => total + topic.count, 0), 272, "TOC chapter totals must equal 272.");

const masterSubjects = pack.subjects.filter((subject) => subject.kind === "master");
const supplementSubjects = pack.subjects.filter((subject) => subject.kind === "supplement");
const masterTopics = masterSubjects.flatMap((subject) => subject.topics);
const supplementTopics = supplementSubjects.flatMap((subject) => subject.topics);
assert.equal(pack.subjects.length, 15, "Expected 13 master topic folders plus two supplement folders.");
assert.equal(masterSubjects.length, 13, "Every supplied top-level topic needs a folder in the app.");
assert.equal(supplementSubjects.length, 2, "Rapid revision and solved maths must remain separate supplement folders.");
assert.equal(masterTopics.length, 272, "Every numbered master chapter needs a reader metadata entry.");
assert.equal(supplementTopics.length, 11, "Expected rapid revision plus ten maths-reader entries.");
assert.deepEqual(masterTopics.map((topic) => topic.chapterNumber), Array.from({ length: 272 }, (_, index) => String(index + 1).padStart(3, "0")), "Every 001–272 chapter must be navigable in order.");
assert.equal(new Set(masterSubjects.map((subject) => subject.folder)).size, 13, "Each master topic must have its own distinct folder.");
assert.ok(masterSubjects.every((subject, index) => subject.id === `topic-${String(index + 1).padStart(2, "0")}`), "Master topic IDs must follow supplied topic order.");
assert.deepEqual(masterSubjects.map((subject) => subject.topics.length), tocTopicCounts.map((topic) => topic.count), "Topic folder chapter counts must follow the source TOC.");

for (const topic of masterTopics) {
  const assetPath = path.join(contentRoot, topic.assetPath);
  assert.ok(fs.existsSync(assetPath), `Missing complete reader asset for chapter ${topic.chapterNumber}: ${topic.assetPath}`);
  assert.match(topic.assetPath, /^topics\/topic-\d{2}-[^/]+\/\d{3}\.json$/, `Chapter ${topic.chapterNumber} must be stored topic-wise/folder-wise.`);
  const asset = JSON.parse(fs.readFileSync(assetPath, "utf8"));
  assert.equal(asset.id, topic.id, `Asset identity mismatch for chapter ${topic.chapterNumber}.`);
  assert.equal(asset.chapterNumber, topic.chapterNumber, `Chapter number mismatch in ${topic.assetPath}.`);
  assert.equal(asset.title, topic.title, `Chapter title mismatch in ${topic.assetPath}.`);
  assert.equal(asset.provenance.sourceFile, pack.sourceFile, `Provenance missing for chapter ${topic.chapterNumber}.`);
  assert.equal(asset.provenance.sourceSha256, sourceHash, `Source hash missing for chapter ${topic.chapterNumber}.`);
  assert.equal(asset.provenance.sourceLineStart, topic.sourceLineStart, `Source line start mismatch for chapter ${topic.chapterNumber}.`);
  assert.equal(asset.provenance.sourceLineEnd, topic.sourceLineEnd, `Source line end mismatch for chapter ${topic.chapterNumber}.`);
  assert.ok(asset.content.trim().length > 100, `Chapter ${topic.chapterNumber} is title-only or incomplete.`);
  assert.ok(source.includes(asset.content), `Chapter ${topic.chapterNumber} content is not traceable to the supplied source.`);
  assert.ok(asset.learningAid && Array.isArray(asset.learningAid.studySteps) && asset.learningAid.studySteps.length >= 2, `Chapter ${topic.chapterNumber} needs a labelled source-derived learning aid.`);
  assert.ok(Array.isArray(asset.learningAid.recallPrompts) && asset.learningAid.recallPrompts.length >= 1, `Chapter ${topic.chapterNumber} needs a recall prompt.`);
  assert.ok(topic.estimatedMinutes >= 5, `Chapter ${topic.chapterNumber} needs a practical reading estimate.`);
}

for (const requiredChapter of [
  ["001", "ಸಿಂಧೂ"], ["044", "ಸಂವಿಧಾನ"], ["064", "ಭೂಗೋಳ"], ["085", "ಭೌತಶಾಸ್ತ್ರ"],
  ["145", "ಸಂಖ್ಯೆಗಳು"], ["160", "ಕನ್ನಡ"], ["189", "ಪ್ರಚಲಿತ"], ["215", "ಕನ್ನಡ"],
  ["230", "Parts of Speech"], ["244", "Computer Fundamentals"], ["272", "ಅಂತಿಮ ಪುಟ"],
]) {
  const found = masterTopics.find((topic) => topic.chapterNumber === requiredChapter[0]);
  assert.ok(found && found.title.includes(requiredChapter[1]), `Required supplied chapter ${requiredChapter[0]} is missing or mislabelled.`);
}
assert.ok(masterSubjects.find((subject) => subject.id === "topic-09").topics.every((topic) => topic.verify), "Every current-affairs chapter must visibly carry a verification flag.");

const rapidSubject = supplementSubjects.find((subject) => subject.id === "supplement-rapid-revision");
const mathsSubject = supplementSubjects.find((subject) => subject.id === "supplement-solved-maths");
assert.ok(rapidSubject && rapidSubject.topics.length === 1, "Rapid revision needs one complete reader asset.");
assert.ok(mathsSubject && mathsSubject.topics.length === 10, "Solved maths must retain all ten source sections.");
for (const topic of supplementTopics) {
  const assetPath = path.join(contentRoot, topic.assetPath);
  assert.ok(fs.existsSync(assetPath), `Missing supplement reader: ${topic.title}`);
  const asset = JSON.parse(fs.readFileSync(assetPath, "utf8"));
  assert.ok(asset.content.length > 100, `Supplement reader is incomplete: ${topic.title}`);
  assert.ok(source.includes(asset.content), `Supplement reader is not traceable: ${topic.title}`);
}
assert.match(JSON.parse(fs.readFileSync(path.join(contentRoot, rapidSubject.topics[0].assetPath), "utf8")).content, /242\.\s+ಮೈಸೂರನ್ನು/u, "The last supplied rapid-revision Q&A must remain readable.");
assert.ok(JSON.parse(fs.readFileSync(path.join(contentRoot, mathsSubject.topics.at(-1).assetPath), "utf8")).content.includes("ಲೆಕ್ಕ 111."), "The final solved maths example must remain readable.");

assert.equal(pack.flashcards.length, 514, "Expected one source-derived recall card per chapter plus all 242 rapid Q&A cards.");
assert.equal(pack.flashcards.filter((card) => card.id.startsWith("card-chapter-")).length, 272, "Every chapter must have a source-derived SRS card.");
assert.equal(pack.flashcards.filter((card) => card.id.startsWith("card-rapid-")).length, 242, "Every rapid Q&A must be available as a spaced-repetition card.");
assert.equal(pack.questions.length, 24, "Expected the explicit source-derived quick-check set.");
assert.ok(pack.questions.every((question) => question.options.length === 4 && question.answer >= 0 && question.answer < 4), "Every source-derived quick check needs four valid options.");
assert.ok(pack.questions.every((question) => question.topicHint === "SOURCE-DERIVED RAPID REVISION"), "Practice questions must be derived only from the bundled rapid-revision section.");
assert.ok(pack.questions.every((question) => question.topicId === "supplement-rapid-revision"), "Bundled checks must identify their source topic for teaching/SRS routing.");

const activity = fs.readFileSync(activityPath, "utf8");
for (const requiredFeature of [
  "MainTab.HOME", "MainTab.STUDY", "MainTab.PRACTICE", "MainTab.ASK_AI", "MainTab.PROFILE",
  "content/master-book/", "assetPath", "masterChapterCount", "Search every bundled note", "FlashcardsPane", "scheduleCard",
  "SOURCE-DERIVED QUICK CHECK", "Gemini is not connected in this build", "Secure backend proxy", "SharedPreferences", "VERIFY",
  "Clear local learning data", "UiLanguage", "Ask Gemini", "GeminiGateway", "GEMINI_BACKEND_URL", "noteExcerpt",
  "Study aid · source-derived", "Master Book · 13 topic folders · 272 chapters",
  "AutoContentSync", "Fetch More Exam Facts & PYQ Angles", "Why you missed this", "−0.25 wrong",
  "AutoTeachCapsule", "AudioTutorBar", "TutorReminderScheduler", "Busy-schedule micro-notifications",
]) {
  assert.ok(activity.includes(requiredFeature), `Missing native app feature: ${requiredFeature}`);
}
assert.ok(fs.existsSync(geminiGuidePath), "The secure Gemini deployment contract must be documented.");
assert.match(fs.readFileSync(geminiGuidePath, "utf8"), /never\W+contain a Gemini API key/i, "Gemini guide must prohibit client credentials.");
assert.match(fs.readFileSync(appBuildPath, "utf8"), /GEMINI_BACKEND_URL/, "Only a public Gemini backend URL may be build-configurable.");
assert.doesNotMatch(activity, /WebView|WebViewAssetLoader|loadUrl\(/, "The build must remain a native Android UI, not a WebView app.");
assert.doesNotMatch(activity, /(sk-ant-|api[_-]?key\s*[=:]|ANTHROPIC_API_KEY|GEMINI_API_KEY)/i, "No AI provider secret/API key may be committed to the Android client.");
assert.doesNotMatch(activity, /generativelanguage\.googleapis\.com/i, "The Android client must call the secure backend, never Gemini directly.");
assert.doesNotMatch(activity, /KEA_FDA_All_6_Notes_Merged|kea-fda-pack|Pocket Edition|keaSyllabusBranches|keaLessonNotes|MaterialViewer/, "Superseded learning sources must not be active.");

assert.ok(fs.existsSync(tutorPath), "The autonomous tutor/sync implementation must be present.");
assert.ok(fs.existsSync(applicationPath), "The app must schedule background live sync.");
assert.ok(fs.existsSync(liveFeedPath), "A public raw-JSON live-feed baseline must be tracked.");
assert.ok(fs.existsSync(liveBuilderPath), "A key-free live-feed generator must be tracked.");
assert.ok(fs.existsSync(dailyFeedWorkflowPath), "The scheduled KEA feed workflow must be tracked.");
const tutor = fs.readFileSync(tutorPath, "utf8");
const manifest = fs.readFileSync(manifestPath, "utf8");
const dailyWorkflow = fs.readFileSync(dailyFeedWorkflowPath, "utf8");
for (const requiredTutorFeature of [
  "class AutoContentSync", "https://en.wikipedia.org", "https://kn.wikipedia.org", "api/rest_v1/page/summary",
  "www.wikidata.org/w/api.php", "LiveKnowledgeStore", "LiveKnowledgeSyncWorker", "TutorReminderWorker",
  "TutorAudioPlayer", "TextToSpeech", "LIVE_CONTENT_URL",
]) assert.ok(tutor.includes(requiredTutorFeature), `Missing autonomous tutor feature: ${requiredTutorFeature}`);
assert.match(fs.readFileSync(liveBuilderPath, "utf8"), /does not invent facts/i, "Live MCQ generator must not fabricate facts with a model.");
assert.match(dailyWorkflow, /schedule:/, "Live-feed workflow must run on a schedule.");
assert.match(dailyWorkflow, /contents: write/, "Live-feed workflow must be able to update its raw JSON cache.");
assert.match(manifest, /POST_NOTIFICATIONS/, "Optional tutor nudges require the notification permission declaration.");
assert.match(fs.readFileSync(applicationPath, "utf8"), /AutoContentSyncScheduler/, "Application must schedule background sync.");
assert.match(fs.readFileSync(appBuildPath, "utf8"), /LIVE_CONTENT_URL/, "Public raw live-feed URL must be build-configurable.");
assert.doesNotMatch(tutor, /(GEMINI_API_KEY|ANTHROPIC_API_KEY|sk-ant-|api[_-]?key\s*[=:])/i, "Autonomous tutor must not contain a paid-provider secret.");
const baselineFeed = JSON.parse(fs.readFileSync(liveFeedPath, "utf8"));
assert.equal(baselineFeed.schemaVersion, 1, "Unexpected raw live-feed schema.");
assert.ok(Array.isArray(baselineFeed.items) && Array.isArray(baselineFeed.mcqs), "Raw live feed needs items and MCQs arrays.");

console.log(`Validated master-book bundle: 13 folders, 272 complete chapters, 242 rapid Q&As, ${pack.solvedMathsExampleCount} solved maths examples, ${pack.flashcards.length} SRS cards and ${pack.questions.length} source-derived checks.`);
