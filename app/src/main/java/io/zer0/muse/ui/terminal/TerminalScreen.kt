package io.zer0.muse.ui.terminal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.zer0.muse.R
import io.zer0.muse.terminal.TerminalSessionManager
import io.zer0.muse.terminal.TerminalSessionStore
import io.zer0.muse.ui.common.icons.MuseIcons
import io.zer0.muse.ui.theme.MusePaddings
import java.util.ArrayDeque

/**
 * v2.x 终端一期:应用沙盒终端页。
 *
 * - 显示:xterm.js(assets/terminal)运行在 WebView 中,ANSI 颜色与 UTF-8 完整支持;
 * - 会话:[TerminalSessionStore] 进程级单例,离开页面会话不杀,回来继续;
 * - 输入:底部 Compose 输入行(IME 友好),本地回显后写入会话 stdin;
 * - 后续:PTY 引擎接入后,交互式程序与 Ctrl-C 信号在此页直接可用,UI 无需改动。
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { TerminalSessionStore.get(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var input by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webReady by remember { mutableStateOf(false) }
    // 未就绪时的输出先入队,ready 后一次性冲刷(主线程访问,无并发问题)
    val pendingJs = remember { ArrayDeque<String>() }

    fun pushToTerminal(bytes: ByteArray) {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val js = "window.MuseTerm && window.MuseTerm.writeB64('$b64');"
        mainHandler.post {
            val wv = webViewRef
            if (wv != null && webReady) {
                wv.evaluateJavascript(js, null)
            } else {
                pendingJs.addLast(js)
            }
        }
    }

    fun pushText(text: String) = pushToTerminal(text.toByteArray(Charsets.UTF_8))

    DisposableEffect(Unit) {
        session.setListener(object : TerminalSessionManager.Listener {
            override fun onOutput(bytes: ByteArray) = pushToTerminal(bytes)

            override fun onExit(code: Int) {
                pushText("\r\n[会话进程已退出 code=$code,点右上角重启]\r\n")
            }
        })
        session.ensureStarted()
        onDispose {
            session.setListener(null)
            // 会话保留(进程级单例),仅回收 WebView,避免泄漏;下次进入重载缓冲
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ── 顶栏 ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = MusePaddings.tightGap, vertical = MusePaddings.tightGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MuseIcons.arrowLeft,
                    contentDescription = stringResource(R.string.terminal_back_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = MuseIcons.terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MusePaddings.contentGap))
            Text(
                text = stringResource(R.string.terminal_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                webViewRef?.evaluateJavascript("window.MuseTerm && window.MuseTerm.clearTerm();", null)
            }) {
                Icon(
                    imageVector = MuseIcons.trash,
                    contentDescription = stringResource(R.string.terminal_clear_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                session.restart()
                pushText("\r\n[会话已重启]\r\n")
            }) {
                Icon(
                    imageVector = MuseIcons.refresh,
                    contentDescription = stringResource(R.string.terminal_restart_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 终端画面(xterm.js) ───────────────────────────────
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.domStorageEnabled = true
                    isVerticalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.parseColor("#101014"))
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onReady() {
                                mainHandler.post {
                                    webReady = true
                                    val wv = webViewRef
                                    if (wv != null) {
                                        while (pendingJs.isNotEmpty()) {
                                            wv.evaluateJavascript(pendingJs.removeFirst(), null)
                                        }
                                    }
                                }
                            }
                        },
                        "MuseBridge",
                    )
                    loadUrl("file:///android_asset/terminal/terminal.html")
                }.also { webViewRef = it }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // ── 输入行 ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = MusePaddings.screen)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "❯",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(MusePaddings.contentGap))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.terminal_input_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                },
            )
            IconButton(onClick = {
                val cmd = input.trimEnd()
                if (cmd.isNotBlank()) {
                    pushText("❯ $cmd\r\n")
                    session.write(cmd + "\n")
                }
                input = ""
            }) {
                Icon(
                    imageVector = MuseIcons.send,
                    contentDescription = stringResource(R.string.terminal_send_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
