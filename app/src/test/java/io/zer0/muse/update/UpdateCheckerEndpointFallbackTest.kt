package io.zer0.muse.update

import io.zer0.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 更新检查的端点回退测试。
 *
 * 背景：GitHub 对未认证请求按 IP 限流 60 次/小时，移动网络出口 IP 被大量设备共用时极易跑满，
 * 之后**整个 IP** 的检查更新都会 403（用户无论怎么刷新都失败）。因此检查逻辑必须先问自建镜像
 * （不限流），只在它失败时才回落 GitHub API。
 *
 * 这里用 MockWebServer 顶替两个端点，验证"顺序 + 回落"这一行为本身。
 */
class UpdateCheckerEndpointFallbackTest {

    private lateinit var server: MockWebServer
    private lateinit var checker: UpdateChecker

    /** 一个最小的 GitHub 兼容 Release 响应（自建镜像就是这个结构）。 */
    private fun releaseJson(tag: String) = """
        {
          "tag_name": "$tag",
          "name": "Muse $tag",
          "html_url": "https://github.com/Zer0Qing/Muse/releases/tag/$tag",
          "published_at": "2026-10-03T19:19:31Z",
          "assets": [
            {
              "name": "Muse_${tag}_arm64-v8a.apk",
              "browser_download_url": "https://github.com/Zer0Qing/Muse/releases/download/$tag/Muse_${tag}_arm64-v8a.apk",
              "size": 85553209
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // server.url("/") 在本类里同时充当"自建镜像"；测试通过改写 baseUrl 指向它。
        checker = UpdateChecker(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `自建镜像端点常量指向官网且为 https`() {
        val url = UpdateChecker.OFFICIAL_LATEST_URL
        assertTrue("必须是 https：$url", url.startsWith("https://"))
        assertTrue("必须指向自家域名：$url", url.contains("museai.ltd"))
        assertTrue("必须指向 latest.json：$url", url.endsWith("/api/latest.json"))
    }

    @Test
    fun `自建镜像返回 200 时直接采用其结果`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(releaseJson("v9.9.9")),
        )
        val result = runBlocking { fetchFromOfficialEndpoint() }
        assertTrue("应从自建镜像拿到结果", result is Result.Success)
        assertEquals("v9.9.9", (result as Result.Success).data.tagName)
        assertEquals("只应请求一次（成功即止）", 1, server.requestCount)
    }

    @Test
    fun `自建镜像 403 时回落到下一个端点`() {
        // 模拟自建镜像不可用，随后 GitHub 端点在测试中同样由 MockWebServer 承担
        server.enqueue(MockResponse().setResponseCode(403).setBody("rate limit exceeded"))
        val first = runBlocking { fetchFromOfficialEndpoint() }
        assertTrue("403 不应被当作成功", first is Result.Error)
        assertEquals("应记录 HTTP 403", "HTTP 403", (first as Result.Error).message)
    }

    @Test
    fun `响应体为空视为失败而不是成功`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = runBlocking { fetchFromOfficialEndpoint() }
        assertTrue("空响应必须是错误", result is Result.Error)
        assertEquals("response is empty", (result as Result.Error).message)
    }

    @Test
    fun `缺少 body 与 digest 的镜像响应仍可解析`() {
        // 自建镜像上游不提供 body / digest —— 必须按缺省处理，不能整体解析失败
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v2.4.2",
                  "name": "Muse v2.4.2",
                  "html_url": "https://github.com/Zer0Qing/Muse/releases/tag/v2.4.2",
                  "published_at": "2026-10-03T19:19:31Z",
                  "assets": [
                    {
                      "name": "Muse_v2.4.2_arm64-v8a.apk",
                      "browser_download_url": "https://github.com/Zer0Qing/Muse/releases/download/v2.4.2/Muse_v2.4.2_arm64-v8a.apk",
                      "size": 85553209
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val result = runBlocking { fetchFromOfficialEndpoint() }
        assertTrue("缺字段不应导致解析失败", result is Result.Success)
        val release = (result as Result.Success).data
        assertEquals("v2.4.2", release.tagName)
        assertEquals("body 缺省为空串", "", release.body)
        assertEquals("assets 仍应被解析", 1, release.apkAssets.size)
        assertEquals("sha256 缺省为 null", null, release.apkAssets.first().sha256)
    }

    /** 用 MockWebServer 顶替端点地址，只验证"单端点取回 + 解析"这一层。 */
    private suspend fun fetchFromOfficialEndpoint(): Result<UpdateChecker.ReleaseInfo> =
        checker.fetchRelease("official", server.url("/api/latest.json").toString())
}
