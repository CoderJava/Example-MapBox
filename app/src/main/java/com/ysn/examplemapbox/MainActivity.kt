package com.ysn.examplemapbox

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.markerview.MarkerViewManager

class MainActivity : AppCompatActivity(), MapboxMap.OnMapClickListener {

    lateinit var mapView: MapView
    lateinit var mapBoxMap: MapboxMap
    lateinit var markerViewManager: MarkerViewManager
    lateinit var permissionsManager: PermissionsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.access_token))
        setContentView(R.layout.activity_main)
        initMapBox(savedInstanceState)
    }

    private fun initMapBox(savedInstanceState: Bundle?) {
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        val permissionListener: PermissionsListener = object : PermissionsListener {
            override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
                /* Nothing to do in here */
            }

            override fun onPermissionResult(granted: Boolean) {
                if (granted) {
                    syncMapBox()
                } else {
                    val alertDialogInfo = AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.info))
                        .setCancelable(false)
                        .setMessage(getString(R.string.permissions_denied))
                        .setPositiveButton(getString(R.string.dismiss)) { _, _ ->
                            finish()
                        }
                        .create()
                    alertDialogInfo.show()
                }
            }
        }
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            syncMapBox()
        } else {
            permissionsManager = PermissionsManager(permissionListener)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun syncMapBox() {
        mapView.getMapAsync { mapBoxMap ->
            markerViewManager = MarkerViewManager(mapView, mapBoxMap)
            this.mapBoxMap = mapBoxMap
            this.mapBoxMap.setStyle(Style.MAPBOX_STREETS) {
                mapBoxMap.addOnMapClickListener(this)
                showingDeviceLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showingDeviceLocation() {
        val locationComponent = mapBoxMap.locationComponent
        locationComponent.activateLocationComponent(this, mapBoxMap.style!!)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        markerViewManager.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState!!)
    }

    override fun onMapClick(point: LatLng): Boolean {
        mapBoxMap.addMarker(
            MarkerOptions()
                .position(point)
        )
        return true
    }

}
