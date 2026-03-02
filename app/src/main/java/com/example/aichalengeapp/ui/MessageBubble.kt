package com.example.aichalengeapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.vm.ChatViewModel

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

    // ВАЖНО: определяем, был ли текст "обрезан" в свернутом виде
    var hasOverflowWhenCollapsed by remember(message.id) { mutableStateOf(false) }

    // Кнопка должна показываться:
    // - если текст был обрезан (в свернутом виде) — показываем "Показать ещё"
    // - если раскрыт — показываем "Свернуть" (даже если overflow сейчас false)
    val canToggle = hasOverflowWhenCollapsed || expanded

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
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expanded) {
                            hasOverflowWhenCollapsed = result.hasVisualOverflow
                        }
                    }
                )

                if (canToggle) {
                    TextButton(
                        onClick = { onToggleExpand(message.id) },
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
