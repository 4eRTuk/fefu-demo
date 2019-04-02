package com.nextgis.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.Layer
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.api.MapViewEventListener
import com.nextgis.maplibui.mapui.MapViewOverlays
import com.nextgis.maplibui.overlay.CurrentLocationOverlay
import com.nextgis.maplibui.util.ConstantsUI
import kotlin.random.Random

class MainActivity : AppCompatActivity(), MapViewEventListener {
    override fun onLayersReordered() {
    }

    override fun onLayerDrawFinished(id: Int, percent: Float) {
    }

    override fun onSingleTapUp(event: MotionEvent?) {
        event?.let {
            val tolerance = resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP.toDouble()
            val dMinX = event.x - tolerance
            val dMaxX = event.x + tolerance
            val dMinY = event.y - tolerance
            val dMaxY = event.y + tolerance
            val envelope = GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)
            val mapEnv = mapView?.screenToMap(envelope) ?: return

            if (overlay!!.isVisible) {
                overlay!!.selectBus(mapEnv)?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        findViewById<ImageView>(R.id.avatar).imageTintList = ColorStateList.valueOf(Color.BLUE)
                    }

                    findViewById<View>(R.id.people).visibility = View.VISIBLE
                    findViewById<ProgressBar>(R.id.people).progress = Random(System.currentTimeMillis()).nextInt(0, 100)
                    findViewById<TextView>(R.id.title).text = getString(R.string.bus)
                    findViewById<TextView>(R.id.category).text = ""
                    findViewById<TextView>(R.id.description).text = getString(R.string.bus_desc)
                    findViewById<View>(R.id.info).visibility = View.VISIBLE

                    mapView?.let { map ->
                        it.getCoordinates(GeoConstants.CRS_WEB_MERCATOR)?.let { location ->
                            val center = location.copy() as GeoPoint
                            center.y -= 1000
                            map.setZoomAndCenter(map.zoomLevel, center)
                        }
                    }
                    return
                }
            }
        }

    }

    override fun onLayerAdded(id: Int) {
    }

    override fun onLayerDeleted(id: Int) {
    }

    override fun onLayerChanged(id: Int) {
    }

    override fun onExtentChanged(zoom: Float, center: GeoPoint?) {
    }

    override fun onLayerDrawStarted() {
    }

    override fun onLongPress(event: MotionEvent?) {
    }

    override fun panStart(e: MotionEvent?) {
    }

    override fun panMoveTo(e: MotionEvent?) {
    }

    override fun panStop() {
    }

    var mapView: MapViewOverlays? = null
    var preferences: SharedPreferences? = null
    var overlay: BusesOverlay? = null
    var authorized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val app = application as GISApplication
        val map = app.map as MapDrawable
        mapView = MapViewOverlays(this, map)
        findViewById<FrameLayout>(R.id.map).addView(mapView)
        findViewById<Button>(R.id.zoom_in).setOnClickListener {
            mapView!!.zoomOut()
        }
        findViewById<FloatingActionButton>(R.id.location).setOnClickListener {
            locatePosition()
        }
        overlay = BusesOverlay(this, mapView!!)
        mapView!!.addOverlay(overlay)

        val locationOverlay = CurrentLocationOverlay(this, mapView!!)
        mapView!!.addOverlay(locationOverlay)
        locationOverlay.startShowingCurrentLocation()

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        authorized = preferences!!.getBoolean("authorized", false)
        setCenter()

        if (!preferences!!.getBoolean("signed", false)) {
            val intent = Intent(this, SigninActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<ImageButton>(R.id.close).setOnClickListener {
            findViewById<View>(R.id.info).visibility = View.GONE
            overlay?.defaultColor()
        }

    }

    override fun onStart() {
        super.onStart()
        mapView?.addListener(this)
    }


    override fun onStop() {
        super.onStop()
        mapView?.let {
            val point = it.mapCenter
            preferences?.edit()?.putFloat("zoom", it.zoomLevel)
                ?.putFloat("scroll_x", point.x.toFloat())
                ?.putFloat("scroll_y", point.y.toFloat())
                ?.apply()
        }
        mapView?.removeListener(this)
    }

    private fun setCenter() {
        mapView?.let {
            val mapZoom = preferences?.getFloat("zoom", 15f)
            val x = preferences?.getFloat("scroll_x", 14682143.82f)
            val y = preferences?.getFloat("scroll_y", 5316524.04f)
            val mapScrollX = x?.toDouble() ?: 0.0
            val mapScrollY = y?.toDouble() ?: 0.0

            it.setZoomAndCenter(mapZoom ?: it.minZoom, GeoPoint(mapScrollX, mapScrollY))
        }
    }


    fun locatePosition() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (!isPermissionGranted(permission)) {
            val permissions = arrayOf(permission)
            ActivityCompat.requestPermissions(this, permissions, 5)
        } else {
            setLocation()
        }
    }

    @SuppressLint("MissingPermission")
    fun setLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (location != null) {
            val point = GeoPoint()
            point.setCoordinates(location.longitude, location.latitude)
            point.crs = GeoConstants.CRS_WGS84

            if (point.project(GeoConstants.CRS_WEB_MERCATOR)) {
                mapView?.panTo(point)
            }
        } else {
            Toast.makeText(this, R.string.error_no_location, Toast.LENGTH_SHORT).show()
        }
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            5 -> locatePosition()
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when(item?.itemId) {
            R.id.action_signout -> {
                val preferences = PreferenceManager.getDefaultSharedPreferences(this)
                preferences.edit().remove("signed").remove("authorized").apply()
                val app = application as? IGISApplication
                app?.getAccount(SigninActivity.AUTHORITY)?.let { app.removeAccount(it) }
                (app?.map as MapDrawable).delete()
                signin()
                true
            }
            R.id.action_layers -> {
                val layers = arrayOf("Магазины и автоматы", "Кафе и рестораны", "Автобусы")
                val checked = BooleanArray(layers.size)

                val app = application as? IGISApplication
                val map = app?.map as MapDrawable?

                val shops = map?.getLayerByName(SigninActivity.LAYERS[0].second) as Layer
                val vending = map.getLayerByName(SigninActivity.LAYERS[1].second) as Layer
                shops.let { checked[0] = it.isVisible }
                val cafe = map.getLayerByName(SigninActivity.LAYERS[2].second) as Layer
                cafe.let { checked[1] = it.isVisible }
                overlay?.let { checked[2] = it.isVisible }

                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.track_list)
                    .setMultiChoiceItems(layers, checked) { _, which, selected ->
                        checked[which] = selected
                    }
                    .setPositiveButton(R.string.ok) { _, _ ->
                        shops.let { it.isVisible = checked[0] }
                        vending.let { it.isVisible = checked[0] }
                        cafe.let { it.isVisible = checked[1] }
                        overlay?.setVisibility(checked[2])
                    }

                builder.create().show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signin() {
        val intent = Intent(this, SigninActivity::class.java)
        startActivity(intent)
        finish()
    }

}
