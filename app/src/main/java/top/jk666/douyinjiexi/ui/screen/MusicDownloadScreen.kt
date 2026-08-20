package top.jk666.douyinjiexi.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import top.jk666.douyinjiexi.model.MusicPlatform
import top.jk666.douyinjiexi.ui.component.MusicResultCard
import top.jk666.douyinjiexi.ui.component.MusicSearchList
import top.jk666.douyinjiexi.ui.component.MusicSkeleton
import top.jk666.douyinjiexi.viewmodel.MainViewModel

private val CyberCyan = Color(0xFF00E5FF)
private val DeepPurple = Color(0xFF651FFF)
private val DarkInput = Color(0xFF121212)
private val CardBg = Color(0xFF1A1A24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDownloadScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val selectedMusicPlatform by viewModel.selectedMusicPlatform.collectAsState()
    val musicInput by viewModel.musicInput.collectAsState()
    val musicResult by viewModel.musicResult.collectAsState()
    val musicSearchResults by viewModel.musicSearchResults.collectAsState()
    val isSearchingMusic by viewModel.isSearchingMusic.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    if (downloadProgress != null) {
        MusicDownloadDialog(downloadProgress = downloadProgress)
    }

    val qualityOptions = listOf(
        "standard" to "标准",
        "exhigh" to "极高",
        "lossless" to "无损",
        "hires" to "Hi-Res",
        "jyeffect" to "超清母带",
        "sky" to "沉浸环绕声",
        "jymaster" to "高清臻音"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        MusicTitle()

        Spacer(modifier = Modifier.height(8.dp))

        MusicPlatformSelector(
            selectedPlatform = selectedMusicPlatform,
            onPlatformSelected = viewModel::onMusicPlatformSelected
        )

        Spacer(modifier = Modifier.height(4.dp))

        MusicInputField(
            value = musicInput,
            onValueChange = viewModel::onMusicInputChange,
            placeholder = if (selectedMusicPlatform == MusicPlatform.QQ)
                "输入QQ音乐链接或歌曲ID..."
            else
                "输入歌名搜索 / 网易云链接 / 歌曲ID..."
        )

        if (selectedMusicPlatform == MusicPlatform.NETEASE) {
            MusicQualitySelector(
                selectedQuality = selectedQuality,
                qualityOptions = qualityOptions,
                onQualitySelected = viewModel::onQualitySelected
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        MusicParseButton(
            isLoading = isSearchingMusic,
            enabled = musicInput.isNotBlank() && !isSearchingMusic,
            onClick = viewModel::searchMusic,
            buttonText = if (selectedMusicPlatform == MusicPlatform.QQ) "解析歌曲" else "搜索/解析"
        )

        errorMessage?.let { msg ->
            MusicErrorCard(
                message = msg,
                onDismiss = { viewModel.clearError() }
            )
        }

        if (isSearchingMusic) {
            MusicSkeleton()
        }

        AnimatedVisibility(
            visible = musicSearchResults.isNotEmpty() && !isSearchingMusic,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg.copy(alpha = 0.85f))
                    .padding(12.dp)
            ) {
                MusicSearchList(
                    items = musicSearchResults,
                    onItemClick = { viewModel.selectMusicItem(it.id) }
                )
            }
        }

        AnimatedVisibility(
            visible = musicResult != null && !isSearchingMusic,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
        ) {
            musicResult?.let { result ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg.copy(alpha = 0.85f))
                        .padding(16.dp)
                ) {
                    Column {
                        MusicResultCard(
                            result = result,
                            onDownload = viewModel::downloadMusic,
                            isDownloading = isDownloading,
                            selectedQuality = selectedQuality
                        )
                        result.url?.let { url ->
                            Spacer(modifier = Modifier.height(12.dp))
                            key(url) {
                                MusicPlayerCard(url = url, title = result.name)
                            }
                        }
                    }
                }
            }
        }

        if (musicResult == null && musicSearchResults.isEmpty() && !isSearchingMusic && errorMessage == null) {
            MusicEmptyState()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MusicTitle() {
    val titleBrush = Brush.linearGradient(
        colors = listOf(CyberCyan, Color(0xFFFF80AB))
    )

    Text(
        text = "音乐下载",
        style = TextStyle(
            brush = titleBrush,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(2f, 2f),
                blurRadius = 8f
            )
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MusicPlatformSelector(
    selectedPlatform: MusicPlatform,
    onPlatformSelected: (MusicPlatform) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(DarkInput.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(
                    if (selectedPlatform == MusicPlatform.QQ)
                        Brush.linearGradient(listOf(CyberCyan, DeepPurple))
                    else
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .clickable { onPlatformSelected(MusicPlatform.QQ) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "QQ音乐",
                fontWeight = if (selectedPlatform == MusicPlatform.QQ) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedPlatform == MusicPlatform.QQ) Color.White else Color.LightGray
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(
                    if (selectedPlatform == MusicPlatform.NETEASE)
                        Brush.linearGradient(listOf(CyberCyan, DeepPurple))
                    else
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                )
                .clickable { onPlatformSelected(MusicPlatform.NETEASE) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "网易云音乐",
                fontWeight = if (selectedPlatform == MusicPlatform.NETEASE) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedPlatform == MusicPlatform.NETEASE) Color.White else Color.LightGray
            )
        }
    }
}

@Composable
private fun MusicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50)
            ),
        placeholder = {
            Text(
                placeholder,
                color = Color.LightGray
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空",
                        tint = CyberCyan
                    )
                }
            }
        },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = DarkInput.copy(alpha = 0.75f),
            unfocusedContainerColor = DarkInput.copy(alpha = 0.75f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = CyberCyan,
            focusedPlaceholderColor = Color.LightGray,
            unfocusedPlaceholderColor = Color.LightGray
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        textStyle = TextStyle(color = Color.White)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicQualitySelector(
    selectedQuality: String,
    qualityOptions: List<Pair<String, String>>,
    onQualitySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = qualityOptions.find { it.first == selectedQuality }?.second ?: "标准",
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    "音质选择",
                    color = Color.LightGray
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50)
                ),
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = DarkInput.copy(alpha = 0.75f),
                unfocusedContainerColor = DarkInput.copy(alpha = 0.75f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.LightGray,
                unfocusedLabelColor = Color.LightGray
            ),
            textStyle = TextStyle(color = Color.White)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkInput.copy(alpha = 0.95f))
        ) {
            qualityOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (value == selectedQuality) CyberCyan else Color.White
                        )
                    },
                    onClick = {
                        onQualitySelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MusicParseButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    buttonText: String
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(CyberCyan, DeepPurple)
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) gradientBrush else Brush.linearGradient(
                        colors = listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "解析中...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            } else {
                Text(
                    buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun MusicErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    val errorRed = Color(0xFFE94057)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = errorRed.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(onClick = onDismiss) {
            Text(
                "关闭",
                color = CyberCyan
            )
        }
    }
}

@Composable
private fun MusicEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🎵",
                fontSize = 48.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "输入歌名或粘贴音乐链接",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 6f
                    )
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "支持QQ音乐 · 网易云音乐",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 6f
                    )
                )
            )
        }
    }
}

@Composable
private fun MusicDownloadDialog(downloadProgress: String?) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("下载中", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        },
        text = {
            Column {
                Text(
                    text = downloadProgress ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                val percentRegex = Regex("(\\d+)%")
                val match = percentRegex.find(downloadProgress ?: "")
                if (match != null) {
                    val percent = match.groupValues[1].toFloatOrNull()
                    if (percent != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = CyberCyan,
                            trackColor = CardBg
                        )
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = CardBg.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp)
    )
}

// 在线播放器：复用 Media3 ExoPlayer，播放/暂停、停止、进度+时间。外部用 key(url) 包裹，切歌自动重建。
@Composable
private fun MusicPlayerCard(url: String, title: String) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlayerReady = playbackState == Player.STATE_READY
                duration = player.duration.coerceAtLeast(0)
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    fun togglePlay() {
        if (!isPlayerReady) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        } else if (isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun stopPlay() {
        player.stop()
        player.clearMediaItems()
        position = 0
        duration = 0
    }

    Surface(
        color = DarkInput.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎵", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "在线试听",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { togglePlay() }) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        fontSize = 26.sp,
                        color = CyberCyan
                    )
                }
                IconButton(onClick = { stopPlay() }) {
                    Text(
                        text = "⏹",
                        fontSize = 24.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(position),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (duration > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = CyberCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val min = s / 60
    val sec = s % 60
    return String.format("%d:%02d", min, sec)
}
