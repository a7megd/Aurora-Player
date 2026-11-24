package com.whisper.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whisper.player.shared.model.ArticleStatus
import com.whisper.player.shared.model.CommunityArticle
import com.whisper.player.shared.model.CommunityComment
import com.whisper.player.shared.model.CommunityPost
import com.whisper.player.shared.model.CommunityPostKind
import com.whisper.player.shared.platform.CommunityService
import kotlinx.coroutines.launch

private val communityTabs = listOf("المنشورات", "الأصدقاء", "الطلبات", "المقالات")

/**
 * Shared community UI. A platform must inject its authenticated CommunityService;
 * no fake local feed is shown when the backend is not configured.
 */
@Composable
fun CommunityScreen(
    service: CommunityService?,
    currentUserId: String?,
    currentUserName: String,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(0) }
    var postText by remember { mutableStateOf("") }
    var articleTitle by remember { mutableStateOf("") }
    var articleBody by remember { mutableStateOf("") }
    var memberQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<com.whisper.player.shared.model.CommunityProfile>()) }
    val scope = rememberCoroutineScope()

    val posts by (service?.feed ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val incoming by (service?.incomingFriendRequests ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val outgoing by (service?.outgoingFriendRequests ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val friends by (service?.friends ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val articles by (service?.articles ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("مجتمع Whisper", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = selectedTab) {
            communityTabs.forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
            }
        }
        if (service == null || currentUserId.isNullOrBlank()) {
            Text(
                "سجّل الدخول لتفعيل المجتمع. يلزم ربط خدمة الحسابات والمجتمع قبل عرض البيانات.",
                modifier = Modifier.padding(vertical = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            when (selectedTab) {
                0 -> FeedTab(
                    posts = posts,
                    postText = postText,
                    onPostText = { postText = it },
                    onPublish = {
                        val body = postText.trim()
                        if (body.isNotEmpty()) scope.launch {
                            runCatching {
                                service.publishPost(
                                    CommunityPost(
                                        id = "post-${currentUserId}-${kotlin.random.Random.nextLong()}",
                                        authorUid = currentUserId,
                                        authorName = currentUserName,
                                        kind = CommunityPostKind.POST,
                                        body = body,
                                        createdAtEpochMillis = 0,
                                    )
                                )
                                postText = ""
                            }.onFailure { onError(it.message ?: "تعذر نشر المنشور") }
                        }
                    },
                    onDelete = { postId -> scope.launch { runCatching { service.deletePost(postId) }.onFailure { onError(it.message ?: "تعذر حذف المنشور") } } },
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    onComment = { postId, body ->
                        scope.launch {
                            runCatching {
                                service.addPostComment(CommunityComment("comment-${kotlin.random.Random.nextLong()}", postId, currentUserId, currentUserName, body, 0))
                            }.onFailure { onError(it.message ?: "تعذر إضافة التعليق") }
                        }
                    },
                )
                1 -> MembersTab(
                    query = memberQuery,
                    onQuery = { memberQuery = it },
                    results = searchResults,
                    friends = friends,
                    onSearch = {
                        scope.launch {
                            runCatching { searchResults = service.searchProfiles(memberQuery.trim()) }
                                .onFailure { onError(it.message ?: "تعذر البحث عن الأعضاء") }
                        }
                    },
                    onAdd = { uid -> scope.launch { runCatching { service.sendFriendRequest(uid) }.onFailure { onError(it.message ?: "تعذر إرسال الطلب") } } },
                    onFollow = { uid -> scope.launch { runCatching { service.followUser(uid) }.onFailure { onError(it.message ?: "تعذرت المتابعة") } } },
                    onBlock = { uid -> scope.launch { runCatching { service.blockUser(uid) }.onFailure { onError(it.message ?: "تعذر حظر العضو") } } },
                    onRemoveFriend = { uid -> scope.launch { runCatching { service.removeFriend(uid) }.onFailure { onError(it.message ?: "تعذرت إزالة الصديق") } } },
                )
                2 -> RequestsTab(
                    incoming = incoming,
                    outgoing = outgoing,
                    onRespond = { id, accept -> scope.launch { runCatching { service.respondToFriendRequest(id, accept) }.onFailure { onError(it.message ?: "تعذر تحديث الطلب") } } },
                )
                else -> ArticlesTab(
                    articles = articles,
                    title = articleTitle,
                    body = articleBody,
                    onTitle = { articleTitle = it },
                    onBody = { articleBody = it },
                    onSave = {
                        val title = articleTitle.trim()
                        val body = articleBody.trim()
                        if (title.isNotEmpty()) scope.launch {
                            runCatching {
                                val now = 0L
                                service.saveArticle(
                                    CommunityArticle(
                                        id = "article-${currentUserId}-${kotlin.random.Random.nextLong()}",
                                        authorUid = currentUserId,
                                        authorName = currentUserName,
                                        title = title,
                                        body = body,
                                        status = ArticleStatus.DRAFT,
                                        createdAtEpochMillis = now,
                                        updatedAtEpochMillis = now,
                                    )
                                )
                                articleTitle = ""
                                articleBody = ""
                            }.onFailure { onError(it.message ?: "تعذر حفظ المقال") }
                        }
                    },
                    onDelete = { id -> scope.launch { runCatching { service.deleteArticle(id) }.onFailure { onError(it.message ?: "تعذر حذف المقال") } } },
                    onPublish = { id -> scope.launch { runCatching { service.publishArticle(id) }.onFailure { onError(it.message ?: "تعذر نشر المقال") } } },
                    currentUserId = currentUserId,
                )
            }
        }
    }
}

@Composable
private fun FeedTab(
    posts: List<CommunityPost>,
    postText: String,
    onPostText: (String) -> Unit,
    onPublish: () -> Unit,
    onDelete: (String) -> Unit,
    currentUserId: String,
    currentUserName: String,
    onComment: (String, String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = postText,
            onValueChange = onPostText,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اكتب منشورًا أو خاطرة") },
            minLines = 2,
        )
        Button(onClick = onPublish, enabled = postText.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
            Text("نشر")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(posts, key = { it.id }) { post ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                        Text(post.body, modifier = Modifier.padding(vertical = 8.dp))
                        Text(post.commentCount.toString() + " تعليق", style = MaterialTheme.typography.labelSmall)
                        var commentText by remember(post.id) { mutableStateOf("") }
                        OutlinedTextField(commentText, { commentText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اكتب تعليقًا") })
                        Button(onClick = { onComment(post.id, commentText.trim()); commentText = "" }, enabled = commentText.isNotBlank()) { Text("تعليق") }
                        if (post.authorUid == currentUserId) {
                            OutlinedButton(onClick = { onDelete(post.id) }) { Text("حذف المنشور") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(
    query: String,
    onQuery: (String) -> Unit,
    results: List<com.whisper.player.shared.model.CommunityProfile>,
    friends: List<com.whisper.player.shared.model.CommunityProfile>,
    onSearch: () -> Unit,
    onAdd: (String) -> Unit,
    onFollow: (String) -> Unit,
    onBlock: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth(), label = { Text("ابحث عن الأعضاء") })
        Button(onClick = onSearch, enabled = query.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("بحث") }
        Text("الأصدقاء", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(friends, key = { it.uid }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRemoveFriend(member.uid) }) { Text("إزالة") }
                            OutlinedButton(onClick = { onBlock(member.uid) }) { Text("حظر") }
                        }
                    }
                }
            }
            items(results.filterNot { candidate -> friends.any { it.uid == candidate.uid } }, key = { it.uid }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onAdd(member.uid) }) { Text("إضافة صديق") }
                            OutlinedButton(onClick = { onFollow(member.uid) }) { Text("متابعة") }
                            OutlinedButton(onClick = { onBlock(member.uid) }) { Text("حظر") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(name: String, subtitle: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RequestsTab(
    incoming: List<com.whisper.player.shared.model.FriendRequest>,
    outgoing: List<com.whisper.player.shared.model.FriendRequest>,
    onRespond: (String, Boolean) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("الطلبات الواردة", style = MaterialTheme.typography.titleMedium) }
        items(incoming, key = { it.id }) { request ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(request.senderName)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(request.id, true) }) { Text("قبول") }
                        OutlinedButton(onClick = { onRespond(request.id, false) }) { Text("رفض") }
                    }
                }
            }
        }
        item { Text("الطلبات المرسلة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(outgoing, key = { it.id }) { request ->
            Text(request.recipientUid, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun ArticlesTab(
    articles: List<CommunityArticle>,
    title: String,
    body: String,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onPublish: (String) -> Unit,
    currentUserId: String,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(title, onTitle, modifier = Modifier.fillMaxWidth(), label = { Text("عنوان المقال") })
        OutlinedTextField(body, onBody, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("محتوى المقال") }, minLines = 4)
        Button(onClick = onSave, enabled = title.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("حفظ كمسودة") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(articles.filter { it.status == ArticleStatus.PUBLISHED || it.authorUid == currentUserId }, key = { it.id }) { article ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(article.title, style = MaterialTheme.typography.titleMedium)
                        Text(article.authorName, style = MaterialTheme.typography.labelSmall)
                        Text(article.body, maxLines = 5, modifier = Modifier.padding(vertical = 6.dp))
                        if (article.authorUid == currentUserId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (article.status == ArticleStatus.DRAFT) {
                                    Button(onClick = { onPublish(article.id) }) { Text("نشر المقال") }
                                }
                                OutlinedButton(onClick = { onDelete(article.id) }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
}
