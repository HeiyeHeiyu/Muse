package io.zer0.muse.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamFinalizationOrderTest {

    @Test
    fun `done event records terminal reason without mutating content before pending flush joins`() {
        val source =
            listOf(
                Path.of("src/main/java/io/zer0/muse/ui/ChatViewModel.kt"),
                Path.of("app/src/main/java/io/zer0/muse/ui/ChatViewModel.kt"),
            )
                .firstOrNull(Files::exists)
                ?.let { Files.newBufferedReader(it).use { reader -> reader.readText() } }
                ?: error("ChatViewModel.kt not found")

        val doneBody = source.substringAfter("is ChatStreamEvent.Done ->")
            .substringBefore("is ChatStreamEvent.Error ->")
        assertTrue(doneBody.contains("doneFinishReason = event.finishReason"))
        assertFalse(doneBody.contains("params.builder.append"))
        assertFalse(doneBody.contains("params.reasoningBuilder.setLength"))

        val finalizationBody = source.substringAfter("// v1.0.4: 流结束 flush pendingBuilder")
            .substringBefore("if (streamError != null)")
        val flushIndex = finalizationBody.indexOf("params.builder.append(pendingBuilder)")
        val reasoningFallbackIndex = finalizationBody.indexOf("params.builder.isEmpty()")
        val truncationIndex = finalizationBody.indexOf("ChatStopReason.isLengthLimited")
        assertTrue(flushIndex >= 0)
        assertTrue(reasoningFallbackIndex > flushIndex)
        assertTrue(truncationIndex > reasoningFallbackIndex)
    }
}
