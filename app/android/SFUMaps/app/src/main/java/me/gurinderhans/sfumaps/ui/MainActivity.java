package me.gurinderhans.sfumaps.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Picture;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.jakewharton.disklrucache.DiskLruCache;
import com.larvalabs.svgandroid.SVGBuilder;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseObject;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.gurinderhans.sfumaps.R;
import me.gurinderhans.sfumaps.devtools.pathmaker.CustomMapFragment;
import me.gurinderhans.sfumaps.devtools.pathmaker.PathMaker;
import me.gurinderhans.sfumaps.devtools.wifirecorder.Controller.RecordWifiDataActivity;
import me.gurinderhans.sfumaps.devtools.wifirecorder.Keys;
import me.gurinderhans.sfumaps.factory.classes.GridNode;
import me.gurinderhans.sfumaps.factory.classes.MapGrid;
import me.gurinderhans.sfumaps.factory.classes.PathSearch;
import me.gurinderhans.sfumaps.utils.AppConfig;
import me.gurinderhans.sfumaps.utils.MapTools;
import me.gurinderhans.sfumaps.utils.MercatorProjection;

import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NONE;
import static com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import static com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;

public class MainActivity extends FragmentActivity implements OnMapClickListener, GoogleMap.OnCameraChangeListener {

	public static final String TAG = MainActivity.class.getSimpleName();
	MapGrid MapGrid;
	PathSearch mPathSearch;
	boolean searchMode = false;
	private GoogleMap Map;

	private DiskLruCache mTileCache;
	private int mMapCurrentZoom;

	public List<Marker> mMapMarkersCurrentZoom = new ArrayList<>();

	// FIXME: temp method
	public static ArrayList<Pair<String, Picture>> getOverlayTiles(Context c) {
		try {
			ArrayList<Pair<String, Picture>> tileFiles = new ArrayList<>();

			Picture currentFile = new SVGBuilder().readFromInputStream(
					c.getAssets().open("sfumap-overlay.svg")).build().getPicture();

			for (int i = 0; i < 25; i++)
				tileFiles.add(Pair.create(i + "", currentFile));

			return tileFiles;

		} catch (IOException e) {
			e.printStackTrace();
			Log.d(MainActivity.TAG, "Could not create Tile Provider. Unable to list map tile files directory");
		}

		return null;
	}

	FindCallback<ParseObject> onZoomChangedCallback = new FindCallback<ParseObject>() {
		@Override
		public void done(List<ParseObject> objects, ParseException e) {
			// hide prev zoom markers
			for (Marker marker : mMapMarkersCurrentZoom)
				marker.remove();

			mMapMarkersCurrentZoom = new ArrayList<>();

			for (ParseObject object : objects) {
				// show these markers
				JSONObject location = object.getJSONObject(Keys.KEY_PLACE_POSITION);
				try {
					float x = (float) location.getDouble("x");
					float y = (float) location.getDouble("y");
					mMapMarkersCurrentZoom.add(
							MapTools.addTextMarker(getApplicationContext(), Map,
									new PointF(x, y), null, 0));
				} catch (Exception exception) {
					// unable to parse location for this place, no marker for this place then
					exception.printStackTrace();
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		// set activity theme to light text
		this.setTheme(R.style.MainActivity);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// setting the status bar transparent
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			getWindow().setStatusBarColor(getResources().getColor(R.color.transparent_status_bar_color));

		// load app preferences
		AppConfig.loadPreferences(this);

		(findViewById(R.id.wifi_data_observer)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(getApplicationContext(), RecordWifiDataActivity.class));
			}
		});

		findViewById(R.id.search_init_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				searchMode = !searchMode;

				((FloatingActionButton) v).setImageResource(searchMode ? R.drawable.ic_close_white_24dp : R.drawable.ic_search_white_24dp);

				if (!searchMode)
					mPathSearch.clearPath();
			}
		});

		// cache for map tiles
		mTileCache = MapTools.openDiskCache(this);

		// starting and ending points are hardcoded for now, but it can work
		MapGrid = new MapGrid(MainActivity.this, new PointF(121f, 100f), new PointF(192f, 183f));

		setUpMapIfNeeded();

		// handles paths search
		mPathSearch = new PathSearch(Map, MapGrid);

		handleDevAndDebugMode(AppConfig.DEBUG_MODE, AppConfig.DEV_MODE);

		mMapCurrentZoom = (int) Map.getCameraPosition().zoom;

		// temp
		MapTools.getZoomMarkers(mMapCurrentZoom, onZoomChangedCallback);
	}

	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the map.
		if (Map == null) {
			CustomMapFragment customMapFragment = ((CustomMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.map));

			Map = customMapFragment.getMap();

			if (Map != null) {

				// set up path maker
				new PathMaker(customMapFragment, Map, MapGrid,
						findViewById(R.id.edit_map_path),
						findViewById(R.id.export_map_path),
						findViewById(R.id.create_box_rect),
						findViewById(R.id.delete_box_rect));

				// set up map UI
				setUpMap();
			}
		}
	}

	private void setUpMap() {

		// hide default overlay and set initial position
		Map.setMapType(MAP_TYPE_NONE);
		Map.setIndoorEnabled(false);
		Map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2f));

		// hide the marker toolbar - the two buttons on the bottom right that go to google maps
		Map.getUiSettings().setMapToolbarEnabled(false);

		// just put the user navigation marker in the center as we don't yet know user's location
//		LatLng mapCenter = new LatLng(0, 0);//MercatorProjection.fromPointToLatLng(new PointF(AppConfig.TILE_SIZE, AppConfig.TILE_SIZE));
//		Map.addMarker(new MarkerOptions()
//				.position(mapCenter)
//				.title("Position")
//				.snippet(MercatorProjection.fromLatLngToPoint(mapCenter).toString())
//				.draggable(true));

		Map.setOnMarkerDragListener(new OnMarkerDragListener() {
			@Override
			public void onMarkerDragStart(Marker marker) {
			}

			@Override
			public void onMarkerDrag(Marker marker) {
			}

			@Override
			public void onMarkerDragEnd(Marker marker) {
				marker.setSnippet(
						MercatorProjection.fromLatLngToPoint(marker.getPosition()).toString());
			}
		});

		Map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
			@Override
			public boolean onMarkerClick(Marker marker) {
				if (!PathMaker.isEditingMap && !searchMode)
					marker.showInfoWindow();
				return true;
			}
		});

		Map.setOnMapClickListener(this);
		Map.setOnCameraChangeListener(this);


		// base map overlay
		Map.addTileOverlay(new TileOverlayOptions().tileProvider(
				new SVGTileProvider(MapTools.getBaseMapTiles(this), getResources().getDisplayMetrics().densityDpi / 160f)
		).zIndex(10));


		// overlay tile provider to switch floor level stuff
		Map.addTileOverlay(new TileOverlayOptions().tileProvider(getTileProvider(
				new SVGTileProvider(getOverlayTiles(this), getResources().getDisplayMetrics().densityDpi / 160f)
		)).zIndex(11));
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
	public void onMapClick(LatLng latLng) {

		if (searchMode)
			mPathSearch.recordPoint(latLng);
		else {
			PointF clickedPoint = MercatorProjection.fromLatLngToPoint(latLng);
			// get markers at the area clicked
			for (int x = 0; x < MapGrid.mapGridSizeX; x++) {
				for (int y = 0; y < MapGrid.mapGridSizeY; y++) {
					GridNode thisNode = MapGrid.getNode(x, y);
					if (MapTools.inRange(thisNode.projCoords, clickedPoint, 0.5f)) {
						Map.addMarker(new MarkerOptions()
								.position(MercatorProjection.fromPointToLatLng(thisNode.projCoords))
								.icon(BitmapDescriptorFactory.fromResource(thisNode.isWalkable() ? R.drawable.map_path : R.drawable.no_path))
								.anchor(0.5f, 0.5f)
								.title("Pos: " + thisNode.projCoords.x + ", " + thisNode.projCoords.y));
					}
				}
			}
		}

	}

	public void handleDevAndDebugMode(boolean debugMode, boolean devMode) {
		if (!debugMode)
			findViewById(R.id.wifi_data_observer).setVisibility(View.GONE);

		if (!devMode)
			findViewById(R.id.pathmaker_actions).setVisibility(View.GONE);

	}

	public TileProvider getTileProvider(SVGTileProvider svgTileProvider) {
		return mTileCache == null
				? svgTileProvider
				: new CachedTileProvider(Integer.toString(1), svgTileProvider, mTileCache);
	}

	@Override
	public void onCameraChange(CameraPosition cameraPosition) {

		if (mMapCurrentZoom != (int) cameraPosition.zoom) { // on zoom change
			mMapCurrentZoom = (int) cameraPosition.zoom;
			MapTools.getZoomMarkers(mMapCurrentZoom, onZoomChangedCallback);
		}
	}
}
