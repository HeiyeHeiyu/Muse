package io.zer0.muse.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubbleActionMenuWiringTest {

    @Test
    fun `reaction actions and badges are not exposed from message bubble`() {
        val source = locateMessageBubbleSource()

        assertFalse(source.contains("MuseReactionSheet("))
        assertFalse(source.contains("showReactionSheet"))
        assertFalse(source.contains("chat_reaction_title"))
        assertFalse(source.contains("reactionIcon("))
        assertFalse(source.contains("reactionLabelRes("))
    }

    @Test
    fun `mobile extended menu keeps delete as its final destructive action`() {
        val source = locateMessageBubbleSource()
        val menuStart = source.indexOf("if (showLanguageSubmenu)")
        require(menuStart >= 0) { "mobile extended menu not found" }
        val menuEnd = source.indexOf("// P5-F: 翻译中指示", menuStart)
        require(menuEnd > menuStart) { "mobile extended menu boundary not found" }
        val menu = source.substring(menuStart, menuEnd)

        assertEquals(1, Regex("icon = MuseIcons\\.trash").findAll(menu).count())
        val editIndex = menu.lastIndexOf("icon = MuseIcons.edit")
        val deleteIndex = menu.lastIndexOf("icon = MuseIcons.trash")
        assertTrue("delete action must follow edit action", deleteIndex > editIndex)
    }

    private fun locateMessageBubbleSource(): String {
        val candidates =
            listOf(
                Path.of("src/main/java/io/zer0/muse/ui/MessageBubble.kt"),
                Path.of("app/src/main/java/io/zer0/muse/ui/MessageBubble.kt"),
            )
        val path = candidates.firstOrNull(Files::exists)
            ?: error("Unable to locate MessageBubble.kt from ${System.getProperty("user.dir")}")
        return Files.newBufferedReader(path).use { it.readText() }
    }
}
