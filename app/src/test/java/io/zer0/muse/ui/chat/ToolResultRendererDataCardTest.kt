package io.zer0.muse.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolResultRendererDataCardTest {

    @Test
    fun cardFenceIsDetectedAsDataCardResult() {
        val method = Class.forName("io.zer0.muse.ui.chat.ToolResultRendererKt")
            .getDeclaredMethod("detectResultKind", String::class.java)
            .apply { isAccessible = true }

        val kind = (method.invoke(
            null,
            """
            ```card
            {"type":"bar","title":"Messages","labels":["Mon"],"values":[1]}
            ```
            """.trimIndent(),
        ) as? Enum<*>)?.name

        assertEquals("DATA_CARD", kind)
    }
}
