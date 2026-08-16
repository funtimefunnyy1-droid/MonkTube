package com.monktube.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object SimpleDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = URL(request.url())
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = httpMethod
            connectTimeout = 30000
            readTimeout = 30000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

            request.headers().forEach { (key, values) ->
                setRequestProperty(key, values.joinToString(", "))
            }

            request.dataToSend()?.let { data ->
                doOutput = true
                outputStream.use { it.write(data) }
            }
        }

        val responseCode = conn.responseCode
        val responseMessage = conn.responseMessage ?: ""
        val headers = conn.headerFields ?: emptyMap()

        val inputStream: InputStream? = try {
            if (responseCode in 200..299) conn.inputStream else conn.errorStream
        } catch (e: Exception) {
            null
        }

        val responseBody = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        return Response(responseCode, responseMessage, headers, responseBody, request.url())
    }
}

object NewPipeHelper {
    private var isInitialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!isInitialized) {
            try {
                NewPipe.init(SimpleDownloader, Localization.DEFAULT)
                isInitialized = true
            } catch (e: Throwable) {
                Log.e("NewPipeHelper", "Init error", e)
            }
        }
    }

    suspend fun getStreamUrl(queryOrId: String): String? = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val input = queryOrId.trim()
            if (input.isEmpty()) return@withContext null

            val videoUrl = when {
                // Full URL or standard watch link
                input.startsWith("http://") || input.startsWith("https://") -> input

                // 11-char Video ID
                input.length == 11 && !input.contains(" ") -> "https://www.youtube.com/watch?v=$input"

                // Search query
                else -> {
                    val searchHandler = ServiceList.YouTube.searchQHFactory.fromQuery(input)
                    val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, searchHandler)
                    val firstVideo = searchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .firstOrNull()

                    firstVideo?.url ?: "https://www.youtube.com/watch?v=$input"
                }
            }

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            // 1. Try progressive video streams
            val videoStream = streamInfo.videoStreams.firstOrNull { it.url != null }?.url
            if (!videoStream.isNullOrEmpty()) return@withContext videoStream

            // 2. Try audio-only streams
            val audioStream = streamInfo.audioStreams.firstOrNull { it.url != null }?.url
            if (!audioStream.isNullOrEmpty()) return@withContext audioStream

            // 3. Try video-only streams
            val videoOnlyStream = streamInfo.videoOnlyStreams.firstOrNull { it.url != null }?.url
            if (!videoOnlyStream.isNullOrEmpty()) return@withContext videoOnlyStream

            null
        } catch (e: Throwable) {
            Log.e("NewPipeHelper", "Failed to extract stream", e)
            null
        }
    }
}
