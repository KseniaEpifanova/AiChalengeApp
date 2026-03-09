package com.example.aichalengeapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.vm.ChatViewModel

data class BubbleRenderConfig(
    val maxLines: Int,
    val overflow: TextOverflow
)

internal fun bubbleRenderConfig(expanded: Boolean, collapsedLines: Int): BubbleRenderConfig {
    return if (expanded) {
        BubbleRenderConfig(maxLines = Int.MAX_VALUE, overflow = TextOverflow.Visible)
    } else {
        BubbleRenderConfig(maxLines = collapsedLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MessageBubble(
    message: ChatViewModel.UiMessage,
    onToggleExpand: (Long) -> Unit,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 8
) {
    val cfg = LocalConfiguration.current
    val maxBubbleWidth = (cfg.screenWidthDp * 0.85f).dp

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isUser) 18.dp else 6.dp,
        bottomEnd = if (message.isUser) 6.dp else 18.dp
    )

    val containerColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val expanded = message.isExpanded
    val renderConfig = bubbleRenderConfig(expanded = expanded, collapsedLines = collapsedLines)

    var hasOverflow by remember(message.id, message.text) { mutableStateOf(false) }

    val canToggle = hasOverflow || expanded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = maxBubbleWidth)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    maxLines = renderConfig.maxLines,
                    overflow = renderConfig.overflow,
                    onTextLayout = { result ->
                        if (!expanded) {
                            hasOverflow = result.hasVisualOverflow
                        } else {
                            hasOverflow = true
                            Log.d("MessageBubble", "expanded=true textLength=${message.text.length} maxLines=${renderConfig.maxLines}")
                        }
                    }
                )

                val footer = message.tokenInfo
                if (!footer.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canToggle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { onToggleExpand(message.id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = if (expanded) "Свернуть" else "Показать ещё",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
