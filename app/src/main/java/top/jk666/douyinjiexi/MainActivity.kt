package top.jk666.douyinjiexi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import top.jk666.douyinjiexi.ui.screen.MainScreen
import top.jk666.douyinjiexi.ui.theme.DouyinJieXiTheme
import top.jk666.douyinjiexi.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            DouyinJieXiTheme {
                MainScreen(viewModel = viewModel)
            }
        }

        viewModel.checkForUpdate()
    }

    companion object {
        private const val REQUEST_STORAGE = 1001
    }
}
