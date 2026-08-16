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

data class ExtractedMedia(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val streamUrl: String?
)

object SimpleDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 30000
            readTimeout = 30000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            request.headers().forEach { (k, v) ->
                setRequestProperty(k, v.joinToString(", "))
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
    private fun ensureInit() {
        if (!isInitialized) {
            try {
                NewPipe.init(SimpleDownloader, Localization.DEFAULT)
                isInitialized = true
            } catch (e: Throwable) {
                Log.e("NewPipeHelper", "Init failed", e)
            }
        }
    }

    suspend fun getStreamUrl(queryOrId: String): ExtractedMedia? = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            val input = queryOrId.trim()
            if (input.isEmpty()) return@withContext null

            var targetUrl = when {
                input.startsWith("http://") || input.startsWith("https://") -> input
                input.length == 11 && !input.contains(" ") -> "https://www.youtube.com/watch?v=$input"
                else -> null
            }

            if (targetUrl == null) {
                val searchHandler = ServiceList.YouTube.searchQHFactory.fromQuery(input)
                val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, searchHandler)
                val topItem = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull()
                targetUrl = topItem?.url ?: "https://www.youtube.com/watch?v=$input"
            }

            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, targetUrl)

            val streamUrl = streamInfo.audioStreams.firstOrNull { it.url != null }?.url
                ?: streamInfo.videoStreams.firstOrNull { it.url != null }?.url
                ?: streamInfo.videoOnlyStreams.firstOrNull { it.url != null }?.url

            ExtractedMedia(
                videoId = streamInfo.id ?: input,
                title = streamInfo.name ?: "Unknown Title",
                uploader = streamInfo.uploaderName ?: "YouTube",
                thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url ?: "https://img.youtube.com/vi/${streamInfo.id}/hqdefault.jpg",
                streamUrl = streamUrl
            )
        } catch (e: Throwable) {
            Log.e("NewPipeHelper", "Extraction error", e)
            null
        }
    }
}
