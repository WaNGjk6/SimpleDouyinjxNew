package top.jk666.douyinjiexi.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.jk666.douyinjiexi.api.UpdateInfo
import top.jk666.douyinjiexi.ui.component.MediaResultCard
import top.jk666.douyinjiexi.util.PlatformType
import top.jk666.douyinjiexi.viewmodel.MainViewModel

private val CyberCyan = Color(0xFF00E5FF)
private val PinkAccent = Color(0xFFFF80AB)
private val DeepPurple = Color(0xFF651FFF)
private val DarkInput = Color(0xFF121212)
private val CardBg = Color(0xFF1A1A24)

@Composable
fun MediaExtractScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val inputText by viewModel.inputText.collectAsState()
    val detectedPlatform by viewModel.detectedPlatform.collectAsState()
    val parseResult by viewModel.parseResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val aiAnalysisResult by viewModel.aiAnalysisResult.collectAsState()
    val isAiAnalyzing by viewModel.isAiAnalyzing.collectAsState()
    val hasUpdate by viewModel.hasUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    if (downloadProgress != null) {
        CyberDownloadDialog(downloadProgress = downloadProgress)
    }

    if (hasUpdate && updateInfo != null) {
        CyberUpdateDialog(
            updateInfo = updateInfo!!,
            updateProgress = updateProgress,
            onUpdate = { viewModel.startUpdate() },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        FloatingTitle()

        Spacer(modifier = Modifier.height(8.dp))

        FloatingInputField(
            value = inputText,
            onValueChange = viewModel::onInputChange
        )

        Spacer(modifier = Modifier.height(4.dp))

        AnimatedVisibility(
            visible = detectedPlatform != PlatformType.UNKNOWN,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FloatingPlatformBadge(detectedPlatform)
        }

        Spacer(modifier = Modifier.height(4.dp))

        FloatingParseButton(
            isLoading = isLoading,
            enabled = inputText.isNotBlank() && !isLoading && detectedPlatform.isMediaPlatform,
            onClick = viewModel::parseLink
        )

        errorMessage?.let { msg ->
            FloatingErrorCard(
                message = msg,
                onDismiss = { viewModel.clearError() },
                onAiAnalyze = { viewModel.triggerAiAnalysis() },
                isAiAnalyzing = isAiAnalyzing,
                aiAnalysisResult = aiAnalysisResult
            )
        }

        if (isLoading) {
            FloatingLoadingAnimation()
        }

        AnimatedVisibility(
            visible = parseResult != null && !isLoading,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
        ) {
            parseResult?.let { result ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg.copy(alpha = 0.85f))
                        .padding(16.dp)
                ) {
                    MediaResultCard(
                        result = result,
                        platform = detectedPlatform,
                        onDownloadVideo = viewModel::downloadVideo,
                        onDownloadImage = viewModel::downloadImage,
                        onDownloadAllImages = viewModel::downloadAllImages,
                        onDownloadVideoByUrl = viewModel::downloadVideoByUrl,
                        isDownloading = isDownloading
                    )
                }
            }
        }

        if (parseResult == null && !isLoading && errorMessage == null) {
            FloatingEmptyState()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FloatingTitle() {
    val titleBrush = Brush.linearGradient(
        colors = listOf(CyberCyan, PinkAccent)
    )

    Text(
        text = "全能提取",
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
private fun FloatingInputField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

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
                "请粘贴分享链接...",
                color = Color.LightGray
            )
        },
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空",
                            tint = CyberCyan
                        )
                    }
                }
                IconButton(onClick = {
                    clipboardManager.getText()?.let { text ->
                        onValueChange(text.toString())
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "粘贴",
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
        maxLines = 3,
        textStyle = TextStyle(color = Color.White)
    )
}

@Composable
private fun FloatingPlatformBadge(platform: PlatformType) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (platform.isMediaPlatform)
            CyberCyan.copy(alpha = 0.15f)
        else
            Color(0xFFE94057).copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (platform.iconRes != 0) {
                Text("  ", fontSize = 14.sp)
            }
            Text(
                text = if (platform.isMediaPlatform) {
                    "已识别: ${platform.label}"
                } else {
                    "${platform.label} — 请切换到音乐下载Tab"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (platform.isMediaPlatform) CyberCyan else Color(0xFFE94057)
            )
        }
    }
}

@Composable
private fun FloatingParseButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
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
                    "开始解析",
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
private fun FloatingLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚡",
            fontSize = 48.sp,
            modifier = Modifier.offset(y = (-2).dp)
        )
        Text(
            text = "正在突破次元壁进行解析...",
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f
                )
            )
        )
    }
}

@Composable
private fun FloatingEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📱", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "粘贴链接，自动识别平台",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f
                    )
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "支持抖音 · 快手 · 小红书 · 豆包生图的无水印提取",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}

@Composable
private fun CyberDownloadDialog(downloadProgress: String?) {
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

@Composable
private fun FloatingErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onAiAnalyze: () -> Unit,
    isAiAnalyzing: Boolean,
    aiAnalysisResult: String?
) {
    val errorRed = Color(0xFFE94057)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = errorRed.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠️",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "哎呀，解析撞墙了",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = errorRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已生成错误报告。您可以在「设置-运行日志」中查看详细代码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                )
            ) {
                Text("关闭", fontSize = 14.sp)
            }

            Button(
                onClick = onAiAnalyze,
                modifier = Modifier.weight(1f),
                enabled = !isAiAnalyzing,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan.copy(alpha = 0.2f),
                    contentColor = CyberCyan,
                    disabledContainerColor = CyberCyan.copy(alpha = 0.1f),
                    disabledContentColor = CyberCyan.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
            ) {
                if (isAiAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CyberCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("思考中...", fontSize = 14.sp)
                } else {
                    Text("🤖 让 AI 帮我分析", fontSize = 14.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = aiAnalysisResult != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            aiAnalysisResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCyan.copy(alpha = 0.05f))
                        .border(
                            width = 1.dp,
                            color = CyberCyan.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "🤖",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "AI 分析结果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberUpdateDialog(
    updateInfo: UpdateInfo,
    updateProgress: Int?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(CyberCyan, DeepPurple)
    )

    AlertDialog(
        onDismissRequest = {
            if (updateProgress == null) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🚀",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "发现新版本",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "v${updateInfo.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (updateProgress == null) {
                    Text(
                        text = "更新日志：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = updateInfo.updateLog.ifBlank { "优化用户体验，修复已知问题" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    )

                    } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "正在下载新包：$updateProgress%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { (updateProgress ?: 0) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyberCyan,
                            trackColor = CardBg
                        )
                        if (updateProgress >= 100) {
                            Text(
                                text = "✅ 下载完成，正在拉起安装...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyberCyan
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (updateProgress == null) {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = gradientBrush, shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "立即升级",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (updateProgress == null && !updateInfo.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "稍后再说",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        },
        containerColor = CardBg.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp)
    )
}
