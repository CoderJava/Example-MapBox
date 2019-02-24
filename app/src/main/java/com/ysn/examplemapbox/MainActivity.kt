package com.ysn.examplemapbox

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.markerview.MarkerViewManager
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_style_maps.view.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), MapboxMap.OnMapClickListener, View.OnClickListener {

    private lateinit var mapView: MapView
    private lateinit var mapBoxMap: MapboxMap
    private lateinit var markerViewManager: MarkerViewManager
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var alertDialogStyleMaps: AlertDialog
    private val markers = ArrayList<Marker>()
    lateinit var currentRoute: DirectionsRoute
    private var navigationMapRoute: NavigationMapRoute? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.access_token))
        setContentView(R.layout.activity_main)
        initMapBox(savedInstanceState)
        initButtonStartNavigationListener()
    }

    private fun initButtonStartNavigationListener() {
        button_start_navigation.setOnClickListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("MissingPermission", "InflateParams")
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_item_get_current_location_menu_main_activity -> {
                val locationEngine = LocationEngineProvider.getBestLocationEngine(this)
                locationEngine.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                    override fun onSuccess(result: LocationEngineResult?) {
                        val lastLocation = result?.lastLocation
                        val cameraPosition = CameraPosition.Builder()
                            .target(LatLng(lastLocation!!.latitude, lastLocation.longitude))
                            .build()
                        mapBoxMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000 * 5)
                    }

                    override fun onFailure(exception: Exception) {
                        Toast.makeText(this@MainActivity, "Error occured: ${exception.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                })
            }
            R.id.menu_item_style_menu_main_activity -> {
                val viewDialogStyleMaps = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.dialog_style_maps, null)
                viewDialogStyleMaps.text_view_street.setOnClickListener(this)
                viewDialogStyleMaps.text_view_outdoor.setOnClickListener(this)
                viewDialogStyleMaps.text_view_light.setOnClickListener(this)
                viewDialogStyleMaps.text_view_dark.setOnClickListener(this)
                viewDialogStyleMaps.text_view_satellite.setOnClickListener(this)
                viewDialogStyleMaps.text_view_satellite_street.setOnClickListener(this)
                viewDialogStyleMaps.text_view_traffic_day.setOnClickListener(this)
                viewDialogStyleMaps.text_view_traffic_night.setOnClickListener(this)
                alertDialogStyleMaps = AlertDialog.Builder(this@MainActivity)
                    .setView(viewDialogStyleMaps)
                    .create()
                alertDialogStyleMaps.show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initMapBox(savedInstanceState: Bundle?) {
        mapView = findViewById(R.id.map_view)
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
                mapBoxMap.setOnMarkerClickListener {
                    for (itemMarker in markers) {
                        if (itemMarker.position == it.position) {
                            markers.remove(itemMarker)
                            this.mapBoxMap.removeMarker(itemMarker)
                            button_start_navigation.visibility = View.GONE
                            break
                        }
                    }
                    true
                }
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

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.text_view_street -> {
                mapBoxMap.setStyle(Style.MAPBOX_STREETS)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_outdoor -> {
                mapBoxMap.setStyle(Style.OUTDOORS)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_light -> {
                mapBoxMap.setStyle(Style.LIGHT)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_dark -> {
                mapBoxMap.setStyle(Style.DARK)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_satellite -> {
                mapBoxMap.setStyle(Style.SATELLITE)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_satellite_street -> {
                mapBoxMap.setStyle(Style.SATELLITE_STREETS)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_traffic_day -> {
                mapBoxMap.setStyle(Style.TRAFFIC_DAY)
                alertDialogStyleMaps.dismiss()
            }
            R.id.text_view_traffic_night -> {
                mapBoxMap.setStyle(Style.TRAFFIC_NIGHT)
                alertDialogStyleMaps.dismiss()
            }
            R.id.button_start_navigation -> {
                val navigationLauncherOptions = NavigationLauncherOptions.builder()
                    .directionsRoute(currentRoute)
                    .shouldSimulateRoute(true)
                    .build()
                NavigationLauncher.startNavigation(this@MainActivity, navigationLauncherOptions)
            }
        }
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
        if (markers.size == 2) {
            mapBoxMap.removeMarker(markers[1])
            markers.removeAt(1)
        }
        markers.add(
            mapBoxMap.addMarker(
                MarkerOptions()
                    .position(point)
            )
        )
        if (markers.size == 2) {
            val originPoint = Point.fromLngLat(markers[0].position.longitude, markers[0].position.latitude)
            val destinationPoint = Point.fromLngLat(markers[1].position.longitude, markers[1].position.latitude)
            NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken()!!)
                .origin(originPoint)
                .destination(destinationPoint)
                .voiceUnits(DirectionsCriteria.IMPERIAL)
                .build()
                .getRoute(object : Callback<DirectionsResponse> {
                    override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                        Log.d(javaClass.simpleName, "Error occured: ${t.message}")
                        button_start_navigation.visibility = View.GONE
                    }

                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        if (response.body() == null) {
                            Log.d(javaClass.simpleName, "No routes found, make sure you set the right user and access token.")
                            return
                        } else if (response.body()!!.routes().size < 1) {
                            Log.d(javaClass.simpleName, "No routes found")
                            return
                        }
                        currentRoute = response.body()!!.routes()[0]

                        if (navigationMapRoute != null) {
                            navigationMapRoute?.removeRoute()
                        } else {
                            navigationMapRoute = NavigationMapRoute(null, mapView, mapBoxMap, R.style.NavigationMapRoute)
                        }
                        navigationMapRoute?.addRoute(currentRoute)
                        button_start_navigation.visibility = View.VISIBLE
                    }
                })
        }
        return true
    }

}
