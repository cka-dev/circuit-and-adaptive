package com.example.slackadaptive.screens

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.example.slackadaptive.data.Channel
import com.example.slackadaptive.data.ChatMessage
import com.example.slackadaptive.data.DirectMessage
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.ParcelableScreen
import kotlinx.parcelize.Parcelize

/**
 * Parcelable content key used by `rememberListDetailPaneScaffoldNavigator<WorkspacePaneKey>()`.
 *
 * Carries `channelId` even when navigating to a thread (`threadMessageId != null`) so that
 * when transitioning from a Compact 1-pane screen to a Large 2- or 3-pane screen, both the
 * Detail pane (`channelId`) and Extra pane (`threadMessageId`) remain populated and selected.
 */
@Parcelize
data class WorkspacePaneKey(
    val channelId: String,
    val threadMessageId: String? = null,
) : Parcelable

/**
 * Ambient multi-pane layout context provided by `WorkspaceUi`.
 *
 * Keeping layout-visibility flags (`isListVisible`, `isDetailVisible`, `isExtraVisible`) and
 * highlight state (`selectedChannelId`, `activeThreadMessageId`) in a CompositionLocal rather
 * than inside the [ParcelableScreen] keys ensures that folding/unfolding the device does NOT
 * change the `Screen` key passed to `CircuitContent`—preserving presenter instances, scroll
 * positions, and message drafts across posture transitions.
 */
@Immutable
data class WorkspacePaneState(
    val selectedChannelId: String? = null,
    val activeThreadMessageId: String? = null,
    val isListVisible: Boolean = true,
    val isDetailVisible: Boolean = false,
    val isExtraVisible: Boolean = false,
)

val LocalWorkspacePaneState = compositionLocalOf { WorkspacePaneState() }

enum class TopLevelTab(val label: String) {
    HOME("Home"),
    DMS("DMs"),
    ACTIVITY("Activity"),
    YOU("You"),
}

/**
 * Root adaptive workspace screen hosting the outer `NavigationSuiteScaffold` and
 * the inner `NavigableListDetailPaneScaffold` (List + Detail + Extra panes).
 */
@Parcelize
data object WorkspaceScreen : ParcelableScreen {
    @Immutable
    data class State(
        val selectedTab: TopLevelTab,
        val showAdaptiveDebugBanner: Boolean,
        val totalUnreadCount: Int,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class SelectTab(val tab: TopLevelTab) : Event
        data object ToggleAdaptiveDebugBanner : Event
    }
}

/**
 * List Pane Screen: displays Slack channels and Direct Messages.
 */
@Parcelize
data class ChannelListScreen(
    val filterDmsOnly: Boolean = false,
) : ParcelableScreen {
    @Immutable
    data class State(
        val workspaceName: String,
        val searchQuery: String,
        val channels: List<Channel>,
        val directMessages: List<DirectMessage>,
        val selectedChannelId: String?,
        val filterDmsOnly: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class UpdateSearchQuery(val query: String) : Event
        data class SelectChannel(val channelId: String) : Event
    }
}

/**
 * Detail Pane Screen: displays messages in a single channel or DM conversation.
 */
@Parcelize
data class ChatScreen(
    val channelId: String,
) : ParcelableScreen {
    @Immutable
    data class State(
        val channel: Channel?,
        val messages: List<ChatMessage>,
        val draftMessage: String,
        val activeThreadMessageId: String?,
        val showBackButton: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class UpdateDraft(val text: String) : Event
        data object SendMessage : Event
        data class OpenThread(val messageId: String) : Event
        data class ToggleReaction(val messageId: String, val emoji: String) : Event
        data object NavigateBack : Event
    }
}

/**
 * Extra Pane Screen: displays the parent message and replies for a specific thread.
 */
@Parcelize
data class ThreadScreen(
    val channelId: String,
    val parentMessageId: String,
) : ParcelableScreen {
    @Immutable
    data class State(
        val channelName: String,
        val parentMessage: ChatMessage?,
        val draftReply: String,
        val showBackButton: Boolean,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class UpdateDraftReply(val text: String) : Event
        data object SendReply : Event
        data object CloseThread : Event
    }
}
