package top.jk666.douyinjiexi.util

import top.jk666.douyinjiexi.R
import top.jk666.douyinjiexi.model.MusicPlatform
import top.jk666.douyinjiexi.model.Platform

enum class PlatformType {
    DOUYIN, KUAISHOU, XHS, DOUBAO, QQ_MUSIC, NETEASE_MUSIC, UNKNOWN;

    val label: String
        get() = when (this) {
            DOUYIN -> "抖音"
            KUAISHOU -> "快手"
            XHS -> "小红书"
            DOUBAO -> "豆包"
            QQ_MUSIC -> "QQ音乐"
            NETEASE_MUSIC -> "网易云音乐"
            UNKNOWN -> "未知平台"
        }

    val iconRes: Int
        get() = when (this) {
            DOUYIN -> R.drawable.ic_platform_douyin
            KUAISHOU -> R.drawable.ic_platform_kuaishou
            XHS -> R.drawable.ic_platform_xiaohongshu
            DOUBAO -> R.drawable.ic_platform_doubao
            QQ_MUSIC -> R.drawable.ic_music_qq
            NETEASE_MUSIC -> R.drawable.ic_music_netease
            UNKNOWN -> 0
        }

    fun toPlatform(): Platform? = when (this) {
        DOUYIN -> Platform.DOUYIN
        KUAISHOU -> Platform.KUAISHOU
        XHS -> Platform.XHS
        DOUBAO -> Platform.DOUBAO
        else -> null
    }

    fun toMusicPlatform(): MusicPlatform? = when (this) {
        QQ_MUSIC -> MusicPlatform.QQ
        NETEASE_MUSIC -> MusicPlatform.NETEASE
        else -> null
    }

    val isMediaPlatform: Boolean
        get() = this == DOUYIN || this == KUAISHOU || this == XHS || this == DOUBAO

    val isMusicPlatform: Boolean
        get() = this == QQ_MUSIC || this == NETEASE_MUSIC
}

object PlatformDetector {

    private val douyinDomains = listOf(
        "v.douyin.com", "www.douyin.com", "iesdouyin.com",
        "douyin.com", "vm.tiktok.com"
    )

    private val kuaishouDomains = listOf(
        "v.kuaishou.com", "www.kuaishou.com", "kuaishou.com",
        "v.m.chenzhongtech.com", "gifshow.com"
    )

    private val xhsDomains = listOf(
        "xhslink.com", "xiaohongshu.com", "www.xiaohongshu.com",
        "xhs.link"
    )

    private val doubaoDomains = listOf(
        "doubao.com", "www.doubao.com"
    )

    private val neteaseDomains = listOf(
        "music.163.com", "y.music.163.com", "163.com"
    )

    private val qqMusicDomains = listOf(
        "y.qq.com", "c.y.qq.com", "qq.com/music"
    )

    fun detect(input: String): PlatformType {
        val text = input.trim().lowercase()

        val urlRegex = Regex("""https?://[^\s<>"'）】）》\]]+""")
        val url = urlRegex.find(text)?.value ?: text

        return when {
            douyinDomains.any { url.contains(it) } -> PlatformType.DOUYIN
            kuaishouDomains.any { url.contains(it) } -> PlatformType.KUAISHOU
            xhsDomains.any { url.contains(it) } -> PlatformType.XHS
            doubaoDomains.any { url.contains(it) } -> PlatformType.DOUBAO
            neteaseDomains.any { url.contains(it) } -> PlatformType.NETEASE_MUSIC
            qqMusicDomains.any { url.contains(it) } -> PlatformType.QQ_MUSIC
            else -> PlatformType.UNKNOWN
        }
    }

    fun extractUrl(text: String): String {
        val regex = Regex("""https?://[^\s<>"'）】）》\]]+""")
        return regex.find(text)?.value ?: text.trim()
    }
}
