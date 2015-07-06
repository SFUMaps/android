package me.gurinderhans.sfumaps;

import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.gurinderhans.sfumaps.wifirecorder.Controller.RecordWifiDataActivity;

public class MainActivity extends FragmentActivity implements OnCameraChangeListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    // TODO: either disable indoor map of real life buildings on map, or simply don't allow that much zooming in

    private GoogleMap Map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // load app preferences
        AppConfig.loadPreferences(this);

        (findViewById(R.id.backend_panel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), RecordWifiDataActivity.class));
            }
        });

        setUpMapIfNeeded();


        // some random sample text for just doing it

        // random locations
        MapTools.addTextAndIconMarker(this,
                Map,
                new PointF(107f, 150f),
                MapTools.createPureTextIcon(this, "Naheeno Park", null),
                0f,
                null,
                MapTools.MapLabelIconAlign.TOP);


        MapTools.addTextAndIconMarker(this,
                Map,
                new PointF(121.805f, 104.698f),
                MapTools.createPureTextIcon(this, "W.A.C Bennett Library", null),
                0f,
                null,
                MapTools.MapLabelIconAlign.RIGHT);

        MapTools.addTextAndIconMarker(this,
                Map,
                new PointF(121.625f, 112.704f),
                MapTools.createPureTextIcon(this, "Food Court", null),
                0f,
                null,
                MapTools.MapLabelIconAlign.LEFT);

        MapTools.addTextAndIconMarker(this,
                Map,
                new PointF(98.211f, 120.623f),
                MapTools.createPureTextIcon(this, "Terry Fox Field", null),
                0f,
                null,
                MapTools.MapLabelIconAlign.TOP);


        // add road markers
        MapTools.addTextMarker(this,
                Map,
                new PointF(90.98202f, 139.12495f),
                MapTools.createPureTextIcon(this, "Gaglardi Way", null),
                -28f);

        MapTools.addTextMarker(this,
                Map,
                new PointF(35.420155f, 110.39347f),
                MapTools.createPureTextIcon(this, "University Dr W", null),
                -38f);

        MapTools.addTextMarker(this,
                Map,
                new PointF(198.84691f, 108.44006f),
                MapTools.createPureTextIcon(this, "University High Street", null),
                0f);

        MapTools.addTextMarker(this,
                Map,
                new PointF(214.28412f, 81.674225f),
                MapTools.createPureTextIcon(this, "University Crescent", null),
                5f);
    }

    /**
     * If (Map == null) then get the map fragment and initialize it.
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
     * <ul>
     * <li>Define map settings</li>
     * <li>Set custom map tiles</li>
     * <li>Get user's initial location here</li>
     * <li>Draw the recorded paths here</li>
     * </ul>
     */
    private void setUpMap() {

        // hide default overlay and set initial position
        Map.setMapType(GoogleMap.MAP_TYPE_NONE);
        Map.setIndoorEnabled(false);
        Map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(49.2788738, -122.9161411), 16f));

        // set max zoom for map
        Map.setOnCameraChangeListener(this);

        // hide the marker toolbar - the two buttons on the bottom right that go to google maps
        Map.getUiSettings().setMapToolbarEnabled(false);

        // just put the user navigation marker in the center as we don't yet know user's location
        LatLng mapCenter = new LatLng(0, 0);//MercatorProjection.fromPointToLatLng(new PointF(AppConfig.TILE_SIZE, AppConfig.TILE_SIZE));
        Map.addMarker(new MarkerOptions()
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

        // Polylines are useful for marking paths and routes on the map.
        Polyline polyline = Map.addPolyline(new PolylineOptions().geodesic(true)
                .add(new LatLng(-33.866, 151.195))  // Sydney
                .add(new LatLng(-18.142, 178.431))  // Fiji
                .add(new LatLng(21.291, -157.821))  // Hawaii
                .add(new LatLng(37.423, -122.091))  // Mountain View
        );
        polyline.setZIndex(1000); //Or some large number :)

        // draw our recorded paths
//        drawRecordedPaths = new DrawRecordedPaths(getApplicationContext(), Map);

        // add custom overlay
        try {

            ArrayList<File> tileFiles = new ArrayList<>();
            String[] files = getAssets().list(AppConfig.TILE_PATH);

            for (String f : files) {
                if (MapTools.copyTileAsset(this, f)) {
                    tileFiles.add(MapTools.getTileFile(this, f));
                    Log.i(TAG, "copied: " + f + " to files dir && " + "added: " + f + " to tileFiles list");
                }
            }

            TileProvider provider = new SVGTileProvider(tileFiles, getResources().getDisplayMetrics().densityDpi / 160f);
            Map.addTileOverlay(new TileOverlayOptions().tileProvider(provider));

        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Could not create Tile Provider. Unable to list map tile files directory");
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        ((TextView) findViewById(R.id.mapZoomLevelDisplay)).setText(cameraPosition.zoom + "");
    }
}
