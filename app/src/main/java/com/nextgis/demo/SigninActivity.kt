package com.nextgis.demo

import android.Manifest
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.display.FieldStyleRule
import com.nextgis.maplib.display.RuleFeatureRenderer
import com.nextgis.maplib.display.SimpleFeatureRenderer
import com.nextgis.maplib.display.SimpleMarkerStyle
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.maplibui.service.LayerFillService

class SigninActivity : AppCompatActivity() {
    private var dialog: ProgressDialog? = null
    private var authorized = false
    private var receiver: BroadcastReceiver? = null
    private var total = LAYERS.size

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        findViewById<Button>(R.id.skip).setOnClickListener { load() }
        findViewById<Button>(R.id.signin).setOnClickListener { load(true) }
    }

    private fun signin() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putBoolean("authorized", authorized).apply()
        preferences.edit().putBoolean("signed", true).apply()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun load(authorized: Boolean = false) {
        this.authorized = authorized
        if (!PermissionUtil.hasPermission(this, Manifest.permission.WRITE_SYNC_SETTINGS)
            || !PermissionUtil.hasPermission(this, Manifest.permission.GET_ACCOUNTS)) {
            requestPermission()
            return
        }

        dialog = ProgressDialog(this)
        dialog?.isIndeterminate = true
        dialog?.setCancelable(false)
        dialog?.setMessage(getString(R.string.message_loading))
        dialog?.show()

        val fullUrl = FULL_URL
        val accountName = AUTHORITY
        val app = application as? IGISApplication
        app?.addAccount(accountName, fullUrl, Constants.NGW_ACCOUNT_GUEST, null, "ngw")?.let {
            if (!it) {
                Toast.makeText(this, R.string.error_auth, Toast.LENGTH_SHORT).show()
                app.getAccount(accountName)?.let { app.removeAccount(it) }
                dialog?.dismiss()
                return
            } else {
                layers()
            }
        }
    }

    companion object {
        const val AUTHORITY = "dvfu-demo.nextgis.com"
        const val FULL_URL = "http://$AUTHORITY"
        const val PERMISSIONS_CODE = 47
        const val INSTANCE = "http://${AUTHORITY}/resource/"
        val LAYERS = arrayListOf(
            Pair("$INSTANCE/7", "Магазины"),
            Pair("$INSTANCE/8", "Вендинговые автоматы"),
            Pair("$INSTANCE/13", "Кафе и рестораны")
        )
    }

    private fun requestPermission() {
        val permissions = arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.WRITE_SYNC_SETTINGS)
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var granted = requestCode == PERMISSIONS_CODE
        for (result in grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                granted = false

        if (granted)
            load(authorized)
        else
            Toast.makeText(this, R.string.error_auth, Toast.LENGTH_SHORT).show()
    }

    private fun layers() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action?.equals(LayerFillService.ACTION_STOP) == true) {
                    Toast.makeText(this@SigninActivity, R.string.canceled, Toast.LENGTH_SHORT).show()
                    clear()
                    return
                }

                val serviceStatus = intent.getShortExtra(LayerFillService.KEY_STATUS, 0)
                when (serviceStatus) {
                    LayerFillService.STATUS_STOP -> {
                        total--
                        if (total <= 0)
                            style()
                    }
                }
            }
        }

        val intentFilter = IntentFilter(LayerFillService.ACTION_UPDATE)
        intentFilter.addAction(LayerFillService.ACTION_STOP)
        registerReceiver(receiver, intentFilter)

        val intent = Intent(this, LayerFillService::class.java)
        intent.action = LayerFillService.ACTION_ADD_TASK

        val app = application as? IGISApplication
        val map = app?.map as MapDrawable?
        val accountName = AUTHORITY

        for (layer in LAYERS) {
            val uri = Uri.parse(Uri.decode(layer.first))
            val id = uri.lastPathSegment?.toLongOrNull()
            intent.putExtra(LayerFillService.KEY_REMOTE_ID, id)
            intent.putExtra(LayerFillService.KEY_ACCOUNT, accountName)
            intent.putExtra(LayerFillService.KEY_NAME, layer.second)
            intent.putExtra(LayerFillService.KEY_LAYER_GROUP_ID, map?.id)
            intent.putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.NGW_LAYER)
            intent.putExtra(LayerFillService.KEY_URI, uri)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun clear() {
        val app = application as? FEFUApplication
        (app?.map as MapDrawable).delete()
        app.addInitialLayers()
        total = LAYERS.size
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
        }
    }

    private fun style() {
        val style = SimpleMarkerStyle.MarkerStyleCircle
        val cafeStyle = SimpleMarkerStyle(Color.GREEN, Color.BLACK, 6f, style)
        val app = application as? FEFUApplication
        val map = app?.map as MapDrawable
        val cafe = map.getLayerByName(LAYERS[2].second) as VectorLayer
        cafe.renderer = SimpleFeatureRenderer(cafe, cafeStyle)
        cafe.save()

        val shop = map.getLayerByName(LAYERS[0].second) as VectorLayer
        val shopStyle = FieldStyleRule(shop)
        shopStyle.key = "category_id"
        val groceryStyle = SimpleMarkerStyle(Color.LTGRAY, Color.BLACK, 5f, style)
        shopStyle.setStyle("1", groceryStyle)
        val supermarketStyle = SimpleMarkerStyle(Color.GRAY, Color.BLACK, 5f, style)
        shopStyle.setStyle("2", supermarketStyle)
        val pharmacyStyle = SimpleMarkerStyle(Color.MAGENTA, Color.BLACK, 5f, style)
        shopStyle.setStyle("3", pharmacyStyle)
        shop.renderer = RuleFeatureRenderer(shop, shopStyle, groceryStyle)
        shop.save()

        app.addInitialLayers()
        signin()
    }

}
