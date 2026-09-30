package com.example.slackadaptive.features.channellist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.slackadaptive.data.Channel
import com.example.slackadaptive.data.DirectMessage
import com.example.slackadaptive.data.SlackRepository
import com.example.slackadaptive.screens.ChannelListScreen
import com.example.slackadaptive.screens.ChatScreen
import com.example.slackadaptive.screens.LocalWorkspacePaneState
import com.example.slackadaptive.ui.theme.SlackAubergine
import com.example.slackadaptive.ui.theme.SlackGreenOnline
import com.example.slackadaptive.ui.theme.SlackMentionBadge
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

/**
 * Circuit Presenter for [ChannelListScreen].
 *
 * Notice that this Presenter has ZERO dependency on `ListDetailPaneScaffold`—it simply
 * emits `navigator.goTo(ChatScreen(channelId = event.channelId))`.
 * The parent `WorkspaceUi` intercepts that `NavEvent.GoTo` via `CircuitContent(onNavEvent = ...)`
 * and routes it into the `Detail` pane of `NavigableListDetailPaneScaffold`.
 */
class ChannelListPresenter(
    private val screen: ChannelListScreen,
    private val navigator: Navigator,
    private val repository: SlackRepository,
) : Presenter<ChannelListScreen.State> {

    @Composable
    override fun present(): ChannelListScreen.State {
        val searchQueryState = rememberRetained { mutableStateOf("") }
        val searchQuery by searchQueryState
        val allChannels by repository.channels.collectAsState()
        val allDms by repository.directMessages.collectAsState()
        val paneState = LocalWorkspacePaneState.current

        val filteredChannels = if (screen.filterDmsOnly) {
            emptyList()
        } else if (searchQuery.isBlank()) {
            allChannels
        } else {
            allChannels.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.topic.contains(searchQuery, ignoreCase = true)
            }
        }

        val filteredDms = if (searchQuery.isBlank()) {
            allDms
        } else {
            allDms.filter {
                it.userName.contains(searchQuery, ignoreCase = true) ||
                    it.userRole.contains(searchQuery, ignoreCase = true)
            }
        }

        return ChannelListScreen.State(
            workspaceName = "Acme Engineering",
            searchQuery = searchQuery,
            channels = filteredChannels,
            directMessages = filteredDms,
            selectedChannelId = paneState.selectedChannelId,
            filterDmsOnly = screen.filterDmsOnly,
        ) { event ->
            when (event) {
                is ChannelListScreen.Event.UpdateSearchQuery -> {
                    searchQueryState.value = event.query
                }
                is ChannelListScreen.Event.SelectChannel -> {
                    repository.markChannelRead(event.channelId)
                    navigator.goTo(ChatScreen(channelId = event.channelId))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListUi(
    state: ChannelListScreen.State,
    modifier: Modifier = Modifier,
) {
    val starredChannels = state.channels.filter { it.isStarred }
    val regularChannels = state.channels.filterNot { it.isStarred }
    val listState = rememberLazyListState()

    // Ensure the selected channel or DM is scrolled into view when transitioning
    // between Compact (1-pane) and Large (2/3-pane) layouts.
    LaunchedEffect(state.selectedChannelId, starredChannels.size, regularChannels.size, state.directMessages.size) {
        val selectedId = state.selectedChannelId ?: return@LaunchedEffect
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == selectedId }
        if (!isVisible) {
            val orderedKeys = buildList {
                if (starredChannels.isNotEmpty()) {
                    add("header_starred")
                    starredChannels.forEach { add(it.id) }
                    add("divider_starred")
                }
                if (regularChannels.isNotEmpty()) {
                    add("header_channels")
                    regularChannels.forEach { add(it.id) }
                    add("divider_channels")
                }
                if (state.directMessages.isNotEmpty()) {
                    add("header_dms")
                    state.directMessages.forEach { add(it.id) }
                }
            }
            val targetIndex = orderedKeys.indexOf(selectedId)
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Workspace Header
            Surface(
                color = SlackAubergine,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.workspaceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SlackGreenOnline),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Circuit 0.39",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = {
                            state.eventSink(ChannelListScreen.Event.UpdateSearchQuery(it))
                        },
                        placeholder = {
                            Text(
                                text = "Jump to channel or DM...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search channels",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        state.eventSink(ChannelListScreen.Event.UpdateSearchQuery(""))
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                            focusedBorderColor = Color.White.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (starredChannels.isNotEmpty()) {
                    item(key = "header_starred") {
                        SectionHeader(
                            title = "Starred",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    items(starredChannels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            isSelected = channel.id == state.selectedChannelId,
                            onClick = {
                                state.eventSink(ChannelListScreen.Event.SelectChannel(channel.id))
                            },
                        )
                    }
                    item(key = "divider_starred") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                if (regularChannels.isNotEmpty()) {
                    item(key = "header_channels") {
                        SectionHeader(title = "Channels")
                    }
                    items(regularChannels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            isSelected = channel.id == state.selectedChannelId,
                            onClick = {
                                state.eventSink(ChannelListScreen.Event.SelectChannel(channel.id))
                            },
                        )
                    }
                    item(key = "divider_channels") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                if (state.directMessages.isNotEmpty()) {
                    item(key = "header_dms") {
                        SectionHeader(title = "Direct Messages")
                    }
                    items(state.directMessages, key = { it.id }) { dm ->
                        DirectMessageRow(
                            dm = dm,
                            isSelected = dm.id == state.selectedChannelId,
                            onClick = {
                                state.eventSink(ChannelListScreen.Event.SelectChannel(dm.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else if (channel.unreadCount > 0) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (channel.isPrivate) Icons.Default.Lock else Icons.Default.Tag,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || channel.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.topic,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (channel.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                containerColor = SlackMentionBadge,
                contentColor = Color.White,
            ) {
                Text(
                    text = channel.unreadCount.toString(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DirectMessageRow(
    dm: DirectMessage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else if (dm.unreadCount > 0) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = dm.avatarInitials,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (dm.isOnline) SlackGreenOnline else Color.Gray),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dm.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected || dm.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dm.statusEmoji != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dm.statusEmoji,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = dm.userRole,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (dm.unreadCount > 0) {
            Badge(
                containerColor = SlackMentionBadge,
                contentColor = Color.White,
            ) {
                Text(
                    text = dm.unreadCount.toString(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
