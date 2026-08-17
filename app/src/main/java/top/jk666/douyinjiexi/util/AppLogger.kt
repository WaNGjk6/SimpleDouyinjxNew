package top.jk666.douyinjiexi.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogger {

    private const val MAX_LOGS = 200
    private const val TAG_APP = "DouyinJieXi"

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val logBuffer = CopyOnWriteArrayList<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun d(tag: String, message: String) {
        val entry = formatEntry("D", tag, message)
        addEntry(entry)
        Log.d(TAG_APP, "[$tag] $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val entry = formatEntry("E", tag, message)
        addEntry(entry)
        if (throwable != null) {
            Log.e(TAG_APP, "[$tag] $message", throwable)
        } else {
            Log.e(TAG_APP, "[$tag] $message")
        }
    }

    fun clear() {
        logBuffer.clear()
        _logs.value = emptyList()
    }

    private fun formatEntry(level: String, tag: String, message: String): String {
        val time = timeFormat.format(Date())
        return "[$time] [$level/$tag] $message"
    }

    private fun addEntry(entry: String) {
        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOGS) {
            logBuffer.removeAt(0)
        }
        _logs.value = logBuffer.toList()
    }
}
