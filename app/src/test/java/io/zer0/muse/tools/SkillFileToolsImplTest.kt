package io.zer0.muse.tools

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SkillFileToolsImplTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun writeReadRoundTripInSandbox() {
        val impl = SkillFileToolsImpl(context, OkHttpClient())
        val write = impl.execWriteFile(
            mapOf("path" to "notes/test.txt", "content" to "hello file tools"),
        )
        assertFalse(write.contains("error", ignoreCase = true))

        val read = impl.execReadFile(mapOf("path" to "notes/test.txt"))
        assertTrue(read.contains("hello file tools"))
    }

    @Test
    fun resolveSandboxFileRejectsTraversal() {
        val impl = SkillFileToolsImpl(context, OkHttpClient())
        val result = impl.execReadFile(mapOf("path" to "../../outside.txt"))
        assertTrue(result.contains("路径", ignoreCase = true) || result.contains("violation", ignoreCase = true))
    }

    @Test
    fun resolveSandboxFileRejectsSiblingPrefixEscape() {
        val impl = SkillFileToolsImpl(context, OkHttpClient())
        val filesDir = context.filesDir
        val sibling = java.io.File(filesDir.parentFile, "${filesDir.name}-evil").apply { mkdirs() }
        val secret = java.io.File(sibling, "secret.txt").apply { writeText("outside") }
        try {
            val result = impl.execReadFile(
                mapOf("path" to "../${filesDir.name}-evil/secret.txt"),
            )
            assertTrue(
                "兄弟目录前缀不能被当成沙盒子路径: $result",
                result.contains("路径", ignoreCase = true) || result.contains("violation", ignoreCase = true),
            )
        } finally {
            secret.delete()
            sibling.delete()
        }
    }

    @Test
    fun listPublicFilesUsesProviderLimitArguments() = runBlocking {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(
                any<Uri>(),
                any(),
                isNull(),
                isNull(),
                match { it.contains("LIMIT") },
            )
        } throws IllegalArgumentException("Invalid token LIMIT")
        every {
            resolver.query(
                any<Uri>(),
                any(),
                any<Bundle>(),
                any<CancellationSignal>(),
            )
        } answers {
            val args = thirdArg<Bundle>()
            assertTrue(args.containsKey(ContentResolver.QUERY_ARG_LIMIT))
            assertTrue(args.getInt(ContentResolver.QUERY_ARG_LIMIT) <= 200)
            MatrixCursor(
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
            ).apply {
                addRow(arrayOf<Any>(7L, "report.txt", 12L, 100L))
            }
        }
        val fakeContext = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val result = SkillFileToolsImpl(fakeContext, OkHttpClient()).execListPublicFiles(
            mapOf("directory" to "Downloads", "limit" to "5"),
        )

        assertTrue(result.contains("report.txt"))
        assertTrue(result.contains("content://"))
    }
}
