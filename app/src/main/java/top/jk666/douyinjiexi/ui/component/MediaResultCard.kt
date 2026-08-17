package top.jk666.douyinjiexi.ui.component

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import top.jk666.douyinjiexi.model.AuthorInfo
import top.jk666.douyinjiexi.model.LivePhoto
import top.jk666.douyinjiexi.model.MusicInfo
import top.jk666.douyinjiexi.model.ParseResult
import top.jk666.douyinjiexi.ui.theme.DouyinPink
import top.jk666.douyinjiexi.util.PlatformType

private val CyberCardBg = Color(0xFF1E293B).copy(alpha = 0.7f)
private val CyberCyan = Color(0xFF00E5FF)
private val CyberBlue = Color(0xFF0072FF)
private val CyberTextPrimary = Color(0xFFF1F5F9)
private val CyberTextSecondary = Color(0xFF94A3B8)
private val CyberSurface = Color(0xFF0F172A).copy(alpha = 0.6f)

private val PrimaryGradient = Brush.horizontalGradient(
    listOf(CyberCyan, CyberBlue)
)
private val PrimaryGradientDisabled = Brush.horizontalGradient(
    listOf(Color.Gray, Color.DarkGray)
)

@Composable
fun MediaResultCard(
    result: ParseResult,
    platform: PlatformType,
    onDownloadVideo: () -> Unit,
    onDownloadImage: (String) -> Unit,
    onDownloadAllImages: () -> Unit,
    onDownloadVideoByUrl: (String) -> Unit,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    // 封面点击预览：视频 → 播放器；图集/实况 → 全屏查看
    var coverPreviewVideo by remember { mutableStateOf(false) }
    var coverPreviewImages by remember { mutableStateOf<Int?>(null) }

    fun copyLink() {
        val link = when {
            !result.videoUrl.isNullOrBlank() -> result.videoUrl
            result.images.isNotEmpty() -> result.images.firstOrNull()
            result.livePhotos.isNotEmpty() -> result.livePhotos.firstOrNull()?.imageUrl
            else -> null
        }
        if (link != null) {
            clipboardManager.setText(AnnotatedString(link))
            Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "暂无链接可复制", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText() {
        val sb = StringBuilder()
        if (result.title.isNotBlank()) sb.appendLine("标题：${result.title}")
        if (result.author.nickname.isNotBlank() && result.author.nickname != "未知作者") {
            sb.appendLine("作者：${result.author.nickname}")
        }
        result.statistics?.let { s ->
            if (s.playCount > 0) sb.appendLine("播放：${formatNum(s.playCount)}")
            if (s.diggCount > 0) sb.appendLine("点赞：${formatNum(s.diggCount)}")
            if (s.commentCount > 0) sb.appendLine("评论：${formatNum(s.commentCount)}")
            if (s.shareCount > 0) sb.appendLine("分享：${formatNum(s.shareCount)}")
            if (s.collectCount > 0) sb.appendLine("收藏：${formatNum(s.collectCount)}")
        }
        val text = sb.toString().trimEnd()
        clipboardManager.setText(AnnotatedString(text.ifEmpty { "暂无文案可复制" }))
        Toast.makeText(context, "文案已复制", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CyberCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
    ) {
        Column {
            val contentType = result.type.name
            val coverUrl = when {
                contentType == "ALBUM" || result.images.isNotEmpty() -> result.images.firstOrNull() ?: result.cover
                else -> result.cover
            }

            if (coverUrl != null) {
                CyberCoverSection(
                    coverUrl = coverUrl,
                    title = result.title,
                    author = result.author,
                    isPlayable = result.videoUrl != null,
                    onClick = {
                        when {
                            !result.videoUrl.isNullOrBlank() -> coverPreviewVideo = true
                            result.images.isNotEmpty() -> coverPreviewImages = 0
                            result.livePhotos.isNotEmpty() -> coverPreviewImages = 0
                        }
                    }
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Spacer(modifier = Modifier.height(2.dp))
                CyberStatsBar(result)

                result.music?.let { m ->
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberMusicBar(m)
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    contentType == "LIVE" || result.livePhotos.isNotEmpty() -> {
                        CyberActionButtons(
                            isVideo = false,
                            isDownloading = isDownloading,
                            onPrimary = onDownloadAllImages,
                            onSecondary = ::copyLink,
                            onTertiary = ::copyText
                        )
                    }
                    contentType == "ALBUM" || result.images.isNotEmpty() -> {
                        CyberActionButtons(
                            isVideo = false,
                            isDownloading = false,
                            onPrimary = onDownloadAllImages,
                            onSecondary = ::copyLink,
                            onTertiary = ::copyText
                        )
                    }
                    result.videoUrl != null -> {
                        CyberActionButtons(
                            isVideo = true,
                            isDownloading = isDownloading,
                            onPrimary = onDownloadVideo,
                            onSecondary = ::copyLink,
                            onTertiary = ::copyText
                        )
                    }
                }
            }

            when {
                contentType == "ALBUM" || result.images.isNotEmpty() -> {
                    CyberAlbumCarousel(
                        images = result.images,
                        onDownloadSingle = onDownloadImage
                    )
                }
                contentType == "LIVE" || result.livePhotos.isNotEmpty() -> {
                    CyberLivePhotoGrid(
                        livePhotos = result.livePhotos,
                        fallbackImages = result.images,
                        onDownloadImage = onDownloadImage,
                        onDownloadVideo = onDownloadVideoByUrl
                    )
                }
                result.videoUrl != null -> {
                    CyberVideoResultSection(
                        videoUrl = result.videoUrl!!,
                        coverUrl = result.cover,
                        onDownload = onDownloadVideo,
                        isDownloading = isDownloading
                    )
                }
            }
        }
    }

    // 封面点击触发的预览
    if (coverPreviewVideo && !result.videoUrl.isNullOrBlank()) {
        VideoPlayerDialog(
            videoUrl = result.videoUrl!!,
            onDismiss = { coverPreviewVideo = false }
        )
    }
    if (coverPreviewImages != null) {
        val previewImages = result.images.ifEmpty { result.livePhotos.map { it.imageUrl } }
        if (previewImages.isNotEmpty()) {
            FullScreenImageViewer(
                images = previewImages,
                initialPage = coverPreviewImages!!,
                onDismiss = { coverPreviewImages = null },
                onDownload = { url, _ -> onDownloadImage(url) }
            )
        }
    }
}

@Composable
private fun CyberCoverSection(
    coverUrl: String,
    title: String,
    author: AuthorInfo,
    isPlayable: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    if (author.avatar != null) {
                        AsyncImage(
                            model = author.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = author.nickname.take(1),
                            modifier = Modifier.align(Alignment.Center),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CyberCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = author.nickname,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!author.uniqueId.isNullOrBlank()) {
                        Text(
                            text = "@${author.uniqueId}",
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (title.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }

        if (isPlayable) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CyberActionButtons(
    isVideo: Boolean,
    isDownloading: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: () -> Unit
) {
    if (isVideo) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GradientPrimaryButton(
                    text = if (isDownloading) "⏳ 下载中..." else "⬇ 保存视频",
                    enabled = !isDownloading,
                    onClick = onPrimary
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedSecondaryButton(text = "🔗 复制链接", onClick = onSecondary)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FullWidthCyberPrimaryButton(
                text = "⬇ 一键保存全部",
                onClick = onPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedSecondaryButton(text = "📋 复制文案", onClick = onTertiary)
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedSecondaryButton(text = "🔗 复制链接", onClick = onSecondary)
                }
            }
        }
    }
}

@Composable
private fun CyberStatsBar(result: ParseResult) {
    val contentType = result.type.name
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (contentType == "LIVE" && result.livePhotos.isNotEmpty()) {
            Text(
                text = "🔴 实况 x${result.livePhotos.size}",
                fontSize = 12.sp,
                color = DouyinPink,
                fontWeight = FontWeight.Bold
            )
        } else if (contentType == "ALBUM" && result.images.isNotEmpty()) {
            Text(
                text = "🖼️ 图集 x${result.images.size}",
                fontSize = 12.sp,
                color = CyberCyan,
                fontWeight = FontWeight.Bold
            )
        }
        result.statistics?.let { s ->
            if (s.playCount > 0) Text(text = "▶️ ${formatNum(s.playCount)}", fontSize = 12.sp, color = CyberTextSecondary)
            if (s.diggCount > 0) Text(text = "❤️ ${formatNum(s.diggCount)}", fontSize = 12.sp, color = CyberTextSecondary)
            if (s.commentCount > 0) Text(text = "💬 ${formatNum(s.commentCount)}", fontSize = 12.sp, color = CyberTextSecondary)
            if (s.shareCount > 0) Text(text = "🔁 ${formatNum(s.shareCount)}", fontSize = 12.sp, color = CyberTextSecondary)
            if (s.collectCount > 0) Text(text = "⭐ ${formatNum(s.collectCount)}", fontSize = 12.sp, color = CyberTextSecondary)
        }
    }
}

@Composable
private fun CyberMusicBar(music: MusicInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF334155))
        ) {
            if (music.cover != null) {
                AsyncImage(
                    model = music.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🎵 ${music.title ?: "未知曲目"}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CyberTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (music.author != null) {
                Text(
                    text = music.author,
                    fontSize = 12.sp,
                    color = CyberTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CyberVideoResultSection(
    videoUrl: String,
    coverUrl: String?,
    onDownload: () -> Unit,
    isDownloading: Boolean
) {
    var showVideoPlayer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .background(Color.Black)
            .clickable { showVideoPlayer = true }
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isDownloading) Color.Gray else DouyinPink)
                .clickable(enabled = !isDownloading) { onDownload() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isDownloading) "下载中..." else "保存视频",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }

    if (showVideoPlayer) {
        VideoPlayerDialog(
            videoUrl = videoUrl,
            onDismiss = { showVideoPlayer = false }
        )
    }
}

@Composable
private fun VideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .align(Alignment.Center)
            )

            Text(
                text = "✕ 关闭",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CyberAlbumCarousel(
    images: List<String>,
    onDownloadSingle: (String) -> Unit
) {
    if (images.isEmpty()) return

    var fullScreenIndex by remember { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState(pageCount = { images.size })

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable { fullScreenIndex = page }
            ) {
                AsyncImage(
                    model = images[page],
                    contentDescription = "图片 ${page + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onDownloadSingle(images[page]) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⬇", fontSize = 14.sp, color = Color.White)
                }
            }
        }

        if (images.size > 1) {
            CyberPagerIndicator(
                pagerState = pagerState,
                pageCount = images.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }

    if (fullScreenIndex != null) {
        FullScreenImageViewer(
            images = images,
            initialPage = fullScreenIndex!!,
            onDismiss = { fullScreenIndex = null },
            onDownload = { url, _ -> onDownloadSingle(url) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CyberPagerIndicator(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = pagerState.currentPage == index
            val animatedWidth by animateFloatAsState(
                targetValue = if (isActive) 24f else 8f,
                animationSpec = tween(300),
                label = "dotWidth"
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = tween(300),
                label = "dotAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(animatedWidth.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CyberCyan.copy(alpha = animatedAlpha))
            )
        }
    }
}

@Composable
private fun CyberLivePhotoGrid(
    livePhotos: List<LivePhoto>,
    fallbackImages: List<String>,
    onDownloadImage: (String) -> Unit,
    onDownloadVideo: (String) -> Unit
) {
    var fullScreenIndex by remember { mutableStateOf<Int?>(null) }
    val displayImages = livePhotos.map { it.imageUrl }.ifEmpty { fallbackImages }

    CyberPhotoGrid(
        livePhotos = livePhotos,
        fallbackImages = fallbackImages,
        onDownloadImage = onDownloadImage,
        onDownloadLiveVideo = { url -> onDownloadVideo(url) },
        onPhotoClick = { index -> fullScreenIndex = index }
    )

    if (fullScreenIndex != null) {
        FullScreenImageViewer(
            images = displayImages,
            initialPage = fullScreenIndex!!,
            onDismiss = { fullScreenIndex = null },
            onDownload = { url, _ -> onDownloadImage(url) }
        )
    }
}

@Composable
private fun CyberPhotoGrid(
    livePhotos: List<LivePhoto>,
    fallbackImages: List<String>,
    onDownloadImage: (String) -> Unit,
    onDownloadLiveVideo: (String) -> Unit,
    onPhotoClick: (Int) -> Unit,
    live: Boolean = livePhotos.isNotEmpty()
) {
    val photos = if (live) livePhotos else fallbackImages.map { LivePhoto(it) }
    val chunked = photos.chunked(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chunked.forEachIndexed { rowIndex, rowPhotos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowPhotos.forEachIndexed { colIndex, photo ->
                    val globalIndex = rowIndex * 3 + colIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { onPhotoClick(globalIndex) }
                    ) {
                        AsyncImage(
                            model = photo.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { onDownloadImage(photo.imageUrl) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "⬇", fontSize = 11.sp, color = Color.White)
                            }
                            if (live && photo.videoUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyberCyan.copy(alpha = 0.7f))
                                        .clickable { onDownloadLiveVideo(photo.videoUrl) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "▶", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (live && photo.videoUrl != null) "实况" else "图",
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                repeat(3 - rowPhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FullScreenImageViewer(
    images: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit,
    onDownload: ((String, Int) -> Unit)? = null
) {
    val viewerPager = rememberPagerState(initialPage = initialPage, pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = viewerPager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = "图片 ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✕ 关闭",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "${viewerPager.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    onDownload?.let { download ->
                        Text(
                            text = "📥 保存",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DouyinPink.copy(alpha = 0.8f))
                                .clickable {
                                    download(images[viewerPager.currentPage], viewerPager.currentPage)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    } ?: Spacer(modifier = Modifier.width(60.dp))
                }
            }
        }
    }
}

@Composable
private fun GradientPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        elevation = null,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .drawBehind {
                drawRoundRect(
                    brush = if (enabled) PrimaryGradient else PrimaryGradientDisabled,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.height / 2f, size.height / 2f
                    )
                )
            },
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            style = TextStyle(letterSpacing = 1.sp)
        )
    }
}

@Composable
private fun OutlinedSecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CyberCyan.copy(alpha = 0.08f),
            contentColor = CyberCyan
        ),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            style = TextStyle(letterSpacing = 0.5.sp)
        )
    }
}

@Composable
private fun FullWidthCyberPrimaryButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        elevation = null,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .drawBehind {
                drawRoundRect(
                    brush = PrimaryGradient,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.height / 2f, size.height / 2f
                    )
                )
            },
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            style = TextStyle(letterSpacing = 1.sp)
        )
    }
}

private fun formatNum(num: Long?): String {
    if (num == null) return "0"
    return when {
        num >= 100_000_000 -> "%.1f亿".format(num / 100_000_000.0)
        num >= 10_000 -> "%.1f万".format(num / 10_000.0)
        else -> num.toString()
    }
}
