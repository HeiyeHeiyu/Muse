package io.zer0.muse.accessibility

/** 无障碍路径/文本策略纯函数,供 accessibility 与 app 模块共用并便于单测。 */
object AccessibilityPathUtils {

    /**
     * 解析节点路径字符串。
     *
     * 合法路径形如 "0.1.2",根必须为 0;非法输入返回空列表。
     */
    fun parseNodePath(path: String): List<Int> {
        if (path.isBlank()) return emptyList()
        val parts = path.split('.')
        val indices = parts.map { it.toIntOrNull() ?: return emptyList() }
        if (indices.first() != 0) return emptyList()
        return indices
    }

    /** 密码字段保留控件元数据用于定位/输入,但绝不把值或描述交给自动化模型。 */
    fun textForModel(value: CharSequence?, isPassword: Boolean): String? =
        if (isPassword) null else value?.toString()?.takeIf { it.isNotBlank() }

    /** 转义文本中的换行/制表符/反斜杠/方括号,保证单行输出。 */
    fun escapeText(text: String): String = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("]", "\\]")
}
