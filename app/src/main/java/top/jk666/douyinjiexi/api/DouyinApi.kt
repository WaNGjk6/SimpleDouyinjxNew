package top.jk666.douyinjiexi.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jk666.douyinjiexi.BuildConfig
import top.jk666.douyinjiexi.model.*
import top.jk666.douyinjiexi.util.AppLogger
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object DouyinApi {

    private const val TAG = "DouyinApi"
    // 小红书解析接口 Key：从 local.properties 注入（见 build.gradle.kts），不硬编码入库
    private val XHS_API_KEY: String get() = BuildConfig.XHS_API_KEY

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

    private fun JsonObject.safeGetArray(key: String): JsonArray? {
        if (!has(key) || get(key).isJsonNull) return null
        return try { getAsJsonArray(key) } catch (_: Exception) { null }
    }

    private fun JsonElement?.safeString(): String? {
        if (this == null || this.isJsonNull) return null
        return try { this.asString } catch (_: Exception) { null }
    }

    private fun JsonObject.resolvePath(path: String): JsonElement? {
        val parts = path.split(".")
        var current: JsonElement = this
        for (part in parts) {
            when {
                current.isJsonNull -> return null
                current.isJsonObject -> {
                    val obj = current.asJsonObject
                    if (!obj.has(part) || obj.get(part).isJsonNull) return null
                    current = obj.get(part)
                }
                current.isJsonArray -> {
                    val index = part.toIntOrNull() ?: return null
                    val arr = current.asJsonArray
                    if (index < 0 || index >= arr.size()) return null
                    current = arr.get(index)
                }
                else -> return null
            }
        }
        return if (current.isJsonNull) null else current
    }

    private fun JsonObject.flexString(vararg paths: String): String? {
        for (path in paths) {
            val value = resolvePath(path)?.safeString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun JsonObject.flexLong(vararg paths: String): Long? {
        for (path in paths) {
            val element = resolvePath(path)
            if (element != null && !element.isJsonNull) {
                try {
                    return element.asLong
                } catch (_: Exception) {
                    val str = element.safeString()
                    val longVal = str?.toLongOrNull()
                    if (longVal != null) return longVal
                }
            }
        }
        return null
    }

    private fun JsonObject.flexStringArray(path: String): List<String> {
        val arr = resolvePath(path)
        if (arr == null || !arr.isJsonArray) return emptyList()
        return arr.asJsonArray.mapNotNull { it.safeString() }
    }

    private fun JsonObject.flexObject(vararg paths: String): JsonObject? {
        for (path in paths) {
            val element = resolvePath(path)
            if (element != null && element.isJsonObject) return element.asJsonObject
        }
        return null
    }

    private suspend fun expandShortUrl(originalUrl: String): String {
        val shortLinkPatterns = listOf(
            "xhslink.com", "xiaohongshu.com/discovery",
            "v.kuaishou.com", "v.kuaishou.com/",
            "v.douyin.com", "vm.tiktok.com",
            "c.tb.cn", "m.tb.cn",
            "suo.im", "t.cn", "dwz.cn", "sinaurl.cn"
        )
        val isShortLink = shortLinkPatterns.any { originalUrl.contains(it, ignoreCase = true) }
        if (!isShortLink) {
            AppLogger.d(TAG, "非短链接，跳过展开: ${originalUrl.take(60)}")
            return originalUrl
        }
        AppLogger.d(TAG, "===== 开始短链接展开 =====")
        AppLogger.d(TAG, "原始URL: $originalUrl")
        val httpsUrl = if (originalUrl.startsWith("http://", ignoreCase = true)) {
            originalUrl.replaceFirst("http://", "https://")
        } else {
            originalUrl
        }
        if (httpsUrl != originalUrl) {
            AppLogger.d(TAG, "协议升级: $originalUrl -> $httpsUrl")
        }
        var response: okhttp3.Response? = null
        try {
            val request = Request.Builder()
                .url(httpsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .get()
                .build()
            AppLogger.d(TAG, "发送展开请求: $httpsUrl")
            response = client.newCall(request).execute()
            AppLogger.d(TAG, "展开响应状态码: ${response.code}")
            AppLogger.d(TAG, "展开响应协议: ${response.protocol}")
            val finalUrl = response.request.url.toString()
            val redirectCount = response.priorResponse?.let {
                var count = 0
                var r: okhttp3.Response? = it
                while (r != null) { count++; r = r.priorResponse }
                count
            } ?: 0
            AppLogger.d(TAG, "重定向次数: $redirectCount")
            if (finalUrl != httpsUrl) {
                AppLogger.d(TAG, "短链接展开成功: $originalUrl -> $finalUrl")
            } else {
                AppLogger.d(TAG, "短链接未发生重定向，返回原URL: $finalUrl")
            }
            return finalUrl
        } catch (e: Exception) {
            AppLogger.e(TAG, "HTTPS展开失败: ${e.javaClass.simpleName}: ${e.message}")
            if (httpsUrl != originalUrl) {
                AppLogger.d(TAG, "尝试使用原始HTTP协议重试...")
                try {
                    val request = Request.Builder()
                        .url(originalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .get()
                        .build()
                    response = client.newCall(request).execute()
                    val finalUrl = response.request.url.toString()
                    AppLogger.d(TAG, "HTTP重试展开成功: $originalUrl -> $finalUrl")
                    return finalUrl
                } catch (e2: Exception) {
                    AppLogger.e(TAG, "HTTP重试也失败: ${e2.javaClass.simpleName}: ${e2.message}")
                }
            }
            AppLogger.e(TAG, "短链接展开最终失败，回退使用原始URL", e)
            return originalUrl
        } finally {
            response?.close()
        }
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

    private fun logParseResult(apiName: String, result: ParseResult) {
        AppLogger.d(TAG, "解析结果 [$apiName] - " +
            "title: ${result.title.ifBlank { "空" }}, " +
            "author: ${result.author.nickname}, " +
            "videoUrl: ${if (result.videoUrl.isNullOrBlank()) "空" else "有"}, " +
            "images数: ${result.images.size}, " +
            "livePhotos数: ${result.livePhotos.size}, " +
            "type: ${result.type}")
    }

    suspend fun parseDouyinWithFallback(url: String, apiEnabled: List<Boolean> = listOf(true, true, true, true, true, true)): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 开始抖音解析 Fallback 流程 =====")
        AppLogger.d(TAG, "目标URL: $url")
        AppLogger.d(TAG, "API开关状态: ${apiEnabled.mapIndexed { i, enabled -> "方案${i+1}=${if (enabled) "开" else "关"}" }.joinToString(", ")}")

        val allApis = listOf(
            "PP视频解析" to { parseApi1(url) },
            "柠檬解析" to { parseApi2(url) },
            "初梦科技" to { parseApi3(url) },
            "API Store" to { parseApi4(url) },
            "新野API" to { parseApi5(url) },
            "远梦API" to { parseApi6(url) }
        )

        val apis = allApis.filterIndexed { index, _ ->
            apiEnabled.getOrElse(index) { true }
        }

        if (apis.isEmpty()) {
            AppLogger.e(TAG, "所有解析接口均被用户关闭！")
            throw Exception("所有可用的解析接口已关闭，请前往设置开启")
        }

        AppLogger.d(TAG, "已启用接口数: ${apis.size}/${allApis.size}")

        val errors = mutableListOf<String>()
        var partialResult: ParseResult? = null
        var partialSource = ""

        for ((name, api) in apis) {
            try {
                AppLogger.d(TAG, "----- 正在尝试请求: [$name] -----")
                val result = api()
                logParseResult(name, result)

                val hasMedia = !result.videoUrl.isNullOrBlank()
                    || result.images.isNotEmpty()
                    || result.livePhotos.isNotEmpty()

                val hasMetadata = result.author.nickname != "未知作者"
                    || result.statistics != null
                    || result.title.isNotBlank()

                AppLogger.d(TAG, "数据检测 [$name]: hasMedia=$hasMedia, hasMetadata=$hasMetadata")

                if (hasMedia) {
                    if (partialResult != null) {
                        AppLogger.d(TAG, "执行数据缝合：将 [$partialSource] 的作者/统计数据合并到 [$name] 的媒体数据中")
                        val mergedFields = mutableListOf<String>()
                        if (partialResult!!.author.nickname != "未知作者" && result.author.nickname == "未知作者") {
                            mergedFields.add("作者(${partialResult!!.author.nickname})")
                        }
                        if (partialResult!!.statistics != null && result.statistics == null) {
                            mergedFields.add("统计数据(点赞${partialResult!!.statistics!!.diggCount})")
                        }
                        if (partialResult!!.title.isNotBlank() && result.title.isBlank()) {
                            mergedFields.add("标题(${partialResult!!.title})")
                        }
                        AppLogger.d(TAG, "缝合字段: ${mergedFields.ifEmpty { listOf("无额外字段需要缝合") }.joinToString(", ")}")
                    }
                    val merged = mergeResults(partialResult, result)
                    AppLogger.d(TAG, "===== 解析成功，返回合并结果 =====")
                    AppLogger.d(TAG, "最终结果: title=${merged.title}, author=${merged.author.nickname}, videoUrl=${!merged.videoUrl.isNullOrBlank()}, images=${merged.images.size}, livePhotos=${merged.livePhotos.size}")
                    return@withContext merged
                }

                if (hasMetadata && partialResult == null) {
                    partialResult = result
                    partialSource = name
                    AppLogger.d(TAG, "暂存半成品数据 [$name]: 作者=${result.author.nickname}, 统计=${result.statistics != null}，等待下一个接口提供媒体数据...")
                    errors.add("$name: 获取到作者/统计信息，等待媒体数据...")
                } else {
                    AppLogger.d(TAG, "触发 Fallback：[$name] 未获取到媒体数据，切换到下一个接口")
                    errors.add("$name: 未获取到媒体数据")
                }
            } catch (e: Exception) {
                val reason = when (e) {
                    is SocketTimeoutException -> "超时"
                    is UnknownHostException -> "网络不可达"
                    else -> e.message ?: "未知错误"
                }
                AppLogger.e(TAG, "解析异常 [$name]: $reason", e)
                errors.add("$name: $reason")
            }
        }

        AppLogger.d(TAG, "所有主接口轮询完毕，partialResult=${partialResult != null}")

        if (partialResult != null) {
            AppLogger.d(TAG, "尝试调用抖音实况解析接口作为最终补充...")
            try {
                val liveResult = parseDouyinLiveInternal(url)
                AppLogger.d(TAG, "实况接口返回: images=${liveResult.images.size}, videoUrls=${liveResult.videoUrls.size}, livePhotos=${liveResult.livePhotos.size}")
                val mergedWithLive = mergeResults(partialResult, liveResult)
                if (!mergedWithLive.videoUrl.isNullOrBlank()
                    || mergedWithLive.images.isNotEmpty()
                    || mergedWithLive.livePhotos.isNotEmpty()
                ) {
                    AppLogger.d(TAG, "===== 实况补充成功，返回缝合结果 =====")
                    return@withContext mergedWithLive
                }
                AppLogger.d(TAG, "实况接口也未返回媒体数据，返回之前的半成品数据")
            } catch (e: Exception) {
                AppLogger.e(TAG, "实况接口调用失败: ${e.message}", e)
            }
            return@withContext partialResult
        }

        AppLogger.e(TAG, "===== 所有接口均失败 =====")
        AppLogger.e(TAG, "失败详情:\n${errors.joinToString("\n")}")
        throw Exception("所有已开启的接口均失败:\n${errors.joinToString("\n")}\n\n请前往设置检查接口开关或稍后重试")
    }

    private fun mergeResults(base: ParseResult?, extra: ParseResult): ParseResult {
        if (base == null) return extra

        AppLogger.d(TAG, "--- 数据缝合开始 ---")
        AppLogger.d(TAG, "base: 作者=${base.author.nickname}, 统计=${base.statistics != null}, video=${!base.videoUrl.isNullOrBlank()}, images=${base.images.size}, livePhotos=${base.livePhotos.size}")
        AppLogger.d(TAG, "extra: 作者=${extra.author.nickname}, 统计=${extra.statistics != null}, video=${!extra.videoUrl.isNullOrBlank()}, images=${extra.images.size}, livePhotos=${extra.livePhotos.size}")

        val mergedAuthor = if (base.author.nickname != "未知作者") base.author else extra.author
        val mergedStats = base.statistics ?: extra.statistics
        val mergedTitle = if (base.title.isNotBlank()) base.title else extra.title
        val mergedDesc = base.desc ?: extra.desc
        val mergedCover = base.cover ?: extra.cover ?: extra.images.firstOrNull()
        val mergedMusic = base.music ?: extra.music

        if (mergedAuthor !== base.author) {
            AppLogger.d(TAG, "缝合：补充作者 ${extra.author.nickname}")
        }
        if (mergedStats !== base.statistics && base.statistics == null) {
            AppLogger.d(TAG, "缝合：补充统计数据 点赞=${extra.statistics?.diggCount}")
        }
        if (mergedTitle != base.title && base.title.isBlank()) {
            AppLogger.d(TAG, "缝合：补充标题")
        }

        val finalImages = extra.images.ifEmpty { base.images }
        val finalLivePhotos = extra.livePhotos.ifEmpty { base.livePhotos }

        val finalType = when {
            finalLivePhotos.isNotEmpty() -> {
                AppLogger.d(TAG, "缝合类型推断：有 ${finalLivePhotos.size} 个实况 → LIVE")
                ContentType.LIVE
            }
            finalImages.isNotEmpty() -> {
                AppLogger.d(TAG, "缝合类型推断：有 ${finalImages.size} 张图片 → ALBUM")
                ContentType.ALBUM
            }
            else -> {
                AppLogger.d(TAG, "缝合类型推断：无图片/实况 → 沿用 ${extra.type}")
                extra.type
            }
        }

        val rawVideoUrl = extra.videoUrl ?: base.videoUrl
        val finalVideoUrl = if (finalType == ContentType.ALBUM) {
            if (rawVideoUrl != null) AppLogger.d(TAG, "缝合防假视频：ALBUM类型强制清除videoUrl")
            null
        } else {
            rawVideoUrl
        }

        AppLogger.d(TAG, "--- 缝合完成: type=$finalType, video=${finalVideoUrl != null}, images=${finalImages.size}, livePhotos=${finalLivePhotos.size} ---")

        return ParseResult(
            type = finalType,
            title = mergedTitle,
            desc = mergedDesc,
            cover = mergedCover,
            author = mergedAuthor,
            videoUrl = finalVideoUrl,
            videoUrls = extra.videoUrls.ifEmpty { base.videoUrls },
            images = finalImages,
            music = mergedMusic,
            statistics = mergedStats,
            platform = extra.platform,
            imageCount = finalImages.size.coerceAtLeast(extra.imageCount ?: 0),
            livePhotos = finalLivePhotos
        )
    }

    suspend fun parseDouyin(url: String): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "调用单接口抖音解析: $url")
        val apiUrl = "https://api.bugpk.com/api/douyin?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        parseDouyinResponse(fetchJson(apiUrl, "初梦科技-抖音"), url)
    }

    suspend fun parseDouyinLive(shareUrl: String): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "调用抖音实况解析: $shareUrl")
        parseDouyinLiveInternal(shareUrl)
    }

    private fun parseDouyinLiveInternal(url: String): ParseResult {
        val apiUrl = "https://api.bugpk.com/api/dylive?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        val json = fetchJson(apiUrl, "抖音实况专用接口")
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")

        val images = mutableListOf<String>()
        if (data.has("images") && data.get("images").isJsonArray) {
            data.getAsJsonArray("images").forEach { element ->
                try { images.add(element.asString) } catch (_: Exception) {}
            }
        }

        val videoUrls = mutableListOf<String>()
        if (data.has("url") && data.get("url").isJsonArray) {
            data.getAsJsonArray("url").forEach { element ->
                try { videoUrls.add(element.asString) } catch (_: Exception) {}
            }
        }

        val livePhotos = images.mapIndexed { index, imageUrl ->
            LivePhoto(
                imageUrl = imageUrl,
                videoUrl = videoUrls.getOrNull(index)
            )
        }

        val authorName = data.safeGet("auther") ?: data.safeGet("author") ?: "未知作者"
        val authorAvatar = data.safeGet("avatar")
        AppLogger.d(TAG, "实况接口作者解析: auther=${data.safeGet("auther")}, author=${data.safeGet("author")}, avatar=$authorAvatar")

        val author = AuthorInfo(
            nickname = authorName,
            avatar = authorAvatar,
            uniqueId = data.safeGet("uid"),
            followerCount = data.safeGet("followerCount")?.toLongOrNull(),
            totalFavorited = data.safeGet("totalFavorited")?.toLongOrNull()
        )

        val musicObj = data.safeGetObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title"),
                author = musicObj.safeGet("author"),
                cover = musicObj.safeGet("avatar") ?: musicObj.safeGet("cover"),
                url = musicObj.safeGet("url")
            )
        } else null

        val result = ParseResult(
            type = ContentType.LIVE,
            title = data.safeGet("title") ?: data.safeGet("desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover"),
            author = author,
            videoUrl = videoUrls.firstOrNull(),
            videoUrls = videoUrls,
            images = images,
            music = music,
            statistics = null,
            platform = Platform.DOUYIN,
            imageCount = images.size,
            livePhotos = livePhotos
        )
        AppLogger.d(TAG, "实况解析完成: images=${images.size}, videoUrls=${videoUrls.size}, livePhotos=${livePhotos.size}")
        return result
    }

    suspend fun parseKuaishou(url: String): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 开始快手解析 =====")
        AppLogger.d(TAG, "[KS-001] 输入URL: $url")
        AppLogger.d(TAG, "[KS-002] URL协议: ${if (url.startsWith("https")) "HTTPS" else if (url.startsWith("http")) "HTTP" else "未知"}")
        try {
            val realUrl = expandShortUrl(url)
            if (realUrl != url) {
                AppLogger.d(TAG, "[KS-003] 短链展开: $url -> $realUrl")
            } else {
                AppLogger.d(TAG, "[KS-003] URL未变化（非短链或展开失败）")
            }
            val encodedUrl = java.net.URLEncoder.encode(realUrl, "UTF-8")
            AppLogger.d(TAG, "[KS-004] URL编码后: $encodedUrl")
            val apiUrl1 = "https://api.bugpk.com/api/kuaishou?url=$encodedUrl&type=video"
            AppLogger.d(TAG, "[KS-005] 视频接口完整URL: $apiUrl1")
            val json1 = fetchJson(apiUrl1, "快手-视频接口")
            val json1Str = json1.toString()
            AppLogger.d(TAG, "[KS-006] 视频接口响应长度: ${json1Str.length} 字符")
            AppLogger.d(TAG, "[KS-007] 视频接口响应前500字符: ${json1Str.take(500)}")
            val result = parseKuaishouResponse(json1)
            AppLogger.d(TAG, "[KS-008] 视频接口解析结果: type=${result.type}, images=${result.images.size}, videoUrl=${result.videoUrl != null}, author=${result.author.nickname}")
            if (result.images.isEmpty() && result.videoUrl.isNullOrBlank()) {
                AppLogger.d(TAG, "[KS-009] 视频接口无媒体数据，尝试图集接口...")
                val apiUrl2 = "https://api.bugpk.com/api/kuaishou?url=$encodedUrl&type=images"
                AppLogger.d(TAG, "[KS-010] 图集接口完整URL: $apiUrl2")
                val json2 = fetchJson(apiUrl2, "快手-图集接口")
                val json2Str = json2.toString()
                AppLogger.d(TAG, "[KS-011] 图集接口响应长度: ${json2Str.length} 字符")
                AppLogger.d(TAG, "[KS-012] 图集接口响应前500字符: ${json2Str.take(500)}")
                val result2 = parseKuaishouResponse(json2)
                AppLogger.d(TAG, "[KS-013] 图集接口解析结果: type=${result2.type}, images=${result2.images.size}, videoUrl=${result2.videoUrl != null}")
                result2
            } else {
                AppLogger.d(TAG, "[KS-009] 视频接口已有数据，跳过图集接口")
                result
            }
        } catch (e: Exception) {
            val reason = when (e) {
                is SocketTimeoutException -> "请求超时"
                is UnknownHostException -> "网络不可达"
                is java.net.ConnectException -> "连接失败"
                is javax.net.ssl.SSLException -> "SSL证书错误"
                else -> "${e.javaClass.simpleName}: ${e.message}"
            }
            AppLogger.e(TAG, "[KS-ERR] 快手解析异常: $reason")
            AppLogger.e(TAG, "[KS-ERR] 异常堆栈:", e)
            throw Exception("快手解析失败: $reason")
        }
    }

    suspend fun parseXhs(url: String): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 开始小红书解析(新接口) =====")
        AppLogger.d(TAG, "[XHS-NEW-001] 输入URL: $url")
        try {
            var submitUrl = url.trim().removeSurrounding("`")
            if (submitUrl.startsWith("http://", ignoreCase = true)) {
                submitUrl = submitUrl.replaceFirst("http://", "https://")
                AppLogger.d(TAG, "[XHS-NEW-002] HTTP升级为HTTPS: $submitUrl")
            }
            AppLogger.d(TAG, "[XHS-NEW-003] 提交给接口的URL: $submitUrl")
            val encodedUrl = java.net.URLEncoder.encode(submitUrl, "UTF-8")
            AppLogger.d(TAG, "[XHS-NEW-004] URL编码后: $encodedUrl")
            val apiUrl = "https://apione.apibyte.cn/xhsvideoparse?key=$XHS_API_KEY&url=$encodedUrl"
            AppLogger.d(TAG, "[XHS-NEW-005] 新接口完整URL: $apiUrl")
            val json = fetchJson(apiUrl, "小红书-新接口")
            val jsonStr = json.toString()
            AppLogger.d(TAG, "[XHS-NEW-006] 响应长度: ${jsonStr.length} 字符")
            AppLogger.d(TAG, "[XHS-NEW-007] 响应前800字符: ${jsonStr.take(800)}")
            val result = parseXhsNewResponse(json)
            AppLogger.d(TAG, "[XHS-NEW-008] 解析结果: type=${result.type}, title=${result.title.take(20)}, images=${result.images.size}, videoUrl=${!result.videoUrl.isNullOrBlank()}, author=${result.author.nickname}")
            result
        } catch (e: Exception) {
            val reason = when (e) {
                is SocketTimeoutException -> "请求超时"
                is UnknownHostException -> "网络不可达"
                is java.net.ConnectException -> "连接失败"
                is javax.net.ssl.SSLException -> "SSL证书错误"
                else -> "${e.javaClass.simpleName}: ${e.message}"
            }
            AppLogger.e(TAG, "[XHS-NEW-ERR] 小红书解析异常: $reason")
            AppLogger.e(TAG, "[XHS-NEW-ERR] 异常堆栈:", e)
            throw Exception("小红书解析失败: $reason")
        }
    }

    suspend fun parseDoubao(url: String): ParseResult = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "开始豆包解析: $url")
        try {
            val apiUrl = "https://api.bugpk.com/api/doubao?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            AppLogger.d(TAG, "豆包请求URL: $apiUrl")
            val json = fetchJson(apiUrl, "豆包接口")
            AppLogger.d(TAG, "豆包响应JSON长度: ${json.toString().length}")
            val result = parseDoubaoResponse(json)
            logParseResult("豆包", result)
            result
        } catch (e: Exception) {
            val reason = when (e) {
                is SocketTimeoutException -> "请求超时"
                is UnknownHostException -> "网络不可达"
                else -> e.message ?: "未知错误"
            }
            AppLogger.e(TAG, "豆包解析异常: $reason", e)
            throw Exception("豆包解析失败: $reason")
        }
    }

    private fun parseDouyinResponse(json: JsonObject, sourceUrl: String): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "抖音响应 data 键: ${data.keySet().joinToString(", ")}")

        val nickname = data.flexString(
            "author.nickname",
            "author.name",
            "author",
            "additional_data.0.nickname",
            "nickname"
        ) ?: "未知作者"

        val avatar = data.flexString(
            "author.avatar",
            "avatar",
            "additional_data.0.url"
        )

        val uniqueId = data.flexString(
            "author.unique_id",
            "unique_id",
            "uid"
        )

        val followerCount = data.flexLong(
            "author.follower_count",
            "follower_count",
            "followerCount"
        )

        val totalFavorited = data.flexLong(
            "author.total_favorited",
            "total_favorited",
            "totalFavorited"
        )

        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = uniqueId,
            followerCount = followerCount,
            totalFavorited = totalFavorited
        )
        AppLogger.d(TAG, "弹性解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val statistics = extractStatisticsFlexible(data)
        AppLogger.d(TAG, "弹性解析到统计: ${statistics != null}, 点赞=${statistics?.diggCount}")

        val musicObj = data.flexObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title") ?: musicObj.safeGet("name"),
                author = musicObj.safeGet("author") ?: musicObj.safeGet("artist"),
                cover = musicObj.safeGet("cover") ?: musicObj.safeGet("avatar"),
                url = musicObj.safeGet("url") ?: musicObj.safeGet("play_url")
            )
        } else null

        val images = extractImagesFlexible(data)
        AppLogger.d(TAG, "弹性解析到图片列表: ${images.size} 张")

        var videoUrl = extractValidVideoUrl(data, sourceUrl)

        val rawType = ContentType.fromString(data.safeGet("type") ?: data.safeGet("aweme_type"))

        val livePhotos = if (rawType == ContentType.LIVE) {
            val videoList = if (data.has("url") && data.get("url").isJsonArray) {
                data.getAsJsonArray("url").mapNotNull { it.safeString() }
            } else emptyList()
            images.mapIndexed { index, imageUrl ->
                LivePhoto(imageUrl = imageUrl, videoUrl = videoList.getOrNull(index))
            }
        } else emptyList()

        val actualType = when {
            livePhotos.isNotEmpty() -> {
                AppLogger.d(TAG, "类型精准推断：检测到 ${livePhotos.size} 个实况，标记为LIVE")
                ContentType.LIVE
            }
            images.isNotEmpty() -> {
                AppLogger.d(TAG, "类型精准推断：检测到 ${images.size} 张图片，标记为ALBUM")
                ContentType.ALBUM
            }
            else -> {
                AppLogger.d(TAG, "类型精准推断：沿用原始类型 $rawType")
                rawType
            }
        }

        val finalVideoUrl = if (actualType == ContentType.ALBUM) {
            if (videoUrl != null) AppLogger.d(TAG, "防假视频：ALBUM类型强制清除videoUrl")
            null
        } else videoUrl

        AppLogger.d(TAG, "最终结果: type=$actualType, videoUrl=${finalVideoUrl != null}, images=${images.size}, livePhotos=${livePhotos.size}")

        return ParseResult(
            type = actualType,
            title = data.flexString("title", "desc", "additional_data.0.desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover") ?: data.safeGet("dynamic_cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = music,
            statistics = statistics,
            platform = Platform.DOUYIN,
            imageCount = images.size,
            livePhotos = livePhotos
        )
    }

    private fun parseApiStoreResponse(json: JsonObject, sourceUrl: String): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "API Store响应 data 键: ${data.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "API Store响应 data JSON片段: ${data.toString().take(500)}")

        val nickname = extractAuthorName(data)
        val avatar = extractAuthorAvatar(data)
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = data.safeGet("unique_id") ?: data.safeGet("uid"),
            followerCount = data.safeGet("follower_count")?.toLongOrNull(),
            totalFavorited = data.safeGet("total_favorited")?.toLongOrNull()
        )
        AppLogger.d(TAG, "API Store解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val likeCount = (data.safeGet("like") ?: data.safeGet("like_count") ?: data.safeGet("digg_count"))?.toLongOrNull()
        val statistics = if (likeCount != null) {
            Statistics(
                playCount = (data.safeGet("play_count") ?: data.safeGet("view_count"))?.toLongOrNull() ?: 0L,
                diggCount = likeCount,
                commentCount = data.safeGet("comment_count")?.toLongOrNull() ?: 0L,
                shareCount = data.safeGet("share_count")?.toLongOrNull() ?: 0L,
                collectCount = data.safeGet("collect_count")?.toLongOrNull() ?: 0L
            ).also {
                AppLogger.d(TAG, "API Store解析到统计数据: 点赞=$likeCount")
            }
        } else null

        val musicObj = data.safeGetObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title") ?: musicObj.safeGet("name"),
                author = musicObj.safeGet("author") ?: musicObj.safeGet("artist"),
                cover = musicObj.safeGet("cover") ?: musicObj.safeGet("avatar"),
                url = musicObj.safeGet("url") ?: musicObj.safeGet("play_url")
            )
        } else null

        val videoUrl = extractValidVideoUrl(data, sourceUrl)
        AppLogger.d(TAG, "API Store extractValidVideoUrl结果: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val images = extractImagesFlexible(data)
        AppLogger.d(TAG, "API Store解析到图片: ${images.size} 张")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        val result = ParseResult(
            type = actualType,
            title = data.safeGet("title") ?: data.safeGet("desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = music,
            statistics = statistics,
            platform = Platform.DOUYIN,
            imageCount = images.size
        )
        logParseResult("API Store(专属)", result)
        return result
    }

    private fun parseXinyeResponse(json: JsonObject, sourceUrl: String): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "新野API响应 data 键: ${data.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "新野API响应 data JSON片段: ${data.toString().take(500)}")

        val additionalData = data.safeGetArray("additional_data")
        val firstAdditional = if (additionalData != null && additionalData.size() > 0) {
            try { additionalData.get(0).asJsonObject } catch (_: Exception) { null }
        } else null
        AppLogger.d(TAG, "新野API additional_data: ${if (firstAdditional != null) "存在(键: ${firstAdditional.keySet().joinToString(", ")})" else "不存在"}")

        val nickname = firstAdditional?.safeGet("nickname")
            ?: data.safeGet("nickname")
            ?: extractAuthorName(data)

        val avatar = firstAdditional?.safeGet("url")
            ?: data.safeGet("avatar")
            ?: extractAuthorAvatar(data)

        val title = firstAdditional?.safeGet("desc")
            ?: data.safeGet("title")
            ?: data.safeGet("desc")
            ?: ""

        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = firstAdditional?.safeGet("uid") ?: data.safeGet("uid") ?: data.safeGet("unique_id"),
            followerCount = (firstAdditional?.safeGet("follower_count") ?: data.safeGet("follower_count"))?.toLongOrNull(),
            totalFavorited = (firstAdditional?.safeGet("total_favorited") ?: data.safeGet("total_favorited"))?.toLongOrNull()
        )
        AppLogger.d(TAG, "新野API解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val videoUrl = extractValidVideoUrl(data, sourceUrl)
        AppLogger.d(TAG, "新野API extractValidVideoUrl结果: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val images = extractImagesFlexible(data)
        AppLogger.d(TAG, "新野API解析到图片: ${images.size} 张")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        val result = ParseResult(
            type = actualType,
            title = title,
            desc = firstAdditional?.safeGet("desc") ?: data.safeGet("desc"),
            cover = firstAdditional?.safeGet("cover") ?: data.safeGet("cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = null,
            statistics = null,
            platform = Platform.DOUYIN,
            imageCount = images.size
        )
        logParseResult("新野API(专属)", result)
        return result
    }

    private fun parseYuanmengResponse(json: JsonObject, sourceUrl: String): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "远梦API响应 data 键: ${data.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "远梦API响应 data JSON片段: ${data.toString().take(500)}")

        val nickname = data.safeGet("nickname")
            ?: extractAuthorName(data)

        val title = data.safeGet("desc")
            ?: data.safeGet("title")
            ?: ""

        val author = AuthorInfo(
            nickname = nickname,
            avatar = extractAuthorAvatar(data),
            uniqueId = data.safeGet("uid") ?: data.safeGet("unique_id"),
            followerCount = data.safeGet("follower_count")?.toLongOrNull(),
            totalFavorited = data.safeGet("total_favorited")?.toLongOrNull()
        )
        AppLogger.d(TAG, "远梦API解析到作者: ${author.nickname}")

        val videoUrl = extractValidVideoUrl(data, sourceUrl)
        AppLogger.d(TAG, "远梦API extractValidVideoUrl结果: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val images = extractImagesFlexible(data)
        AppLogger.d(TAG, "远梦API解析到图片: ${images.size} 张")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        val result = ParseResult(
            type = actualType,
            title = title,
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = null,
            statistics = null,
            platform = Platform.DOUYIN,
            imageCount = images.size
        )
        logParseResult("远梦API(专属)", result)
        return result
    }

    private fun extractStatisticsFlexible(data: JsonObject): Statistics? {
        val statsObj = data.flexObject("statistics", "stats", "counts")
        if (statsObj != null) {
            return Statistics(
                playCount = statsObj.flexLong("play_count", "view_count", "digg_count") ?: 0L,
                diggCount = statsObj.flexLong("digg_count", "diggCount", "like_count", "like") ?: 0L,
                commentCount = statsObj.flexLong("comment_count", "commentCount") ?: 0L,
                shareCount = statsObj.flexLong("share_count", "shareCount") ?: 0L,
                collectCount = statsObj.flexLong("collect_count", "collectCount") ?: 0L
            )
        }

        val likeCount = data.flexLong("like", "like_count", "digg_count")
        if (likeCount != null) {
            return Statistics(
                playCount = 0L,
                diggCount = likeCount,
                commentCount = 0L,
                shareCount = 0L,
                collectCount = 0L
            )
        }

        return null
    }

    private fun extractImagesFlexible(data: JsonObject): List<String> {
        val images = mutableListOf<String>()
        val imagesArray = data.safeGetArray("images")
        if (imagesArray != null) {
            imagesArray.forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("url")
                                ?: element.asJsonObject.safeGet("src")
                                ?: element.asJsonObject.safeGet("urlDefault")
                            if (url != null) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return images
    }

    private fun extractAuthorName(data: JsonObject): String {
        val authorField = data.get("author")
        if (authorField != null && !authorField.isJsonNull) {
            if (authorField.isJsonPrimitive) {
                val name = authorField.asString
                if (name.isNotBlank()) return name
            } else if (authorField.isJsonObject) {
                val obj = authorField.asJsonObject
                val name = obj.safeGet("name") ?: obj.safeGet("nickname")
                if (!name.isNullOrBlank()) return name
            }
        }
        return data.safeGet("nickname") ?: data.safeGet("author_name") ?: "未知作者"
    }

    private fun extractAuthorAvatar(data: JsonObject): String? {
        val authorField = data.get("author")
        if (authorField != null && !authorField.isJsonNull && authorField.isJsonObject) {
            val avatar = authorField.asJsonObject.safeGet("avatar")
            if (!avatar.isNullOrBlank()) return avatar
        }
        return data.safeGet("avatar") ?: data.safeGet("head_url")
    }

    private fun extractVideoUrlFlexible(data: JsonObject): String? {
        AppLogger.d(TAG, "开始提取视频直链，data键: ${data.keySet().joinToString(", ")}")
        val directUrl = data.safeGet("url")
        if (!directUrl.isNullOrBlank()) {
            val normalized = if (directUrl.startsWith("//")) "https:$directUrl" else directUrl
            if (normalized.startsWith("http", ignoreCase = true)) {
                AppLogger.d(TAG, "从data.url提取到视频直链: ${normalized.take(80)}")
                return normalized
            }
        }
        val videoUrl = data.safeGet("video_url")
        if (!videoUrl.isNullOrBlank()) {
            val normalized = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
            if (normalized.startsWith("http", ignoreCase = true)) {
                AppLogger.d(TAG, "从data.video_url提取到视频直链: ${normalized.take(80)}")
                return normalized
            }
        }
        AppLogger.d(TAG, "未能从data中直接提取到视频直链，尝试候选键...")
        return extractValidVideoUrlSimple(data)
    }

    private fun identifyVideoSource(data: JsonObject): String {
        return when {
            data.flexString("video.play_url") != null -> "video.play_url"
            data.flexString("video.1080p") != null -> "video.1080p"
            data.flexString("video.720p") != null -> "video.720p"
            data.safeGet("url") != null -> "url"
            data.safeGet("video_url") != null -> "video_url"
            data.safeGet("play_url") != null -> "play_url"
            data.safeGet("video_url_HQ") != null -> "video_url_HQ"
            else -> "未找到"
        }
    }

    private val VIDEO_CANDIDATE_KEYS = listOf(
        "video_url_HQ",
        "video.1080p",
        "video.720p",
        "video.play_url",
        "video_url",
        "play_url",
        "url"
    )

    private fun extractValidVideoUrl(data: JsonObject, sourceUrl: String): String? {
        AppLogger.d(TAG, "视频链接深度清洗开始，sourceUrl=$sourceUrl")
        for (key in VIDEO_CANDIDATE_KEYS) {
            val raw = data.flexString(key)
            if (raw.isNullOrBlank()) {
                continue
            }
            val trimmed = raw.trim()
            val normalizedUrl = when {
                trimmed.startsWith("//") -> "https:$trimmed"
                else -> trimmed
            }
            if (!normalizedUrl.startsWith("http", ignoreCase = true)) {
                AppLogger.d(TAG, "拒绝 [$key]: 非http链接 -> ${normalizedUrl.take(60)}")
                continue
            }
            if (normalizedUrl == sourceUrl) {
                AppLogger.d(TAG, "拒绝 [$key]: 与原始分享链接相同(假直链) -> ${normalizedUrl.take(60)}")
                continue
            }
            if (trimmed != normalizedUrl) {
                AppLogger.d(TAG, "URL协议补全 [$key]: $trimmed -> $normalizedUrl")
            }
            AppLogger.d(TAG, "视频链接校验通过 [$key]: ${normalizedUrl.take(80)}")
            return normalizedUrl
        }
        AppLogger.d(TAG, "视频链接深度清洗结束：所有候选键均未通过校验")
        return null
    }

    private fun extractValidVideoUrlSimple(data: JsonObject): String? {
        for (key in VIDEO_CANDIDATE_KEYS) {
            val raw = data.flexString(key)
            if (raw.isNullOrBlank()) continue
            val trimmed = raw.trim()
            val normalizedUrl = when {
                trimmed.startsWith("//") -> "https:$trimmed"
                else -> trimmed
            }
            if (!normalizedUrl.startsWith("http", ignoreCase = true)) continue
            if (trimmed != normalizedUrl) {
                AppLogger.d(TAG, "Simple URL协议补全 [$key]: $trimmed -> $normalizedUrl")
            }
            return normalizedUrl
        }
        return null
    }

    private fun parseKuaishouResponse(json: JsonObject): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "快手响应 data 键: ${data.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "快手响应 data JSON片段: ${data.toString().take(500)}")

        val nickname = extractAuthorName(data)
        val avatar = extractAuthorAvatar(data)
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = data.safeGet("unique_id") ?: data.safeGet("user_id") ?: data.safeGet("uid"),
            followerCount = data.safeGet("follower_count")?.toLongOrNull(),
            totalFavorited = data.safeGet("total_favorited")?.toLongOrNull()
        )
        AppLogger.d(TAG, "快手解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val statsObj = data.safeGetObject("statistics") ?: data.safeGetObject("counts")
        val statistics = if (statsObj != null) {
            Statistics(
                playCount = (statsObj.safeGet("play_count") ?: statsObj.safeGet("view_count"))?.toLongOrNull() ?: 0L,
                diggCount = (statsObj.safeGet("digg_count") ?: statsObj.safeGet("like_count"))?.toLongOrNull() ?: 0L,
                commentCount = (statsObj.safeGet("comment_count"))?.toLongOrNull() ?: 0L,
                shareCount = (statsObj.safeGet("share_count"))?.toLongOrNull() ?: 0L,
                collectCount = (statsObj.safeGet("collect_count"))?.toLongOrNull() ?: 0L
            ).also {
                AppLogger.d(TAG, "快手解析到统计数据: 点赞=${it.diggCount}, 播放=${it.playCount}")
            }
        } else {
            AppLogger.d(TAG, "快手未找到统计数据")
            null
        }

        val musicObj = data.safeGetObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title") ?: musicObj.safeGet("name"),
                author = musicObj.safeGet("author") ?: musicObj.safeGet("artist"),
                cover = musicObj.safeGet("cover") ?: musicObj.safeGet("avatar"),
                url = musicObj.safeGet("url") ?: musicObj.safeGet("play_url")
            )
        } else null

        val images = mutableListOf<String>()
        if (data.has("images") && data.get("images").isJsonArray) {
            data.getAsJsonArray("images").forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("url")
                                ?: element.asJsonObject.safeGet("src")
                                ?: element.asJsonObject.safeGet("urlDefault")
                            if (url != null) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "快手解析到图片: ${images.size} 张")

        val videoUrl = extractVideoUrlFlexible(data)
        AppLogger.d(TAG, "快手解析到视频URL: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        return ParseResult(
            type = actualType,
            title = data.safeGet("title") ?: data.safeGet("desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover") ?: data.safeGet("dynamic_cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = music,
            statistics = statistics,
            platform = Platform.KUAISHOU,
            imageCount = images.size
        )
    }

    private fun parseXhsNewResponse(json: JsonObject): ParseResult {
        AppLogger.d(TAG, "[XHS-NEW-PARSE] 开始解析新接口响应")
        AppLogger.d(TAG, "[XHS-NEW-PARSE] 顶层键: ${json.keySet().joinToString(", ")}")
        val data = json.safeGetObject("data")
        if (data == null) {
            AppLogger.e(TAG, "[XHS-NEW-PARSE] data节点为空或不是对象")
            throw Exception("返回数据格式错误: data节点缺失")
        }
        AppLogger.d(TAG, "[XHS-NEW-PARSE] data键: ${data.keySet().joinToString(", ")}")
        val noteId = data.safeGet("noteId")
        val title = data.safeGet("title") ?: ""
        val desc = data.safeGet("desc")
        val type = data.safeGet("type")
        AppLogger.d(TAG, "[XHS-NEW-PARSE] noteId=$noteId, title=${title.take(30)}, type=$type")
        val authorObj = data.safeGetObject("author")
        val nickname = authorObj?.safeGet("nickname") ?: "未知作者"
        val avatar = authorObj?.safeGet("avatar")
        val userId = authorObj?.safeGet("userId")
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = userId,
            followerCount = null,
            totalFavorited = null
        )
        AppLogger.d(TAG, "[XHS-NEW-PARSE] 作者: nickname=$nickname, userId=$userId, avatar=${avatar != null}")
        val interactObj = data.safeGetObject("interactInfo")
        val statistics = if (interactObj != null) {
            Statistics(
                playCount = 0L,
                diggCount = interactObj.safeGet("likedCount")?.toLongOrNull() ?: 0L,
                commentCount = interactObj.safeGet("commentCount")?.toLongOrNull() ?: 0L,
                shareCount = interactObj.safeGet("shareCount")?.toLongOrNull() ?: 0L,
                collectCount = interactObj.safeGet("collectedCount")?.toLongOrNull() ?: 0L
            ).also {
                AppLogger.d(TAG, "[XHS-NEW-PARSE] 互动数据: 点赞=${it.diggCount}, 评论=${it.commentCount}, 收藏=${it.collectCount}, 分享=${it.shareCount}")
            }
        } else {
            AppLogger.d(TAG, "[XHS-NEW-PARSE] 无互动数据(interactInfo为空)")
            null
        }
        val videoObj = data.safeGetObject("video")
        var videoUrl: String? = null
        var videoCover: String? = null
        if (videoObj != null) {
            videoUrl = videoObj.safeGet("urlHd") ?: videoObj.safeGet("url")
            videoCover = videoObj.safeGet("cover")
            val urlsArray = videoObj.safeGetArray("urls")
            if (urlsArray != null && urlsArray.size() > 0) {
                AppLogger.d(TAG, "[XHS-NEW-PARSE] 多清晰度视频数: ${urlsArray.size()}")
                val firstHd = urlsArray.firstOrNull { element ->
                    element.isJsonObject && element.asJsonObject.safeGet("quality") == "SUPER_HD"
                }?.asJsonObject
                if (firstHd != null) {
                    videoUrl = firstHd.safeGet("url") ?: videoUrl
                    AppLogger.d(TAG, "[XHS-NEW-PARSE] 使用SUPER_HD清晰度")
                }
            }
            if (videoUrl != null) {
                val normalized = if (videoUrl!!.startsWith("//")) "https:$videoUrl" else videoUrl
                videoUrl = normalized
            }
            AppLogger.d(TAG, "[XHS-NEW-PARSE] 视频URL: ${videoUrl?.take(80) ?: "无"}")
        } else {
            AppLogger.d(TAG, "[XHS-NEW-PARSE] 无视频数据(video为空)")
        }
        val images = mutableListOf<String>()
        val imagesArray = data.safeGetArray("images")
        if (imagesArray != null) {
            imagesArray.forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("url")
                            if (url != null) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "[XHS-NEW-PARSE] 图片数量: ${images.size}")
        val actualType = when {
            type == "video" && videoUrl != null -> ContentType.VIDEO
            images.isNotEmpty() -> ContentType.ALBUM
            else -> ContentType.VIDEO
        }
        val finalVideoUrl = if (actualType == ContentType.ALBUM) {
            AppLogger.d(TAG, "[XHS-NEW-PARSE] ALBUM类型，清除videoUrl")
            null
        } else videoUrl
        AppLogger.d(TAG, "[XHS-NEW-PARSE] 最终类型: $actualType, videoUrl=${finalVideoUrl != null}, images=${images.size}")
        return ParseResult(
            type = actualType,
            title = title,
            desc = desc,
            cover = videoCover,
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = null,
            statistics = statistics,
            platform = Platform.XHS,
            imageCount = images.size
        )
    }

    private fun parseXhsResponse(json: JsonObject): ParseResult {
        AppLogger.d(TAG, "小红书响应顶层键: ${json.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "小红书响应 JSON片段: ${json.toString().take(500)}")

        val hasDataObject = json.has("data") && json.get("data").isJsonObject
        val hasDataArray = json.has("data") && json.get("data").isJsonArray

        if (hasDataObject) {
            AppLogger.d(TAG, "小红书检测到data为对象，使用标准格式解析")
            return parseXhsStandardFormat(json)
        } else if (hasDataArray || json.has("nickname") || json.has("noteId")) {
            AppLogger.d(TAG, "小红书检测到data为数组或存在noteId/nickname字段，使用apihz格式解析")
            return parseXhsApihzFormat(json)
        } else {
            AppLogger.d(TAG, "小红书响应格式未识别，尝试标准格式解析")
            return parseXhsStandardFormat(json)
        }
    }

    private fun parseXhsStandardFormat(json: JsonObject): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "小红书(标准)响应 data 键: ${data.keySet().joinToString(", ")}")

        val nickname = extractAuthorName(data)
        val avatar = extractAuthorAvatar(data)
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = data.safeGet("unique_id") ?: data.safeGet("user_id") ?: data.safeGet("uid"),
            followerCount = data.safeGet("follower_count")?.toLongOrNull(),
            totalFavorited = data.safeGet("total_favorited")?.toLongOrNull()
        )
        AppLogger.d(TAG, "小红书(标准)解析到作者: ${author.nickname}")

        val statsObj = data.safeGetObject("statistics") ?: data.safeGetObject("counts")
        val statistics = if (statsObj != null) {
            Statistics(
                playCount = (statsObj.safeGet("play_count") ?: statsObj.safeGet("view_count"))?.toLongOrNull() ?: 0L,
                diggCount = (statsObj.safeGet("digg_count") ?: statsObj.safeGet("like_count"))?.toLongOrNull() ?: 0L,
                commentCount = (statsObj.safeGet("comment_count"))?.toLongOrNull() ?: 0L,
                shareCount = (statsObj.safeGet("share_count"))?.toLongOrNull() ?: 0L,
                collectCount = (statsObj.safeGet("collect_count"))?.toLongOrNull() ?: 0L
            )
        } else null

        val musicObj = data.safeGetObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title") ?: musicObj.safeGet("name"),
                author = musicObj.safeGet("author") ?: musicObj.safeGet("artist"),
                cover = musicObj.safeGet("cover") ?: musicObj.safeGet("avatar"),
                url = musicObj.safeGet("url") ?: musicObj.safeGet("play_url")
            )
        } else null

        val images = mutableListOf<String>()
        if (data.has("images") && data.get("images").isJsonArray) {
            data.getAsJsonArray("images").forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("url")
                                ?: element.asJsonObject.safeGet("src")
                                ?: element.asJsonObject.safeGet("urlDefault")
                            if (url != null) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "小红书(标准)解析到图片: ${images.size} 张")

        val videoUrl = extractVideoUrlFlexible(data)
        AppLogger.d(TAG, "小红书(标准)解析到视频URL: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        return ParseResult(
            type = actualType,
            title = data.safeGet("title") ?: data.safeGet("desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover") ?: data.safeGet("dynamic_cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = music,
            statistics = statistics,
            platform = Platform.XHS,
            imageCount = images.size
        )
    }

    private fun parseXhsApihzFormat(json: JsonObject): ParseResult {
        AppLogger.d(TAG, "小红书(apihz)开始解析，顶层键: ${json.keySet().joinToString(", ")}")

        val nickname = json.safeGet("nickname") ?: "未知作者"
        val avatar = json.safeGet("avatar")
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = json.safeGet("userId") ?: json.safeGet("user_id"),
            followerCount = null,
            totalFavorited = null
        )
        AppLogger.d(TAG, "小红书(apihz)解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val images = mutableListOf<String>()
        val dataArray = json.safeGetArray("data")
        if (dataArray != null) {
            AppLogger.d(TAG, "小红书(apihz) data数组大小: ${dataArray.size()}")
            dataArray.forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("urlDefault")
                                ?: element.asJsonObject.safeGet("url")
                                ?: element.asJsonObject.safeGet("urlPre")
                            if (!url.isNullOrBlank()) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "小红书(apihz)解析到图片: ${images.size} 张")

        var videoUrl: String? = null
        val videoArray = json.safeGetArray("video")
        if (videoArray != null && videoArray.size() > 0) {
            AppLogger.d(TAG, "小红书(apihz) video数组大小: ${videoArray.size()}")
            try {
                val videoObj = videoArray.get(0).asJsonObject
                videoUrl = videoObj.safeGet("masterUrl") ?: videoObj.safeGet("url")
                if (videoUrl == null) {
                    val backupUrls = videoObj.safeGetArray("backupUrls")
                    if (backupUrls != null && backupUrls.size() > 0) {
                        videoUrl = backupUrls.get(0).asString
                    }
                }
                if (videoUrl != null) {
                    val normalized = if (videoUrl!!.startsWith("//")) "https:$videoUrl" else videoUrl
                    if (!normalized.startsWith("http", ignoreCase = true)) videoUrl = null
                    else videoUrl = normalized
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "小红书(apihz)视频解析异常: ${e.message}", e)
            }
        }
        AppLogger.d(TAG, "小红书(apihz)解析到视频URL: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        return ParseResult(
            type = actualType,
            title = json.safeGet("title") ?: json.safeGet("desc") ?: "",
            desc = json.safeGet("desc"),
            cover = null,
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = null,
            statistics = null,
            platform = Platform.XHS,
            imageCount = images.size
        )
    }

    private fun parseDoubaoResponse(json: JsonObject): ParseResult {
        val data = json.safeGetObject("data") ?: throw Exception("返回数据格式错误")
        AppLogger.d(TAG, "豆包响应 data 键: ${data.keySet().joinToString(", ")}")
        AppLogger.d(TAG, "豆包响应 data JSON片段: ${data.toString().take(500)}")

        val nickname = extractAuthorName(data)
        val avatar = extractAuthorAvatar(data)
        val author = AuthorInfo(
            nickname = nickname,
            avatar = avatar,
            uniqueId = data.safeGet("unique_id") ?: data.safeGet("user_id") ?: data.safeGet("uid"),
            followerCount = data.safeGet("follower_count")?.toLongOrNull(),
            totalFavorited = data.safeGet("total_favorited")?.toLongOrNull()
        )
        AppLogger.d(TAG, "豆包解析到作者: ${author.nickname}, 头像: ${author.avatar != null}")

        val statsObj = data.safeGetObject("statistics") ?: data.safeGetObject("counts")
        val statistics = if (statsObj != null) {
            Statistics(
                playCount = (statsObj.safeGet("play_count") ?: statsObj.safeGet("view_count"))?.toLongOrNull() ?: 0L,
                diggCount = (statsObj.safeGet("digg_count") ?: statsObj.safeGet("like_count"))?.toLongOrNull() ?: 0L,
                commentCount = (statsObj.safeGet("comment_count"))?.toLongOrNull() ?: 0L,
                shareCount = (statsObj.safeGet("share_count"))?.toLongOrNull() ?: 0L,
                collectCount = (statsObj.safeGet("collect_count"))?.toLongOrNull() ?: 0L
            ).also {
                AppLogger.d(TAG, "豆包解析到统计数据: 点赞=${it.diggCount}")
            }
        } else {
            AppLogger.d(TAG, "豆包未找到统计数据")
            null
        }

        val musicObj = data.safeGetObject("music")
        val music = if (musicObj != null) {
            MusicInfo(
                title = musicObj.safeGet("title") ?: musicObj.safeGet("name"),
                author = musicObj.safeGet("author") ?: musicObj.safeGet("artist"),
                cover = musicObj.safeGet("cover") ?: musicObj.safeGet("avatar"),
                url = musicObj.safeGet("url") ?: musicObj.safeGet("play_url")
            )
        } else null

        val images = mutableListOf<String>()
        if (data.has("images") && data.get("images").isJsonArray) {
            data.getAsJsonArray("images").forEach { element ->
                try {
                    when {
                        element.isJsonPrimitive -> images.add(element.asString)
                        element.isJsonObject -> {
                            val url = element.asJsonObject.safeGet("url")
                                ?: element.asJsonObject.safeGet("src")
                                ?: element.asJsonObject.safeGet("urlDefault")
                            if (url != null) images.add(url)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        AppLogger.d(TAG, "豆包解析到图片: ${images.size} 张")

        val videoUrl = extractVideoUrlFlexible(data)
        AppLogger.d(TAG, "豆包解析到视频URL: ${videoUrl != null}, 值: ${videoUrl?.take(80) ?: "null"}")

        val actualType = if (images.isNotEmpty()) ContentType.ALBUM else ContentType.VIDEO
        val finalVideoUrl = if (actualType == ContentType.ALBUM) null else videoUrl

        return ParseResult(
            type = actualType,
            title = data.safeGet("title") ?: data.safeGet("desc") ?: "",
            desc = data.safeGet("desc"),
            cover = data.safeGet("cover") ?: data.safeGet("dynamic_cover"),
            author = author,
            videoUrl = finalVideoUrl,
            videoUrls = emptyList(),
            images = images,
            music = music,
            statistics = statistics,
            platform = Platform.DOUBAO,
            imageCount = images.size
        )
    }

    private fun parseApi1(url: String): ParseResult {
        val apiUrl = "https://apione.apibyte.cn/api/video?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案一实际请求URL: $apiUrl")
        return parseDouyinResponse(fetchJson(apiUrl, "PP视频解析"), url)
    }

    private fun parseApi2(url: String): ParseResult {
        val apiUrl = "https://api.xhus.cn/api/douyin?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案二实际请求URL: $apiUrl")
        return parseDouyinResponse(fetchJson(apiUrl, "柠檬解析"), url)
    }

    private fun parseApi3(url: String): ParseResult {
        val apiUrl = "https://api.bugpk.com/api/douyin?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案三实际请求URL: $apiUrl")
        return parseDouyinResponse(fetchJson(apiUrl, "初梦科技"), url)
    }

    private fun parseApi4(url: String): ParseResult {
        val apiUrl = "https://apis.jxcxin.cn/api/douyin?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案四实际请求URL: $apiUrl")
        return parseApiStoreResponse(fetchJson(apiUrl, "API Store"), url)
    }

    private fun parseApi5(url: String): ParseResult {
        val apiUrl = "https://api.xinyew.cn/api/douyinjx?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案五实际请求URL: $apiUrl")
        return parseXinyeResponse(fetchJson(apiUrl, "新野API"), url)
    }

    private fun parseApi6(url: String): ParseResult {
        val apiUrl = "https://api.mmp.cc/api/Jiexi?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        AppLogger.d(TAG, "方案六实际请求URL: $apiUrl")
        return parseYuanmengResponse(fetchJson(apiUrl, "远梦API"), url)
    }
}
