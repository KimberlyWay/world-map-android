package com.example.worldmap

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoApiTest {
    @Test
    fun searchUrlEncodesQuery() {
        val url = GeoApi.searchUrl("Эйфелева башня & Co")
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?"))
        assertTrue(url.contains("q=%D0%AD"))
        assertTrue(url.endsWith("+%26+Co"))
    }

    @Test
    fun reverseUrlUsesDotAndLimitsDetail() {
        val world = GeoApi.reverseUrl(55.75, 37.62, 1.0)
        assertTrue(world.contains("lat=55.750000&lon=37.620000"))
        assertTrue(world.endsWith("zoom=3"))
        assertTrue(GeoApi.reverseUrl(0.0, 0.0, 21.0).endsWith("zoom=18"))
    }

    @Test
    fun parsesSearchResults() {
        val json = """[{"lat":"48.8582599","lon":"2.2945006","name":"Эйфелева башня",
            "display_name":"Эйфелева башня, Париж, Франция",
            "boundingbox":["48.8574753","48.8590453","2.2933119","2.2956897"]},
            {"name":"без координат"}]"""
        val places = GeoApi.parseSearch(json)
        assertEquals(1, places.size)
        assertEquals("Эйфелева башня", places[0].name)
        assertEquals(48.8582599, places[0].lat, 1e-9)
        assertTrue(places[0].zoom > 15)
    }

    @Test
    fun nameFallsBackToAddress() {
        val place = GeoApi.parseReverse("""{"lat":"1","lon":"2","name":"","display_name":"Улица, Город"}""")
        assertEquals("Улица", place?.name)
    }

    @Test
    fun badAnswersGiveNothing() {
        assertTrue(GeoApi.parseSearch("не json").isEmpty())
        assertTrue(GeoApi.parseSearch("[]").isEmpty())
        assertNull(GeoApi.parseReverse("""{"error":"Unable to geocode"}"""))
        assertNull(GeoApi.parseReverse("не json"))
    }

    @Test
    fun bigObjectsGetSmallZoom() {
        val country = GeoApi.zoomFor(JSONArray("""["41.1","82.0","-180","180"]"""))
        val city = GeoApi.zoomFor(JSONArray("""["55.49","56.02","36.8","37.97"]"""))
        assertEquals(GeoApi.MIN_ZOOM, country, 1e-9)
        assertTrue(city > 7 && city < 10)
        assertEquals(12.0, GeoApi.zoomFor(null), 1e-9)
    }

    @Test
    fun longitudeWrapsAround() {
        assertEquals(-170.0, GeoApi.normalizeLon(190.0), 1e-9)
        assertEquals(170.0, GeoApi.normalizeLon(-190.0), 1e-9)
        assertEquals(37.6, GeoApi.normalizeLon(397.6), 1e-9)
    }

    @Test
    fun coordinatesUseDot() {
        assertEquals("55.75000, -37.50000", GeoApi.formatCoordinates(55.75, -37.5))
    }
}
