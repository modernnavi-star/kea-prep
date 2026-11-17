package com.modernnavi.keaprep

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * A device-native deep-dive dialog. It renders only public-source extracts, makes source links
 * explicit, and keeps a result transient until the learner chooses Save to My Offline Notes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun InternetDeepDiveDialog(
    title: String,
    initialQuery: String,
    deepDive: InternetDeepDive?,
    isLoading: Boolean,
    status: String,
    onFetch: (String) -> Unit,
    onSave: (InternetDeepDive) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable(title, initialQuery) { mutableStateOf(initialQuery) }
    var savedQuery by remember { mutableStateOf("") }
    val saved = deepDive != null && savedQuery == deepDive.query

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text("Internet deep-dive · VERIFY", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    "Searches English Wikipedia, Kannada Wikipedia and Wikidata using public key-free endpoints. These are extra references, not official KEA material.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Topic or follow-up keyword") },
                    supportingText = { Text("Example: Article 243G, Vijayanagara, MS Excel shortcut") },
                )
                Button(
                    onClick = { onFetch(query) },
                    enabled = !isLoading && query.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLoading) "Searching public sources…" else "Get More Details from Internet")
                }
                if (status.isNotBlank()) {
                    Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (deepDive != null) {
                    HorizontalDivider()
                    Text("Key exam takeaways · VERIFY", fontWeight = FontWeight.Bold)
                    deepDive.keyTakeaways.forEach { takeaway ->
                        Text("• $takeaway", fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onSave(deepDive)
                            savedQuery = deepDive.query
                        },
                        enabled = !saved,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (saved) "Saved to My Offline Notes" else "Save to My Offline Notes") }
                    Text(
                        "Saved material is appended to this topic’s local notes and is included in Listen & Learn. It stays available offline.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text("Related sub-articles / deeper concepts", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        deepDive.relatedArticles.forEach { article ->
                            AssistChip(
                                onClick = {
                                    query = article.title
                                    onFetch(article.title)
                                },
                                label = { Text(article.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }

                    Text("Quick external references", fontWeight = FontWeight.Bold)
                    val firstArticle = deepDive.relatedArticles.firstOrNull()
                    if (firstArticle != null) {
                        TextButton(onClick = { openExternal(context, firstArticle.sourceUrl) }) {
                            Text("Open Full Article · ${firstArticle.sourceName}")
                        }
                    }
                    TextButton(onClick = {
                        openExternal(context, "https://www.google.com/search?q=${Uri.encode("KEA ${deepDive.query} previous year questions")}")
                    }) { Text("Search KEA Previous Questions on Google") }
                    Text(
                        "Sources: ${deepDive.sources.joinToString(" · ")}",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
