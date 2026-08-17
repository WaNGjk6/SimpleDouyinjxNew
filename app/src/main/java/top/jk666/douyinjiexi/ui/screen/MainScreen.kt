package top.jk666.douyinjiexi.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import top.jk666.douyinjiexi.R
import top.jk666.douyinjiexi.util.SettingsManager
import top.jk666.douyinjiexi.viewmodel.MainViewModel

data class TabItem(
    val label: String,
    val icon: String
)

private val DeepCyan = Color(0xFF0F172A)
private val CyberCyan = Color(0xFF00E5FF)

private val backgroundResources = mapOf(
    "bg_main" to R.drawable.bg_main,
    "bg_main_2" to R.drawable.bg_main_2,
    "bg_main_3" to R.drawable.bg_main_3,
    "bg_main_4" to R.drawable.bg_main_4
)

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val backgroundImage by SettingsManager.getBackgroundImageFlow(context)
        .collectAsState(initial = "bg_main")

    val tabs = listOf(
        TabItem("视频/图文", "🎬"),
        TabItem("音乐下载", "🎵"),
        TabItem("设置", "⚙️")
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    val bgResId = backgroundResources[backgroundImage] ?: R.drawable.bg_main

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bgResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = DeepCyan.copy(alpha = 0.6f),
                    contentColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    tab.icon,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberCyan,
                                selectedTextColor = CyberCyan,
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray,
                                indicatorColor = CyberCyan.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> MediaExtractScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
                1 -> MusicDownloadScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
                2 -> Box(modifier = Modifier.padding(padding)) { SettingsScreen() }
            }
        }
    }
}
