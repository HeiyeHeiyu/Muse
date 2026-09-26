package io.zer0.muse.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.zer0.muse.data.assistant.AssistantCardExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.x: ShareIntentHandler 角色包接收分支测试。
 *
 * 覆盖: VIEW + application/x-muse-assistant → ImportAssistantCard;
 * 其他 mime 不误触发; 既有 ACTION_SEND 文本路径回归不受影响。
 */
class ShareIntentHandlerTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `view intent with pack mime returns ImportAssistantCard`() = runBlocking {
        val intent = mockk<Intent>(relaxed = true)
        val uri = mockk<Uri>(relaxed = true)
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns uri
        every { intent.type } returns AssistantCardExporter.MIME_TYPE

        val result = ShareIntentHandler(context).handle(intent)

        assertTrue("应返回 ImportAssistantCard,实际: $result", result is ShareIntentHandler.ShareResult.ImportAssistantCard)
        assertEquals(
            uri,
            (result as ShareIntentHandler.ShareResult.ImportAssistantCard).uri,
        )
    }

    @Test
    fun `view intent with unrelated mime is not treated as card import`() = runBlocking {
        val intent = mockk<Intent>(relaxed = true)
        val uri = mockk<Uri>(relaxed = true)
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns uri
        every { intent.type } returns "application/pdf"

        val result = ShareIntentHandler(context).handle(intent)

        assertTrue("非角色包 mime 不应触发导入,实际: $result", result !is ShareIntentHandler.ShareResult.ImportAssistantCard)
    }

    @Test
    fun `send text path still prefills (regression)`() = runBlocking {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.data } returns null
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns "分享的文本"

        val result = ShareIntentHandler(context).handle(intent)

        assertTrue("应走预填路径,实际: $result", result is ShareIntentHandler.ShareResult.PrefillText)
        assertEquals("分享的文本", (result as ShareIntentHandler.ShareResult.PrefillText).text)
    }
}
