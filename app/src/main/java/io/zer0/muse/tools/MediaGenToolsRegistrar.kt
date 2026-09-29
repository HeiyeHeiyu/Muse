package io.zer0.muse.tools

/**
 * P2-23: 媒体生成工具注册器(图片 / 视频 / 二维码)。
 *
 * 从 ChatViewModel.registerMediaTools() 原样迁出 —— ToolDef(name / description /
 * parameters / required / riskLevel / parameterTypes)一字未改,风险等级保持
 * generate_image / generate_video = HIGH、generate_qr_code = SAFE。
 *
 * 改为在 App 启动时由 [ToolRegistrarBootstrapper] 强制实例化注册,因此
 * 定时任务 / 群聊 / 子代理等无 UI 链路也能在 [ToolRegistry] 中查到并执行这些工具,
 * 不再依赖"用户打开过聊天页"。实现见 [MediaGenToolsImpl]。
 *
 * [impl] 由 Koin 注入(与 ChatViewModel 安装投递宿主的是同一实例)。
 */
class MediaGenToolsRegistrar(
    private val toolRegistry: ToolRegistry,
    private val impl: MediaGenToolsImpl,
) {

    init {
        registerAll()
    }

    fun registerAll() {
        toolRegistry.register(
            ToolRegistry.ToolDef(
                name = "generate_image",
                description = "根据用户描述生成图片。仅在用户明确要求画图、设计、头像、海报等场景调用。会消耗绘图 API 额度。",
                parameters = mapOf(
                    "prompt" to "必填。写清主体/风格/构图,英文效果更佳,如 'a cute cat on a sofa, watercolor style'",
                    "model" to "可选,绘图模型 ID(如 dall-e-3 / gpt-image-1);默认用供应商默认模型",
                    "size" to "可选,尺寸,如 1024x1024 / 1792x1024 / 1024x1792;Agnes 支持比例 1:1 / 16:9 / 3:2",
                    "quality" to "可选,如 standard / hd",
                    "style" to "可选,如 vivid / natural",
                    "n" to "可选,生成数量,默认 1",
                    "reference_image" to "可选,参考图 URL 或 base64(图生图/编辑)。本地参考图由用户在审批卡片中选择后注入,LLM 无需填写",
                ),
                required = setOf("prompt"),
                riskLevel = ToolRiskLevel.HIGH,
                parameterTypes = mapOf("n" to "integer"),
            ),
        ) { args -> impl.execGenerateImage(args) }

        toolRegistry.register(
            ToolRegistry.ToolDef(
                name = "generate_video",
                // v1.0.75 fix (工具审查 02): 补 prompt 示例与返回说明
                description = "根据用户描述生成短视频。仅在用户明确要求视频/动画时调用。自动选择支持视频输出的供应商/模型,返回视频 URL。",
                parameters = mapOf(
                    "prompt" to "必填,视频内容描述,英文或中文均可",
                    "model" to "可选,视频模型 ID;未指定时自动选择第一个支持视频输出的模型",
                    "provider_id" to "可选,供应商 ID;未指定时自动选择第一个支持视频输出的供应商",
                    "duration" to "可选,视频时长(秒),仅支持 5 或 10,默认 5",
                    "resolution" to "可选,分辨率,如 720p / 1080p,默认 720p",
                ),
                required = setOf("prompt"),
                riskLevel = ToolRiskLevel.HIGH,
                parameterTypes = mapOf("duration" to "integer"),
            ),
        ) { args -> impl.execGenerateVideo(args) }

        toolRegistry.register(
            ToolRegistry.ToolDef(
                name = "generate_qr_code",
                description = "把任意文本(如链接、WiFi 密码、联系方式)转换为二维码图片,并在对话中展示。",
                parameters = mapOf(
                    "content" to "必填,要编码成二维码的文本",
                    "size" to "可选,二维码边长像素,默认 400,范围 128-1024",
                ),
                required = setOf("content"),
                riskLevel = ToolRiskLevel.SAFE,
                parameterTypes = mapOf("size" to "integer"),
            ),
        ) { args -> impl.execGenerateQrCode(args) }
    }
}
