package top.jk666.douyinjiexi.api

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.jk666.douyinjiexi.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateLog: String,
    val downloadUrlGlobal: String,
    val downloadUrlChina: String,
    val forceUpdate: Boolean
) {
    val downloadUrl: String
        get() = downloadUrlChina.ifBlank { downloadUrlGlobal }
}

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val UPDATE_URL = "https://update.jikai666.top/simpledouyinjx/update.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val gson = Gson()

    suspend fun checkIsOverseasNetwork(): Boolean = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 网络环境嗅探 =====")
        try {
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .get()
                .build()
            AppLogger.d(TAG, "发送嗅探请求: https://www.google.com/generate_204")
            val response = probeClient.newCall(request).execute()
            AppLogger.d(TAG, "嗅探响应状态码: ${response.code}")
            val isOverseas = response.code == 204 || response.code == 200
            AppLogger.d(TAG, "网络嗅探结果: 海外=$isOverseas")
            return@withContext isOverseas
        } catch (e: SocketTimeoutException) {
            AppLogger.d(TAG, "网络嗅探超时，判定为国内网络")
            return@withContext false
        } catch (e: Exception) {
            AppLogger.d(TAG, "网络嗅探异常: ${e.javaClass.simpleName}: ${e.message}，判定为国内网络")
            return@withContext false
        }
    }

    suspend fun checkUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 检查应用更新 =====")
        AppLogger.d(TAG, "当前版本号: $currentVersionCode")
        AppLogger.d(TAG, "更新配置URL: $UPDATE_URL")
        try {
            val request = Request.Builder()
                .url(UPDATE_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .get()
                .build()
            AppLogger.d(TAG, "发送更新检查请求...")
            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "更新检查响应状态码: ${response.code}")
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "更新检查失败: HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                AppLogger.e(TAG, "更新检查响应体为空")
                return@withContext null
            }
            AppLogger.d(TAG, "更新配置JSON长度: ${body.length}")
            AppLogger.d(TAG, "更新配置内容: ${body.take(500)}")
            val json = gson.fromJson(body, JsonObject::class.java)
            val versionCode = json.get("versionCode")?.asInt ?: 0
            val versionName = json.get("versionName")?.asString ?: "未知"
            val updateLog = json.get("updateLog")?.asString ?: ""
            val downloadUrlGlobal = json.get("downloadUrlGlobal")?.asString
                ?: json.get("downloadUrl")?.asString
                ?: ""
            val downloadUrlChina = json.get("downloadUrlChina")?.asString
                ?: json.get("downloadUrl")?.asString
                ?: ""
            val forceUpdate = json.get("forceUpdate")?.asBoolean ?: false
            val updateInfo = UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                updateLog = updateLog,
                downloadUrlGlobal = downloadUrlGlobal,
                downloadUrlChina = downloadUrlChina,
                forceUpdate = forceUpdate
            )
            AppLogger.d(TAG, "解析到版本信息: v$versionName($versionCode), 强制更新=$forceUpdate")
            AppLogger.d(TAG, "全球链接: $downloadUrlGlobal")
            AppLogger.d(TAG, "国内链接: $downloadUrlChina")
            if (versionCode > currentVersionCode) {
                AppLogger.d(TAG, "发现新版本: $currentVersionCode -> $versionCode")
                return@withContext updateInfo
            } else {
                AppLogger.d(TAG, "当前已是最新版本")
                return@withContext null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新检查异常: ${e.javaClass.simpleName}: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getSmartDownloadUrl(updateInfo: UpdateInfo): String {
        AppLogger.d(TAG, "===== 智能选择下载链接 =====")
        val isOverseas = checkIsOverseasNetwork()
        val targetUrl = if (isOverseas) {
            AppLogger.d(TAG, "检测到海外/代理网络，使用全球链接")
            updateInfo.downloadUrlGlobal.ifBlank { updateInfo.downloadUrlChina }
        } else {
            AppLogger.d(TAG, "检测到国内网络，使用国内直链")
            updateInfo.downloadUrlChina.ifBlank { updateInfo.downloadUrlGlobal }
        }
        AppLogger.d(TAG, "网络嗅探结果：海外=$isOverseas，即将使用链接：$targetUrl")
        return targetUrl
    }

    // 下载到「公共下载目录 Download/」（卸载应用后文件仍在），否则 APK 落在应用私有缓存、卸载即被清掉、
    // 用户卸载后就找不到安装包。Android 10+ 用 MediaStore(免权限)，更老用文件路径(需 WRITE_EXTERNAL_STORAGE)。
    private fun resolveUpdateApkStream(context: Context, fileName: String): Pair<OutputStream, File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法在公共下载目录创建条目")
            val out = context.contentResolver.openOutputStream(uri) ?: throw Exception("无法打开下载输出流")
            // MediaStore 落盘路径通常就是 /storage/emulated/0/Download/<fileName>，供 FileProvider 安装使用
            return out to File(downloadsDir, fileName)
        } else {
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            return FileOutputStream(file) to file
        }
    }

    suspend fun downloadApk(
        url: String,
        context: Context,
        fileName: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "===== 开始下载APK(保存到公共下载目录) =====")
        AppLogger.d(TAG, "下载URL: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .get()
                .build()
            AppLogger.d(TAG, "发送下载请求...")
            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "下载响应状态码: ${response.code}")
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载失败: HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body
                ?: run { AppLogger.e(TAG, "下载响应体为空"); return@withContext null }
            val contentLength = body.contentLength()
            AppLogger.d(TAG, "文件大小: ${contentLength / 1024 / 1024}MB")
            val (outputStream, targetFile) = resolveUpdateApkStream(context, fileName)
            AppLogger.d(TAG, "保存到公共下载目录: ${targetFile.absolutePath}")
            outputStream.use { outer ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgress = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outer.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                                if (progress % 25 == 0) {
                                    AppLogger.d(TAG, "下载进度: $progress%")
                                }
                            }
                        }
                    }
                    outer.flush()
                }
            }
            AppLogger.d(TAG, "APK下载完成: ${targetFile.length() / 1024 / 1024}MB")
            return@withContext targetFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK下载异常: ${e.javaClass.simpleName}: ${e.message}", e)
            return@withContext null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        AppLogger.d(TAG, "===== 安装APK =====")
        AppLogger.d(TAG, "APK路径: ${apkFile.absolutePath}")
        AppLogger.d(TAG, "APK大小: ${apkFile.length() / 1024 / 1024}MB")
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            AppLogger.d(TAG, "FileProvider URI: $uri")
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            context.startActivity(intent)
            AppLogger.d(TAG, "已启动系统安装器")
        } catch (e: Exception) {
            AppLogger.e(TAG, "安装APK异常: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
