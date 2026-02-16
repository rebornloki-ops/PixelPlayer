package com.theveloper.pixelplay.data.netease

import android.net.Uri
import com.theveloper.pixelplay.utils.LogUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Netease Cloud Music audio.
 *
 * Resolves `netease://{songId}` URIs by fetching temporary streaming URLs
 * from the Netease API and proxying the audio data to ExoPlayer.
 *
 * Follows the same architectural pattern as [TelegramStreamProxy] using Ktor CIO.
 */
@Singleton
class NeteaseStreamProxy @Inject constructor(
    private val repository: NeteaseRepository,
    private val okHttpClient: OkHttpClient
) {
    private var server: ApplicationEngine? = null
    private var actualPort: Int = 0

    // Cache of resolved streaming URLs (they expire, so we track timestamp)
    private val urlCache = ConcurrentHashMap<Long, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long) {
        // Netease URLs typically expire in ~20 minutes, re-fetch after 15
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 15 * 60 * 1000
    }

    fun isReady(): Boolean = actualPort > 0

    fun getProxyUrl(songId: Long): String {
        if (actualPort == 0) {
            Timber.w("NeteaseStreamProxy: getProxyUrl called but actualPort is 0")
            return ""
        }
        return "http://127.0.0.1:$actualPort/netease/$songId"
    }

    /**
     * Parse a `netease://` URI and return the proxy URL.
     * Returns null if the URI is not a valid Netease URI.
     */
    fun resolveNeteaseUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "netease") return null
        val songId = uri.host?.toLongOrNull() ?: return null
        return getProxyUrl(songId)
    }

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                server = createServer(freePort)
                server!!.start(wait = false)
                actualPort = freePort
                Timber.d("NeteaseStreamProxy started on port $actualPort")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NeteaseStreamProxy")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        urlCache.clear()
        Timber.d("NeteaseStreamProxy stopped")
    }

    private fun createServer(port: Int): ApplicationEngine {
        return embeddedServer(CIO, port = port) {
            routing {
                get("/netease/{songId}") {
                    val songId = call.parameters["songId"]?.toLongOrNull()
                    if (songId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Song ID")
                        return@get
                    }

                    try {
                        val streamUrl = getOrFetchStreamUrl(songId)
                        if (streamUrl == null) {
                            call.respond(HttpStatusCode.NotFound, "No stream URL available")
                            return@get
                        }

                        // Proxy the audio stream
                        val rangeHeader = call.request.headers["Range"]
                        val requestBuilder = Request.Builder().url(streamUrl)
                        if (rangeHeader != null) {
                            requestBuilder.header("Range", rangeHeader)
                        }

                        val response = withContext(Dispatchers.IO) {
                            okHttpClient.newCall(requestBuilder.build()).execute()
                        }
                        val body = response.body

                        if (body == null) {
                            call.respond(HttpStatusCode.BadGateway, "No response body")
                            return@get
                        }

                        val contentLength = response.header("Content-Length")
                        val contentRange = response.header("Content-Range")
                        val acceptRanges = response.header("Accept-Ranges")

                        if (response.code == 206) {
                            call.response.status(HttpStatusCode.PartialContent)
                        }
                        call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                        contentLength?.let { call.response.header("Content-Length", it) }
                        contentRange?.let { call.response.header("Content-Range", it) }

                        call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                            withContext(Dispatchers.IO) {
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(64 * 1024)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        writeFully(buffer, 0, bytesRead)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")) {
                            // Client disconnected, normal behavior
                        } else {
                            Timber.e(e, "Error streaming Netease song $songId")
                        }
                    }
                }
            }
        }
    }

    private suspend fun getOrFetchStreamUrl(songId: Long): String? {
        // Check cache first
        urlCache[songId]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }

        // Fetch fresh URL
        val result = repository.getSongUrl(songId)
        return result.getOrNull()?.also { url ->
            urlCache[songId] = CachedUrl(url, System.currentTimeMillis())
        }
    }
}
