package com.example.worldmap

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var infoCard: View
    private lateinit var infoTitle: TextView
    private lateinit var infoAddress: TextView
    private lateinit var infoCoordinates: TextView

    private var map: MapLibreMap? = null
    private var selected: Place? = null
    private var styleIndex = 0

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Номер последнего запроса: ответы на более старые запросы игнорируются. */
    @Volatile
    private var latestRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация библиотеки карты обязательна до setContentView.
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        progress = findViewById(R.id.progress)
        infoCard = findViewById(R.id.infoCard)
        infoTitle = findViewById(R.id.infoTitle)
        infoAddress = findViewById(R.id.infoAddress)
        infoCoordinates = findViewById(R.id.infoCoordinates)

        styleIndex = getPreferences(Context.MODE_PRIVATE)
            .getInt(KEY_STYLE, 0)
            .coerceIn(0, MAP_STYLES.lastIndex)

        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFailLoadingMapListener {
            Toast.makeText(this, R.string.error_map, Toast.LENGTH_LONG).show()
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            if (savedInstanceState == null) readyMap.cameraPosition = worldCamera()
            val margin = dp(12)
            readyMap.uiSettings.setCompassMargins(0, dp(84), margin, 0)
            readyMap.addOnMapClickListener { point ->
                describePoint(point)
                true
            }
            applyStyle()
        }

        findViewById<ImageButton>(R.id.searchButton).setOnClickListener { search() }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search()
                true
            } else {
                false
            }
        }
        findViewById<ImageButton>(R.id.zoomInButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<ImageButton>(R.id.zoomOutButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        findViewById<ImageButton>(R.id.worldButton).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(worldCamera()), ANIMATION_MS)
        }
        findViewById<ImageButton>(R.id.styleButton).setOnClickListener { chooseStyle() }
        findViewById<ImageButton>(R.id.closeCardButton).setOnClickListener { select(null) }
        infoCoordinates.setOnClickListener { copyCoordinates() }
    }

    private fun worldCamera(): CameraPosition = CameraPosition.Builder()
        .target(LatLng(30.0, 30.0))
        .zoom(1.0)
        .bearing(0.0)
        .tilt(0.0)
        .build()

    private fun applyStyle() {
        val readyMap = map ?: return
        readyMap.setStyle(Style.Builder().fromUri(MAP_STYLES[styleIndex].url)) { style ->
            style.useRussianLabels()
            style.addPinLayer()
            style.showPin(selected)
        }
    }

    private fun chooseStyle() {
        val titles = MAP_STYLES.map { getString(it.titleRes) }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.style_dialog_title)
            .setSingleChoiceItems(titles, styleIndex) { dialog, which ->
                dialog.dismiss()
                if (which != styleIndex) {
                    styleIndex = which
                    getPreferences(Context.MODE_PRIVATE).edit().putInt(KEY_STYLE, which).apply()
                    applyStyle()
                }
            }
            .show()
    }

    /** Поиск по названию: запрос к API, затем список найденных мест. */
    private fun search() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        request(GeoApi.searchUrl(query)) { body ->
            val places = GeoApi.parseSearch(body)
            when (places.size) {
                0 -> Toast.makeText(this, R.string.nothing_found, Toast.LENGTH_SHORT).show()
                1 -> flyTo(places[0])
                else -> showResults(places)
            }
        }
    }

    private fun showResults(places: List<Place>) {
        val titles = places.map { it.address.ifEmpty { it.name } }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.results_title)
            .setItems(titles) { _, which -> flyTo(places[which]) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun flyTo(place: Place) {
        select(place)
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), place.zoom),
            ANIMATION_MS
        )
    }

    /** Нажатие на карту: ставим метку и спрашиваем у API, что находится в этой точке. */
    private fun describePoint(point: LatLng) {
        val zoom = map?.cameraPosition?.zoom ?: return
        val lat = point.latitude
        val lon = GeoApi.normalizeLon(point.longitude)
        val pending = Place(getString(R.string.loading_place), "", lat, lon, zoom)
        select(pending)
        request(GeoApi.reverseUrl(lat, lon, zoom)) { body ->
            val found = GeoApi.parseReverse(body)
            // Метка остаётся там, куда нажали, а не в центре найденного объекта.
            select(
                found?.copy(lat = lat, lon = lon)
                    ?: pending.copy(name = getString(R.string.unknown_place))
            )
        }
    }

    private fun request(url: String, onSuccess: (String) -> Unit) {
        val number = ++latestRequest
        progress.visibility = View.VISIBLE
        executor.execute {
            // Пока ждали очереди, пользователь мог нажать ещё раз: старый запрос не отправляем.
            if (number != latestRequest) return@execute
            val result = try {
                Result.success(GeoClient.get(url))
            } catch (e: IOException) {
                Result.failure(e)
            }
            mainHandler.post {
                if (isFinishing || number != latestRequest) return@post
                progress.visibility = View.GONE
                result.onSuccess(onSuccess).onFailure {
                    if (selected?.name == getString(R.string.loading_place)) {
                        select(selected?.copy(name = getString(R.string.unknown_place)))
                    }
                    Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Показывает карточку и метку выбранного места; null — скрывает их. */
    private fun select(place: Place?) {
        selected = place
        map?.style?.showPin(place)
        if (place == null) {
            // Крестик на карточке отменяет и ещё не пришедший ответ.
            latestRequest++
            progress.visibility = View.GONE
            infoCard.visibility = View.GONE
            return
        }
        infoCard.visibility = View.VISIBLE
        infoTitle.text = place.name
        infoAddress.text = place.address
        val showAddress = place.address.isNotEmpty() && place.address != place.name
        infoAddress.visibility = if (showAddress) View.VISIBLE else View.GONE
        infoCoordinates.text = GeoApi.formatCoordinates(place.lat, place.lon)
    }

    private fun copyCoordinates() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("coordinates", infoCoordinates.text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // MapView должен получать все события жизненного цикла экрана.
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        executor.shutdown()
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val KEY_STYLE = "style"
        const val ANIMATION_MS = 1500
    }
}
