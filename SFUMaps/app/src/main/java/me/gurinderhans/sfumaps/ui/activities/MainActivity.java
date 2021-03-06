package me.gurinderhans.sfumaps.ui.activities;

import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.jakewharton.disklrucache.DiskLruCache;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.interfaces.OnCheckedChangeListener;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SwitchDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.SaveCallback;
import com.tokenautocomplete.TokenCompleteTextView.TokenListener;

import java.util.ArrayList;
import java.util.List;

import me.gurinderhans.sfumaps.R;
import me.gurinderhans.sfumaps.app.AppConfig;
import me.gurinderhans.sfumaps.devtools.PathMaker;
import me.gurinderhans.sfumaps.devtools.placecreator.controllers.PlaceFormDialog;
import me.gurinderhans.sfumaps.factory.classes.MapPlace;
import me.gurinderhans.sfumaps.factory.classes.PathSearch;
import me.gurinderhans.sfumaps.factory.classes.mapgraph.MapGraph;
import me.gurinderhans.sfumaps.factory.classes.mapgraph.MapGraphEdge;
import me.gurinderhans.sfumaps.factory.classes.mapgraph.MapGraphNode;
import me.gurinderhans.sfumaps.factory.libs.sliding_up_panel.SlidingUpPanel;
import me.gurinderhans.sfumaps.ui.controllers.SlidingUpPanelController;
import me.gurinderhans.sfumaps.ui.views.CustomMapFragment;
import me.gurinderhans.sfumaps.ui.views.MapPlaceSearchCompletionView;
import me.gurinderhans.sfumaps.utils.CachedTileProvider;
import me.gurinderhans.sfumaps.utils.MercatorProjection;
import me.gurinderhans.sfumaps.utils.SVGTileProvider;
import me.gurinderhans.sfumaps.utils.Tools;
import me.gurinderhans.sfumaps.utils.Tools.DataUtils;

import static android.view.View.VISIBLE;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NONE;
import static com.mikepenz.google_material_typeface_library.GoogleMaterial.Icon.gmd_developer_mode;
import static com.mikepenz.google_material_typeface_library.GoogleMaterial.Icon.gmd_place;
import static com.mikepenz.google_material_typeface_library.GoogleMaterial.Icon.gmd_settings;
import static me.gurinderhans.sfumaps.app.Keys.ParseMapPlace.CLASS;
import static me.gurinderhans.sfumaps.app.Keys.ParseMapPlace.PARENT_PLACE;
import static me.gurinderhans.sfumaps.factory.classes.MapPlace.mAllMapPlaces;
import static me.gurinderhans.sfumaps.utils.MarkerCreator.createPlaceMarker;
import static me.gurinderhans.sfumaps.utils.MercatorProjection.fromPointToLatLng;
import static me.gurinderhans.sfumaps.utils.Tools.ViewUtils.LinearViewAnimatorTranslateYToPos;
import static me.gurinderhans.sfumaps.utils.Tools.ViewUtils.hideKeyboard;

public class MainActivity extends AppCompatActivity
		implements
		OnCameraChangeListener,
		OnMapClickListener,
		OnMapLongClickListener,
		OnMarkerClickListener,
		OnMarkerDragListener,
		OnClickListener,
		OnCheckedChangeListener {

	protected static final String TAG = MainActivity.class.getSimpleName();

	/**
	 * The Map View
	 */
	private GoogleMap mMap;


	/**
	 * Custom controller to handle the sliding panel
	 */
	private SlidingUpPanelController mPanelController;


	/**
	 * Main activity toolbar wrapper view
	 * <p>
	 * The CardView simple provides the toolbar a bottom shadow
	 */
	private CardView mToolbarWrapper;


	/**
	 * Get directions floating action button
	 */
	private FloatingActionButton mDirectionsFAB;


	/**
	 * The main app search box
	 */
	private MapPlaceSearchCompletionView mMapSearchView;


	/**
	 * Toolbar search box, holds the place navigating from
	 */
	private MapPlaceSearchCompletionView mNavigationFromSearchView;


	/**
	 * Toolbar search box, holds the place navigating to
	 */
	private MapPlaceSearchCompletionView mNavigationToSearchView;


	/**
	 * Stores the current map zoom
	 * <p>
	 * When onCameraChange() is called, this gets compared to the 'new' zoom to see if the zoom
	 * level actually changed.
	 */
	private int mMapZoom;


	/**
	 * Disk cache used to cache map tiles that were not previously in cache.
	 */
	private DiskLruCache mTileCache;


	/**
	 * Search adapter used for searching through map places
	 */
	private ArrayAdapter<MapPlace> mMapSearchAdapter;


	/**
	 * True if the app is in navigation mode showing a route
	 */
	private boolean mNavigationMode = false;


	/**
	 * PathSearch.class used to create path searches
	 */
	private PathSearch mPathSearch;


	/**
	 * Holds the current selected place, i.e. the clicked marker
	 */
	private MapPlace mFocusedMapPlace;


	/**
	 * Handles the complex states of the activity managing the data and the views
	 */
	private StateHandler mActivityStateHandler = new StateHandler();


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		this.setTheme(R.style.MainActivity);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// setup from top down
		setupStatusBar();
		setUpSearchAndToolbarAndDrawer();
		setUpMap();
		loadPlaces();
		setupFABAndSlidingPanel();
		updateDevMode();

		// path maker
		PathMaker.createPathMaker(this, mMap);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.get_directions_fab:

				mNavigationMode = true;
				mActivityStateHandler.setPreNavigationState();

				break;
		}
	}

	@Override
	public void onCameraChange(CameraPosition cameraPosition) {

		// 1. limit map max zoom
		float maxZoom = 8f;
		if (cameraPosition.zoom > maxZoom)
			mMap.animateCamera(CameraUpdateFactory.zoomTo(maxZoom));

		// 2. load this zoom markers
		if (mMapZoom != (int) cameraPosition.zoom) { // on zoom change
			mMapZoom = (int) cameraPosition.zoom;
			syncMarkers();
		}
	}

	@Override
	public void onMapLongClick(LatLng latLng) {
		if (AppConfig.DEV_MODE_SWITCH && !PathMaker.isEditingMap) {

			MapPlace newPlace = new MapPlace();
			newPlace.setPosition(MercatorProjection.fromLatLngToPoint(latLng));
			newPlace.setMapGizmo(createPlaceMarker(getApplicationContext(), mMap, newPlace));
			mAllMapPlaces.add(newPlace);

			// send to edit
			new PlaceFormDialog(this,
					getPlaceIndex(fromPointToLatLng(newPlace.getPosition())))
					.show();
		}
	}

	@Override
	public void onMarkerDragStart(Marker marker) {
	}

	@Override
	public void onMarkerDrag(Marker marker) {
	}

	@Override
	public void onMapClick(LatLng latLng) {
		if (!mNavigationMode) {
			mFocusedMapPlace = null;
			mActivityStateHandler.setDefaultState();
		}
	}

	@Override
	public boolean onMarkerClick(Marker marker) {

		int clickedPlaceIndex = getPlaceIndex(marker.getPosition());
		if (clickedPlaceIndex != -1) {
			if (AppConfig.DEV_MODE_SWITCH) {
				// edit place
				new PlaceFormDialog(this, clickedPlaceIndex).show();
			} else {

				if (!mNavigationMode) {

					mFocusedMapPlace = mAllMapPlaces.get(clickedPlaceIndex);

					mMap.animateCamera(CameraUpdateFactory.newLatLng(
							MercatorProjection.fromPointToLatLng(mFocusedMapPlace.getPosition())
					));

					mActivityStateHandler.setPlaceViewState();
				}

			}
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:

				mNavigationMode = false;

				if (mFocusedMapPlace == null)
					mActivityStateHandler.setDefaultState();
				else
					mActivityStateHandler.setPlaceViewState();

				// TMP call
				mPathSearch.clearPaths();

				break;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onMarkerDragEnd(Marker marker) {

		// find the clicked marker
		int draggedPlaceIndex = getPlaceIndex(marker.getPosition());
		if (draggedPlaceIndex != -1) {
			mAllMapPlaces.get(draggedPlaceIndex).setPosition(
					MercatorProjection.fromLatLngToPoint(marker.getPosition())
			);

			mAllMapPlaces.get(draggedPlaceIndex).pinInBackground(new SaveCallback() {
				@Override
				public void done(ParseException e) {
					if (e == null)
						Snackbar.make(findViewById(android.R.id.content), "Place location updated", Snackbar.LENGTH_LONG).show();
				}
			});
		}
	}

	@Override
	public void onCheckedChanged(IDrawerItem iDrawerItem, CompoundButton compoundButton, boolean b) {
		AppConfig.DEV_MODE_SWITCH = b;
		updateDevMode();
	}


	/**
	 * Makes the status bar transparent and allows views to be drawn behind the status bar
	 */
	private void setupStatusBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.transparent_status_bar_color));
	}


	/**
	 * Initializes map search box and toolbar search
	 * TODO: refactor, this method does too much
	 */
	private void setUpSearchAndToolbarAndDrawer() {
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		int statusBarHeight = getStatusBarHeight();
		int extraPadding = getResources().getDimensionPixelOffset(R.dimen.activity_main_toolbar_bottom_padding);
		toolbar.setPadding(0, statusBarHeight + extraPadding, 0, extraPadding);

		// setup drawer
		new DrawerBuilder()
				.withFullscreen(true)
				.withActivity(this)
				.addDrawerItems(
						new PrimaryDrawerItem().withName(R.string.drawer_your_places).withIcon(gmd_place),
						new SwitchDrawerItem().withName(R.string.drawer_dev_mode).withIcon(gmd_developer_mode)
								.withOnCheckedChangeListener(this),
						new SecondaryDrawerItem().withName(R.string.drawer_settings).withIcon(gmd_settings)
				)
				.build();

		// setup toolbar
		mToolbarWrapper = (CardView) findViewById(R.id.toolbar_cardview_shadow_wrapper);
		mToolbarWrapper.setTranslationY(-getToolbarHeight());

		// add the search layout
		View view = LayoutInflater.from(this).inflate(R.layout.activity_main_toolbar_search, toolbar, false);
		toolbar.addView(view);

		// customize action bar
		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayShowTitleEnabled(false);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		// search
		mMapSearchView = (MapPlaceSearchCompletionView) findViewById(R.id.main_search_view);
		mMapSearchView.setLayoutId(R.layout.activity_main_placesearch_token_layout);

		mNavigationFromSearchView = (MapPlaceSearchCompletionView) toolbar.findViewById(R.id.place_from);
		mNavigationToSearchView = (MapPlaceSearchCompletionView) toolbar.findViewById(R.id.place_to);

		mMapSearchAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);

		// setup search adapter
		mMapSearchView.setAdapter(mMapSearchAdapter);
		mNavigationFromSearchView.setAdapter(mMapSearchAdapter);
		mNavigationToSearchView.setAdapter(mMapSearchAdapter);

		// start search listener
		TokenListener startSearchListener = new TokenListener() {
			@Override
			public void onTokenAdded(Object o) {

				if (mNavigationFromSearchView.getObjects().size() == 0 || mNavigationToSearchView.getObjects().size() == 0)
					return;

				// start search
				hideKeyboard(MainActivity.this);

				mPathSearch.newSearch(
						mNavigationFromSearchView.getObjects().get(0),
						mNavigationToSearchView.getObjects().get(0)
				);

				// center camera on map path
				mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder()
								.include(fromPointToLatLng(mNavigationFromSearchView.getObjects().get(0).getPosition()))
								.include(fromPointToLatLng(mNavigationToSearchView.getObjects().get(0).getPosition()))
								.build(), 100)
				);
			}

			@Override
			public void onTokenRemoved(Object o) {

			}
		};
		mNavigationFromSearchView.setTokenListener(startSearchListener);
		mNavigationToSearchView.setTokenListener(startSearchListener);

		// map search listener
		mMapSearchView.setTokenListener(new TokenListener() {
			@Override
			public void onTokenAdded(Object o) {
				Log.i(TAG, "search view token: ADDED");

				// if mMapSearchView is focused, that means the token was added through the
				// search view, so we need to do stuff a bit differently here
				if (mMapSearchView.isFocused()) {
					Log.i(TAG, "adding token through search view itself");
					hideKeyboard(MainActivity.this);

					mFocusedMapPlace = mMapSearchView.getObjects().get(0);

					mActivityStateHandler.setPlaceViewState();

					// animate map camera to the place
					mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(fromPointToLatLng(mFocusedMapPlace.getPosition()), mFocusedMapPlace.getZooms().get(0)));
				}
			}

			@Override
			public void onTokenRemoved(Object o) {
			}
		});
	}


	/**
	 * Retrieves the Google Maps fragment and loads the custom settings such as custom map tiles
	 * plus registers different listener events on the map
	 */
	private void setUpMap() {

		// cache for map tiles
		mTileCache = Tools.TileManager.openDiskCache(this);

		// map view
		CustomMapFragment fragment = ((CustomMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map));
		mMap = fragment.getMap();

		mMap.setMapType(MAP_TYPE_NONE);
		mMap.setIndoorEnabled(false);

		// hide the marker toolbar - the two buttons on the bottom right that go to google maps
		mMap.getUiSettings().setMapToolbarEnabled(false);

		// event listeners
		mMap.setOnCameraChangeListener(this);
		mMap.setOnMapClickListener(this);
		mMap.setOnMapLongClickListener(this);
		mMap.setOnMarkerClickListener(this);
		mMap.setOnMarkerDragListener(this);


		// move compass icon button to the right side
		try {
			if (fragment.getView() != null) {
				View compassView = fragment.getView().findViewWithTag("GoogleMapCompass");

				RelativeLayout.LayoutParams compassViewLayoutParams = (RelativeLayout.LayoutParams) compassView.getLayoutParams();
				compassViewLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
				compassViewLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				// TODO: 15-09-22 is this in px or dp?
				compassViewLayoutParams.setMargins(0, 200, 20, 0);
				compassView.setLayoutParams(compassViewLayoutParams);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		// base map overlay
		mMap.addTileOverlay(new TileOverlayOptions()
				.tileProvider(
						getTileProvider(1, new SVGTileProvider(Tools.TileManager.getBaseMapTiles(this),
								getResources().getDisplayMetrics().densityDpi / 160f)))
				.zIndex(10));

		// overlay tile provider to switch floor level stuff
		mMap.addTileOverlay(new TileOverlayOptions()
				.tileProvider(
						getTileProvider(2, new SVGTileProvider(Tools.TileManager.getOverlayTiles(this),
								getResources().getDisplayMetrics().densityDpi / 160f)))
				.zIndex(11));

		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2f));

		mPathSearch = new PathSearch(this, mMap);
	}


	/**
	 * Makes an asynchronous call to the Parse servers and loads all the @link{MapPlace} objects
	 * into search adapter @link{mMapSearchAdapter}
	 */
	public void loadPlaces() {

		ParseQuery<ParseObject> query = ParseQuery.getQuery(CLASS);
		DataUtils.parseFetchClass(this, query, new ArrayList<String>() {{
			add(PARENT_PLACE);
		}}, new DataUtils.FetchResultsCallback() {
			@Override
			public void onResults(List<?> objects) {

				for (Object Oplace : objects) {
					MapPlace place = (MapPlace) Oplace;

					// FIXME: 15-09-20 an expensive call for the UI thread
					Marker marker = createPlaceMarker(getApplicationContext(), mMap, place);
					marker.setVisible(false);

					place.setMapGizmo(marker);

					mAllMapPlaces.add(place);

				}
				mMapSearchAdapter.addAll(mAllMapPlaces);

				syncMarkers();

			}
		});
	}


	/**
	 * Sets up the @link{SlidingUpPanel} and @link{FloatingActionButton} to be used for the app
	 */
	private void setupFABAndSlidingPanel() {
		mDirectionsFAB = (FloatingActionButton) findViewById(R.id.get_directions_fab);
		mDirectionsFAB.setOnClickListener(this);

		mPanelController = new SlidingUpPanelController(
				(SlidingUpPanel) findViewById(R.id.sliding_panel), mDirectionsFAB);
	}


	/**
	 * Helper method to search through the local places list and find the place mathcing given
	 * input position
	 *
	 * @param placePos - position of the place to find
	 * @return - index of place in List @link{mAllMapPlaces}
	 */
	private int getPlaceIndex(LatLng placePos) {

		for (int i = 0; i < mAllMapPlaces.size(); i++) {
			// level the LatLng to same 'precision'
			PointF thisMarkerPoint = MercatorProjection.fromLatLngToPoint(
					mAllMapPlaces.get(i).getMapGizmo().getPosition());

			if (thisMarkerPoint.equals(MercatorProjection.fromLatLngToPoint(placePos)))
				return i;
		}

		return -1;
	}


	/**
	 * Called on zoom change, syncs the map markers to match current zoom level
	 */
	private void syncMarkers() {
		for (MapPlace el : mAllMapPlaces)
			el.getMapGizmo().setVisible(el.getZooms().contains(mMapZoom));
	}


	/**
	 * Helper method to find height of the system status bar
	 *
	 * @return - height of the status bar
	 */
	public int getStatusBarHeight() {
		int result = 0;
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}


	/**
	 * Helper method to find height of the activity toolbar
	 *
	 * @return - the total height of toolbar
	 */
	public int getToolbarHeight() {
		int statusBarHeight = getStatusBarHeight();
		int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.activity_main_toolbar_height);
		int extraPadding = getResources().getDimensionPixelOffset(R.dimen.activity_main_toolbar_bottom_padding);

		return statusBarHeight + toolbarHeight + extraPadding;
	}


	/**
	 * Helper method to choose tile provider
	 *
	 * @param layer           - layer number for overlay tile provider to keep cache tiles for each overlay separate
	 * @param svgTileProvider - an instance of SVGTileProvider.class
	 * @return - IF cache supported, CachedTileProvider object ELSE the given SVGTileProvider object
	 */
	public TileProvider getTileProvider(int layer, SVGTileProvider svgTileProvider) {
		return mTileCache == null
				? svgTileProvider
				: new CachedTileProvider(Integer.toString(layer), svgTileProvider, mTileCache);
	}


	/**
	 * Enable / Disable dev mode
	 */
	private void updateDevMode() {
		// show hide the edit path layout
		findViewById(R.id.dev_overlay).setVisibility(AppConfig.DEV_MODE_SWITCH ? VISIBLE : View.INVISIBLE);

		// toggle pathmaker overlays
		MapGraph graph = MapGraph.getInstance();
		for (MapGraphNode node : graph.getNodes())
			if (node.getMapGizmo() != null)
				node.getMapGizmo().setVisible(AppConfig.DEV_MODE_SWITCH);

		for (MapGraphEdge edge : graph.getEdges())
			if (edge.getMapGizmo() != null)
				edge.getMapGizmo().setVisible(AppConfig.DEV_MODE_SWITCH);
	}


	/**
	 * Inner class to separate state handler code from the rest of the Controller
	 */
	class StateHandler {

		// state constants
		static final int DEFAULT_STATE = 0;
		static final int PLACE_VIEW_STATE = 1;
		static final int PRE_NAVIGATION_STATE = 2;

		/**
		 * On start the app will be started with the DEFAULT_STATE
		 */
		int mState = DEFAULT_STATE;


		/**
		 * Toolbar is hidden
		 * Panel is hidden
		 * FAB is position bottom right
		 * Search is showing
		 */
		void setDefaultState() {
			mState = DEFAULT_STATE;
			Log.i(TAG, "state: DEFAULT_STATE");

			/* 1. Data */

			mMapSearchView.clear();
			mNavigationFromSearchView.clear();
			mNavigationToSearchView.clear();

			/* 2. Views */

			// hide toolbar
			mToolbarWrapper.animate()
					.translationY(-getToolbarHeight())
					.setInterpolator(new AccelerateInterpolator())
					.setDuration(150l)
					.start();

			mDirectionsFAB.show();
			LinearViewAnimatorTranslateYToPos(mDirectionsFAB.getTranslationY(), 0, 80l, new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mDirectionsFAB.setTranslationY(Float.parseFloat(animation.getAnimatedValue().toString()));
				}
			});

			// TODO: 15-09-20 show search

			mPanelController.panel.showPanel(false);
		}


		/**
		 * Toolbar is hidden
		 * Panel is shown
		 * Fab is aligned with Panel
		 * Search is shown with the place being viewed
		 */
		void setPlaceViewState() {
			mState = PLACE_VIEW_STATE;
			Log.i(TAG, "state: PLACE_VIEW_STATE");


			/* 1. Data */


			// sync map search box with this place
			mNavigationFromSearchView.clear();
			mNavigationToSearchView.clear();
			mMapSearchView.clear();
			mMapSearchView.addObject(mFocusedMapPlace);

			mPanelController.setPanelData(mFocusedMapPlace);


			/* 2. Views */


			// hide toolbar
			mToolbarWrapper.animate()
					.translationY(-getToolbarHeight())
					.setInterpolator(new AccelerateInterpolator())
					.setDuration(150l)
					.start();


			// TODO: 15-09-20 show search

			mPanelController.panel.showPanel(true);

			mDirectionsFAB.show();
			// animate fab a little up
			LinearViewAnimatorTranslateYToPos(mDirectionsFAB.getTranslationY(), -50, 80l, new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mDirectionsFAB.setTranslationY(Float.parseFloat(animation.getAnimatedValue().toString()));
				}
			});

			mMapSearchView.clearFocus();

		}


		/**
		 * Toolbar is shown, may not may not be filled
		 * Panel is hidden
		 * FAB is hidden
		 * Search is hidden
		 */
		void setPreNavigationState() {
			mState = PRE_NAVIGATION_STATE;
			Log.i(TAG, "state: PRE_NAVIGATION_STATE");

			/* 1. Data */

			mNavigationToSearchView.clear();
			mNavigationToSearchView.addObject(mFocusedMapPlace);
			mNavigationFromSearchView.clear();


			/* 2. Views */

			// show toolbar
			mToolbarWrapper.animate()
					.translationY(0)
					.setInterpolator(new AccelerateInterpolator())
					.setDuration(150l)
					.start();

			// TODO: 15-09-20 hide search

			mDirectionsFAB.hide();

			mPanelController.panel.showPanel(false);

		}
	}
}