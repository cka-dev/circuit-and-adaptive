package com.example.slackadaptive.features.chat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.slackadaptive.data.ChatMessage
import com.example.slackadaptive.data.Reaction
import com.example.slackadaptive.data.SlackRepository
import com.example.slackadaptive.screens.ChatScreen
import com.example.slackadaptive.screens.LocalWorkspacePaneState
import com.example.slackadaptive.screens.ThreadScreen
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

/**
 * Circuit Presenter for [ChatScreen] (Detail Pane).
 *
 * Completely independent of `ListDetailPaneScaffold`:
 * - Opening a message thread emits `navigator.goTo(ThreadScreen(screen.channelId, event.messageId))`.
 * - Pressing Back emits `navigator.pop()`.
 * The parent `WorkspaceUi` intercepts those `NavEvent`s via `CircuitContent(onNavEvent = ...)`
 * and routes them into the scaffold's `Extra` pane or back stack.
 */
class ChatPresenter(
    private val screen: ChatScreen,
    private val navigator: Navigator,
    private val repository: SlackRepository,
) : Presenter<ChatScreen.State> {

    @Composable
    override fun present(): ChatScreen.State {
        val channels by repository.channels.collectAsState()
        val directMessages by repository.directMessages.collectAsState()
        val messagesByChannel by repository.messagesByChannel.collectAsState()
        val paneState = LocalWorkspacePaneState.current

        val channel = repository.resolveChannel(
            channelId = screen.channelId,
            channelList = channels,
            dmList = directMessages,
        )
        val messages = messagesByChannel[screen.channelId].orEmpty()

        val draftState = rememberRetained(screen.channelId) { mutableStateOf("") }
        val draft by draftState

        return ChatScreen.State(
            channel = channel,
            messages = messages,
            draftMessage = draft,
            activeThreadMessageId = if (paneState.isExtraVisible) paneState.activeThreadMessageId else null,
            showBackButton = !paneState.isListVisible,
        ) { event ->
            when (event) {
                is ChatScreen.Event.UpdateDraft -> {
                    draftState.value = event.text
                }
                ChatScreen.Event.SendMessage -> {
                    if (draftState.value.isNotBlank()) {
                        repository.sendMessage(screen.channelId, draftState.value)
                        draftState.value = ""
                    }
                }
                is ChatScreen.Event.OpenThread -> {
                    navigator.goTo(
                        ThreadScreen(
                            channelId = screen.channelId,
                            parentMessageId = event.messageId,
                        )
                    )
                }
                is ChatScreen.Event.ToggleReaction -> {
                    repository.toggleReaction(screen.channelId, event.messageId, event.emoji)
                }
                ChatScreen.Event.NavigateBack -> {
                    navigator.pop()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatUi(
    state: ChatScreen.State,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Channel Top Bar
            ChatHeader(
                channelName = state.channel?.name ?: state.channel?.id.orEmpty(),
                topic = state.channel?.topic.orEmpty(),
                memberCount = state.channel?.memberCount ?: 0,
                isPrivate = state.channel?.isPrivate == true,
                showBackButton = state.showBackButton,
                onBackClick = { state.eventSink(ChatScreen.Event.NavigateBack) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        isThreadActive = message.id == state.activeThreadMessageId,
                        onOpenThread = {
                            state.eventSink(ChatScreen.Event.OpenThread(message.id))
                        },
                        onToggleReaction = { emoji ->
                            state.eventSink(ChatScreen.Event.ToggleReaction(message.id, emoji))
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Message Composer
            MessageComposerBar(
                channelName = state.channel?.name ?: "channel",
                draftText = state.draftMessage,
                onDraftChanged = { state.eventSink(ChatScreen.Event.UpdateDraft(it)) },
                onSendClick = { state.eventSink(ChatScreen.Event.SendMessage) },
            )
        }
    }
}

@Composable
private fun ChatHeader(
    channelName: String,
    topic: String,
    memberCount: Int,
    isPrivate: Boolean,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (showBackButton) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to channels",
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (topic.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "Members",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = memberCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isThreadActive: Boolean,
    onOpenThread: () -> Unit,
    onToggleReaction: (String) -> Unit,
) {
    val containerColor = if (isThreadActive) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderStroke = if (isThreadActive) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = borderStroke,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // Author Avatar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = message.authorInitials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.authorName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reactions & Quick Reaction Trigger
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.reactions.forEach { reaction ->
                        ReactionChip(
                            reaction = reaction,
                            onClick = { onToggleReaction(reaction.emoji) },
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onToggleReaction("🙌") },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddReaction,
                                contentDescription = "Add reaction",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Thread Action Row (opens Extra Pane in ListDetailPaneScaffold)
                val replyCount = message.replies.size
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (replyCount > 0) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.clickable(onClick = onOpenThread),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (replyCount > 0) {
                                "$replyCount ${if (replyCount == 1) "reply" else "replies"} · View thread in Extra Pane →"
                            } else {
                                "Reply in thread"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: Reaction,
    onClick: () -> Unit,
) {
    val backgroundColor = if (reaction.userReacted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (reaction.userReacted) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(text = reaction.emoji, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MessageComposerBar(
    channelName: String,
    draftText: String,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        OutlinedTextField(
            value = draftText,
            onValueChange = onDraftChanged,
            placeholder = {
                Text(
                    text = "Message #$channelName",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSendClick,
            enabled = draftText.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
            )
        }
    }
}
