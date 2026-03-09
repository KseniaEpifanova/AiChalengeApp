package com.example.aichalengeapp.ui

import androidx.compose.ui.text.style.TextOverflow
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBubbleRenderConfigTest {

    @Test
    fun `collapsed config uses limited lines with ellipsis`() {
        val cfg = bubbleRenderConfig(expanded = false, collapsedLines = 8)
        assertEquals(8, cfg.maxLines)
        assertEquals(TextOverflow.Ellipsis, cfg.overflow)
    }

    @Test
    fun `expanded config uses full lines without clipping`() {
        val cfg = bubbleRenderConfig(expanded = true, collapsedLines = 8)
        assertEquals(Int.MAX_VALUE, cfg.maxLines)
        assertEquals(TextOverflow.Visible, cfg.overflow)
    }
}
