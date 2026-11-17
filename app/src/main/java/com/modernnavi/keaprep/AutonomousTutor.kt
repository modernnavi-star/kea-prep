package com.modernnavi.keaprep

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Public-reference enrichment only. This has no API key and sends no learner data: it requests
 * an encyclopedia title or the public repository's generated feed, then caches the response on
 * the device. Every result remains visibly external/verify-before-exam material.
 */
data class ExtraTopicNote(
    val topicId: String,
    val bullets: List<String>,
    val sources: List<String>,
    val fetchedAt: Long,
)

data class LiveFeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val sourceUrl: String,
    val publishedAt: String,
    val paper: String,
)

data class LiveMcq(
    val id: String,
    val topicId: String,
    val paper: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val sourceUrl: String,
)

data class CachedLiveFeed(
    val generatedAt: String,
    val items: List<LiveFeedItem>,
    val mcqs: List<LiveMcq>,
)

/** A transparent result from a public web lookup, kept separate until the learner chooses Save. */
data class InternetArticle(
    val title: String,
    val extract: String,
    val sourceName: String,
    val sourceUrl: String,
)

data class InternetDeepDive(
    val topicId: String,
    val query: String,
    val keyTakeaways: List<String>,
    val relatedArticles: List<InternetArticle>,
    val sources: List<String>,
    val fetchedAt: Long,
)

data class DeepDiveOutcome(val deepDive: InternetDeepDive?, val message: String)
data class SyncOutcome(val updated: Boolean, val message: String)

class LiveKnowledgeStore(context: Context) {
    private val preferences = context.getSharedPreferences("kea_live_knowledge", Context.MODE_PRIVATE)

    fun topicNote(topicId: String): ExtraTopicNote? = runCatching {
        val raw = preferences.getString("topic_$topicId", null) ?: return null
        val json = JSONObject(raw)
        ExtraTopicNote(
            topicId = json.getString("topicId"),
            bullets = json.optJSONArray("bullets").toStrings(),
            sources = json.optJSONArray("sources").toStrings(),
            fetchedAt = json.optLong("fetchedAt"),
        )
    }.getOrNull()

    fun saveTopicNote(note: ExtraTopicNote) {
        val payload = JSONObject().apply {
            put("topicId", note.topicId)
            put("bullets", JSONArray(note.bullets))
            put("sources", JSONArray(note.sources))
            put("fetchedAt", note.fetchedAt)
        }
        preferences.edit().putString("topic_${note.topicId}", payload.toString()).apply()
    }

    fun feed(): CachedLiveFeed? = runCatching {
        val raw = preferences.getString("live_feed", null) ?: return null
        parseFeed(JSONObject(raw))
    }.getOrNull()

    fun saveFeed(feed: CachedLiveFeed) {
        val items = JSONArray().apply {
            feed.items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id); put("title", item.title); put("summary", item.summary)
                    put("sourceName", item.sourceName); put("sourceUrl", item.sourceUrl)
                    put("publishedAt", item.publishedAt); put("paper", item.paper)
                })
            }
        }
        val mcqs = JSONArray().apply {
            feed.mcqs.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id); put("topicId", item.topicId); put("paper", item.paper)
                    put("question", item.question); put("options", JSONArray(item.options)); put("answer", item.answer)
                    put("explanation", item.explanation); put("sourceUrl", item.sourceUrl)
                })
            }
        }
        preferences.edit()
            .putString("live_feed", JSONObject().put("generatedAt", feed.generatedAt).put("items", items).put("mcqs", mcqs).toString())
            .putLong("last_live_sync", System.currentTimeMillis())
            .apply()
    }

    /** Learner-approved deep dives become part of the local chapter notes and audio queue. */
    fun appendInternetDeepDive(deepDive: InternetDeepDive) {
        val existing = topicNote(deepDive.topicId)
        val additions = deepDive.keyTakeaways.map { "• $it" }
        val bullets = (existing?.bullets.orEmpty() + additions).distinct().takeLast(36)
        val sources = (existing?.sources.orEmpty() + deepDive.sources).distinct().takeLast(24)
        saveTopicNote(
            ExtraTopicNote(
                topicId = deepDive.topicId,
                bullets = bullets,
                sources = sources,
                fetchedAt = deepDive.fetchedAt,
            ),
        )
    }

    fun lastSyncedAt(): Long = preferences.getLong("last_live_sync", 0L)

    fun notificationLine(): String {
        val item = feed()?.items?.firstOrNull()
        return item?.let { "KEA update: ${it.title.take(115)}" }
            ?: "KEA micro-revision: open today’s 3-minute capsule and recall one fact."
    }

    companion object {
        fun parseFeed(json: JSONObject): CachedLiveFeed {
            val items = json.optJSONArray("items").toObjects { item ->
                LiveFeedItem(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    summary = item.optString("summary"),
                    sourceName = item.optString("sourceName"),
                    sourceUrl = item.optString("sourceUrl"),
                    publishedAt = item.optString("publishedAt"),
                    paper = item.optString("paper", "Paper 1"),
                )
            }
            val mcqs = json.optJSONArray("mcqs").toObjects { item ->
                LiveMcq(
                    id = item.optString("id"),
                    topicId = item.optString("topicId", "topic-09"),
                    paper = item.optString("paper", "Paper 1"),
                    question = item.optString("question"),
                    options = item.optJSONArray("options").toStrings(),
                    answer = item.optInt("answer"),
                    explanation = item.optString("explanation"),
                    sourceUrl = item.optString("sourceUrl"),
                )
            }.filter { it.options.size == 4 && it.answer in 0..3 }
            return CachedLiveFeed(json.optString("generatedAt"), items, mcqs)
        }
    }
}

private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
private fun <T> JSONArray?.toObjects(transform: (JSONObject) -> T): List<T> = if (this == null) emptyList() else List(length()) { index -> transform(getJSONObject(index)) }

class AutoContentSync(private val context: Context) {
    private val store = LiveKnowledgeStore(context)
    private val remoteFeedUrl = BuildConfig.LIVE_CONTENT_URL.trim()

    suspend fun syncRemoteFeed(): SyncOutcome = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext SyncOutcome(false, "Offline — cached lessons remain available.")
        if (!remoteFeedUrl.startsWith("https://")) return@withContext SyncOutcome(false, "No HTTPS live-feed URL is configured.")
        runCatching {
            val payload = getJson(remoteFeedUrl)
            val feed = LiveKnowledgeStore.parseFeed(payload)
            store.saveFeed(feed)
            SyncOutcome(true, "Live feed synced: ${feed.items.size} updates and ${feed.mcqs.size} checks.")
        }.getOrElse { error ->
            SyncOutcome(false, "Live feed unavailable — using cached content. ${error.message ?: ""}".trim())
        }
    }

    /**
     * Learner-triggered CORS-safe public lookup. Android does not need a browser proxy or a key;
     * the same `origin=*` public endpoints are safe when this UI is later hosted in a WebView too.
     * Results are deliberately not persisted until [LiveKnowledgeStore.appendInternetDeepDive] runs.
     */
    suspend fun fetchInternetDeepDive(topicId: String, query: String): DeepDiveOutcome = withContext(Dispatchers.IO) {
        val cleanedQuery = query.trim().replace(Regex("\\s+"), " ").take(180)
        if (cleanedQuery.length < 2) return@withContext DeepDiveOutcome(null, "Enter at least two characters to search public reference sources.")
        if (!isOnline()) return@withContext DeepDiveOutcome(null, "Offline — saved internet notes and core lessons remain available.")
        runCatching {
            val articles = buildList {
                addAll(fetchWikipediaSearch("en", cleanedQuery, 3))
                addAll(fetchWikipediaSearch("kn", cleanedQuery, 2))
                fetchWikidataArticle(cleanedQuery)?.let(::add)
            }.distinctBy { it.sourceUrl }.take(6)
            val keyTakeaways = articles.flatMap { article ->
                article.extract
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
                    .filter { it.length > 20 }
                    .take(2)
                    .map { "${article.sourceName}: $it" }
            }.distinct().take(9)
            if (articles.isEmpty() || keyTakeaways.isEmpty()) error("No readable public-reference extract was returned for this term.")
            InternetDeepDive(
                topicId = topicId,
                query = cleanedQuery,
                keyTakeaways = keyTakeaways,
                relatedArticles = articles,
                sources = articles.map { it.sourceUrl }.distinct(),
                fetchedAt = System.currentTimeMillis(),
            )
        }.fold(
            onSuccess = { DeepDiveOutcome(it, "Found ${it.keyTakeaways.size} cited public-reference takeaways. Save only what helps your revision.") },
            onFailure = { DeepDiveOutcome(null, "Could not fetch public-reference details. Existing offline notes were not changed. ${it.message.orEmpty()}".trim()) },
        )
    }

    /** Auto-teach enrichment uses the same transparent public lookup and safely caches it. */
    suspend fun enrichTopic(topicId: String, topicTitle: String, subjectTitle: String): SyncOutcome {
        val query = topicQueries(topicTitle, subjectTitle).firstOrNull().orEmpty()
        val outcome = fetchInternetDeepDive(topicId, query)
        val deepDive = outcome.deepDive ?: return SyncOutcome(false, outcome.message)
        store.appendInternetDeepDive(deepDive)
        return SyncOutcome(true, "Saved ${deepDive.keyTakeaways.size} extra reference facts for offline use.")
    }

    private fun topicQueries(title: String, subject: String): List<String> {
        val aliases = listOf(
            "ಪಂಚಾಯತ್" to "Panchayati raj",
            "ಸಂವಿಧಾನ" to "Constitution of India",
            "ವಿಜಯನಗರ" to "Vijayanagara Empire",
            "ಕಂಪ್ಯೂಟರ್" to "Computer network",
            "ಜಾಲ" to "Computer network",
            "ಕನ್ನಡ" to "Kannada literature",
            "ಭೂಗೋಳ" to "Geography of Karnataka",
            "ಕರ್ನಾಟಕ" to "Karnataka",
            "ಮೌರ್ಯ" to "Maurya Empire",
            "ಹರಪ್ಪಾ" to "Indus Valley Civilisation",
            "ಭೌತ" to "Physics",
            "ಜೀವ" to "Biology",
        )
        val alias = aliases.firstOrNull { title.contains(it.first, ignoreCase = true) || subject.contains(it.first, ignoreCase = true) }?.second
        return listOfNotNull(title.trim().take(160), alias, subject.trim().take(120)).distinct()
    }

    /**
     * Required free search/extract endpoint: no paid key, no browser-only header and `origin=*`
     * for WebView/browser compatibility. The title-derived URL is an external-reference fallback.
     */
    private fun fetchWikipediaSearch(language: String, query: String, limit: Int): List<InternetArticle> {
        val base = "https://$language.wikipedia.org"
        val url = "$base/w/api.php?action=query&generator=search&gsrsearch=${encodeQuery(query)}&gsrlimit=$limit&prop=extracts&exintro=1&explaintext=1&format=json&origin=*"
        val pages = getJson(url).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val sourceName = if (language == "kn") "Kannada Wikipedia" else "English Wikipedia"
        return buildList {
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val title = page.optString("title").trim()
                val extract = page.optString("extract").trim()
                if (title.isNotBlank() && extract.length > 20) {
                    add(InternetArticle(title, extract.take(2_400), sourceName, "$base/wiki/${encodePath(title)}"))
                }
            }
        }.take(limit)
    }

    /** Required key-free Wikidata entity lookup, useful for names, acts and canonical entities. */
    private fun fetchWikidataArticle(query: String): InternetArticle? = runCatching {
        val url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${encodeQuery(query)}&language=en&format=json&origin=*"
        val result = getJson(url).optJSONArray("search")?.optJSONObject(0) ?: return null
        val label = result.optString("label").trim()
        val description = result.optString("description").trim()
        if (label.isBlank() || description.isBlank()) return null
        InternetArticle(label, "$label: $description", "Wikidata", result.optString("concepturi").ifBlank { url })
    }.getOrNull()

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("HTTP $code")
        return JSONObject(body)
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private fun encodePath(value: String): String = encodeQuery(value).replace("+", "%20")
}

object AutoContentSyncScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<LiveKnowledgeSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("kea_auto_content_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class LiveKnowledgeSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val outcome = AutoContentSync(applicationContext).syncRemoteFeed()
        return if (outcome.updated || outcome.message.startsWith("Offline")) Result.success() else Result.retry()
    }
}

object TutorReminderScheduler {
    const val DEFAULT_TIMES = "08:00,13:30,21:30"
    private const val CHANNEL_ID = "kea_micro_tutor"

    fun schedule(context: Context, times: String) {
        val workManager = WorkManager.getInstance(context)
        parseTimes(times).forEach { time ->
            val delay = delayUntil(time)
            val request = PeriodicWorkRequestBuilder<TutorReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("kea_tutor_reminder")
                .build()
            workManager.enqueueUniquePeriodicWork("kea_tutor_reminder_${time.replace(":", "")}", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelAllWorkByTag("kea_tutor_reminder")

    fun parseTimes(raw: String): List<String> = raw.split(',').map { it.trim() }
        .filter { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
        .distinct()
        .take(3)
        .ifEmpty { listOf("08:00", "13:30", "21:30") }

    private fun delayUntil(time: String): Long {
        val parts = time.split(":")
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    fun notificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "KEA micro-lessons", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Optional 15-second KEA facts and rapid checks"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun post(context: Context, line: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        notificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.modernnavi.keaprep.R.drawable.ic_launcher)
            .setContentTitle("KEA Prep · 15-second tutor")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}

class TutorReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        TutorReminderScheduler.post(applicationContext, LiveKnowledgeStore(applicationContext).notificationLine())
        return Result.success()
    }
}

/** Android TextToSpeech keeps audio entirely on-device when a language voice is installed. */
class TutorAudioPlayer(context: Context) : TextToSpeech.OnInitListener {
    var isPlaying by mutableStateOf(false)
        private set
    var nowPlayingTitle by mutableStateOf("Listen & Learn")
        private set
    private var ready = false
    private var tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    fun playCapsule(
        title: String,
        corePoints: List<String>,
        extraPoints: List<String>,
        questions: List<LiveMcq>,
        kannada: Boolean,
    ) {
        if (!ready) return
        tts.stop()
        nowPlayingTitle = title
        isPlaying = true
        val locale = if (kannada) Locale("kn", "IN") else Locale("en", "IN")
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        val lines = buildList {
            add("Three minute KEA lesson. $title")
            corePoints.take(3).forEach { add(it.removePrefix("• ")) }
            if (extraPoints.isNotEmpty()) {
                add("Extra public-reference facts. Verify changing information before the exam.")
                extraPoints.take(3).forEach { add(it.removePrefix("• ")) }
            }
            questions.take(2).forEach { question ->
                add(question.question)
                question.options.forEachIndexed { index, option -> add("Option ${index + 1}. $option") }
                add("Pause for recall.")
                add("Correct answer. Option ${question.answer + 1}. ${question.options[question.answer]}. ${question.explanation}")
            }
            add("End of micro lesson. Open the chapter for complete notes.")
        }
        lines.forEachIndexed { index, line ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(line.take(3_800), queueMode, null, "kea_audio_$index")
            if (line == "Pause for recall.") tts.playSilentUtterance(4_000L, TextToSpeech.QUEUE_ADD, "kea_pause_$index")
        }
        // TextToSpeech offers no reliable queue-complete callback on every device; this is reset when Stop is tapped.
    }

    fun stop() {
        tts.stop()
        isPlaying = false
        nowPlayingTitle = "Listen & Learn"
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
