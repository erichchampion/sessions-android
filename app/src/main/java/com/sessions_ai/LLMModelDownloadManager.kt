package com.sessions_ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class LLMModelDownloadManager(private val context: Context) {

    fun getModelFile(config: LLMModelConfiguration): File {
        return File(context.filesDir, config.localFileName)
    }

    fun isModelDownloaded(config: LLMModelConfiguration): Boolean {
        val file = getModelFile(config)
        if (!file.exists()) return false

        val currentSize = file.length()
        val minSize = (config.expectedFileSize * (1.0 - config.fileSizeTolerance)).toLong()
        val maxSize = (config.expectedFileSize * (1.0 + config.fileSizeTolerance)).toLong()

        return currentSize in minSize..maxSize
    }

    suspend fun downloadModel(config: LLMModelConfiguration): Flow<DownloadState> = flow {
        val targetFile = getModelFile(config)
        
        if (isModelDownloaded(config)) {
            emit(DownloadState.Completed)
            return@flow
        }

        emit(DownloadState.Downloading(0f))

        val urlStr = "https://huggingface.co/${config.repository}/resolve/main/${config.fileName}"
        
        
        var currentUrlStr = urlStr
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        var connected = false

        while (!connected && redirectCount < 5) {
            connection = URL(currentUrlStr).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false // Manually handle cross-domain
            connection.connect()

            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrlStr = location
                        redirectCount++
                    } else {
                        break // Cannot follow redirect
                    }
                }
                in 200..299 -> {
                    connected = true
                }
                else -> {
                    break // Break on errors
                }
            }
        }

        if (connection != null && connected) {
            val fileLength = connection.contentLengthLong

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var count: Int
                    var totalRead: Long = 0
                    var lastEmitTime: Long = 0

                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        totalRead += count

                        // Throttle emits to avoid overwhelming the UI
                        val currentTime = System.currentTimeMillis()
                        if (fileLength > 0 && currentTime - lastEmitTime > 100) {
                            val progress = totalRead.toFloat() / fileLength.toFloat()
                            emit(DownloadState.Downloading(progress))
                            lastEmitTime = currentTime
                        }
                    }
                }
            }
            
            if (isModelDownloaded(config)) {
                emit(DownloadState.Completed)
            } else {
                emit(DownloadState.Error("Download incomplete: file size mismatch. Possible network drop or out of storage."))
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
        } else {
            val code = connection?.responseCode ?: -1
            emit(DownloadState.Error("Server returned HTTP $code"))
        }
    }.catch { e ->
        e.printStackTrace()
        emit(DownloadState.Error(e.message ?: "Unknown download error"))
        // Clean up partial file
        val targetFile = getModelFile(config)
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }.flowOn(Dispatchers.IO)
}
