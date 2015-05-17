package me.gurinderhans.sfumaps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends FragmentActivity {

    public static final String TAG = MainActivity.class.getSimpleName();

    WifiManager wifiManager; // system service that handles wifi
    WifiReceiver wifiReceiver; // broadcast receiver that listens for wifi scans and gets back the results
    Handler mHandler; // handler for initiating a wifi scan
    Runnable wifiScanner; // runs the system wifi scan

    GoogleMap Map;
    Marker userNavMarker; // marks users current location

    DrawRecordedPaths drawRecordedPaths; // reference to our custom class that draws recorded paths

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // load app preferences
        AppConfig.loadPreferences(getApplicationContext());

        // load up the variables
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mHandler = new Handler();
        wifiReceiver = new WifiReceiver();

        // create the runnable to handle scans
        wifiScanner = new Runnable() {
            @Override
            public void run() {
                wifiManager.startScan();
            }
        };

        setUpMapIfNeeded();

    }

    /**
     * If (Map == null) then get the map fragment and initialize it
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (Map == null) {
            // Try to obtain the map from the SupportMapFragment.
            Map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (Map != null)
                setUpMap();
        }
    }

    /**
     * - define map settings
     * - set custom map tiles
     * - TODO: figure out user's initial location here
     * - draw the recorded paths here
     */
    private void setUpMap() {

        Map.setMapType(GoogleMap.MAP_TYPE_NONE); // hide the default google maps overlay
        Map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0f, 0f), 2.0f)); // set the camera to (0,0) with zoom=2

        // here we add our own tile overlay with custom image tiles
        Map.addTileOverlay(new TileOverlayOptions().tileProvider(new CustomMapTileProvider(getResources().getAssets())));

        // hide the marker toolbar - the two buttons on the bottom right that open in google maps
        Map.getUiSettings().setMapToolbarEnabled(false);

        // draw our recorded paths
        drawRecordedPaths = new DrawRecordedPaths(getApplicationContext(), Map);

        // just put the user navigation marker in the center as we don't yet know user's location
        LatLng mapCenter = MercatorProjection.fromPointToLatLng(new PointF(AppConfig.TILE_SIZE / 2, AppConfig.TILE_SIZE / 2)); //west
        userNavMarker = Map.addMarker(new MarkerOptions()
                .position(mapCenter)
                .title("Position")
                .snippet(MercatorProjection.fromLatLngToPoint(mapCenter).toString())
                .draggable(true));

        Map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
            }

            @Override
            public void onMarkerDrag(Marker marker) {
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                Log.i(TAG, marker.getPosition() + "");
                marker.setSnippet(MercatorProjection.fromLatLngToPoint(marker.getPosition()).toString());
            }
        });

        // register receiver
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

    }

    @Override
    protected void onResume() {
        super.onResume();

        setUpMapIfNeeded();

        mHandler.postDelayed(wifiScanner, 0); // start scanner
//        showData(wifiManager.getScanResults());
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(wifiReceiver);
    }


    // broadcast receiver for knowing when the wifi scan finishes
    private class WifiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {

            ArrayList<PointF> matchedPoints = new ArrayList<>();

            for (ScanResult result : wifiManager.getScanResults()) {
                if (AppConfig.ALL_SSIDS.contains(result.SSID)) {
                    ArrayList<HashMap<String, Object>> points = DrawRecordedPaths.allAPs.get(result.BSSID);
                    if (points != null) {
                        for (HashMap<String, Object> recordedPoint : points) {
                            if (result.level == recordedPoint.get(Keys.KEY_RSSI)) {
                                matchedPoints.add((PointF) recordedPoint.get(Keys.KEY_POINT));
                            }
                        }
                    } else {
                        Log.i(TAG, "AP not found in database, use: " + DrawRecordedPaths.allAPs);
                    }
                }
            }

            // for now we assume user's position is the centroid of all points
            if (matchedPoints.size() > 0) {
                PointF centroid = getCentroid(matchedPoints);
                userNavMarker.setPosition(MercatorProjection.fromPointToLatLng(centroid));
            }

            mHandler.postDelayed(wifiScanner, 0);

        }
    }


    /**
     * @param points - list of points
     * @return - return the centroid point (mean value)
     */
    PointF getCentroid(ArrayList<PointF> points) {

        float Sx = 0, Sy = 0;

        for (PointF point : points) {
            Sx += point.x;
            Sy += point.y;
        }

        Sx /= points.size();
        Sy /= points.size();


        return new PointF(Sx, Sy);
    }

}
