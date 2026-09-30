package com.example.slackadaptive.features.thread

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
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
import androidx.compose.ui.unit.dp
import com.example.slackadaptive.data.ChatMessage
import com.example.slackadaptive.data.SlackRepository
import com.example.slackadaptive.data.ThreadReply
import com.example.slackadaptive.screens.LocalWorkspacePaneState
import com.example.slackadaptive.screens.ThreadScreen
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter

/**
 * Circuit Presenter for [ThreadScreen] (Extra Pane).
 *
 * Closing the thread simply calls `navigator.pop()`, which `WorkspaceUi` intercepts via
 * `CircuitContent(onNavEvent = ...)` to pop the `Extra` pane from `ListDetailPaneScaffold`.
 */
class ThreadPresenter(
    private val screen: ThreadScreen,
    private val navigator: Navigator,
    private val repository: SlackRepository,
) : Presenter<ThreadScreen.State> {

    @Composable
    override fun present(): ThreadScreen.State {
        val channels by repository.channels.collectAsState()
        val directMessages by repository.directMessages.collectAsState()
        val messagesByChannel by repository.messagesByChannel.collectAsState()
        val paneState = LocalWorkspacePaneState.current

        val channel = repository.resolveChannel(
            channelId = screen.channelId,
            channelList = channels,
            dmList = directMessages,
        )
        val parentMessage = messagesByChannel[screen.channelId]
            .orEmpty()
            .find { it.id == screen.parentMessageId }

        val draftState = rememberRetained(screen.parentMessageId) { mutableStateOf("") }
        val draftReply by draftState

        return ThreadScreen.State(
            channelName = channel?.name ?: screen.channelId,
            parentMessage = parentMessage,
            draftReply = draftReply,
            showBackButton = !paneState.isDetailVisible,
        ) { event ->
            when (event) {
                is ThreadScreen.Event.UpdateDraftReply -> {
                    draftState.value = event.text
                }
                ThreadScreen.Event.SendReply -> {
                    if (draftState.value.isNotBlank()) {
                        repository.sendThreadReply(
                            channelId = screen.channelId,
                            parentMessageId = screen.parentMessageId,
                            text = draftState.value,
                        )
                        draftState.value = ""
                    }
                }
                ThreadScreen.Event.CloseThread -> {
                    navigator.pop()
                }
            }
        }
    }
}

@Composable
fun ThreadUi(
    state: ThreadScreen.State,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thread Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (state.showBackButton) {
                    IconButton(
                        onClick = { state.eventSink(ThreadScreen.Event.CloseThread) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to conversation",
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thread",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "#${state.channelName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { state.eventSink(ThreadScreen.Event.CloseThread) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close thread pane",
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            val parentMessage = state.parentMessage
            if (parentMessage == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "Select a message thread to view replies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "parent_${parentMessage.id}") {
                        ParentMessageCard(parentMessage)
                    }

                    item(key = "replies_divider") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "${parentMessage.replies.size} ${if (parentMessage.replies.size == 1) "reply" else "replies"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }

                    items(parentMessage.replies, key = { it.id }) { reply ->
                        ThreadReplyItem(reply = reply)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Reply Composer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    OutlinedTextField(
                        value = state.draftReply,
                        onValueChange = {
                            state.eventSink(ThreadScreen.Event.UpdateDraftReply(it))
                        },
                        placeholder = {
                            Text(
                                text = "Reply to thread...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { state.eventSink(ThreadScreen.Event.SendReply) },
                        enabled = state.draftReply.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send thread reply",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentMessageCard(message: ChatMessage) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = message.authorInitials,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ThreadReplyItem(reply: ThreadReply) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = reply.authorInitials,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = reply.authorName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = reply.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
