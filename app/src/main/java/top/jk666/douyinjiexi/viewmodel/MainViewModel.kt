package top.jk666.douyinjiexi.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.jk666.douyinjiexi.api.AiAnalyzer
import top.jk666.douyinjiexi.api.DouyinApi
import top.jk666.douyinjiexi.api.MusicApi
import top.jk666.douyinjiexi.api.UpdateInfo
import top.jk666.douyinjiexi.api.UpdateManager
import top.jk666.douyinjiexi.model.*
import top.jk666.douyinjiexi.util.AppLogger
import top.jk666.douyinjiexi.util.Downloader
import top.jk666.douyinjiexi.util.PlatformDetector
import top.jk666.douyinjiexi.util.PlatformType
import top.jk666.douyinjiexi.util.SettingsManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _detectedPlatform = MutableStateFlow(PlatformType.UNKNOWN)
    val detectedPlatform: StateFlow<PlatformType> = _detectedPlatform

    private val _parseResult = MutableStateFlow<ParseResult?>(null)
    val parseResult: StateFlow<ParseResult?> = _parseResult

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _selectedMusicPlatform = MutableStateFlow(MusicPlatform.QQ)
    val selectedMusicPlatform: StateFlow<MusicPlatform> = _selectedMusicPlatform

    private val _musicInput = MutableStateFlow("")
    val musicInput: StateFlow<String> = _musicInput

    private val _musicResult = MutableStateFlow<MusicResult?>(null)
    val musicResult: StateFlow<MusicResult?> = _musicResult

    private val _musicSearchResults = MutableStateFlow<List<MusicSearchItem>>(emptyList())
    val musicSearchResults: StateFlow<List<MusicSearchItem>> = _musicSearchResults

    private val _isSearchingMusic = MutableStateFlow(false)
    val isSearchingMusic: StateFlow<Boolean> = _isSearchingMusic

    private val _selectedQuality = MutableStateFlow("standard")
    val selectedQuality: StateFlow<String> = _selectedQuality

    private val _aiAnalysisResult = MutableStateFlow<String?>(null)
    val aiAnalysisResult: StateFlow<String?> = _aiAnalysisResult

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing

    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _updateProgress = MutableStateFlow<Int?>(null)
    val updateProgress: StateFlow<Int?> = _updateProgress

    fun onInputChange(text: String) {
        _inputText.value = text
        _detectedPlatform.value = PlatformDetector.detect(text)
    }

    fun onMusicInputChange(text: String) {
        _musicInput.value = text
    }

    fun onMusicPlatformSelected(platform: MusicPlatform) {
        _selectedMusicPlatform.value = platform
        _musicResult.value = null
        _musicSearchResults.value = emptyList()
        _errorMessage.value = null
    }

    fun onQualitySelected(quality: String) {
        _selectedQuality.value = quality
    }

    fun parseLink() {
        val input = _inputText.value.trim()
        if (input.isEmpty()) {
            _errorMessage.value = "请输入链接"
            return
        }

        val detected = _detectedPlatform.value
        if (detected == PlatformType.UNKNOWN) {
            _errorMessage.value = "无法识别链接所属平台，请检查链接格式"
            return
        }

        if (!detected.isMediaPlatform) {
            _errorMessage.value = "此链接属于${detected.label}，请切换到音乐下载Tab"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _parseResult.value = null
            _aiAnalysisResult.value = null
            _isAiAnalyzing.value = false

            try {
                val extractedUrl = PlatformDetector.extractUrl(input)
                val result = when (detected) {
                    PlatformType.DOUYIN -> {
                        val apiEnabled = SettingsManager.getApiEnabledList(getApplication())
                        DouyinApi.parseDouyinWithFallback(extractedUrl, apiEnabled)
                    }
                    PlatformType.KUAISHOU -> DouyinApi.parseKuaishou(extractedUrl)
                    PlatformType.XHS -> DouyinApi.parseXhs(extractedUrl)
                    PlatformType.DOUBAO -> DouyinApi.parseDoubao(extractedUrl)
                    PlatformType.OTHER_MEDIA -> DouyinApi.parseOtherMedia(extractedUrl)
                    else -> throw Exception("不支持的平台")
                }
                _parseResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "解析失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchMusic() {
        val input = _musicInput.value.trim()
        if (input.isEmpty()) {
            _errorMessage.value = "请输入歌曲名或链接"
            return
        }

        viewModelScope.launch {
            _isSearchingMusic.value = true
            _errorMessage.value = null
            _musicResult.value = null
            _musicSearchResults.value = emptyList()

            try {
                if (_selectedMusicPlatform.value == MusicPlatform.QQ) {
                    val songId = MusicApi.extractQQMusicId(input)
                    if (songId != null) {
                        _musicResult.value = MusicApi.parseQQMusic(songId)
                    } else {
                        _errorMessage.value = "无法识别QQ音乐链接/ID，请输入正确的QQ音乐链接或数字ID"
                    }
                } else {
                    val songId = MusicApi.extractNeteaseId(input)
                    if (songId != null) {
                        _musicResult.value = MusicApi.getNeteaseDetail(songId, _selectedQuality.value)
                    } else {
                        _musicSearchResults.value = MusicApi.searchNetease(input)
                        if (_musicSearchResults.value.isEmpty()) {
                            _errorMessage.value = "未找到相关歌曲，请尝试其他关键词"
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "音乐解析失败: ${e.message}"
            } finally {
                _isSearchingMusic.value = false
            }
        }
    }

    fun selectMusicItem(songId: String) {
        viewModelScope.launch {
            _isSearchingMusic.value = true
            _errorMessage.value = null

            try {
                _musicResult.value = MusicApi.getNeteaseDetail(songId, _selectedQuality.value)
                _musicSearchResults.value = emptyList()
            } catch (e: Exception) {
                _errorMessage.value = "获取歌曲详情失败: ${e.message}"
            } finally {
                _isSearchingMusic.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _aiAnalysisResult.value = null
        _isAiAnalyzing.value = false
    }

    fun dismissUpdate() {
        _hasUpdate.value = false
        _updateInfo.value = null
        _updateProgress.value = null
    }

    fun checkForUpdate() {
        AppLogger.d(TAG, "触发应用更新检查")
        viewModelScope.launch {
            try {
                val packageInfo = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                AppLogger.d(TAG, "当前版本号: $currentVersionCode")
                val updateInfo = UpdateManager.checkUpdate(currentVersionCode)
                if (updateInfo != null) {
                    _updateInfo.value = updateInfo
                    _hasUpdate.value = true
                    AppLogger.d(TAG, "发现新版本: v${updateInfo.versionName}")
                } else {
                    AppLogger.d(TAG, "当前已是最新版本")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "版本号获取异常: ${e.message}")
            }
        }
    }

    fun startUpdate() {
        val info = _updateInfo.value
        if (info == null) {
            AppLogger.e(TAG, "更新中止: updateInfo 为 null")
            return
        }
        AppLogger.d(TAG, "开始更新: v${info.versionName}")
        viewModelScope.launch {
            _updateProgress.value = 0
            try {
                val smartUrl = UpdateManager.getSmartDownloadUrl(info)
                AppLogger.d(TAG, "智能选择的下载链接: $smartUrl")
                val apkFile = UpdateManager.downloadApk(
                    url = smartUrl,
                    context = getApplication(),
                    fileName = "聚合解析-${info.versionName}.apk",
                    onProgress = { progress ->
                        _updateProgress.value = progress
                    }
                )
                if (apkFile != null) {
                    AppLogger.d(TAG, "APK下载成功，开始安装")
                    _updateProgress.value = 100
                    UpdateManager.installApk(getApplication(), apkFile)
                } else {
                    AppLogger.e(TAG, "APK下载失败")
                    _errorMessage.value = "更新包下载失败，请重试"
                    _updateProgress.value = null
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新异常: ${e.message}", e)
                _errorMessage.value = "更新失败: ${e.message}"
                _updateProgress.value = null
            }
        }
    }

    fun triggerAiAnalysis() {
        val errorMsg = _errorMessage.value
        if (errorMsg.isNullOrEmpty()) {
            AppLogger.d(TAG, "AI分析中止: 无错误信息")
            return
        }
        AppLogger.d(TAG, "触发AI分析: $errorMsg")
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            _aiAnalysisResult.value = null
            try {
                val result = AiAnalyzer.analyzeError(errorMsg)
                _aiAnalysisResult.value = result
                AppLogger.d(TAG, "AI分析完成: ${result.take(50)}...")
            } catch (e: Exception) {
                AppLogger.e(TAG, "AI分析异常: ${e.message}", e)
                _aiAnalysisResult.value = "🤖 哎呀，AI 脑子也短路了，请直接查看运行日志吧~"
            } finally {
                _isAiAnalyzing.value = false
            }
        }
    }

    fun downloadVideo() {
        AppLogger.d(TAG, "触发下载操作: downloadVideo")
        val result = _parseResult.value
        if (result == null) {
            AppLogger.e(TAG, "下载中止: parseResult 为 null")
            _errorMessage.value = "无解析结果可下载"
            return
        }
        val videoUrl = result.videoUrl
        if (videoUrl.isNullOrEmpty()) {
            AppLogger.e(TAG, "下载中止: videoUrl 为空 | type=${result.type}, images=${result.images.size}, livePhotos=${result.livePhotos.size}")
            _errorMessage.value = "无视频链接可下载"
            return
        }
        AppLogger.d(TAG, "视频URL: ${videoUrl.take(80)}")

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = "正在连接..."
            try {
                val subpath = SettingsManager.getDownloadSubpath(getApplication())
                val platformName = result.platform.displayName
                val fileName = "${platformName}_${System.currentTimeMillis()}.mp4"
                AppLogger.d(TAG, "开始下载视频: fileName=$fileName, subpath=$subpath")
                val success = Downloader.downloadAndAwait(
                    context = getApplication(),
                    url = videoUrl,
                    fileName = fileName,
                    mimeType = "video/mp4",
                    subpath = subpath,
                    onProgress = { progress ->
                        _downloadProgress.value = "正在下载 $progress%"
                    }
                )
                AppLogger.d(TAG, "视频下载结果: success=$success")
                if (success) {
                    Toast.makeText(getApplication(), "视频已保存到 Movies/$subpath", Toast.LENGTH_SHORT).show()
                } else {
                    AppLogger.e(TAG, "视频下载失败: downloadAndAwait 返回 false")
                    _errorMessage.value = "下载失败，请重试"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "视频下载异常: ${e.message}", e)
                _errorMessage.value = "下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }

    fun downloadVideoByUrl(url: String) {
        AppLogger.d(TAG, "触发下载操作: downloadVideoByUrl, url=${url.take(80)}")
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = "正在连接..."
            try {
                val subpath = SettingsManager.getDownloadSubpath(getApplication())
                val platformName = _detectedPlatform.value.label
                val fileName = "${platformName}_实况_${System.currentTimeMillis()}.mp4"
                AppLogger.d(TAG, "开始下载实况视频: fileName=$fileName")
                val success = Downloader.downloadAndAwait(
                    context = getApplication(),
                    url = url,
                    fileName = fileName,
                    mimeType = "video/mp4",
                    subpath = subpath,
                    onProgress = { progress ->
                        _downloadProgress.value = "正在下载 $progress%"
                    }
                )
                AppLogger.d(TAG, "实况视频下载结果: success=$success")
                if (success) {
                    Toast.makeText(getApplication(), "视频已保存到 Movies/$subpath", Toast.LENGTH_SHORT).show()
                } else {
                    AppLogger.e(TAG, "实况视频下载失败")
                    _errorMessage.value = "下载失败，请重试"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "实况视频下载异常: ${e.message}", e)
                _errorMessage.value = "下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }

    fun downloadImage(imageUrl: String) {
        AppLogger.d(TAG, "触发下载操作: downloadImage, url=${imageUrl.take(80)}")
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = "正在连接..."
            try {
                val subpath = SettingsManager.getDownloadSubpath(getApplication())
                val platformName = _detectedPlatform.value.label
                val fileName = "${platformName}_${System.currentTimeMillis()}.jpg"
                AppLogger.d(TAG, "开始下载图片: fileName=$fileName")
                val success = Downloader.downloadAndAwait(
                    context = getApplication(),
                    url = imageUrl,
                    fileName = fileName,
                    mimeType = "image/jpeg",
                    subpath = subpath,
                    onProgress = { progress ->
                        _downloadProgress.value = "正在下载 $progress%"
                    }
                )
                AppLogger.d(TAG, "图片下载结果: success=$success")
                if (success) {
                    Toast.makeText(getApplication(), "图片已保存到 Pictures/$subpath", Toast.LENGTH_SHORT).show()
                } else {
                    AppLogger.e(TAG, "图片下载失败")
                    _errorMessage.value = "下载失败，请重试"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "图片下载异常: ${e.message}", e)
                _errorMessage.value = "下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }

    fun downloadAllImages() {
        AppLogger.d(TAG, "触发下载操作: downloadAllImages")
        val result = _parseResult.value
        if (result == null) {
            AppLogger.e(TAG, "下载中止: parseResult 为 null")
            _errorMessage.value = "无解析结果可下载"
            return
        }
        val images = result.images
        if (images.isEmpty()) {
            AppLogger.e(TAG, "下载中止: 图片列表为空 | type=${result.type}, livePhotos=${result.livePhotos.size}")
            _errorMessage.value = "无图片可下载"
            return
        }
        AppLogger.d(TAG, "准备批量下载 ${images.size} 张图片")

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = "准备下载 0/${images.size}..."
            try {
                val subpath = SettingsManager.getDownloadSubpath(getApplication())
                var successCount = 0
                val platformName = result.platform.displayName
                images.forEachIndexed { index, imageUrl ->
                    AppLogger.d(TAG, "下载图片 [${index + 1}/${images.size}]: ${imageUrl.take(60)}")
                    _downloadProgress.value = "正在下载 ${index + 1}/${images.size}..."
                    val fileName = "${platformName}_${System.currentTimeMillis()}_$index.jpg"
                    val success = Downloader.downloadAndAwait(
                        context = getApplication(),
                        url = imageUrl,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        subpath = subpath,
                        onProgress = { progress ->
                            _downloadProgress.value = "图片 ${index + 1}/${images.size}  $progress%"
                        }
                    )
                    if (success) successCount++
                    AppLogger.d(TAG, "图片 [${index + 1}/${images.size}] 结果: ${if (success) "成功" else "失败"}")
                }
                AppLogger.d(TAG, "批量下载完成: $successCount/${images.size} 成功")
                Toast.makeText(
                    getApplication(),
                    "已保存 $successCount/${images.size} 张图片",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量下载异常: ${e.message}", e)
                _errorMessage.value = "批量下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }

    fun downloadMusic() {
        AppLogger.d(TAG, "触发下载操作: downloadMusic")
        val result = _musicResult.value
        if (result == null) {
            AppLogger.e(TAG, "下载中止: musicResult 为 null")
            _errorMessage.value = "无音乐结果可下载"
            return
        }
        val url = result.url
        if (url.isNullOrEmpty()) {
            AppLogger.e(TAG, "下载中止: 音乐URL为空 | name=${result.name}, artist=${result.artist}")
            _errorMessage.value = "无音乐链接可下载"
            return
        }
        AppLogger.d(TAG, "音乐URL: ${url.take(80)}")

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = "正在连接..."
            try {
                val subpath = SettingsManager.getDownloadSubpath(getApplication())
                val platformName = result.platform.displayName
                val songName = result.name
                val artist = result.artist
                val extension = when {
                    url.contains(".flac") -> "flac"
                    url.contains(".m4a") -> "m4a"
                    else -> "mp3"
                }
                val fileName = "${platformName}_${artist}_${songName}.$extension"
                    .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                val mimeType = Downloader.getMimeType(fileName)
                AppLogger.d(TAG, "开始下载音乐: fileName=$fileName, mimeType=$mimeType")
                val success = Downloader.downloadAndAwait(
                    context = getApplication(),
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    subpath = subpath,
                    onProgress = { progress ->
                        _downloadProgress.value = "正在下载 $progress%"
                    }
                )
                AppLogger.d(TAG, "音乐下载结果: success=$success")
                if (success) {
                    Toast.makeText(getApplication(), "音乐已保存到 Music/$subpath", Toast.LENGTH_SHORT).show()
                } else {
                    AppLogger.e(TAG, "音乐下载失败")
                    _errorMessage.value = "下载失败，请重试"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "音乐下载异常: ${e.message}", e)
                _errorMessage.value = "下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }
}
