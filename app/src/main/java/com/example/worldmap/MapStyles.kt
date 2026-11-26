package com.example.worldmap

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Оформление карты: адрес стиля задаёт, откуда брать данные и как их рисовать. */
data class MapStyle(val titleRes: Int, val url: String)

/** Бесплатные стили сервиса OpenFreeMap (данные OpenStreetMap). */
val MAP_STYLES = listOf(
    MapStyle(R.string.style_liberty, "https://tiles.openfreemap.org/styles/liberty"),
    MapStyle(R.string.style_bright, "https://tiles.openfreemap.org/styles/bright"),
    MapStyle(R.string.style_positron, "https://tiles.openfreemap.org/styles/positron"),
    MapStyle(R.string.style_dark, "https://tiles.openfreemap.org/styles/dark")
)

private const val PIN_SOURCE = "pin-source"
private const val PIN_LAYER = "pin-layer"

/** Подписи на карте по-русски; где русского названия нет — латиницей или как в оригинале. */
fun Style.useRussianLabels() {
    for (layer in layers) {
        if (layer !is SymbolLayer) continue
        // Меняем только подписи названий; номера дорог (поле ref) не трогаем.
        val current = layer.textField.expression?.toString() ?: continue
        if (!current.contains("name")) continue
        layer.setProperties(
            PropertyFactory.textField(
                Expression.coalesce(
                    Expression.get("name:ru"),
                    Expression.get("name:latin"),
                    Expression.get("name")
                )
            )
        )
    }
}

/** Слой с красной меткой выбранного места. Добавляется заново после каждой смены стиля. */
fun Style.addPinLayer() {
    addSource(GeoJsonSource(PIN_SOURCE))
    addLayer(
        CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(Color.parseColor("#FF3B30")),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
    )
}

/** Ставит метку в точку; null — убирает метку. */
fun Style.showPin(place: Place?) {
    val source = getSourceAs<GeoJsonSource>(PIN_SOURCE) ?: return
    val features = if (place == null) emptyList<Feature>()
    else listOf(Feature.fromGeometry(Point.fromLngLat(place.lon, place.lat)))
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}
