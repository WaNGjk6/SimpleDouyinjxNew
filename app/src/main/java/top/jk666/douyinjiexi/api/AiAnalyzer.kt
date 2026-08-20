package top.jk666.douyinjiexi.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.jk666.douyinjiexi.BuildConfig
import top.jk666.douyinjiexi.util.AppLogger
import java.util.concurrent.TimeUnit

object AiAnalyzer {

    private const val TAG = "AiAnalyzer"
    private const val API_URL = "https://newapi.jikai666.top/v1/chat/completions"
    // AI 接口 Key：已随构建内置于 BuildConfig（见 build.gradle.kts）
    private val API_KEY: String get() = BuildConfig.AI_API_KEY
    private const val MODEL_NAME = "THUDM/GLM-4-9B-0414"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private const val SYSTEM_PROMPT = """你是一个资深的安卓开发工程师和幽默贴心的客服。用户在使用视频提取App时遇到了报错。请用极度通俗、幽默的语言向普通用户解释这个技术报错的原因，并给出解决建议。字数控制在100字以内。"""

    suspend fun analyzeError(errorMessage: String): String = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 开始AI错误分析 =====")
        AppLogger.d(TAG, "错误信息: $errorMessage")
        try {
            val requestBody = buildRequestBody(errorMessage)
            AppLogger.d(TAG, "请求体: ${requestBody.take(500)}")
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(mediaType))
                .build()
            AppLogger.d(TAG, "发送AI请求: $API_URL")
            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "AI响应状态码: ${response.code}")
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无响应体"
                AppLogger.e(TAG, "AI请求失败: HTTP ${response.code}, 响应: $errorBody")
                return@withContext getFallbackMessage()
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                AppLogger.e(TAG, "AI响应体为空")
                return@withContext getFallbackMessage()
            }
            AppLogger.d(TAG, "AI响应体长度: ${responseBody.length}")
            AppLogger.d(TAG, "AI响应体前500字符: ${responseBody.take(500)}")
            return@withContext parseAiResponse(responseBody)
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI分析异常: ${e.javaClass.simpleName}: ${e.message}", e)
            return@withContext getFallbackMessage()
        }
    }

    private fun buildRequestBody(errorMessage: String): String {
        val body = JsonObject().apply {
            addProperty("model", MODEL_NAME)
            addProperty("temperature", 0.7)
            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "当前报错信息是：$errorMessage。请帮我分析一下。")
                })
            }
            add("messages", messages)
        }
        return gson.toJson(body)
    }

    private fun parseAiResponse(responseBody: String): String {
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices.get(0).asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                val content = message?.get("content")?.asString
                if (!content.isNullOrBlank()) {
                    AppLogger.d(TAG, "AI分析成功: ${content.take(100)}...")
                    return content.trim()
                }
            }
            AppLogger.e(TAG, "AI响应格式异常: $responseBody")
            getFallbackMessage()
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析AI响应失败: ${e.message}", e)
            getFallbackMessage()
        }
    }

    private fun getFallbackMessage(): String {
        return "🤖 哎呀，AI 脑子也短路了，请直接查看运行日志吧~"
    }
}
