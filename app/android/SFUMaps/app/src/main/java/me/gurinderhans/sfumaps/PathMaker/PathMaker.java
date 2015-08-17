package me.gurinderhans.sfumaps.PathMaker;

import android.graphics.Point;
import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.gurinderhans.sfumaps.Factory.MapGrid;
import me.gurinderhans.sfumaps.MapTools;
import me.gurinderhans.sfumaps.MercatorProjection;
import me.gurinderhans.sfumaps.R;

/**
 * Created by ghans on 15-08-10.
 */
public class PathMaker implements MapWrapperLayout.OnDragListener {

	public static final String TAG = PathMaker.class.getSimpleName();

	public static final String WALKABLE_KEY = "walkable";
	public static final String INDIVIDUAL_POINTS = "points";
	public static final String BOX_RECTS = "rects";

	JSONObject jsonGridRoot = new JSONObject();

	public final GoogleMap mGoogleMap;
	public MapGrid mGrid;

	// TODO: 15-08-16 improve application mode management
	boolean isEditingMap = false;
	boolean createBoxMode = false;
	boolean deletePathMode = false;

	// this is only used for holding onto ground overlays until removed from map, (NOT List itself)
	private List<GroundOverlay> boxRectList = new ArrayList<>();

	public PathMaker(CustomMapFragment mapFragment, GoogleMap map, final MapGrid grid, final View editButton,
	                 final View exportButton, final View boxButton, final View deleteButton) {
		this.mGoogleMap = map;
		this.mGrid = grid;

		// create the json tree structure
		try {
			jsonGridRoot.put(WALKABLE_KEY, new JSONObject());
			jsonGridRoot.getJSONObject(WALKABLE_KEY).put(INDIVIDUAL_POINTS, new JSONArray());
			jsonGridRoot.getJSONObject(WALKABLE_KEY).put(BOX_RECTS, new JSONArray());
		} catch (JSONException e) {
			e.printStackTrace();
		}

		/* set input listeners on views */

		mapFragment.setOnDragListener(this);

		editButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				isEditingMap = !isEditingMap;

				//
				exportButton.setVisibility(isEditingMap ? View.VISIBLE : View.INVISIBLE);
				boxButton.setVisibility(isEditingMap ? View.VISIBLE : View.INVISIBLE);
				deleteButton.setVisibility(isEditingMap ? View.VISIBLE : View.INVISIBLE);

				((ImageButton) v).setImageResource(isEditingMap ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_menu_edit);

				mGoogleMap.getUiSettings().setScrollGesturesEnabled(!isEditingMap);

				// hide / show the edit layouts
				try {
					JSONObject walkableNode = jsonGridRoot.getJSONObject(WALKABLE_KEY);
					if (isEditingMap && walkableNode.getJSONArray(BOX_RECTS).length() == 0
							&& walkableNode.getJSONArray(INDIVIDUAL_POINTS).length() == 0) { // if their is previously something drawn on screen, then we won't override it

						// load the json file, and load the edit gizmos

						jsonGridRoot = new JSONObject(MapTools.loadFile(v.getContext(), "map_grid.json"));

						walkableNode = jsonGridRoot.getJSONObject(WALKABLE_KEY);

						// draw green box rects
						JSONArray boxRects = walkableNode.getJSONArray(BOX_RECTS);
						for (int i = 0; i < boxRects.length(); i++) {
							String[] boxString = boxRects.getString(i).split(",");
							Point pointA = new Point(Integer.parseInt(boxString[0]), Integer.parseInt(boxString[1]));
							Point pointB = new Point(Integer.parseInt(boxString[2]), Integer.parseInt(boxString[3]));

							PointF sdf = getXYDist(MercatorProjection.fromPointToLatLng(mGrid.getNode(pointA).projCoords), MercatorProjection.fromPointToLatLng(mGrid.getNode(pointB).projCoords));

							boxRectList.add(
									mGoogleMap.addGroundOverlay(new GroundOverlayOptions()
											.anchor(0f, 0f)
											.zIndex(10000)
											.position(MercatorProjection.fromPointToLatLng(mGrid.getNode(pointA).projCoords), sdf.x, sdf.y)
											.image(BitmapDescriptorFactory.fromResource(R.drawable.box_rect_outline))
											.transparency(0.2f))
							);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// export map path
		exportButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					for (int x = 0; x < mGrid.mapGridSizeX; x++)
						for (int y = 0; y < mGrid.mapGridSizeY; y++)
							if (mGrid.getNode(x, y).isWalkable)
								jsonGridRoot.getJSONObject(WALKABLE_KEY).getJSONArray(INDIVIDUAL_POINTS).put(x + "," + y);

					// create file
					MapTools.createFile("map_grid.json", jsonGridRoot.toString(4));
					Toast.makeText(v.getContext(), "Grid exported!", Toast.LENGTH_SHORT).show();

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		});

		boxButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createBoxMode = !createBoxMode;
				v.setBackgroundResource(createBoxMode ? R.drawable.box_rect_outline : R.drawable.sfunetsecuredot);

				if (createBoxMode) {
					deletePathMode = false;
					deleteButton.setBackgroundResource(R.drawable.sfunetsecuredot);
				}
			}
		});

		deleteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				deletePathMode = !deletePathMode;
				v.setBackgroundResource(deletePathMode ? R.drawable.box_rect_outline : R.drawable.sfunetsecuredot);

				if (deletePathMode) {
					createBoxMode = false;
					boxButton.setBackgroundResource(R.drawable.sfunetsecuredot);
				}
			}
		});
	}


	Point mTmpBoxDragStartGridIndices;
	@Nullable
	GroundOverlay mTmpSelectedArea;
	boolean boxCreated;

	@Override
	public void onDrag(MotionEvent ev) {
		if (!isEditingMap)
			return;

		Point currentDragPointIndices = getGridIndices(ev.getX(), ev.getY());

		switch (ev.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:

				boxCreated = false; // no box has been created yet

				if (createBoxMode) {
					mTmpBoxDragStartGridIndices = currentDragPointIndices;
					mTmpSelectedArea = mGoogleMap.addGroundOverlay(new GroundOverlayOptions()
									.position(MercatorProjection.fromPointToLatLng(mGrid.getNode(currentDragPointIndices).projCoords), 1000)
									.image(BitmapDescriptorFactory.fromResource(R.drawable.box_rect_outline))
									.transparency(0.2f)
									.zIndex(10000)
									.anchor(0, 0)
					);
				}
				break;

			case MotionEvent.ACTION_UP:
				if (createBoxMode && boxCreated) {
					// add box to json tree
					try {
						// TODO: 15-08-16 store data in a more JSON fashioned way
						String boxString = mTmpBoxDragStartGridIndices.x + "," + mTmpBoxDragStartGridIndices.y + "," + currentDragPointIndices.x + "," + currentDragPointIndices.y;
						jsonGridRoot.getJSONObject(WALKABLE_KEY).getJSONArray(BOX_RECTS).put(boxString);

						// box was created, so store its ground overlay
						boxRectList.add(mTmpSelectedArea);

						Log.i(TAG, "boxRectList size: " + boxRectList.size() + ", json box array size: " + jsonGridRoot.getJSONObject(WALKABLE_KEY).getJSONArray(BOX_RECTS).length());
					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					if (mTmpSelectedArea != null)
						mTmpSelectedArea.remove();
				}
				mTmpSelectedArea = null;
				break;

			case MotionEvent.ACTION_MOVE:

				if (createBoxMode) {
					boxCreated = !currentDragPointIndices.equals(mTmpBoxDragStartGridIndices);
					try {
						PointF dims = getXYDist(
								MercatorProjection.fromPointToLatLng(
										mGrid.getNode(mTmpBoxDragStartGridIndices).projCoords
								),
								MercatorProjection.fromPointToLatLng(
										mGrid.getNode(currentDragPointIndices).projCoords
								)
						);

						if (dims != null && mTmpSelectedArea != null)
							mTmpSelectedArea.setDimensions(dims.x, dims.y);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (deletePathMode) {
					// TODO: 15-08-16 delete blue points

					try {
						for (int i = 0; i < boxRectList.size(); i++) {
							if (boxRectList.get(i).getBounds().contains(mGoogleMap.getProjection().fromScreenLocation(new Point((int) ev.getX(), (int) ev.getY())))) {
								boxRectList.get(i).remove();
								boxRectList.remove(i);
								jsonGridRoot.getJSONObject(WALKABLE_KEY).getJSONArray(BOX_RECTS).remove(i);
								break;
							}
						}
						Log.i(TAG, "boxRectList size: " + boxRectList.size() + ", json box array size: " + jsonGridRoot.getJSONObject(WALKABLE_KEY).getJSONArray(BOX_RECTS).length());
					} catch (JSONException e) {
						e.printStackTrace();
					}

				} else {
					// add map path marker
					mGoogleMap.addMarker(new MarkerOptions()
							.position(MercatorProjection.fromPointToLatLng(mGrid.getNode(currentDragPointIndices).projCoords))
							.icon(BitmapDescriptorFactory.fromResource(R.drawable.map_path))
							.anchor(0.5f, 0.5f));

					// set to walkable point
					mGrid.getNode(currentDragPointIndices).setWalkable(true);
				}
				break;
			default:
				break;
		}
	}


	/**
	 * Given the screen coordinate it computes the closes grid node to the screen point
	 *
	 * @param screenX - x value of the screen coordinate
	 * @param screenY - y value of the screen coordinate
	 * @return - The (x, y) indices of the grid array
	 */
	public Point getGridIndices(float screenX, float screenY) {

		PointF mapPoint = MercatorProjection.fromLatLngToPoint(
				mGoogleMap.getProjection().fromScreenLocation(new Point((int) screenX, (int) screenY)));
		PointF gridFirstPoint = mGrid.getNode(0, 0).projCoords;

		// convert dist to grid index and return the position of the node at that index
		return new Point((int) ((mapPoint.x - gridFirstPoint.x) / MapGrid.EACH_POINT_DIST), (int) ((mapPoint.y - gridFirstPoint.y) / MapGrid.EACH_POINT_DIST));
	}

	/**
	 * Calculate the horizontal and vertical distance between points a and b
	 *
	 * @param dragStartCoordinates   - screen point
	 * @param dragCurrentCoordinates - indices
	 * @return - {@link Point} object containing the horizontal and vertical distance
	 */
	private PointF getXYDist(LatLng dragStartCoordinates, LatLng dragCurrentCoordinates) {

		// calculate the middle corner point
		PointF dragStart = MercatorProjection.fromLatLngToPoint(dragStartCoordinates);
		PointF dragCurrent = MercatorProjection.fromLatLngToPoint(dragCurrentCoordinates);

		// the middle corner point
		dragCurrent.set(dragCurrent.x, dragStart.y);

		LatLng middleCornerPoint = MercatorProjection.fromPointToLatLng(dragCurrent);

		// horizontal distance
		float hDist = MapTools.LatLngDistance(dragStartCoordinates.latitude, dragStartCoordinates.longitude, middleCornerPoint.latitude, middleCornerPoint.longitude);

		// vertical distance
		float vDist = MapTools.LatLngDistance(dragCurrentCoordinates.latitude, dragCurrentCoordinates.longitude, middleCornerPoint.latitude, middleCornerPoint.longitude);

		return new PointF(hDist, vDist);
	}

}