package top.jk666.douyinjiexi.util

object LinkParser {

    private val URL_PATTERN = Regex(
        """https?://[^\s<>"'`\)]+""",
        RegexOption.IGNORE_CASE
    )

    fun extractUrl(text: String): String? {
        val match = URL_PATTERN.find(text.trim())
        return match?.value
    }

    fun isDouyinLink(url: String): Boolean {
        val douyinDomains = listOf(
            "v.douyin.com",
            "www.douyin.com",
            "www.iesdouyin.com",
            "v.kuaishou.com",
            "www.kuaishou.com"
        )
        return douyinDomains.any { url.contains(it, ignoreCase = true) }
    }
}
