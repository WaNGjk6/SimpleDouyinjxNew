package top.jk666.douyinjiexi.model

data class ParseResult(
    val type: ContentType,
    val title: String,
    val desc: String? = null,
    val cover: String?,
    val author: AuthorInfo,
    val videoUrl: String?,
    val videoUrls: List<String> = emptyList(),
    val images: List<String>,
    val music: MusicInfo?,
    val statistics: Statistics?,
    val platform: Platform = Platform.DOUYIN,
    val imageCount: Int? = null,
    val livePhotos: List<LivePhoto> = emptyList()
)

enum class Platform {
    DOUYIN, DOUBAO, KUAISHOU, XHS;

    val displayName: String
        get() = when (this) {
            DOUYIN -> "抖音"
            DOUBAO -> "豆包"
            KUAISHOU -> "快手"
            XHS -> "小红书"
        }

    val emoji: String
        get() = when (this) {
            DOUYIN -> "🎵"
            DOUBAO -> "🤖"
            KUAISHOU -> "⚡"
            XHS -> "📕"
        }
}

enum class ContentType {
    VIDEO, ALBUM, NOTE, SLIDES, LIVE;

    companion object {
        fun fromString(value: String?): ContentType {
            return when (value?.lowercase()) {
                "video" -> VIDEO
                "album" -> ALBUM
                "note" -> NOTE
                "slides" -> SLIDES
                "live" -> LIVE
                else -> VIDEO
            }
        }
    }
}

data class AuthorInfo(
    val nickname: String,
    val avatar: String?,
    val uniqueId: String?,
    val followerCount: Long? = null,
    val totalFavorited: Long? = null
)

data class MusicInfo(
    val title: String?,
    val author: String?,
    val cover: String?,
    val url: String?
)

data class LivePhoto(
    val imageUrl: String,
    val videoUrl: String? = null
)

data class Statistics(
    val playCount: Long = 0,
    val diggCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val collectCount: Long = 0
)

data class MusicResult(
    val name: String,
    val artist: String,
    val cover: String?,
    val url: String?,
    val lyrics: String?,
    val platform: MusicPlatform,
    val songId: String? = null,
    val album: String? = null,
    val quality: String? = null,
    val fileSize: String? = null
)

enum class MusicPlatform(val displayName: String, val mediaValue: String) {
    QQ("QQ音乐", "tencent"),
    NETEASE("网易云", "netease")
}

data class MusicSearchItem(
    val id: String,
    val name: String,
    val artists: String,
    val album: String?,
    val picUrl: String?,
    val duration: Long = 0
)
