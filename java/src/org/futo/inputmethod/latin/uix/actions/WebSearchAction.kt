package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionSearchEditText
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.theme.Typography
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class WebSearchResultOptions(
    val contextChars: Int? = null,
    val maxResults: Int? = null,
    val filter: String? = null,
    val caseSensitive: Boolean? = null,
    val wholeWords: Boolean? = null,
    val useRegex: Boolean? = null
)

@Serializable
data class WebSearchResultItem(
    val path: String,
    val offset: Long,
    val match_text: String,
    val snippet: String
)

@Serializable
data class WebSearchResponse(
    val query: String,
    val total_matches: Int,
    val files_count: Int,
    val page: Int,
    val limit: Int,
    val total_pages: Int,
    val options: WebSearchResultOptions? = null,
    val results: List<WebSearchResultItem> = emptyList()
)

class WebSearchManager(val context: Context, val coroutineScope: LifecycleCoroutineScope) : PersistentActionState {
    val searchResults = mutableStateListOf<WebSearchResultItem>()
    val searchError = mutableStateOf("")
    val isSearching = mutableStateOf(false)

    private val json = Json { ignoreUnknownKeys = true }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults.clear()
            searchError.value = ""
            return
        }

        isSearching.value = true
        searchError.value = ""

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlString = "https://docdocgo.lak.nz/api/search?q=${encodedQuery}&limit=20"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString<WebSearchResponse>(responseText)
                    withContext(Dispatchers.Main) {
                        searchResults.clear()
                        searchResults.addAll(response.results)
                        isSearching.value = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        searchError.value = "HTTP $responseCode"
                        isSearching.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    searchError.value = e.message ?: "Unknown error"
                    isSearching.value = false
                }
            }
        }
    }

    override suspend fun cleanUp() {}
    override fun close() {}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebSearchResultView(
    modifier: Modifier,
    item: WebSearchResultItem,
    onInsert: (WebSearchResultItem) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                enabled = true,
                onClick = { onInsert(item) },
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.snippet, style = Typography.SmallMl)
        }
    }
}

val WebSearchAction = Action(
    icon = R.drawable.sym_keyboard_search_lxx_light,
    name = R.string.action_web_search_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        WebSearchManager(manager.getContext(), manager.getLifecycleScope())
    },
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val searchManager = persistent as WebSearchManager

        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_web_search_title)
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val searchQueryState = remember { mutableStateOf("") }
                val searchQuery = searchQueryState.value

                Column(modifier = Modifier.fillMaxWidth()) {
                    ActionSearchEditText(
                        text = searchQueryState,
                        placeholder = stringResource(R.string.action_web_search_search),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.sym_keyboard_search_lxx_light),
                                contentDescription = stringResource(R.string.action_web_search_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchManager.performSearch(searchQuery) }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.sym_keyboard_search_lxx_light),
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (searchManager.isSearching.value) {
                        Text(
                            text = "Searching...",
                            modifier = Modifier.padding(16.dp),
                            style = Typography.SmallMl
                        )
                    } else if (searchManager.searchError.value.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.action_web_search_error, searchManager.searchError.value),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = Typography.SmallMl
                        )
                    } else if (searchManager.searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.action_web_search_no_results),
                            modifier = Modifier.padding(16.dp),
                            style = Typography.SmallMl
                        )
                    } else {
                        LazyVerticalStaggeredGrid(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            columns = StaggeredGridCells.Adaptive(160.dp),
                            verticalItemSpacing = 4.dp,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(searchManager.searchResults.size) { index ->
                                val item = searchManager.searchResults[index]
                                WebSearchResultView(
                                    modifier = Modifier,
                                    item = item,
                                    onInsert = {
                                        manager.typeText(it.snippet.replace("\n", " "))
                                        manager.closeActionWindow()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
)
