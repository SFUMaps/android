package me.gurinderhans.sfumaps.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.PointF;
import android.graphics.drawable.PictureDrawable;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;
import com.jakewharton.disklrucache.DiskLruCache;
import com.larvalabs.svgandroid.SVGBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Pattern;

import me.gurinderhans.sfumaps.R;
import me.gurinderhans.sfumaps.devtools.wifirecorder.Keys;
import me.gurinderhans.sfumaps.ui.MainActivity;
import me.gurinderhans.sfumaps.ui.SVGTileProvider;

/**
 * Created by ghans on 2/9/15.
 */
public class MapTools {

	public static final String TAG = MapTools.class.getSimpleName();

	private static String[] mapTileAssets; // tile assets list cache

	// empty constructor
	private MapTools() {
	    /* To make sure this class cannot be instantiated */
	}


	//
	// MARK: Wifi Data parsing methods
	//

	/**
	 * @param dataArray - the data array that we are splitting by keys
	 * @param keyIndex  - the index of the key in that array object
	 * @return - return the [Array] separated by keys
	 */
	public static HashMap<String, ArrayList<HashMap<String, Object>>> separateByKeys(
			ArrayList<HashMap<String, Object>> dataArray, String keyIndex) {

		HashMap<String, ArrayList<HashMap<String, Object>>> separated = new HashMap<>();

		for (HashMap<String, Object> row : dataArray) {

			String key = (String) row.get(keyIndex);

			if (!separated.containsKey(key)) {
				separated.put(key, new ArrayList<HashMap<String, Object>>());
			}

			separated.get(key).add(row);
		}

		return separated;
	}

	/**
	 * @param input - input data to remove dups from
	 */
	public static void removeDups(ArrayList<HashMap<String, Object>> input) {

		Collections.sort(input, new Comparator<HashMap<String, Object>>() {
			@Override
			public int compare(HashMap<String, Object> lhs, HashMap<String, Object> rhs) {
				int firstValue = Integer.parseInt(lhs.get(Keys.KEY_RSSI).toString());
				int secondValue = Integer.parseInt(rhs.get(Keys.KEY_RSSI).toString());
				return (secondValue < firstValue ? -1 : (secondValue == firstValue ? 0 : 1));
			}
		});

		int count = input.size();

		// ___
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				HashMap<String, Object> a = input.get(i);
				HashMap<String, Object> b = input.get(j);

				boolean ssid = a.get(Keys.KEY_SSID).equals(b.get(Keys.KEY_SSID));
				boolean bssid = a.get(Keys.KEY_BSSID).equals(b.get(Keys.KEY_BSSID));

				if (ssid && bssid) {
					input.remove(j--);
					count--;
				}
			}
		}
	}

	/**
	 * @param points - list of points
	 * @return - return the centroid point (mean value)
	 */
	public static PointF getCentroid(ArrayList<PointF> points) {

		float Sx = 0, Sy = 0;

		for (PointF point : points) {
			Sx += point.x;
			Sy += point.y;
		}

		Sx /= points.size();
		Sy /= points.size();


		return new PointF(Sx, Sy);
	}

	/**
	 * Returns true if the given tile file exists as a local asset.
	 */
	public static boolean hasTileAsset(Context context, String filename) {

		// cache the list of available files
		if (mapTileAssets == null) {
			try {
				mapTileAssets = context.getAssets().list(AppConfig.TILE_PATH);
			} catch (IOException e) {
				// no assets
				mapTileAssets = new String[0];
			}
		}

		// search for given filename
		for (String s : mapTileAssets) {
			if (s.equals(filename)) {
				return true;
			}
		}
		return false;
	}


	//
	// MARK: Tile Assets methods
	//

	/**
	 * Copy the file from the assets to the map tiles directory if it was
	 * shipped with the APK.
	 */
	public static boolean copyTileAsset(Context context, String filename) {
		if (!hasTileAsset(context, filename)) {
			// file does not exist as asset
			return false;
		}

		// copy file from asset to internal storage
		try {
			InputStream is = context.getAssets().open(AppConfig.TILE_PATH + File.separator + filename);
			File f = getTileFile(context, filename);
			FileOutputStream os = new FileOutputStream(f);

			byte[] buffer = new byte[1024];
			int dataSize;
			while ((dataSize = is.read(buffer)) > 0) {
				os.write(buffer, 0, dataSize);
			}
			os.close();
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	/**
	 * Return a {@link File} pointing to the storage location for map tiles.
	 */
	public static File getTileFile(Context context, String filename) {
		File folder = new File(context.getFilesDir(), AppConfig.TILE_PATH);
		if (!folder.exists()) {
			folder.mkdirs();
		}
		return new File(folder, filename);
	}

	public static void removeUnusedTiles(Context mContext, final ArrayList<String> usedTiles) {
		// remove all files are stored in the tile path but are not used
		File folder = new File(mContext.getFilesDir(), AppConfig.TILE_PATH);
		File[] unused = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return !usedTiles.contains(filename);
			}
		});

		if (unused != null) {
			for (File f : unused) {
				f.delete();
			}
		}
	}

	// returns an array list of pairs which match the file with the zoom level for the map
	public static ArrayList<Pair<String, Picture>> getBaseMapTiles(Context c) {
		try {
			ArrayList<Pair<String, Picture>> tileFiles = new ArrayList<>();
			String[] files = c.getAssets().list(AppConfig.TILE_PATH);

			// copy tiles to internal storage
			// TODO: check for failure in copying
			for (String f : files)
				copyTileAsset(c, f);

			// TODO: make sure the file at index 0 is the one with lowest zoom
			Picture currentFile = new SVGBuilder().readFromInputStream(
					new FileInputStream(getTileFile(c, files[0]))).build().getPicture();

			// map zoom level -> [0,25) just to keep out of bounds for safety
			for (int i = 0; i < 25; i++) {
				for (String f : files) {
					String zoom_floor_val = f.split(Pattern.quote("."))[0]; // extract zoom and floor level values by removing extension

					// if file zoom value matches with the loop value
					if (zoom_floor_val.split("-")[SVGTileProvider.FILE_NAME_ZOOM_LVL_INDEX].equals(i + ""))
						currentFile = new SVGBuilder().readFromInputStream(
								new FileInputStream(getTileFile(c, f))).build().getPicture();
				}
				tileFiles.add(Pair.create(i + "", currentFile));
			}
			return tileFiles;

		} catch (IOException e) {
			e.printStackTrace();
			Log.d(MainActivity.TAG, "Could not create Tile Provider. Unable to list map tile files directory");
		}

		return null;
	}


	//
	// MARK: Map Maker Labels methods
	//

	public static Marker addTextMarker(Context c, GoogleMap map, PointF screenLocation, Bitmap textIcon, float markerRotation) {
		//
		return map.addMarker(new MarkerOptions()
				.position(MercatorProjection.fromPointToLatLng(screenLocation))
				.icon(BitmapDescriptorFactory.fromBitmap(textIcon))
				.rotation(markerRotation)
				.flat(true).draggable(true)
				.title("Position")
				.snippet(screenLocation.toString()));
	}

	public static Bitmap combineLabelBitmaps(Bitmap a, Bitmap b, MapLabelIconAlign alignment) {

		if (alignment == MapLabelIconAlign.TOP) {
			Bitmap bmp = Bitmap.createBitmap(b.getWidth(), a.getHeight() + b.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bmp);
			canvas.drawBitmap(a, b.getWidth() / 2f - (a.getWidth() / 2), 0f, null);
			canvas.drawBitmap(b, 0, a.getHeight(), null);
			return bmp;
		}

		int width = a.getWidth() + b.getWidth() + 5; // extra padding of 5
		int height = (alignment == MapLabelIconAlign.LEFT) ? a.getHeight() : b.getHeight();

		Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bmp);

		if (alignment == MapLabelIconAlign.LEFT) {
			canvas.drawBitmap(a, 0f, 0f, null);
			canvas.drawBitmap(b, a.getWidth() + 5, -6f, null);
		} else if (alignment == MapLabelIconAlign.RIGHT) {
			canvas.drawBitmap(b, 0f, 0f, null);
			canvas.drawBitmap(a, b.getWidth() + 5, 6f, null);
		}


		return bmp;
	}

	public static Bitmap createPureTextIcon(Context c, String text,
	                                        Pair<Integer, Integer> rotation) {

		IconGenerator generator = new IconGenerator(c);
		generator.setBackground(null);
		generator.setTextAppearance(R.style.MapTextRawStyle);
		generator.setContentPadding(0, 0, 0, 0);

		if (rotation != null) {
			generator.setRotation(rotation.first);
			generator.setContentRotation(rotation.second);
		}

		return generator.makeIcon(text);
	}

	public static Bitmap pictureDrawableToBitmap(Picture picture) {
		PictureDrawable pd = new PictureDrawable(picture);
		Bitmap bitmap = Bitmap.createBitmap(pd.getIntrinsicWidth(), pd.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.drawPicture(pd.getPicture());
		return bitmap;
	}

	public static Marker addTextAndIconMarker(Context c, GoogleMap map, PointF screenLocation,
	                                          Bitmap textIcon, float rotation, Integer imageIconId,
	                                          MapLabelIconAlign imageIconAlignment) {


		// get passed in icon or use the default one
		int iconId = (imageIconId == null) ? R.drawable.location_marker : imageIconId;

		Bitmap markerIcon = pictureDrawableToBitmap(new SVGBuilder().readFromResource(c.getResources(), iconId)
				.build().getPicture());

		// combine text and image
		markerIcon = combineLabelBitmaps(markerIcon, textIcon, imageIconAlignment);

		Pair<Float, Float> labelAnchor = new Pair<>(0f, 0f);

		if (imageIconAlignment == MapLabelIconAlign.LEFT)
			labelAnchor = new Pair<>(0f, 1f);

		else if (imageIconAlignment == MapLabelIconAlign.RIGHT)
			labelAnchor = new Pair<>(1f, 1f);

		else if (imageIconAlignment == MapLabelIconAlign.TOP)
			labelAnchor = new Pair<>(0.5f, 0.5f);


		// add icon image on actual point
		return map.addMarker(new MarkerOptions()
						.position(MercatorProjection.fromPointToLatLng(screenLocation))
						.icon(BitmapDescriptorFactory.fromBitmap(markerIcon))
						.anchor(labelAnchor.first, labelAnchor.second)
						.rotation(rotation)
		);
	}

	// checks if point B is within point A of `range`
	public static boolean inRange(PointF a, PointF b, float range) {
		return Math.abs(a.x - b.x) <= range && Math.abs(a.y - b.y) <= range;
	}

	public static void createFile(String filename, String fileData) {
		File file = new File(Environment.getExternalStorageDirectory() + File.separator + filename);

		try {
			OutputStream fo = new FileOutputStream(file);
			fo.write(fileData.getBytes());
			fo.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String loadFile(Context context, String filename) {
		BufferedReader reader = null;
		StringBuilder buffer = new StringBuilder();
		try {
			reader = new BufferedReader(
					new InputStreamReader(context.getAssets().open(filename)));

			String mLine;
			while ((mLine = reader.readLine()) != null) {
				buffer.append(mLine);
			}
		} catch (IOException e) {
			//log the exception
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					//log the exception
				}
			}
		}

		return buffer.toString();
	}

	/**
	 * @param lat1
	 * @param lng1
	 * @param lat2
	 * @param lng2
	 * @return - distance in meters
	 */
	public static float LatLngDistance(double lat1, double lng1, double lat2, double lng2) {
		double earthRadius = 6371000; //meters
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
				Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
						Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return (float) (earthRadius * c);
	}

	// enum for placing label icon on which side
	public enum MapLabelIconAlign {
		TOP, LEFT, RIGHT
	}


	//
	// MARK: Map cache
	//

	private static final int MAX_DISK_CACHE_BYTES = 1024 * 1024 * 2; // 2MB

	public static DiskLruCache openDiskCache(Context c) {
		File cacheDir = new File(c.getCacheDir(), "tiles");
		try {
			return DiskLruCache.open(cacheDir, 1, 3, MAX_DISK_CACHE_BYTES);
		} catch (IOException e) {
			Log.e(TAG, "Couldn't open disk cache.");

		}
		return null;
	}
	public static void clearDiskCache(Context c) {
		DiskLruCache cache = openDiskCache(c);
		if (cache != null) {
			try {
				Log.d(TAG, "Clearing map tile disk cache");
				cache.delete();
				cache.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

}