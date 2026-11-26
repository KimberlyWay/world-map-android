package com.example.worldmap

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Чистые функции для API геокодирования Nominatim: собрать адрес запроса и разобрать ответ.
 * Без Android и сети — легко тестируются.
 */
object GeoApi {
    private const val BASE = "https://nominatim.openstreetmap.org"
    const val SEARCH_LIMIT = 5
    const val MIN_ZOOM = 1.5
    const val MAX_ZOOM = 17.0

    /** Прямое геокодирование: название → координаты. */
    fun searchUrl(query: String): String =
        "$BASE/search?format=jsonv2&limit=$SEARCH_LIMIT&accept-language=ru&q=" +
            URLEncoder.encode(query, "UTF-8")

    /** Обратное геокодирование: координаты → название. Чем сильнее приближена карта, тем подробнее ответ. */
    fun reverseUrl(lat: Double, lon: Double, mapZoom: Double): String {
        val detail = mapZoom.toInt().coerceIn(3, 18)
        return String.format(
            Locale.US,
            "%s/reverse?format=jsonv2&accept-language=ru&lat=%.6f&lon=%.6f&zoom=%d",
            BASE, lat, lon, detail
        )
    }

    fun parseSearch(json: String): List<Place> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { parsePlace(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** null — в этой точке нет объекта с названием (например, открытый океан) или ответ неверный. */
    fun parseReverse(json: String): Place? {
        return try {
            parsePlace(JSONObject(json))
        } catch (e: JSONException) {
            null
        }
    }

    private fun parsePlace(o: JSONObject): Place? {
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lon = o.optString("lon").toDoubleOrNull() ?: return null
        val address = o.optString("display_name")
        val name = o.optString("name").ifEmpty { address.substringBefore(',') }
        if (name.isEmpty()) return null
        return Place(name, address, lat, lon, zoomFor(o.optJSONArray("boundingbox")))
    }

    /** Масштаб по размеру объекта. boundingbox = [юг, север, запад, восток]. */
    fun zoomFor(box: JSONArray?): Double {
        if (box == null || box.length() < 4) return 12.0
        val latSpan = abs(box.optDouble(1) - box.optDouble(0))
        val lonSpan = abs(box.optDouble(3) - box.optDouble(2))
        val span = max(lonSpan, latSpan * 2)
        if (span.isNaN() || span <= 0.0) return MAX_ZOOM
        return (ln(360.0 / span) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Карту можно прокручивать по кругу, поэтому долготу приводим к диапазону -180…180. */
    fun normalizeLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    fun formatCoordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)
}
