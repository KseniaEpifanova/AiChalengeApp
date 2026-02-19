package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aichalengeapp.data.MessageUi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
@Composable
fun MessageBubble(message: MessageUi) {
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (message.isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .wrapContentWidth()
                    .combinedClickable(
                        onClick = { /* ничего */ },
                        onLongClick = { menuExpanded = true }
                    )
            ) {
                SelectionContainer {

                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = if (message.isUser)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.text))
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
