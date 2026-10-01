package io.zer0.muse.automation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenInfoTest {
    @Test
    fun `best text node prefers exact clickable text over decorative duplicate`() {
        val decorative = UiNode(text = "Settings", boundsLeft = 0, boundsTop = 0, boundsRight = 100, boundsBottom = 40)
        val clickable =
            UiNode(text = "Settings", boundsLeft = 100, boundsTop = 100, boundsRight = 300, boundsBottom = 180, isClickable = true)

        val node = ScreenInfo(nodes = listOf(decorative, clickable)).findBestTextNode("Settings")

        assertEquals(clickable, node)
    }

    @Test
    fun `resource id matcher accepts full id and suffix`() {
        val node = UiNode(viewIdResourceName = "com.example:id/settings", isClickable = true)
        val screen = ScreenInfo(nodes = listOf(node))

        assertEquals(node, screen.findBestViewIdNode("com.example:id/settings"))
        assertEquals(node, screen.findBestViewIdNode("settings"))
    }

    @Test
    fun `exact mode does not match a containing label`() {
        val node = ScreenInfo(nodes = listOf(UiNode(text = "Network settings"))).findBestTextNode("Network", exact = true)

        assertNull(node)
    }
}
