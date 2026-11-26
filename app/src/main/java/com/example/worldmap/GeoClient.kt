package com.example.worldmap

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP-запросы к Nominatim. Вызывать только из одного фонового потока. */
object GeoClient {
    // Правила сервиса: представиться в User-Agent и делать не чаще одного запроса в секунду.
    private const val USER_AGENT = "StudentWorldMap/1.0 (educational Android app)"
    private const val MIN_INTERVAL_MS = 1_100L
    private const val TIMEOUT_MS = 15_000

    private var lastRequestAt = 0L

    @Throws(IOException::class)
    fun get(url: String): String {
        val wait = lastRequestAt + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) SystemClock.sleep(wait)
        lastRequestAt = SystemClock.elapsedRealtime()

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
