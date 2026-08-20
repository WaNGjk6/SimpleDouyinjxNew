package top.jk666.douyinjiexi.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jk666.douyinjiexi.model.MusicPlatform
import top.jk666.douyinjiexi.model.MusicResult
import top.jk666.douyinjiexi.model.MusicSearchItem
import top.jk666.douyinjiexi.util.AppLogger
import java.util.concurrent.TimeUnit

object MusicApi {

    private const val TAG = "MusicApi"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    private fun JsonObject.safeGet(key: String): String? {
        if (!has(key) || get(key).isJsonNull) return null
        return try { get(key).asString } catch (_: Exception) { null }
    }

    private fun JsonObject.safeGetObject(key: String): JsonObject? {
        if (!has(key) || get(key).isJsonNull) return null
        return try { getAsJsonObject(key) } catch (_: Exception) { null }
    }

    private fun fetchJson(requestUrl: String, apiName: String = "未知接口"): JsonObject {
        AppLogger.d(TAG, "正在发送请求: [$apiName] - URL: $requestUrl")
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        val response = client.newCall(request).execute()
        AppLogger.d(TAG, "收到响应 [$apiName] - HTTP状态码: ${response.code}")

        if (!response.isSuccessful) {
            AppLogger.e(TAG, "HTTP请求失败 [$apiName] - 状态码: ${response.code}")
            throw Exception("HTTP ${response.code}")
        }

        val body = response.body?.string()
        if (body.isNullOrEmpty()) {
            AppLogger.e(TAG, "响应体为空 [$apiName]")
            throw Exception("响应为空")
        }
        AppLogger.d(TAG, "响应体长度 [$apiName]: ${body.length} 字符")

        val json = gson.fromJson(body, JsonObject::class.java)

        if (json.has("code")) {
            val codeElement = json.get("code")
            val code = when {
                codeElement.isJsonPrimitive && codeElement.asJsonPrimitive.isNumber -> codeElement.asInt
                codeElement.isJsonPrimitive && codeElement.asJsonPrimitive.isString -> codeElement.asString.toIntOrNull() ?: -1
                else -> -1
            }
            AppLogger.d(TAG, "业务状态码 [$apiName]: $code")
            if (code != 200) {
                val msg = json.safeGet("msg") ?: json.safeGet("message") ?: "请求失败(code=$code)"
                AppLogger.e(TAG, "业务错误 [$apiName]: $msg")
                throw Exception(msg)
            }
        }
        return json
    }

    suspend fun parseQQMusic(songId: String): MusicResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "开始QQ音乐解析: songId=$songId")
        val apiUrl = "https://api.bugpk.com/api/music?id=${java.net.URLEncoder.encode(songId, "UTF-8")}&media=tencent&type=song"
        val json = fetchJson(apiUrl, "QQ音乐接口")

        val result = MusicResult(
            name = json.safeGet("name") ?: "未知歌曲",
            artist = json.safeGet("author") ?: "未知歌手",
            cover = json.safeGet("cover"),
            url = json.safeGet("url"),
            lyrics = json.safeGet("lrc_data") ?: json.safeGet("lyric"),
            platform = MusicPlatform.QQ,
            songId = json.safeGet("song_id") ?: songId
        )
        AppLogger.d(TAG, "QQ音乐解析结果: name=${result.name}, artist=${result.artist}, url=${result.url != null}, lyrics=${result.lyrics != null}")
        result
    }

    suspend fun searchNetease(keyword: String): List<MusicSearchItem> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "开始网易云音乐搜索: keyword=$keyword")
        val apiUrl = "https://api.bugpk.com/api/163_music?type=search&keywords=${java.net.URLEncoder.encode(keyword, "UTF-8")}&limit=20"
        val json = fetchJson(apiUrl, "网易云搜索接口")
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")

        val items = mutableListOf<MusicSearchItem>()
        if (data.has("songs") && data.get("songs").isJsonArray) {
            data.getAsJsonArray("songs").forEach { element ->
                try {
                    val song = element.asJsonObject
                    items.add(
                        MusicSearchItem(
                            id = song.safeGet("id") ?: song.get("id")?.asInt?.toString() ?: "",
                            name = song.safeGet("name") ?: "",
                            artists = song.safeGet("artists") ?: "",
                            album = song.safeGet("album"),
                            picUrl = song.safeGet("picUrl"),
                            duration = song.get("duration")?.asLong ?: 0
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "网易云搜索结果: 找到 ${items.size} 首歌曲")
        items
    }

    suspend fun getNeteaseDetail(songId: String, quality: String = "exhigh"): MusicResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "开始网易云音乐详情: songId=$songId, quality=$quality")
        val apiUrl = "https://api.bugpk.com/api/163_music?type=json&ids=${java.net.URLEncoder.encode(songId, "UTF-8")}&level=${java.net.URLEncoder.encode(quality, "UTF-8")}"
        val json = fetchJson(apiUrl, "网易云详情接口")
        // 实测：bugpk 的 type=json 详情是「平铺在顶层、无 data 包装」，状态字段为 status 而非 code
        AppLogger.d(TAG, "网易云详情顶层键: ${json.keySet().joinToString(", ")}")

        val result = MusicResult(
            name = json.safeGet("name") ?: "未知歌曲",
            artist = json.safeGet("ar_name") ?: json.safeGet("artist") ?: "未知歌手",
            cover = json.safeGet("pic") ?: json.safeGet("cover"),
            url = json.safeGet("url"),
            lyrics = json.safeGet("lyric") ?: json.safeGet("lrc_data"),
            platform = MusicPlatform.NETEASE,
            songId = songId,
            album = json.safeGet("al_name") ?: json.safeGet("album"),
            quality = json.safeGet("level") ?: quality,
            fileSize = json.safeGet("size")
        )
        AppLogger.d(TAG, "网易云详情结果: name=${result.name}, artist=${result.artist}, url=${result.url != null}, quality=${result.quality}")
        result
    }

    suspend fun parseNeteaseUrl(urlOrId: String): MusicResult = withContext(Dispatchers.IO) {
        val songId = extractNeteaseId(urlOrId) ?: urlOrId
        AppLogger.d(TAG, "解析网易云链接: input=$urlOrId, 提取songId=$songId")
        getNeteaseDetail(songId)
    }

    fun extractQQMusicId(input: String): String? {
        AppLogger.d(TAG, "提取QQ音乐ID: input=$input")
        val patterns = listOf(
            Regex("""songid=(\d+)"""),
            Regex("""/song/(\d+)"""),
            Regex("""songDetail/(\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                AppLogger.d(TAG, "QQ音乐ID提取成功: ${match.groupValues[1]}")
                return match.groupValues[1]
            }
        }
        val result = if (input.matches(Regex("""\d+"""))) input else null
        AppLogger.d(TAG, "QQ音乐ID提取结果: $result")
        return result
    }

    fun extractNeteaseId(input: String): String? {
        AppLogger.d(TAG, "提取网易云ID: input=$input")
        val patterns = listOf(
            Regex("""id=(\d+)"""),
            Regex("""/song/(\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                AppLogger.d(TAG, "网易云ID提取成功: ${match.groupValues[1]}")
                return match.groupValues[1]
            }
        }
        val result = if (input.matches(Regex("""\d+"""))) input else null
        AppLogger.d(TAG, "网易云ID提取结果: $result")
        return result
    }
}
