package com.example.slackadaptive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.example.slackadaptive.data.SlackRepository
import com.example.slackadaptive.features.channellist.ChannelListPresenter
import com.example.slackadaptive.features.channellist.ChannelListUi
import com.example.slackadaptive.features.chat.ChatPresenter
import com.example.slackadaptive.features.chat.ChatUi
import com.example.slackadaptive.features.thread.ThreadPresenter
import com.example.slackadaptive.features.thread.ThreadUi
import com.example.slackadaptive.features.workspace.WorkspacePresenter
import com.example.slackadaptive.features.workspace.WorkspaceUi
import com.example.slackadaptive.screens.ChannelListScreen
import com.example.slackadaptive.screens.ChatScreen
import com.example.slackadaptive.screens.ThreadScreen
import com.example.slackadaptive.screens.WorkspaceScreen
import com.example.slackadaptive.ui.theme.SlackAdaptiveTheme
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.navstack.rememberSaveableNavStack
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.ui.ui

class MainActivity : ComponentActivity() {

    private val repository = SlackRepository()

    private val circuit: Circuit = Circuit.Builder()
        .addPresenterFactory { screen, navigator, _ ->
            when (screen) {
                is WorkspaceScreen -> WorkspacePresenter(repository)
                is ChannelListScreen -> ChannelListPresenter(screen, navigator, repository)
                is ChatScreen -> ChatPresenter(screen, navigator, repository)
                is ThreadScreen -> ThreadPresenter(screen, navigator, repository)
                else -> null
            }
        }
        .addUiFactory { screen, _ ->
            when (screen) {
                is WorkspaceScreen -> ui<WorkspaceScreen.State> { state, modifier ->
                    WorkspaceUi(state, modifier)
                }
                is ChannelListScreen -> ui<ChannelListScreen.State> { state, modifier ->
                    ChannelListUi(state, modifier)
                }
                is ChatScreen -> ui<ChatScreen.State> { state, modifier ->
                    ChatUi(state, modifier)
                }
                is ThreadScreen -> ui<ThreadScreen.State> { state, modifier ->
                    ThreadUi(state, modifier)
                }
                else -> null
            }
        }
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SlackAdaptiveTheme {
                CircuitCompositionLocals(circuit) {
                    // Uses SaveableNavStack (introduced in Circuit 0.33.0) for root-level navigation.
                    // Called inside CircuitCompositionLocals so LocalCircuitSaver is provided.
                    val navStack = rememberSaveableNavStack(root = WorkspaceScreen)
                    val navigator = rememberCircuitNavigator(navStack)

                    NavigableCircuitContent(
                        navigator = navigator,
                        navStack = navStack,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }
}
