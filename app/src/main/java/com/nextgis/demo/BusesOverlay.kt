package com.nextgis.demo

import android.content.Context
import android.graphics.*
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplibui.api.Overlay
import com.nextgis.maplibui.api.OverlayItem
import com.nextgis.maplibui.mapui.MapViewOverlays

class BusesOverlay(context: Context, map: MapViewOverlays) : Overlay(context, map) {
    private var items: MutableList<OverlayItem> = arrayListOf()
    private val marker = BitmapFactory.decodeResource(mContext.resources, R.drawable.ic_bus_grey600_18dp)

    init {
        items.add(OverlayItem(mMapViewOverlays.map, coordinates[0].x, coordinates[0].y, marker))
        items.add(OverlayItem(mMapViewOverlays.map, coordinates[1].x, coordinates[1].y, marker))
    }

    override fun draw(canvas: Canvas?, mapDrawable: MapDrawable?) {
        for (i in 0 until items.size) {
            items[i].setCoordinatesFromWGS(coordinates[i].x, coordinates[i].y)
            drawOverlayItem(canvas, items[i])
        }
    }

    override fun drawOnPanning(canvas: Canvas?, currentMouseOffset: PointF?) {
        for (item in items) {
            drawOnPanning(canvas, currentMouseOffset, item)
        }
    }

    override fun drawOnZooming(canvas: Canvas?, currentFocusLocation: PointF?, scale: Float) {
        for (item in items) {
            drawOnZooming(canvas, currentFocusLocation, scale, item, false)
        }
    }

    companion object {
        val coordinates = arrayListOf(GeoPoint(131.890756, 43.033386), GeoPoint(131.889368, 43.025636))
    }

    fun defaultColor() {
        for (item in items)
            item.marker = marker
    }

    fun selectBus(envelope: GeoEnvelope): OverlayItem? {
        for (i in 0 until items.size) {
            val coordinates = items[i].getCoordinates(GeoConstants.CRS_WEB_MERCATOR)
            if (envelope.contains(coordinates)) {
                val marker = marker.copy(Bitmap.Config.ARGB_8888, true)
                items[i].marker = applyColorFilter(marker)
                mMapViewOverlays.postInvalidate()
                return items[i]
            }
        }
        return null
    }

    private fun applyColorFilter(marker: Bitmap): Bitmap {
        val canvas = Canvas(marker)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val filter = PorterDuffColorFilter(Color.BLUE, PorterDuff.Mode.SRC_ATOP)
        paint.colorFilter = filter
        canvas.drawBitmap(marker, 0f, 0f, paint)
        return marker
    }

}
