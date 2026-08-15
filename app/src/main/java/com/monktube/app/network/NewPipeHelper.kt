package com.monktube.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.HttpURLConnection
import java.net.URL

object SimpleDownloader : Downloader() {
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            request.headers().forEach { (k, v) -> setRequestProperty(k, v.joinToString(", ")) }
            connectTimeout = 15000
            readTimeout = 15000
        }
        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else ""
        return org.schabi.newpipe.extractor.downloader.Response(responseCode, conn.responseMessage, conn.headerFields, responseBody, null)
    }
}

object NewPipeHelper {
    init {
        NewPipe.init(SimpleDownloader)
    }

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        streamInfo.videoStreams.firstOrNull()?.content ?: streamInfo.audioStreams.firstOrNull()?.content
    }
}

