package com.modernnavi.keaprep

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeaPrepApp(applicationContext)
        }
    }
}

private data class ContentPack(
    val name: String,
    val version: Int,
    val notice: String,
    val sourceLineCount: Int,
    val sourceHash: String,
    val masterTopicCount: Int,
    val masterChapterCount: Int,
    val supplementCount: Int,
    val rapidRevisionQuestionCount: Int,
    val solvedMathsExampleCount: Int,
    val subjects: List<StudySubject>,
    val questions: List<ModelQuestion>,
    val flashcards: List<Flashcard>,
)

private data class StudySubject(
    val id: String,
    val code: String,
    val title: String,
    val paper: String,
    val kind: String,
    val folder: String,
    val description: String,
    val order: Int,
    val topics: List<StudyTopic>,
)

private data class LearningAid(
    val label: String,
    val studySteps: List<String>,
    val sourceHeadings: List<String>,
    val quickReview: List<String>,
    val recallPrompts: List<String>,
)

private data class StudyTopic(
    val id: String,
    val chapterNumber: String,
    val title: String,
    val order: Int,
    val description: String,
    val content: String,
    val verify: Boolean,
    val minutes: Int,
    val sourceLineStart: Int,
    val sourceLineEnd: Int,
    val learningAid: LearningAid,
)

private data class ModelQuestion(
    val id: String,
    val topicHint: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
)

private data class Flashcard(
    val id: String,
    val topicId: String,
    val front: String,
    val back: String,
)

private class ContentRepository(private val context: Context) {
    private fun assetText(relativePath: String): String = context.assets
        .open("content/master-book/$relativePath")
        .bufferedReader()
        .use { it.readText() }

    private fun JSONArray.strings(): List<String> = List(length()) { index -> getString(index) }

    fun load(): ContentPack {
        val json = JSONObject(assetText("index.json"))
        val subjects = json.getJSONArray("subjects").toList { subjectJson ->
            val topicArray = subjectJson.getJSONArray("topics")
            StudySubject(
                id = subjectJson.getString("id"),
                code = subjectJson.getString("code"),
                title = subjectJson.getString("title"),
                paper = subjectJson.getString("paper"),
                kind = subjectJson.getString("kind"),
                folder = subjectJson.getString("folder"),
                description = subjectJson.getString("description"),
                order = subjectJson.getInt("order"),
                topics = topicArray.toList { topicIndex ->
                    // The index is deliberately slim. The complete chapter stays in its own
                    // topic folder and is loaded locally, never fetched from a network.
                    val detail = JSONObject(assetText(topicIndex.getString("assetPath")))
                    val aid = detail.getJSONObject("learningAid")
                    StudyTopic(
                        id = detail.getString("id"),
                        chapterNumber = detail.getString("chapterNumber"),
                        title = detail.getString("title"),
                        order = detail.getInt("order"),
                        description = detail.optString("description"),
                        content = detail.getString("content"),
                        verify = detail.optBoolean("verify"),
                        minutes = detail.optInt("estimatedMinutes", 10),
                        sourceLineStart = detail.optInt("sourceLineStart"),
                        sourceLineEnd = detail.optInt("sourceLineEnd"),
                        learningAid = LearningAid(
                            label = aid.optString("label"),
                            studySteps = aid.optJSONArray("studySteps")?.strings().orEmpty(),
                            sourceHeadings = aid.optJSONArray("sourceHeadings")?.strings().orEmpty(),
                            quickReview = aid.optJSONArray("quickReview")?.strings().orEmpty(),
                            recallPrompts = aid.optJSONArray("recallPrompts")?.strings().orEmpty(),
                        ),
                    )
                },
            )
        }
        val questions = json.getJSONArray("questions").toList { item ->
            val optionsArray = item.getJSONArray("options")
            ModelQuestion(
                id = item.getString("id"),
                topicHint = item.getString("topicHint"),
                question = item.getString("question"),
                options = List(optionsArray.length()) { optionIndex -> optionsArray.getString(optionIndex) },
                answer = item.getInt("answer"),
                explanation = item.getString("explanation"),
            )
        }
        val cards = json.getJSONArray("flashcards").toList { item ->
            Flashcard(
                id = item.getString("id"),
                topicId = item.getString("topicId"),
                front = item.getString("front"),
                back = item.getString("back"),
            )
        }
        return ContentPack(
            name = json.getString("packName"),
            version = json.getInt("packVersion"),
            notice = json.getString("notice"),
            sourceLineCount = json.getInt("sourceLineCount"),
            sourceHash = json.getString("sourceSha256"),
            masterTopicCount = json.getInt("masterTopicCount"),
            masterChapterCount = json.getInt("masterChapterCount"),
            supplementCount = json.getInt("supplementCount"),
            rapidRevisionQuestionCount = json.getInt("rapidRevisionQuestionCount"),
            solvedMathsExampleCount = json.getInt("solvedMathsExampleCount"),
            subjects = subjects,
            questions = questions,
            flashcards = cards,
        )
    }
}

private inline fun <T> JSONArray.toList(block: (JSONObject) -> T): List<T> =
    List(length()) { index -> block(getJSONObject(index)) }

private class LocalStudyStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("kea_offline_study", Context.MODE_PRIVATE)

    fun completed(): Set<String> = preferences.getStringSet("completed_topics", emptySet()).orEmpty()
    fun bookmarks(): Set<String> = preferences.getStringSet("bookmarked_topics", emptySet()).orEmpty()
    fun goalMinutes(): Int = preferences.getInt("daily_goal_minutes", 30)
    fun fontScale(): Float = preferences.getFloat("font_scale", 1f)
    fun darkTheme(): Boolean = preferences.getBoolean("dark_theme", false)
    fun language(): UiLanguage = runCatching {
        UiLanguage.valueOf(preferences.getString("ui_language", UiLanguage.ENGLISH.name) ?: UiLanguage.ENGLISH.name)
    }.getOrDefault(UiLanguage.ENGLISH)
    fun examDate(): String = preferences.getString("exam_date", "") ?: ""
    fun lastTopic(): String = preferences.getString("last_topic", "") ?: ""
    fun attempts(): Int = preferences.getInt("model_attempts", 0)
    fun correctAttempts(): Int = preferences.getInt("model_correct", 0)

    fun setCompleted(id: String, completed: Boolean) = editSet("completed_topics", id, completed)
    fun setBookmark(id: String, bookmarked: Boolean) = editSet("bookmarked_topics", id, bookmarked)
    fun setGoalMinutes(value: Int) = preferences.edit().putInt("daily_goal_minutes", value).apply()
    fun setFontScale(value: Float) = preferences.edit().putFloat("font_scale", value).apply()
    fun setDarkTheme(value: Boolean) = preferences.edit().putBoolean("dark_theme", value).apply()
    fun setLanguage(value: UiLanguage) = preferences.edit().putString("ui_language", value.name).apply()
    fun setExamDate(value: String) = preferences.edit().putString("exam_date", value.trim()).apply()
    fun setLastTopic(id: String) = preferences.edit().putString("last_topic", id).apply()

    fun recordAttempt(correct: Boolean) {
        preferences.edit()
            .putInt("model_attempts", attempts() + 1)
            .putInt("model_correct", correctAttempts() + if (correct) 1 else 0)
            .apply()
    }

    fun scheduledCards(): Map<String, Long> {
        val stored = preferences.getString("srs_cards", "{}") ?: "{}"
        return runCatching {
            val objectValue = JSONObject(stored)
            buildMap {
                val keys = objectValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, objectValue.getLong(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun scheduleCard(id: String, inDays: Int) {
        val cards = scheduledCards().toMutableMap()
        cards[id] = System.currentTimeMillis() + inDays * 86_400_000L
        val storedCards = JSONObject()
        cards.forEach { (cardId, dueAt) -> storedCards.put(cardId, dueAt) }
        preferences.edit().putString("srs_cards", storedCards.toString()).apply()
    }

    fun clearLearningData() {
        preferences.edit().clear().apply()
    }

    private fun editSet(key: String, id: String, enabled: Boolean) {
        val revised = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (enabled) revised += id else revised -= id
        preferences.edit().putStringSet(key, revised).apply()
    }
}

private enum class UiLanguage(val english: String, val kannada: String) {
    ENGLISH("English", "ಇಂಗ್ಲಿಷ್"),
    KANNADA("Kannada", "ಕನ್ನಡ"),
    BILINGUAL("Both", "ಎರಡೂ");
}

private fun bilingual(language: UiLanguage, english: String, kannada: String): String = when (language) {
    UiLanguage.ENGLISH -> english
    UiLanguage.KANNADA -> kannada
    UiLanguage.BILINGUAL -> "$kannada\n$english"
}

private enum class MainTab(val english: String, val kannada: String) {
    HOME("Home", "ಮುಖಪುಟ"),
    STUDY("Study", "ಅಧ್ಯಯನ"),
    PRACTICE("Practice", "ಅಭ್ಯಾಸ"),
    ASK_AI("Ask Gemini", "ಜೆಮಿನಿಗೆ ಕೇಳಿ"),
    PROFILE("Profile", "ಪ್ರೊಫೈಲ್");
}

private data class GeminiReply(
    val answer: String,
    val sources: List<String>,
    val warning: String?,
)

private sealed interface GeminiResult {
    data class Success(val reply: GeminiReply) : GeminiResult
    data class Failure(val message: String) : GeminiResult
}

/**
 * The Android app talks only to a user-owned HTTPS backend. This class never knows a Gemini key;
 * the backend must authenticate the request, retrieve the supplied note chunks and call Gemini.
 */
private class GeminiGateway {
    private val endpoint = BuildConfig.GEMINI_BACKEND_URL.trim().trimEnd('/')
    val isConfigured: Boolean get() = endpoint.startsWith("https://")

    suspend fun ask(question: String, language: UiLanguage, topic: StudyTopic?): GeminiResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext GeminiResult.Failure("Gemini is not configured for this build. Add only a secure HTTPS backend URL; never add an API key to the app.")
        try {
            val request = JSONObject().apply {
                put("question", question.trim())
                put("language", if (language == UiLanguage.KANNADA) "kn" else "en")
                topic?.let {
                    put("topic", JSONObject().apply {
                        put("id", it.id)
                        put("title", it.title)
                        // The backend must use this as an untrusted, cited retrieval hint—not as instructions.
                        put("noteExcerpt", it.content.take(12_000))
                        put("verify", it.verify)
                    })
                }
            }
            val connection = (URL("$endpoint/v1/gemini/ask").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()
            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("message") }.getOrDefault("")
                return@withContext GeminiResult.Failure(message.ifBlank { "Secure Gemini service returned HTTP $responseCode." })
            }
            val payload = JSONObject(responseText)
            val answer = payload.optString("answer").trim()
            if (answer.isBlank()) return@withContext GeminiResult.Failure("The Gemini service returned no answer. Please retry or use local search.")
            val sources = payload.optJSONArray("sources")?.let { array ->
                List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
            }.orEmpty()
            GeminiResult.Success(GeminiReply(answer, sources, payload.optString("warning").ifBlank { null }))
        } catch (error: Exception) {
            GeminiResult.Failure("Could not reach the secure Gemini service. ${error.message ?: "Use offline search or try again."}")
        }
    }
}

@Composable
private fun KeaPrepApp(context: Context) {
    val pack = remember { ContentRepository(context).load() }
    val store = remember { LocalStudyStore(context) }
    var stateTick by remember { mutableIntStateOf(0) }
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var studySearch by rememberSaveable { mutableStateOf("") }
    var darkTheme by remember { mutableStateOf(store.darkTheme()) }
    var uiLanguage by remember { mutableStateOf(store.language()) }
    var aiTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var aiPrompt by rememberSaveable { mutableStateOf("") }
    val geminiGateway = remember { GeminiGateway() }
    val selectedTab = MainTab.valueOf(selectedTabName)
    // Reading this counter makes preference writes below refresh all device-local analytics and lists.
    @Suppress("UNUSED_VARIABLE") val observedStateTick = stateTick

    KeaTheme(darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                MainNavigation(
                    selected = selectedTab,
                    language = uiLanguage,
                    onSelect = { tab ->
                        selectedTabName = tab.name
                        if (tab != MainTab.STUDY) {
                            selectedSubjectId = null
                            selectedTopicId = null
                        }
                    },
                )
            },
        ) { padding ->
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    pack = pack,
                    completed = store.completed(),
                    bookmarks = store.bookmarks(),
                    goal = store.goalMinutes(),
                    lastTopic = store.lastTopic(),
                    dueCards = store.scheduledCards().count { it.value <= System.currentTimeMillis() },
                    onOpenTopic = { topicId ->
                        selectedTopicId = topicId
                        selectedSubjectId = null
                        selectedTabName = MainTab.STUDY.name
                    },
                    onOpenPractice = { selectedTabName = MainTab.PRACTICE.name },
                )

                MainTab.STUDY -> StudyScreen(
                    modifier = Modifier.padding(padding),
                    pack = pack,
                    completed = store.completed(),
                    bookmarks = store.bookmarks(),
                    selectedSubjectId = selectedSubjectId,
                    selectedTopicId = selectedTopicId,
                    search = studySearch,
                    fontScale = store.fontScale(),
                    language = uiLanguage,
                    onSearchChange = { studySearch = it },
                    onOpenSubject = { selectedSubjectId = it },
                    onOpenTopic = {
                        selectedTopicId = it
                        store.setLastTopic(it)
                    },
                    onBack = {
                        when {
                            selectedTopicId != null -> selectedTopicId = null
                            selectedSubjectId != null -> selectedSubjectId = null
                        }
                    },
                    onToggleComplete = { topicId, marked ->
                        store.setCompleted(topicId, marked)
                        stateTick += 1
                    },
                    onToggleBookmark = { topicId, marked ->
                        store.setBookmark(topicId, marked)
                        stateTick += 1
                    },
                    onAskGemini = { topic, prompt ->
                        aiTopicId = topic.id
                        aiPrompt = prompt
                        selectedTabName = MainTab.ASK_AI.name
                    },
                )

                MainTab.PRACTICE -> PracticeScreen(
                    modifier = Modifier.padding(padding),
                    pack = pack,
                    attempts = store.attempts(),
                    correct = store.correctAttempts(),
                    dueCards = store.scheduledCards(),
                    onRecordAnswer = {
                        store.recordAttempt(it)
                        stateTick += 1
                    },
                    onScheduleCard = { id, days ->
                        store.scheduleCard(id, days)
                        stateTick += 1
                    },
                    onOpenTopic = { topicId ->
                        selectedTopicId = topicId
                        selectedSubjectId = null
                        selectedTabName = MainTab.STUDY.name
                    },
                )

                MainTab.ASK_AI -> AskAiScreen(
                    modifier = Modifier.padding(padding),
                    pack = pack,
                    language = uiLanguage,
                    gateway = geminiGateway,
                    initialTopicId = aiTopicId,
                    initialQuestion = aiPrompt,
                    onSearchNotes = { query ->
                        studySearch = query
                        selectedSubjectId = null
                        selectedTopicId = null
                        selectedTabName = MainTab.STUDY.name
                    },
                    onOpenSource = { topicId ->
                        selectedTopicId = topicId
                        selectedSubjectId = null
                        selectedTabName = MainTab.STUDY.name
                    },
                )

                MainTab.PROFILE -> ProfileScreen(
                    modifier = Modifier.padding(padding),
                    pack = pack,
                    completedCount = store.completed().size,
                    bookmarkCount = store.bookmarks().size,
                    attempts = store.attempts(),
                    correct = store.correctAttempts(),
                    goal = store.goalMinutes(),
                    fontScale = store.fontScale(),
                    darkTheme = darkTheme,
                    language = uiLanguage,
                    examDate = store.examDate(),
                    onGoalChange = { store.setGoalMinutes(it); stateTick += 1 },
                    onFontScaleChange = { store.setFontScale(it); stateTick += 1 },
                    onDarkThemeChange = {
                        darkTheme = it
                        store.setDarkTheme(it)
                    },
                    onLanguageChange = {
                        uiLanguage = it
                        store.setLanguage(it)
                        stateTick += 1
                    },
                    onExamDateChange = { store.setExamDate(it); stateTick += 1 },
                    onClearLearningData = {
                        store.clearLearningData()
                        stateTick += 1
                    },
                )
            }
        }
    }
}

@Composable
private fun KeaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val light = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF075D45),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8F5DD),
        secondary = Color(0xFF3F6656),
        secondaryContainer = Color(0xFFD9EDE0),
        tertiary = Color(0xFF775A00),
        tertiaryContainer = Color(0xFFFFEAAF),
        background = Color(0xFFF7FAF7),
        surface = Color.White,
        surfaceVariant = Color(0xFFE7EFEA),
        error = Color(0xFFB3261E),
    )
    val dark = androidx.compose.material3.darkColorScheme(
        primary = Color(0xFF95DBB5),
        onPrimary = Color(0xFF003823),
        primaryContainer = Color(0xFF005237),
        secondary = Color(0xFFB8D2C1),
        background = Color(0xFF101512),
        surface = Color(0xFF171D19),
        surfaceVariant = Color(0xFF25352D),
        tertiary = Color(0xFFFFD86B),
    )
    MaterialTheme(colorScheme = if (darkTheme) dark else light, content = content)
}

@Composable
private fun MainNavigation(selected: MainTab, language: UiLanguage, onSelect: (MainTab) -> Unit) {
    val destinations = listOf(
        MainTab.HOME to Icons.Outlined.Home,
        MainTab.STUDY to Icons.Outlined.AutoStories,
        MainTab.PRACTICE to Icons.Outlined.Quiz,
        MainTab.ASK_AI to Icons.Outlined.SmartToy,
        MainTab.PROFILE to Icons.Outlined.AccountCircle,
    )
    NavigationBar {
        destinations.forEach { (tab, icon) ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = bilingual(language, tab.english, tab.kannada)) },
                label = {
                    Text(
                        bilingual(language, tab.english, tab.kannada),
                        fontSize = if (language == UiLanguage.BILINGUAL) 8.sp else 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = if (language == UiLanguage.BILINGUAL) 9.sp else 12.sp,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    modifier: Modifier,
    pack: ContentPack,
    completed: Set<String>,
    bookmarks: Set<String>,
    goal: Int,
    lastTopic: String,
    dueCards: Int,
    onOpenTopic: (String) -> Unit,
    onOpenPractice: () -> Unit,
) {
    val allTopics = pack.subjects.flatMap { subject -> subject.topics.map { subject to it } }
    val continueTopic = allTopics.firstOrNull { it.second.id == lastTopic } ?: allTopics.firstOrNull { it.second.id !in completed }
    val todayTopics = allTopics.filter { it.second.id !in completed }.take(3)
    val progress = if (allTopics.isEmpty()) 0f else completed.size.toFloat() / allTopics.size

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("KEA PREP", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("FDA · Group C · Offline study", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroCard(
                    title = "Your offline preparation desk",
                    body = "${pack.masterChapterCount} complete chapters across ${pack.masterTopicCount} topic folders, plus ${pack.supplementCount} revision readers. Read, search, practise and revise offline.",
                    label = "MASTER BOOK · ${pack.sourceLineCount} SOURCE LINES",
                )
            }
            item {
                SectionTitle("Today’s plan", "Base planner")
                PlannerCard(goal = goal, topics = todayTopics, dueCards = dueCards, onOpenTopic = { onOpenTopic(it) })
            }
            if (continueTopic != null) {
                item {
                    SectionTitle("Continue reading", "Offline")
                    val (subject, topic) = continueTopic
                    TopicActionCard(
                        title = topic.title,
                        subtitle = "${subject.code} · ${topic.minutes} min · ${if (topic.verify) "review changing facts" else "detailed notes"}",
                        action = "Resume",
                        onClick = { onOpenTopic(topic.id) },
                    )
                }
            }
            item {
                SectionTitle("Progress snapshot", "On this device")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatLabel("Topics complete", "${completed.size} / ${allTopics.size}")
                            StatLabel("Bookmarks", bookmarks.size.toString())
                            StatLabel("Due cards", dueCards.toString())
                        }
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Small daily sessions beat one long unread plan. Mark a topic done only after a recall attempt.", fontSize = 12.sp)
                    }
                }
            }
            item {
                SectionTitle("Daily model check", "Original practice")
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPractice),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${pack.questions.size} source-derived quick checks", fontWeight = FontWeight.Bold)
                            Text("Built only from the bundled rapid-revision Q&A; not previous-year questions.", fontSize = 12.sp)
                        }
                        Text("Open", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                VerificationNotice(pack.notice)
            }
        }
    }
}

@Composable
private fun HeroCard(title: String, body: String, label: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(title, color = MaterialTheme.colorScheme.onPrimary, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
            Text(body, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(tag.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlannerCard(goal: Int, topics: List<Pair<StudySubject, StudyTopic>>, dueCards: Int, onOpenTopic: (String) -> Unit) {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${goal}-minute base plan", fontWeight = FontWeight.Bold)
            Text("The planner chooses unread topics first. Your profile sets the daily goal; review cards are scheduled at 1, 3, 7 or 21 days.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            topics.forEachIndexed { index, pair ->
                val (subject, topic) = pair
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpenTopic(topic.id) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(25.dp))
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${subject.code} · ${topic.minutes} min", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (dueCards > 0) Text("$dueCards flashcard(s) are due for review.", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TopicActionCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyScreen(
    modifier: Modifier,
    pack: ContentPack,
    completed: Set<String>,
    bookmarks: Set<String>,
    selectedSubjectId: String?,
    selectedTopicId: String?,
    search: String,
    fontScale: Float,
    language: UiLanguage,
    onSearchChange: (String) -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit,
    onToggleComplete: (String, Boolean) -> Unit,
    onToggleBookmark: (String, Boolean) -> Unit,
    onAskGemini: (StudyTopic, String) -> Unit,
) {
    val currentSubject = pack.subjects.firstOrNull { it.id == selectedSubjectId }
    val currentTopic = pack.subjects.flatMap { it.topics }.firstOrNull { it.id == selectedTopicId }
    val owner = pack.subjects.firstOrNull { subject -> subject.topics.any { it.id == selectedTopicId } }

    if (currentTopic != null && owner != null) {
        TopicReader(
            modifier = modifier,
            subject = owner,
            topic = currentTopic,
            completed = currentTopic.id in completed,
            bookmarked = currentTopic.id in bookmarks,
            fontScale = fontScale,
            language = language,
            onBack = onBack,
            onToggleComplete = { onToggleComplete(currentTopic.id, it) },
            onToggleBookmark = { onToggleBookmark(currentTopic.id, it) },
            onAskGemini = { prompt -> onAskGemini(currentTopic, prompt) },
        )
        return
    }

    if (currentSubject != null) {
        TopicList(
            modifier = modifier,
            subject = currentSubject,
            completed = completed,
            bookmarks = bookmarks,
            onBack = onBack,
            onOpenTopic = onOpenTopic,
        )
        return
    }

    val normalizedSearch = search.trim().lowercase(Locale.getDefault())
    val searchResults = if (normalizedSearch.isBlank()) emptyList() else pack.subjects.flatMap { subject ->
        subject.topics.filter { topic ->
            topic.title.lowercase(Locale.getDefault()).contains(normalizedSearch) ||
                topic.content.lowercase(Locale.getDefault()).contains(normalizedSearch)
        }.map { subject to it }
    }

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Study library", fontWeight = FontWeight.Bold) }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    label = { Text("Search every bundled note") },
                    supportingText = { Text("Works offline across detailed notes, Kannada and English content.") },
                )
            }
            if (normalizedSearch.isNotBlank()) {
                item { SectionTitle("${searchResults.size} matching topic(s)", "Local keyword search") }
                items(searchResults, key = { it.second.id }) { (subject, topic) ->
                    SearchResultCard(subject, topic, normalizedSearch, onOpenTopic)
                }
                if (searchResults.isEmpty()) item { EmptyState("No match in this offline pack", "Try a shorter keyword such as 'Panchayat', 'Cauvery', 'percentage' or 'ಸಂಧಿ'.") }
            } else {
                item {
                    VerificationNotice("Open one of the 13 master-book topic folders to read every numbered chapter. Yellow verification notices appear for changing/current material; verify it before an exam.")
                }
                item { Text("Master Book · 13 topic folders · 272 chapters", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                items(pack.subjects.filter { it.kind == "master" }, key = { it.id }) { subject ->
                    SubjectCard(subject, completed, onOpenSubject)
                }
                item { Text("Revision supplements", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(pack.subjects.filter { it.kind == "supplement" }, key = { it.id }) { subject ->
                    SubjectCard(subject, completed, onOpenSubject)
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: StudySubject, completed: Set<String>, onOpen: (String) -> Unit) {
    val done = subject.topics.count { it.id in completed }
    val progress = if (subject.topics.isEmpty()) 0f else done.toFloat() / subject.topics.size
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(subject.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(subject.code, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.width(9.dp))
                Text(subject.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(subject.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("$done/${subject.topics.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SearchResultCard(subject: StudySubject, topic: StudyTopic, query: String, onOpenTopic: (String) -> Unit) {
    val matchAt = topic.content.lowercase(Locale.getDefault()).indexOf(query)
    val excerpt = if (matchAt >= 0) topic.content.substring(maxOf(0, matchAt - 60), minOf(topic.content.length, matchAt + query.length + 120)).replace("\n", " ") else topic.content.take(180).replace("\n", " ")
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(topic.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${subject.code} · ${subject.title}", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(topic.title, fontWeight = FontWeight.Bold)
            Text(excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicList(
    modifier: Modifier,
    subject: StudySubject,
    completed: Set<String>,
    bookmarks: Set<String>,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(subject.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item { VerificationNotice(subject.description) }
            items(subject.topics, key = { it.id }) { topic ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(topic.id) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (topic.id in completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(37.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (topic.id in completed) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                else Text(topic.chapterNumber, fontWeight = FontWeight.Bold, fontSize = if (topic.chapterNumber.length > 2) 10.sp else 13.sp)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(topic.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${topic.minutes} min read${if (topic.verify) " · verify selected facts" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (topic.id in bookmarks) Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicReader(
    modifier: Modifier,
    subject: StudySubject,
    topic: StudyTopic,
    completed: Boolean,
    bookmarked: Boolean,
    fontScale: Float,
    language: UiLanguage,
    onBack: () -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onToggleBookmark: (Boolean) -> Unit,
    onAskGemini: (String) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(subject.code, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    IconButton(onClick = { onToggleBookmark(!bookmarked) }) {
                        Icon(Icons.Outlined.Bookmark, if (bookmarked) "Remove bookmark" else "Bookmark", tint = if (bookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(subject.title.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .7.sp)
            Text(topic.title, fontSize = (24 * fontScale).sp, fontWeight = FontWeight.ExtraBold, lineHeight = (31 * fontScale).sp)
            Text("${topic.minutes} minute detailed reading · ${if (topic.chapterNumber.all { it.isDigit() }) "Chapter ${topic.chapterNumber}" else topic.chapterNumber} · Offline master-book asset", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        bilingual(language, "Bilingual study support", "ದ್ವಿಭಾಷಾ ಅಧ್ಯಯನ ಸಹಾಯ"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        bilingual(
                            language,
                            "Notes stay in their supplied language so names, dates and numbers remain exact. Use Ask Gemini for a cited Kannada or English explanation when a secure backend is configured.",
                            "ಹೆಸರು, ದಿನಾಂಕ ಮತ್ತು ಸಂಖ್ಯೆ ನಿಖರವಾಗಿರಲು ಟಿಪ್ಪಣಿಗಳು ನೀಡಲಾದ ಭಾಷೆಯಲ್ಲೇ ಇವೆ. ಸುರಕ್ಷಿತ ಬ್ಯಾಕೆಂಡ್ ಸಿದ್ಧವಾದಾಗ ಉಲ್ಲೇಖಗಳಿರುವ ಕನ್ನಡ ಅಥವಾ ಇಂಗ್ಲಿಷ್ ವಿವರಣೆಗೆ Ask Gemini ಬಳಸಿ.",
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    OutlinedButton(
                        onClick = {
                            val target = if (language == UiLanguage.KANNADA) "English" else "Kannada"
                            onAskGemini("Explain ${topic.title} in simple $target using only this topic's supplied notes. Keep dates, names and numbers exact; identify facts that need verification.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(bilingual(language, "Ask Gemini about this topic", "ಈ ವಿಷಯದ ಬಗ್ಗೆ ಜೆಮಿನಿಗೆ ಕೇಳಿ")) }
                }
            }
            if (topic.verify) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("VERIFY", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 11.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("This topic includes facts marked (verify) or changing information. Confirm these with an official/current source before final revision.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (topic.description.isNotBlank()) Text(topic.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (14 * fontScale).sp)
            Text("Source lines ${topic.sourceLineStart}–${topic.sourceLineEnd} · ${subject.folder}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            if (topic.learningAid.sourceHeadings.isNotEmpty() || topic.learningAid.quickReview.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Study aid · source-derived", fontWeight = FontWeight.Bold)
                        Text("These reading prompts are mechanically extracted from this chapter's own headings and bullet points. They add structure but do not replace the full source chapter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (topic.learningAid.sourceHeadings.isNotEmpty()) {
                            Text("Source sections", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(topic.learningAid.sourceHeadings.joinToString("  •  "), fontSize = 12.sp)
                        }
                        if (topic.learningAid.quickReview.isNotEmpty()) {
                            Text("Quick recall cues", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            topic.learningAid.quickReview.forEach { cue -> Text("• $cue", fontSize = 12.sp, lineHeight = 18.sp) }
                        }
                        if (topic.learningAid.recallPrompts.isNotEmpty()) {
                            Text("Recall prompts", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            topic.learningAid.recallPrompts.forEach { prompt -> Text("• $prompt", fontSize = 12.sp) }
                        }
                    }
                }
            }
            HorizontalDivider()
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    topic.content.split("\n\n").filter { it.isNotBlank() }.forEach { block ->
                        val readerLine = block.trim()
                        val isHeading = block.lines().size == 1 && (
                            readerLine.startsWith("✦") || readerLine.startsWith("◆") || readerLine.startsWith("✿") ||
                                readerLine.startsWith("❖") || readerLine.startsWith("★") || readerLine.startsWith("⚡") ||
                                readerLine.startsWith("◉") || readerLine.startsWith("π") ||
                                (readerLine.length < 70 && readerLine == readerLine.uppercase(Locale.getDefault()))
                            )
                        Text(
                            text = readerLine,
                            fontSize = ((if (isHeading) 17f else 15f) * fontScale).sp,
                            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = (23f * fontScale).sp,
                        )
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active recall", fontWeight = FontWeight.Bold)
                    Text("Close the screen and state five facts, one comparison and one possible exam question from this topic. Then mark it done.", fontSize = 13.sp)
                }
            }
            Button(onClick = { onToggleComplete(!completed) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(if (completed) "Mark as needs revision" else "Mark topic complete")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PracticeScreen(
    modifier: Modifier,
    pack: ContentPack,
    attempts: Int,
    correct: Int,
    dueCards: Map<String, Long>,
    onRecordAnswer: (Boolean) -> Unit,
    onScheduleCard: (String, Int) -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    var view by rememberSaveable { mutableStateOf("quiz") }
    val accuracy = if (attempts == 0) 0 else (correct * 100 / attempts)
    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Practice", fontWeight = FontWeight.Bold) }) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = view == "quiz", onClick = { view = "quiz" }, label = { Text("Model checks") }) }
                item { FilterChip(selected = view == "cards", onClick = { view = "cards" }, label = { Text("Flashcards") }) }
                item { FilterChip(selected = view == "tools", onClick = { view = "tools" }, label = { Text("Quick tools") }) }
            }
            when (view) {
                "quiz" -> QuizPane(pack.questions, attempts, accuracy, onRecordAnswer)
                "cards" -> FlashcardsPane(pack.flashcards, dueCards, onScheduleCard, onOpenTopic)
                else -> ToolsPane(pack, onOpenTopic)
            }
        }
    }
}

@Composable
private fun QuizPane(questions: List<ModelQuestion>, attempts: Int, accuracy: Int, onRecordAnswer: (Boolean) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var counted by rememberSaveable { mutableStateOf(false) }
    val question = questions[index % questions.size]
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VerificationNotice("SOURCE-DERIVED QUICK CHECK · NOT A PREVIOUS-YEAR QUESTION. Every prompt and option comes from the bundled rapid-revision Q&A. Confirm changing facts against an official/current source.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Question ${index % questions.size + 1} of ${questions.size}", fontWeight = FontWeight.Bold)
            Text("$accuracy% accuracy · $attempts attempts", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question.topicHint, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(question.question, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp)
                question.options.forEachIndexed { optionIndex, option ->
                    val correctOption = checked && optionIndex == question.answer
                    val wrongOption = checked && selected == optionIndex && optionIndex != question.answer
                    val selectedBorder = if (correctOption) MaterialTheme.colorScheme.primary else if (wrongOption) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    OutlinedButton(
                        onClick = { if (!checked) selected = optionIndex },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(if (selected == optionIndex || correctOption) 2.dp else 1.dp, selectedBorder),
                    ) {
                        RadioButton(selected = selected == optionIndex, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
                if (checked) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (selected == question.answer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (selected == question.answer) "Correct" else "Review this one", fontWeight = FontWeight.Bold)
                            Text(question.explanation, fontSize = 13.sp)
                        }
                    }
                }
                if (!checked) {
                    Button(
                        onClick = {
                            checked = true
                            if (!counted) {
                                onRecordAnswer(selected == question.answer)
                                counted = true
                            }
                        },
                        enabled = selected >= 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Check answer") }
                } else {
                    Button(
                        onClick = {
                            index = (index + 1) % questions.size
                            selected = -1
                            checked = false
                            counted = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next source check") }
                }
            }
        }
    }
}

@Composable
private fun FlashcardsPane(
    cards: List<Flashcard>,
    dueCards: Map<String, Long>,
    onScheduleCard: (String, Int) -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    var visibleIndex by rememberSaveable { mutableIntStateOf(0) }
    var answerVisible by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val due = cards.filter { dueCards[it.id] == null || dueCards[it.id]!! <= now }
    val card = (due.ifEmpty { cards })[visibleIndex % (due.ifEmpty { cards }).size]
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VerificationNotice("Offline spaced repetition. A response schedules this card for a local review date; no profile or review history leaves your device.")
        Text("${due.size} card(s) due", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("RECALL", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(card.front, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 29.sp)
                if (answerVisible) {
                    HorizontalDivider()
                    Text(card.back, fontSize = 14.sp, lineHeight = 21.sp)
                } else {
                    OutlinedButton(onClick = { answerVisible = true }, modifier = Modifier.fillMaxWidth()) { Text("Show answer") }
                }
            }
        }
        if (answerVisible) {
            Text("How well did you recall it?", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SrsButton("Again", Modifier.weight(1f)) { onScheduleCard(card.id, 1); visibleIndex++; answerVisible = false }
                SrsButton("Hard", Modifier.weight(1f)) { onScheduleCard(card.id, 3); visibleIndex++; answerVisible = false }
                SrsButton("Good", Modifier.weight(1f)) { onScheduleCard(card.id, 7); visibleIndex++; answerVisible = false }
                SrsButton("Easy", Modifier.weight(1f)) { onScheduleCard(card.id, 21); visibleIndex++; answerVisible = false }
            }
        }
        TextButton(onClick = { onOpenTopic(card.topicId) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Open the full source topic") }
    }
}

@Composable
private fun SrsButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 9.dp)) {
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun ToolsPane(pack: ContentPack, onOpenTopic: (String) -> Unit) {
    val tools = listOf(
        "Maths formula and shortcut chapters" to "ಸೂತ್ರ",
        "Percentage, ratio and calculation practice" to "ಶೇಕಡಾ",
        "Current affairs and static GK tables" to "ಪ್ರಚಲಿತ",
        "Computer abbreviations and office tools" to "ಕಂಪ್ಯೂಟರ್",
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { VerificationNotice("Quick tools open the corresponding supplied topic, rather than a shortened or unverified duplicate. Use keyword search for any other formula, date or abbreviation.") }
        items(tools) { (label, matcher) ->
            val topic = pack.subjects.flatMap { it.topics }.firstOrNull { it.title.contains(matcher, ignoreCase = true) }
            TopicActionCard(label, "Open detailed notes from the offline pack", "Open") { topic?.let { onOpenTopic(it.id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskAiScreen(
    modifier: Modifier,
    pack: ContentPack,
    language: UiLanguage,
    gateway: GeminiGateway,
    initialTopicId: String?,
    initialQuestion: String,
    onSearchNotes: (String) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var answerLanguage by rememberSaveable { mutableStateOf(if (language == UiLanguage.KANNADA) UiLanguage.KANNADA.name else UiLanguage.ENGLISH.name) }
    var requesting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<GeminiResult?>(null) }
    val scope = rememberCoroutineScope()
    val topic = pack.subjects.flatMap { it.topics }.firstOrNull { it.id == initialTopicId }
    val selectedAnswerLanguage = UiLanguage.valueOf(answerLanguage)

    LaunchedEffect(initialQuestion, initialTopicId) {
        if (initialQuestion.isNotBlank()) query = initialQuestion
    }

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text(bilingual(language, "Ask Gemini", "ಜೆಮಿನಿಗೆ ಕೇಳಿ"), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }) },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (gateway.isConfigured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(bilingual(language, "Gemini study assistant", "ಜೆಮಿನಿ ಅಧ್ಯಯನ ಸಹಾಯಕ"), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(bilingual(language, "A secure backend URL is configured. Ask only about the selected notes; generated answers must still be checked against sources.", "ಸುರಕ್ಷಿತ ಬ್ಯಾಕೆಂಡ್ URL ಸಿದ್ಧವಾಗಿದೆ. ಆಯ್ದ ಟಿಪ್ಪಣಿಗಳ ಬಗ್ಗೆ ಮಾತ್ರ ಕೇಳಿ; ರಚಿತ ಉತ್ತರಗಳನ್ನು ಮೂಲಗಳೊಂದಿಗೆ ಪರಿಶೀಲಿಸಬೇಕು."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(bilingual(language, "Gemini is not connected in this build", "ಈ ಬಿಲ್ಡ್‌ನಲ್ಲಿ ಜೆಮಿನಿ ಸಂಪರ್ಕಗೊಂಡಿಲ್ಲ"), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(bilingual(language, "The app has built-in Gemini client support, but no live backend URL or API key. No question is sent anywhere until a secure server is deployed and configured.", "ಆ್ಯಪ್‌ನಲ್ಲಿ ಜೆಮಿನಿ ಕ್ಲೈಂಟ್ ಬೆಂಬಲ ಇದೆ; ಆದರೆ ಲೈವ್ ಬ್ಯಾಕೆಂಡ್ URL ಅಥವಾ API ಕೀ ಇಲ್ಲ. ಸುರಕ್ಷಿತ ಸರ್ವರ್ ನಿಯೋಜಿಸಿ ಕಾನ್ಫಿಗರ್ ಮಾಡುವವರೆಗೆ ಯಾವುದೇ ಪ್ರಶ್ನೆ ಹೊರಗೆ ಕಳುಹಿಸಲಾಗುವುದಿಲ್ಲ."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            if (topic != null) {
                Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(bilingual(language, "Selected source topic", "ಆಯ್ದ ಮೂಲ ವಿಷಯ"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(topic.title, fontWeight = FontWeight.Bold)
                        Text(bilingual(language, "A maximum 12,000-character note excerpt is provided to the configured backend as retrieval context. Do not enter personal data.", "ಗರಿಷ್ಠ 12,000 ಅಕ್ಷರಗಳ ಟಿಪ್ಪಣಿ ಭಾಗವನ್ನು ಕಾನ್ಫಿಗರ್ ಮಾಡಿದ ಬ್ಯಾಕೆಂಡ್‌ಗೆ ಸಂದರ್ಭವಾಗಿ ನೀಡಲಾಗುತ್ತದೆ. ವೈಯಕ್ತಿಕ ಮಾಹಿತಿ ನಮೂದಿಸಬೇಡಿ."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(bilingual(language, "Reply language", "ಉತ್ತರದ ಭಾಷೆ"), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedAnswerLanguage == UiLanguage.ENGLISH, onClick = { answerLanguage = UiLanguage.ENGLISH.name }, label = { Text("English") })
                FilterChip(selected = selectedAnswerLanguage == UiLanguage.KANNADA, onClick = { answerLanguage = UiLanguage.KANNADA.name }, label = { Text("ಕನ್ನಡ") })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text(bilingual(language, "Ask from the supplied notes", "ನೀಡಲಾದ ಟಿಪ್ಪಣಿಗಳಿಂದ ಕೇಳಿ")) },
                placeholder = { Text("Panchayat Raj, Cauvery, ಸಂಧಿ…") },
                supportingText = { Text(bilingual(language, "Use local search at any time. Gemini answers are online-only and source-limited.", "ಯಾವಾಗ ಬೇಕಾದರೂ ಸ್ಥಳೀಯ ಹುಡುಕಾಟ ಬಳಸಿ. ಜೆಮಿನಿ ಉತ್ತರಗಳು ಆನ್‌ಲೈನ್‌ನಲ್ಲಿ ಮಾತ್ರ ಮತ್ತು ಮೂಲ-ಮಿತವಾಗಿರುತ್ತವೆ.")) },
            )
            if (gateway.isConfigured) {
                Button(
                    onClick = {
                        requesting = true
                        result = null
                        scope.launch {
                            result = gateway.ask(query, selectedAnswerLanguage, topic)
                            requesting = false
                        }
                    },
                    enabled = query.isNotBlank() && !requesting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (requesting) bilingual(language, "Asking Gemini…", "ಜೆಮಿನಿಯನ್ನು ಕೇಳಲಾಗುತ್ತಿದೆ…") else bilingual(language, "Ask Gemini", "ಜೆಮಿನಿಗೆ ಕೇಳಿ")) }
            } else {
                OutlinedButton(onClick = { onSearchNotes(query) }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(bilingual(language, "Search offline notes instead", "ಬದಲಿಗೆ ಆಫ್‌ಲೈನ್ ಟಿಪ್ಪಣಿಗಳನ್ನು ಹುಡುಕಿ"))
                }
            }

            when (val response = result) {
                is GeminiResult.Success -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(bilingual(language, "Gemini answer", "ಜೆಮಿನಿ ಉತ್ತರ"), fontWeight = FontWeight.Bold)
                            SelectionContainer { Text(response.reply.answer, fontSize = 14.sp, lineHeight = 21.sp) }
                            response.reply.warning?.let { VerificationNotice(it) }
                            if (response.reply.sources.isNotEmpty()) {
                                Text(bilingual(language, "Sources returned by backend", "ಬ್ಯಾಕೆಂಡ್ ನೀಡಿದ ಮೂಲಗಳು"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                response.reply.sources.forEach { sourceId ->
                                    val source = pack.subjects.flatMap { it.topics }.firstOrNull { it.id == sourceId }
                                    AssistChip(onClick = { if (source != null) onOpenSource(source.id) }, label = { Text(source?.title ?: sourceId) })
                                }
                            }
                        }
                    }
                }
                is GeminiResult.Failure -> VerificationNotice(response.message)
                null -> Unit
            }

            HorizontalDivider()
            Text(bilingual(language, "Offline note search", "ಆಫ್‌ಲೈನ್ ಟಿಪ್ಪಣಿ ಹುಡುಕಾಟ"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(bilingual(language, "This stays on your phone and is not AI-generated.", "ಇದು ನಿಮ್ಮ ಫೋನ್‌ನಲ್ಲೇ ಇರುತ್ತದೆ ಮತ್ತು AI ರಚಿಸಿದುದಲ್ಲ."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            OutlinedButton(onClick = { onSearchNotes(query) }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(bilingual(language, "Search supplied notes", "ನೀಡಲಾದ ಟಿಪ್ಪಣಿಗಳನ್ನು ಹುಡುಕಿ")) }
            HorizontalDivider()
            Text(bilingual(language, "Secure Gemini route", "ಸುರಕ್ಷಿತ ಜೆಮಿನಿ ಮಾರ್ಗ"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            ArchitectureStep("1", "Android app / ಆ್ಯಪ್", "The learner chooses a language and a source topic.")
            ArchitectureStep("2", "Secure backend proxy / ಸುರಕ್ಷಿತ ಬ್ಯಾಕೆಂಡ್", "Authenticates requests, rate-limits use and holds the Gemini credential. No secret belongs in this APK.")
            ArchitectureStep("3", "Notes retrieval / ಟಿಪ್ಪಣಿ ಹುಡುಕಾಟ", "Uses versioned note chunks and returns source topic IDs with every answer.")
            ArchitectureStep("4", "Transparent response / ಪಾರದರ್ಶಕ ಉತ್ತರ", "Shows source chips, verification warnings and a safe failure state.")
            VerificationNotice(bilingual(language, "Privacy: when no backend is configured, no question leaves the device. A production service requires consent, authentication, rate limits and a privacy policy.", "ಗೌಪ್ಯತೆ: ಬ್ಯಾಕೆಂಡ್ ಕಾನ್ಫಿಗರ್ ಆಗಿಲ್ಲದಾಗ ಯಾವುದೇ ಪ್ರಶ್ನೆ ಫೋನ್‌ನಿಂದ ಹೊರಗೆ ಹೋಗುವುದಿಲ್ಲ. ಉತ್ಪಾದನಾ ಸೇವೆಗೆ ಸಮ್ಮತಿ, ದೃಢೀಕರಣ, ದರ ಮಿತಿಗಳು ಮತ್ತು ಗೌಪ್ಯತಾ ನೀತಿ ಅಗತ್ಯ."))
        }
    }
}

@Composable
private fun ArchitectureStep(number: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.size(31.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(number, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    modifier: Modifier,
    pack: ContentPack,
    completedCount: Int,
    bookmarkCount: Int,
    attempts: Int,
    correct: Int,
    goal: Int,
    fontScale: Float,
    darkTheme: Boolean,
    language: UiLanguage,
    examDate: String,
    onGoalChange: (Int) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onLanguageChange: (UiLanguage) -> Unit,
    onExamDateChange: (String) -> Unit,
    onClearLearningData: () -> Unit,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var dateDraft by remember(examDate) { mutableStateOf(examDate) }
    val totalTopics = pack.subjects.sumOf { it.topics.size }
    val accuracy = if (attempts == 0) 0 else correct * 100 / attempts
    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Profile & settings", fontWeight = FontWeight.Bold) }) },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Your local analytics", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatLabel("Complete", "$completedCount / $totalTopics")
                        StatLabel("Bookmarks", bookmarkCount.toString())
                        StatLabel("Accuracy", "$accuracy%")
                    }
                    Text("These are private device-only totals. There is no account or cloud analytics in this build.", fontSize = 12.sp)
                }
            }
            SettingsCard("Study planner") {
                Text("Daily goal: $goal minutes", fontWeight = FontWeight.Bold)
                Slider(value = goal.toFloat(), onValueChange = { onGoalChange(it.toInt().coerceIn(10, 120)) }, valueRange = 10f..120f, steps = 10)
                OutlinedTextField(
                    value = dateDraft,
                    onValueChange = { dateDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exam date (optional, e.g. 15-03-2027)") },
                    supportingText = { Text("Set a target date from the official notification; this base planner does not infer one.") },
                )
                OutlinedButton(onClick = { onExamDateChange(dateDraft) }, modifier = Modifier.fillMaxWidth()) { Text(if (dateDraft.isBlank()) "Keep exam date unset" else "Save exam date") }
            }
            SettingsCard("Language / ಭಾಷೆ") {
                Text("Choose how navigation, study guidance and Gemini prompts are displayed. Supplied note text remains source-faithful; online translation is clearly marked as generated.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    UiLanguage.entries.forEach { choice ->
                        FilterChip(
                            selected = language == choice,
                            onClick = { onLanguageChange(choice) },
                            label = { Text(bilingual(choice, choice.english, choice.kannada), fontSize = 11.sp, textAlign = TextAlign.Center) },
                        )
                    }
                }
                Text("Kannada UI and the supplied Kannada study content work fully offline. English-source topics are never silently machine-translated; use the secure Ask Gemini path for an online explanation when it is deployed.", fontSize = 12.sp)
            }
            SettingsCard("Reading and display") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark theme", fontWeight = FontWeight.Bold)
                        Text("Comfortable reading in low light", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                }
                Text("Reader size: ${"%.2f".format(fontScale)}×", fontWeight = FontWeight.Bold)
                Slider(value = fontScale, onValueChange = onFontScaleChange, valueRange = .85f..1.35f, steps = 4)
            }
            SettingsCard("Content pack") {
                Text(pack.name, fontWeight = FontWeight.Bold)
                Text("Version ${pack.version} · ${pack.masterTopicCount} topic folders · ${pack.masterChapterCount} chapters · ${pack.supplementCount} supplements", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("SHA-256: ${pack.sourceHash.take(16)}…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("All 272 chapters are stored in topic folders as offline reader assets. The complete learner-provided master book is bundled in assets/source for traceability.", fontSize = 12.sp)
            }
            SettingsCard("Important disclaimer") {
                Text("This app is not affiliated with or endorsed by KEA or the Government of Karnataka. Detailed preparation expansions are not a substitute for the official notification, corrigenda or current affairs verification.", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear local learning data")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear progress and settings?") },
            text = { Text("This removes local completions, bookmarks, SRS dates, attempts and settings from this device. Bundled notes remain available offline.") },
            confirmButton = { TextButton(onClick = { onClearLearningData(); showClearDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun StatLabel(label: String, value: String) {
    Column {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VerificationNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text("VERIFY", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
            Spacer(Modifier.width(9.dp))
            Text(text, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
