#!/usr/bin/env node
/*
 * Builds the offline KEA/KPSC master-book bundle from the learner-supplied TXT.
 *
 * Design notes:
 * - The source file is retained verbatim in assets/source for provenance.
 * - Every one of the 272 numbered chapters receives its own JSON asset inside
 *   a topic folder, so no reader page is a title-only placeholder.
 * - The index carries only navigation metadata; the reader loads the full
 *   chapter assets locally at app startup for offline search.
 * - Learning aids are mechanical extracts of source headings/bullets. They are
 *   labelled in the app as source-derived aids, never as new official claims.
 */
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const root = path.join(__dirname, "..");
const sourcePath = path.join(root, "app", "src", "main", "assets", "source", "KEA_Complete_Study_Material.txt");
const contentRoot = path.join(root, "app", "src", "main", "assets", "content", "master-book");
const indexPath = path.join(contentRoot, "index.json");

if (!fs.existsSync(sourcePath)) {
  throw new Error(`Missing canonical master-book source: ${sourcePath}`);
}

const source = fs.readFileSync(sourcePath, "utf8").replace(/\r\n/g, "\n");
const lines = source.split("\n");
const sourceHash = crypto.createHash("sha256").update(source).digest("hex");
const partTwoIndex = lines.findIndex((line) => /◆\s*ಭಾಗ\s*2\s*:/u.test(line));
const mathIndex = lines.findIndex((line) => /π\s*ಭಾಗ\s*3\s*:/u.test(line));

if (partTwoIndex < 0 || mathIndex < 0 || mathIndex <= partTwoIndex) {
  throw new Error("Could not locate the rapid-revision and maths supplements in the master book.");
}

const folderSlugs = [
  "history",
  "constitution-public-administration",
  "geography-environment-disaster-management",
  "general-science",
  "social-science-economy",
  "indian-society-art-culture",
  "maths-reasoning",
  "karnataka-special",
  "current-affairs-static-gk",
  "kannada-grammar",
  "general-english",
  "computer-knowledge",
  "exam-strategy",
];

function cleanTitle(value) {
  return value
    .replace(/[|]+$/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function safeId(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function withoutOuterBlankLines(block) {
  const result = [...block];
  while (result.length && !result[0].trim()) result.shift();
  while (result.length && !result.at(-1).trim()) result.pop();
  return result;
}

function isSeparator(line) {
  return /^[=─—_-]{8,}$/.test(line.trim());
}

function chapterContent(block, title) {
  const body = [...block];
  let firstTitle = body.findIndex((line) => cleanTitle(line) === title);
  if (firstTitle < 0) firstTitle = 0;
  let start = firstTitle + 1;
  while (start < body.length && (!body[start].trim() || isSeparator(body[start]))) start += 1;
  const cleaned = withoutOuterBlankLines(body.slice(start)).map((line) => line.replace(/\s+$/u, ""));
  return cleaned.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

function extractHeadings(content) {
  const headings = [];
  for (const rawLine of content.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("•") || line.startsWith("➤") || /^\d+[.)]/u.test(line)) continue;
    if (
      /^(?:[✦◆✿❖★⚡◉]|π\s*ವಿಭಾಗ|ಪರೀಕ್ಷಾ ಫ್ಲ್ಯಾಷ್|ಫ್ಲ್ಯಾಷ್ ಸರಪಳಿ|ಅಂತಿಮ)/u.test(line) ||
      (line.length <= 72 && !/[.!?。]/u.test(line) && !line.includes("–") && !line.includes("="))
    ) {
      const normalized = cleanTitle(line.replace(/^[✦◆✿❖★⚡◉]\s*/u, ""));
      if (normalized.length >= 3 && !headings.includes(normalized)) headings.push(normalized);
    }
    if (headings.length === 5) break;
  }
  return headings;
}

function extractQuickReview(content) {
  const bullets = [];
  for (const rawLine of content.split("\n")) {
    const line = rawLine.trim().replace(/^•\s*/u, "");
    if (rawLine.trim().startsWith("•") && line.length >= 12 && !bullets.includes(line)) bullets.push(line);
    if (bullets.length === 3) break;
  }
  return bullets;
}

function chapterAid(content, title) {
  const sourceHeadings = extractHeadings(content);
  const quickReview = extractQuickReview(content);
  return {
    label: "Source-derived study aid · not a new official claim",
    studySteps: [
      "Read the full source chapter once before using this aid.",
      "Cover the quick-review points and recall them in your own words.",
      "Revisit the source headings during spaced revision; verify changing facts from an official current source.",
    ],
    sourceHeadings,
    quickReview,
    recallPrompts: (sourceHeadings.length ? sourceHeadings : [title]).slice(0, 3).map((heading) => `Explain this source section: ${heading}`),
  };
}

function needsVerification(content, subjectNumber) {
  if (subjectNumber === 9) return true;
  return /(\(verify\)|ಪರಿಶೀಲ|ಬದಲಾಗುವ|ತಾಜಾ|ಪ್ರಸ್ತುತ|ಹಾಲಿ|ಇತ್ತೀಚಿನ|ನೇಮಕ|ಬಜೆಟ್|current affairs|current office|202[4-9])/iu.test(content);
}

function writeJson(relativePath, value) {
  const absolutePath = path.join(contentRoot, relativePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
  fs.writeFileSync(absolutePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

// Read the 13 master topic declarations and their chapter counts from the supplied TOC.
const topicDeclarations = [];
for (let index = 0; index < partTwoIndex; index += 1) {
  const match = lines[index].match(/^ಟಾಪಿಕ್\s+(\d{2})\s*:\s*(.+?)\s+—\s*ಪುಟ\s*ರಿಂದ\s*•\s*(\d+)\s+ಅಧ್ಯಾಯ/u);
  if (match) {
    topicDeclarations.push({
      number: Number(match[1]),
      title: cleanTitle(match[2]),
      declaredChapterCount: Number(match[3]),
      tocLine: index + 1,
    });
  }
}
if (topicDeclarations.length !== 13) {
  throw new Error(`Expected 13 topic declarations in the supplied TOC, found ${topicDeclarations.length}.`);
}

// Numbered body markers only occur in the master-book body before Part 2.
const starts = [];
for (let index = 0; index < partTwoIndex; index += 1) {
  if (/^\d{3}$/.test(lines[index].trim())) starts.push({ index, number: Number(lines[index].trim()) });
}
if (starts.length !== 272 || starts.some((start, index) => start.number !== index + 1)) {
  throw new Error(`Expected a complete sequential 001–272 chapter body; found ${starts.length} markers.`);
}
if (topicDeclarations.reduce((sum, topic) => sum + topic.declaredChapterCount, 0) !== starts.length) {
  throw new Error("TOC chapter totals do not equal the numbered chapter body count.");
}

fs.rmSync(contentRoot, { recursive: true, force: true });
const subjects = [];
const chapterCards = [];
let chapterCursor = 0;

for (const declaration of topicDeclarations) {
  const topicNumber = declaration.number;
  const folder = `topics/topic-${String(topicNumber).padStart(2, "0")}-${folderSlugs[topicNumber - 1]}`;
  const subjectId = `topic-${String(topicNumber).padStart(2, "0")}`;
  const chapterStarts = starts.slice(chapterCursor, chapterCursor + declaration.declaredChapterCount);
  chapterCursor += declaration.declaredChapterCount;
  const topics = [];

  for (const [offset, start] of chapterStarts.entries()) {
    const next = starts[starts.indexOf(start) + 1];
    const endIndexExclusive = next ? next.index : partTwoIndex;
    const rawBlock = withoutOuterBlankLines(lines.slice(start.index + 1, endIndexExclusive));
    const title = cleanTitle(rawBlock.find((line) => line.trim() && !isSeparator(line)) || `Chapter ${start.number}`);
    const content = chapterContent(rawBlock, title);
    if (content.length < 100) throw new Error(`Chapter ${String(start.number).padStart(3, "0")} is unexpectedly short.`);
    const id = `chapter-${String(start.number).padStart(3, "0")}`;
    const assetPath = `${folder}/${String(start.number).padStart(3, "0")}.json`;
    const sourceLineStart = start.index + 1;
    const sourceLineEnd = endIndexExclusive;
    const learningAid = chapterAid(content, title);
    const topic = {
      id,
      chapterNumber: String(start.number).padStart(3, "0"),
      title,
      order: offset + 1,
      description: `Master Book · Topic ${String(topicNumber).padStart(2, "0")} · source lines ${sourceLineStart}–${sourceLineEnd}`,
      assetPath,
      verify: needsVerification(`${title}\n${content}`, topicNumber),
      estimatedMinutes: Math.max(5, Math.min(35, Math.ceil(content.length / 950))),
      sourceLineStart,
      sourceLineEnd,
    };
    writeJson(assetPath, {
      ...topic,
      content,
      learningAid,
      provenance: {
        sourceFile: path.basename(sourcePath),
        sourceLineStart,
        sourceLineEnd,
        sourceSha256: sourceHash,
        extraction: "Verbatim numbered chapter body from the learner-supplied master book; only surrounding decoration/title separators are omitted because the title is stored separately.",
      },
    });
    topics.push(topic);
    const review = learningAid.quickReview.length ? learningAid.quickReview.join("\n• ") : content.split("\n").filter(Boolean).slice(0, 3).join("\n");
    chapterCards.push({
      id: `card-${id}`,
      topicId: id,
      front: `ಅಧ್ಯಾಯ ${topic.chapterNumber}: ${title}\nName the key points before revealing the source-derived recall cues.`,
      back: `• ${review}`.slice(0, 1800),
    });
  }

  subjects.push({
    id: subjectId,
    code: `T${String(topicNumber).padStart(2, "0")}`,
    title: declaration.title,
    paper: topicNumber in { 10: true, 11: true, 12: true } ? "Paper 2" : "Master Book",
    kind: "master",
    folder,
    description: `Master Book topic ${String(topicNumber).padStart(2, "0")} · ${declaration.declaredChapterCount} complete readable chapters · folder ${folder}`,
    order: topicNumber,
    sourceTocLine: declaration.tocLine,
    topics,
  });
}
if (chapterCursor !== 272) throw new Error(`Assigned ${chapterCursor}, not 272, master chapters.`);

// Supplement 1: retain the complete rapid-revision Q&A section and turn its supplied 242 Q&As into SRS cards.
const rapidSection = withoutOuterBlankLines(lines.slice(partTwoIndex, mathIndex));
const rapidContent = rapidSection.join("\n").trim();
const rapidQuestions = [];
let pendingQuestion = null;
for (const line of rapidSection) {
  const question = line.match(/^(\d+)\.\s+(.+)$/u);
  if (question) {
    pendingQuestion = { number: Number(question[1]), question: cleanTitle(question[2]) };
    continue;
  }
  const answer = line.match(/^➤\s*(.+)$/u);
  if (answer && pendingQuestion) {
    rapidQuestions.push({ ...pendingQuestion, answer: cleanTitle(answer[1]) });
    pendingQuestion = null;
  }
}
if (rapidQuestions.length !== 242 || rapidQuestions.some((item, index) => item.number !== index + 1)) {
  throw new Error(`Expected the supplied rapid-revision section to contain 242 ordered Q&As, found ${rapidQuestions.length}.`);
}
const rapidTopic = {
  id: "supplement-rapid-revision",
  chapterNumber: "RR",
  title: "ರ‍್ಯಾಪಿಡ್ ರಿವಿಷನ್ ಒನ್‌ಲೈನರ್ · 242 ಪ್ರಶ್ನೆ–ಉತ್ತರ",
  order: 1,
  description: `Supplement · 242 source Q&As · source lines ${partTwoIndex + 1}–${mathIndex}`,
  assetPath: "supplements/rapid-revision/rapid-revision-242.json",
  verify: true,
  estimatedMinutes: Math.max(15, Math.ceil(rapidContent.length / 950)),
  sourceLineStart: partTwoIndex + 1,
  sourceLineEnd: mathIndex,
};
writeJson(rapidTopic.assetPath, {
  ...rapidTopic,
  content: rapidContent,
  learningAid: {
    label: "Source-derived recall deck · not a new factual claim",
    studySteps: ["Read a question, hide the answer, then state the answer aloud.", "Flag changing current-affairs answers and verify them before an exam."],
    sourceHeadings: extractHeadings(rapidContent),
    quickReview: ["242 question–answer prompts from the supplied book."],
    recallPrompts: ["Attempt each question before revealing its supplied answer."],
  },
  provenance: {
    sourceFile: path.basename(sourcePath),
    sourceLineStart: partTwoIndex + 1,
    sourceLineEnd: mathIndex,
    sourceSha256: sourceHash,
    extraction: "Verbatim rapid-revision supplement from the learner-supplied master book.",
  },
});
const rapidCards = rapidQuestions.map((item) => ({
  id: `card-rapid-${String(item.number).padStart(3, "0")}`,
  topicId: rapidTopic.id,
  front: item.question,
  back: item.answer,
}));

// Supplement 2: split the complete solved-maths workbook by its ten explicit section headings.
const mathSection = withoutOuterBlankLines(lines.slice(mathIndex));
const mathStarts = [];
for (let index = 0; index < mathSection.length; index += 1) {
  const match = mathSection[index].match(/^π\s*ವಿಭಾಗ\s*(\d+)\s*:\s*(.+)$/u);
  if (match) mathStarts.push({ index, number: Number(match[1]), title: cleanTitle(match[2]) });
}
if (mathStarts.length !== 10 || mathStarts.some((item, index) => item.number !== index + 1)) {
  throw new Error(`Expected 10 maths workbook sections, found ${mathStarts.length}.`);
}
const mathExampleCount = mathSection.filter((line) => /^ಲೆಕ್ಕ\s+\d+\./u.test(line.trim())).length;
if (mathExampleCount < 100) throw new Error(`Expected at least 100 solved maths examples, found ${mathExampleCount}.`);
const mathsTopics = [];
for (const [offset, section] of mathStarts.entries()) {
  const next = mathStarts[offset + 1];
  const sectionLines = withoutOuterBlankLines(mathSection.slice(section.index, next ? next.index : mathSection.length));
  const content = sectionLines.join("\n").trim();
  const id = `supplement-maths-${String(section.number).padStart(2, "0")}`;
  const assetPath = `supplements/solved-maths/section-${String(section.number).padStart(2, "0")}.json`;
  const sourceLineStart = mathIndex + section.index + 1;
  const sourceLineEnd = mathIndex + (next ? next.index : mathSection.length);
  const topic = {
    id,
    chapterNumber: `M${section.number}`,
    title: `ಗಣಿತ ವರ್ಕ್‌ಬುಕ್ ${section.number}: ${section.title}`,
    order: section.number,
    description: `Solved maths workbook · section ${section.number} · source lines ${sourceLineStart}–${sourceLineEnd}`,
    assetPath,
    verify: false,
    estimatedMinutes: Math.max(8, Math.min(35, Math.ceil(content.length / 900))),
    sourceLineStart,
    sourceLineEnd,
  };
  writeJson(assetPath, {
    ...topic,
    content,
    learningAid: chapterAid(content, topic.title),
    provenance: {
      sourceFile: path.basename(sourcePath),
      sourceLineStart,
      sourceLineEnd,
      sourceSha256: sourceHash,
      extraction: "Verbatim solved-maths workbook section from the learner-supplied master book.",
    },
  });
  mathsTopics.push(topic);
}

subjects.push(
  {
    id: "supplement-rapid-revision",
    code: "REV",
    title: "Rapid revision · 242 Q&A",
    paper: "Supplements",
    kind: "supplement",
    folder: "supplements/rapid-revision",
    description: "Complete rapid-revision section preserved as one readable source reader and 242 offline recall cards.",
    order: 14,
    sourceTocLine: partTwoIndex + 1,
    topics: [rapidTopic],
  },
  {
    id: "supplement-solved-maths",
    code: "MATH",
    title: "Solved maths workbook",
    paper: "Supplements",
    kind: "supplement",
    folder: "supplements/solved-maths",
    description: `All ${mathExampleCount} solved examples, arranged in the ten sections supplied by the book.`,
    order: 15,
    sourceTocLine: mathIndex + 1,
    topics: mathsTopics,
  },
);

// Create small source-derived practice checks from the rapid Q&A, using only other supplied answers as distractors.
const questions = rapidQuestions.slice(0, 24).map((item, index) => {
  const distractors = [];
  for (let shift = 17; distractors.length < 3 && shift < rapidQuestions.length; shift += 29) {
    const candidate = rapidQuestions[(index + shift) % rapidQuestions.length].answer;
    if (candidate !== item.answer && !distractors.includes(candidate)) distractors.push(candidate);
  }
  const options = [item.answer, ...distractors];
  const rotation = index % options.length;
  const rotated = [...options.slice(rotation), ...options.slice(0, rotation)];
  return {
    id: `rapid-check-${String(index + 1).padStart(2, "0")}`,
    topicId: rapidTopic.id,
    topicHint: "SOURCE-DERIVED RAPID REVISION",
    question: item.question,
    options: rotated,
    answer: rotated.indexOf(item.answer),
    explanation: `The supplied rapid-revision answer is: ${item.answer}`,
  };
});
if (questions.some((question) => question.options.length !== 4 || question.answer < 0)) {
  throw new Error("Could not produce four source-derived options for every rapid check.");
}

const pack = {
  packVersion: 2,
  packName: "KEA / KPSC Complete Study Material — Master Book",
  generatedAt: "2026-11-16",
  sourceFile: path.basename(sourcePath),
  sourceSha256: sourceHash,
  sourceLineCount: source.endsWith("\n") ? lines.length - 1 : lines.length,
  masterTopicCount: 13,
  masterChapterCount: 272,
  supplementCount: 11,
  rapidRevisionQuestionCount: rapidQuestions.length,
  solvedMathsExampleCount: mathExampleCount,
  notice: "Independent preparation material, not an official KEA syllabus or notification. The source includes changing current-affairs material through August 2026; verify office holders, schemes, dates, statistics, recruitment notices and corrigenda with official/current sources before an exam.",
  subjects,
  questions,
  flashcards: [...chapterCards, ...rapidCards],
};
writeJson("index.json", pack);

console.log(`Built ${path.relative(root, contentRoot)}: 13 master topics, 272 complete chapter files, ${rapidQuestions.length} rapid-revision Q&As, ${mathExampleCount} solved maths examples, ${pack.flashcards.length} flashcards and ${questions.length} source-derived checks.`);
