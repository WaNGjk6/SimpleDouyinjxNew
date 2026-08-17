package top.jk666.douyinjiexi.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object Downloader {

    private const val TAG = "Downloader"
    private const val PROGRESS_THROTTLE_MS = 200L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadAndAwait(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String,
        subpath: String = "DouyinJieXi",
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val safeSubpath = subpath.replace(Regex("[/\\\\:*?\"<>|]"), "").ifBlank { "DouyinJieXi" }
        val baseDir = when {
            mimeType.startsWith("video") -> Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio") -> Environment.DIRECTORY_MUSIC
            mimeType.startsWith("image") -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        AppLogger.d(TAG, "准备下载: URL=${url.take(80)}")
        AppLogger.d(TAG, "下载参数: fileName=$fileName, mimeType=$mimeType, dir=$baseDir/$safeSubpath")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            AppLogger.d(TAG, "下载响应: HTTP ${response.code}, Content-Length=${response.header("Content-Length") ?: "未知"}")

            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载HTTP失败: ${response.code}")
                response.close()
                return@withContext false
            }

            val body = response.body ?: run {
                AppLogger.e(TAG, "下载响应体为空")
                return@withContext false
            }

            val totalBytes = body.contentLength()
            AppLogger.d(TAG, "文件总大小: ${if (totalBytes > 0) "${totalBytes / 1024}KB" else "未知"}")

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadWithMediaStore(context, fileName, mimeType, safeSubpath, baseDir, body.byteStream(), totalBytes, onProgress)
            } else {
                downloadWithFileApi(fileName, safeSubpath, baseDir, body.byteStream(), totalBytes, onProgress)
            }

            response.close()

            if (success) {
                AppLogger.d(TAG, "下载完成: $baseDir/$safeSubpath/$fileName")
            } else {
                AppLogger.e(TAG, "下载写入失败: $fileName")
            }
            return@withContext success
        } catch (e: java.net.SocketTimeoutException) {
            AppLogger.e(TAG, "下载超时: ${e.message}", e)
            return@withContext false
        } catch (e: java.net.UnknownHostException) {
            AppLogger.e(TAG, "下载网络不可达: ${e.message}", e)
            return@withContext false
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载异常: ${e.message}", e)
            return@withContext false
        }
    }

    private fun downloadWithMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        subpath: String,
        baseDir: String,
        inputStream: java.io.InputStream,
        totalBytes: Long,
        onProgress: ((Int) -> Unit)?
    ): Boolean {
        AppLogger.d(TAG, "使用 MediaStore API (Android ${Build.VERSION.SDK_INT})")

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$subpath")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = when {
            mimeType.startsWith("video") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mimeType.startsWith("audio") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            mimeType.startsWith("image") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, contentValues)
        if (uri == null) {
            AppLogger.e(TAG, "MediaStore insert 返回 null")
            return false
        }

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                writeStream(inputStream, outputStream, totalBytes, onProgress)
            } ?: run {
                AppLogger.e(TAG, "MediaStore openOutputStream 返回 null")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            AppLogger.d(TAG, "MediaStore 写入成功: $uri")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "MediaStore 写入异常: ${e.message}", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadWithFileApi(
        fileName: String,
        subpath: String,
        baseDir: String,
        inputStream: java.io.InputStream,
        totalBytes: Long,
        onProgress: ((Int) -> Unit)?
    ): Boolean {
        AppLogger.d(TAG, "使用 File API (Android ${Build.VERSION.SDK_INT})")

        val rootDir = when (baseDir) {
            Environment.DIRECTORY_MOVIES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            Environment.DIRECTORY_MUSIC -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            Environment.DIRECTORY_PICTURES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        val targetDir = File(rootDir, subpath)
        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            AppLogger.d(TAG, "创建目录: ${targetDir.absolutePath}, 成功=$created")
        }

        val targetFile = File(targetDir, fileName)
        AppLogger.d(TAG, "目标文件: ${targetFile.absolutePath}")

        return try {
            FileOutputStream(targetFile).use { outputStream ->
                writeStream(inputStream, outputStream, totalBytes, onProgress)
            }
            AppLogger.d(TAG, "File API 写入成功: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "File API 写入异常: ${e.message}", e)
            targetFile.delete()
            false
        }
    }

    private fun writeStream(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((Int) -> Unit)?
    ) {
        val bufferedInput = BufferedInputStream(inputStream, 8192)
        val buffer = ByteArray(8192)
        var downloadedBytes = 0L
        var lastProgressReportMs = 0L
        var bytesRead: Int

        while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            if (onProgress != null && totalBytes > 0) {
                val now = System.currentTimeMillis()
                if (now - lastProgressReportMs >= PROGRESS_THROTTLE_MS) {
                    val progress = (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
                    onProgress(progress)
                    lastProgressReportMs = now
                }
            }
        }

        outputStream.flush()

        if (onProgress != null) {
            onProgress(100)
        }

        AppLogger.d(TAG, "流写入完成: ${downloadedBytes / 1024}KB")
    }

    fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp4") -> "video/mp4"
            fileName.endsWith(".mp3") -> "audio/mpeg"
            fileName.endsWith(".m4a") -> "audio/mp4"
            fileName.endsWith(".flac") -> "audio/flac"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".png") -> "image/png"
            else -> "*/*"
        }
    }
}
