/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */
package com.wikonos.views;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.wikonos.fingerprint.R;
import com.wikonos.fingerprint.activities.DefaultActivity;
import com.wikonos.fingerprint.activities.LocationListActivity.MapData;
import com.wikonos.logs.ErrorLog;
import com.wikonos.utils.BitmapCache;
import com.wikonos.utils.DataPersistence;

/**
 * Map view
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class MapView extends View {

	protected static Toast alert;

	protected BitmapCache mBitmapCache;
	
	private static int count = 0;
	/**
	 * Bitmap downloader
	 */
	class BitmapDownloaderTask extends AsyncTask<String, Void, Void> {
		protected int x, y, scale;

		protected StringBuilder error = new StringBuilder();

		public BitmapDownloaderTask(int scale, int x, int y) {
			//Log.d("Fingerprint", "Started " + x + "," + y);
			this.scale = scale;
			this.x = x;
			this.y = y;
		}

		@Override
		// Actual download method, run in the task thread
		protected Void doInBackground(String... params) {
			Bitmap[][] bitmap = mImageViewReference.get(scale);
			Bitmap result = null;
			
			boolean fromNet = false;
			//See if this tile exists in the cache, otherwise download
			if(mBitmapCache != null && mBitmapCache.get(x,y) != null){
				result = mBitmapCache.get(x,y);
			}
			
			if(result == null){
				fromNet = true;
				result = downloadBitmap(params[0], error);
				mBitmapCache.set(x,y,result);
			}
			
			Log.d("Fingerprint", (fromNet ? "Net" : "Cache") + ", null? " + (result == null ? "yes" : "no"));
			
			if (!isCancelled())
				bitmap[x][y] = result;
			//Log.d("Fingerprint", "Finished " + count++ + ", " + x + "," + y);
			return null;
		}

		@Override
		// Once the image is downloaded, associates it to the imageView
		protected void onPostExecute(Void v) {
			if (!isCancelled()) {
				if (mImageViewReference.get(scale)[x][y] != null) {
					invalidate();
				}
				if (error.length() > 0) {
					if (alert == null)
						alert = Toast.makeText(getContext(), error, 500);
					alert.setText(error);
					alert.show();
				}
			}
		}
	}

	/**
	 * Modes
	 */
	public static final int DRAG = 1;

	public static final int NONE = 0;

	public static final int ZOOM = 2;

	/**
	 * Downloading bitmap
	 * 
	 * @param url
	 * @return
	 */
	static Bitmap downloadBitmap(String url, StringBuilder error) {
		final AndroidHttpClient client = AndroidHttpClient
				.newInstance("Android");
		final HttpGet getRequest = new HttpGet(url);

		try {
			HttpResponse response = client.execute(getRequest);
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != HttpStatus.SC_OK) {
				Log.w("ImageDownloader", "Error " + statusCode
						+ " while retrieving bitmap from " + url);
				ErrorLog.msg("ImageDownload: Error " + statusCode
						+ " while retrieving bitmap from " + url);
				error.append("Error " + statusCode
						+ " while retrieving bitmap from " + url);
				return null;
			}

			final HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream inputStream = null;
				try {
					inputStream = entity.getContent();
					return BitmapFactory.decodeStream(inputStream);
				} catch (Exception e) {
					Log.w("ImageDownloader",
							"Error while decoding bitmap from " + url);
					ErrorLog.msg("ImageDownloader: Error while decoding bitmap from "
							+ url);
					throw e;
				} finally {

					if (inputStream != null) {
						inputStream.close();
					}
					entity.consumeContent();
				}
			}
		} catch (Exception e) {
			getRequest.abort();
			e.printStackTrace();
		} finally {
			if (client != null) {
				client.close();
			}
		}
		return null;
	}

	@SuppressWarnings("unused")
	private static float dist(PointF point, MotionEvent event) {
		float x = point.x - event.getX();
		float y = point.y - event.getY();
		return FloatMath.sqrt(x * x + y * y);
	}

	private static void midPoint(PointF point, MotionEvent event) {
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

	private static float spacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	public float mDistX;

	public float mDistY;

	// first marker point
	public PointF mMarker = new PointF(-1, -1);

	// second marker point
	public PointF mMarker2 = new PointF(-1, -1);

	/**
	 * all tasks
	 */
	protected WeakHashMap<String, BitmapDownloaderTask> mImageTaskDownloader = new WeakHashMap<String, BitmapDownloaderTask>();
	/**
	 * all images
	 */
	protected WeakHashMap<Integer, Bitmap[][]> mImageViewReference = new WeakHashMap<Integer, Bitmap[][]>();
	protected boolean isTouchInPointer;

	/**
	 * Info about map
	 */
	protected MapData mData;
	protected Matrix mMapM = new Matrix();
	protected PointF mMid = new PointF();
	/** Variables for TouchEvent */

	protected int mMode = NONE;
	protected float mOldDist;
	protected Matrix mPM = new Matrix();

	protected Bitmap mPointerBegin, mPointerEnd;

	protected RectF mPointerRect;

	protected ArrayList<HashMap<String, String>> mPoints;

	protected Matrix mSavedMapM = new Matrix();

	protected Matrix mSavedPointM = new Matrix();

	protected Matrix mSavedXM = new Matrix();

	protected float mScale;

	protected ArrayList<Integer> mScales;
	protected PointF mStart = new PointF();
	protected long mStartClick;

	protected float mStartX, mStartY;

	protected Matrix mXM = new Matrix();

	protected RectF mXPointerRect;
	protected float tileSizeX = 240f;

	protected float tileSizeY = 160f;

	protected Toast mToast = Toast.makeText(getContext(), "", 500);

	private boolean mCanBePointed = false;

	/**
	 * current map scale used
	 * 
	 */
	private float currentMapScale = 1f;

	private float currentScale = 1f;

	/**
	 * current tile scale
	 * 
	 */
	private int currentTileScale;

	private boolean detectedViewSizes = false;

	private int mSelectedPoint = -1;

	private boolean mIsFirstMarker = true;

	/**
	 * 75 => 1 100 => 1.67
	 */
	private HashMap<Integer, Float> scales = new HashMap<Integer, Float>();

	public MapView(Context context) {
		super(context);
		init();
	}

	public MapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public MapView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	/**
	 * 
	 * Sets pointer mode
	 * 
	 * @param flag
	 */
	public void canBePointed(boolean flag) {
		mCanBePointed = flag;

	}

	/**
	 * Sets first pointer coords
	 */
	public void setFirstMarker() {
		mIsFirstMarker = true;
	}

	/**
	 * Sets second pointer coords
	 */
	public void setLastMarker() {
		mIsFirstMarker = false;
	}

	public int getMarkerX() {
		return (int) (mIsFirstMarker ? mMarker.x : mMarker2.x);
	}

	public int getMarkerY() {
		return (int) (mIsFirstMarker ? mMarker.y : mMarker2.y);
	}

	public int getNewMarkerX() {
		RectF tmp = new RectF();
		mPM.mapRect(tmp, mPointerRect);
		float left = tmp.left;
		mXM.mapRect(tmp, mXPointerRect);
		left -= tmp.left;
		left /= getScale();
		return (int) (mStartX + left);
	}

	public int getNewMarkerY() {
		RectF tmp = new RectF();
		mPM.mapRect(tmp, mPointerRect);
		float top = tmp.top;
		mXM.mapRect(tmp, mXPointerRect);
		top -= tmp.top;
		top /= getScale();
		return (int) (mStartY + top);

	}

	/**
	 * Zoom, move, etc operations
	 */
	public boolean onTouchEvent(MotionEvent event) {

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			mSavedMapM.set(mMapM);
			mSavedPointM.set(mPM);
			mSavedXM.set(mXM);
			mStart.set(event.getX(), event.getY());
			mMode = DRAG;
			mStartClick = System.currentTimeMillis();
			break;
		case MotionEvent.ACTION_POINTER_UP:
			mMode = NONE;
			break;
		case MotionEvent.ACTION_CANCEL:
			break;
		case MotionEvent.ACTION_UP:
			long diff = System.currentTimeMillis() - mStartClick;
			/**
			 * Finds click coordinates with reverse matrix
			 */
			float[] coord = new float[2];
			coord[0] = event.getX();
			coord[1] = event.getY();

			Matrix matrix = new Matrix();
			mMapM.invert(matrix);
			matrix.mapPoints(coord);

			boolean isClick = dist(mStart, event) < 20;

			/**
			 * Detect marker point
			 */
			if (isClick && diff <= 500) {
				coord[0] /= currentMapScale;
				coord[1] /= currentMapScale;
				if (mIsFirstMarker) {
					mMarker = new PointF(coord[0], coord[1]);
				} else {
					mMarker2 = new PointF(coord[0], coord[1]);
				}
			}

			if (!mCanBePointed && isClick && diff <= 500) {
				if (mPoints != null) {
					float[] vals = new float[9];
					mMapM.getValues(vals);

					int z = 0;
					for (HashMap<String, String> data : mPoints) {
						int x = Float.valueOf(data.get("point_x")).intValue()
								- Float.valueOf(6 / currentMapScale).intValue();
						int y = Float.valueOf(data.get("point_y")).intValue()
								- Float.valueOf(6 / currentMapScale).intValue();
						int x2 = x
								+ Float.valueOf(24 / currentMapScale)
								.intValue();
						int y2 = y
								+ Float.valueOf(24 / currentMapScale)
								.intValue();

						/**
						 * Check it
						 */
						RectF tmp = new RectF(x, y, x2, y2);
						if (tmp.contains(coord[0], coord[1])) {
							mToast.setText(data.get("point_name"));
							mToast.show();
							mSelectedPoint = z;
						}
						z++;
					}
				}
			}
			mMode = NONE;
			break;

		case MotionEvent.ACTION_POINTER_DOWN:
			if (!isTouchInPointer) {
				mOldDist = spacing(event);
				if (mOldDist > 10f) {
					mSavedMapM.set(mMapM);
					mSavedPointM.set(mPM);
					mSavedXM.set(mXM);
					midPoint(mMid, event);
					mMode = ZOOM;
				}
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (mMode == DRAG) {
				mDistX = event.getX() - mStart.x;
				mDistY = event.getY() - mStart.y;
				mPM.set(mSavedPointM);
				mPM.postTranslate(mDistX, mDistY);
				if (!isTouchInPointer) {
					mMapM.set(mSavedMapM);
					mXM.set(mSavedXM);
					mMapM.postTranslate(mDistX, mDistY);
					mXM.postTranslate(mDistX, mDistY);
				}
			} else if (mMode == ZOOM && !isTouchInPointer) {
				float newDist = spacing(event);
				if (newDist > 10.0f) {

					currentScale = newDist / mOldDist;
					Iterator<Integer> it = scales.keySet().iterator();

					/**
					 * extract current matrix scale
					 */
					float[] values = new float[9];
					mMapM.getValues(values);

					boolean requiredNewZoom = false;

					Integer zoom = null;

					while (it.hasNext()) {
						zoom = it.next();
						if (values[Matrix.MSCALE_X] > 1) {
							/**
							 * increase zoom
							 */
							if (values[Matrix.MSCALE_X] > scales.get(zoom)
									&& scales.get(zoom) > 1) {
								requiredNewZoom = true;
								while (it.hasNext()) {
									Integer zoomN = it.next();
									if (values[Matrix.MSCALE_X] > zoomN) {
										zoom = zoomN;
										continue;
									}
									break;
								}
							}

						} else if (values[Matrix.MSCALE_X] < 1) {
							/**
							 * decrease zoom
							 */
							if (values[Matrix.MSCALE_X] < scales.get(zoom)
									&& scales.get(zoom) < 1) {
								requiredNewZoom = true;
								break;
							}
						}
					}
					if (requiredNewZoom) {
						mOldDist = spacing(event);
						// currentScale = scales.get(zoom);
						loadTiles(zoom);
						mPM.reset();
						mMapM.reset();
						mXM.reset();
						mSavedPointM.reset();
						mSavedMapM.reset();
						mSavedXM.reset();
						mMapM.postTranslate(values[Matrix.MTRANS_X],
								values[Matrix.MTRANS_Y]);
						mSavedMapM.postTranslate(values[Matrix.MTRANS_X],
								values[Matrix.MTRANS_Y]);
						invalidate();
						return true;
					}

					mPM.set(mSavedPointM);
					mMapM.set(mSavedMapM);
					mXM.set(mSavedXM);
					mPM.postScale(currentScale, currentScale, mMid.x, mMid.y);
					mMapM.postScale(currentScale, currentScale, mMid.x, mMid.y);
					mXM.postScale(currentScale, currentScale, mMid.x, mMid.y);
				}
			}
			break;
		}
		invalidate();
		return true;
	}

	public void resetMarkers() {
		mMarker = new PointF(-1, -1);
		mMarker2 = new PointF(-1, -1);
		invalidate();
	}

	/**
	 * Set map data
	 * 
	 * @param data
	 */
	public void setData(MapData data) {
		mData = data;

		mScales = new ArrayList<Integer>(data.zoom.keySet());

		Collections.sort(mScales);
	}

	/**
	 * Set points on map
	 * 
	 * @param points
	 */
	public void setPoints(ArrayList<HashMap<String, String>> points) {
		mPoints = points;
		invalidate();
	}

	/**
	 * Gets scale
	 */
	protected float getScale() {
		float[] matrixValues = new float[9];
		mMapM.getValues(matrixValues);
		return matrixValues[Matrix.MSCALE_X];
	}

	/**
	 * Init
	 */
	protected void init() {
		Resources res = getContext().getResources();
		mPointerBegin = ((BitmapDrawable) res
				.getDrawable(R.drawable.arrow_blue)).getBitmap();
		mPointerEnd = ((BitmapDrawable) res.getDrawable(R.drawable.arrow_red))
				.getBitmap();
		mPointerRect = new RectF(0, 0, mPointerBegin.getWidth(),
				mPointerBegin.getHeight());
		mMapM.reset();
		mPM.reset();
		mSavedMapM.reset();
	}

	/**
	 * Loads required tiles
	 * 
	 * @param w
	 * @param h
	 */
	protected void loadRequiredTiles(int w, int h) {

		Collections.sort(mScales);

		Iterator<Integer> it = mScales.iterator();

		while (it.hasNext()) {
			Integer zoom = it.next();
			if (w * 0.9 <= mData.zoom.get(zoom).x * (int) tileSizeX) {

				currentTileScale = zoom;

				break;
			}

			if (!it.hasNext()) {
				currentTileScale = zoom;
			}
		}

		loadTiles(currentTileScale);
	}

	/**
	 * Loading tiles
	 * 
	 * @param scale
	 */
	protected void loadTiles(int scale) {

		/**
		 * Load Zoom
		 */
		try {

			if(mData == null || mData.zoom == null) return;

			int x = mData.zoom.get(scale).x;
			int y = mData.zoom.get(scale).y;

			currentTileScale = scale;

			currentMapScale = (float) scale / 100;

			Log.v("lss1", "load scale " + scale + " x,y " + x + "," + y);

			/**
			 * counting scales
			 */
			for (Integer zoom : mScales) {
				scales.put(
						zoom,
						Float.valueOf(mData.zoom.get(zoom).x * tileSizeX)
						/ (mData.zoom.get(currentTileScale).x * tileSizeX));
			}

			if (!mImageViewReference.containsKey(scale))
				mImageViewReference.put(scale, new Bitmap[x][y]);

			DataPersistence d = new DataPersistence(getContext());
			
			String imageName = mData.img.replace(".jpeg", "");

			//See if there is a cache for this map
			if(mBitmapCache == null) {
				mBitmapCache = new BitmapCache(getContext(), scale, imageName);
			}
			
			if(!mBitmapCache.getImageName().equals(imageName) || mBitmapCache.getScale() != scale) {
				mBitmapCache.clearCache(getContext());
			}
			
			ExecutorService es = Executors.newFixedThreadPool(16);
			
			for (int k = 0; k < x; k++) {
				for (int z = 0; z < y; z++) {
					String extension = "";
					if (mData.img.contains(".jpeg")) {
						extension = ".jpeg";
					} else if (mData.img.contains(".jpg")) {
						extension = ".jpg";
					} else if (mData.img.contains(".png")) {
						extension = ".png";
					}
					String imageName = mData.img.replace(extension, "");
					String url = new StringBuilder()
					.append(d.getServerName() + getContext().getString(R.string.plans_url))
					.append(imageName).append("/" + scale + "/")
					.append(imageName).append("-").append(k + 1)
					.append("-").append(z + 1).append(extension)
					.toString();

					if (!mImageTaskDownloader.containsKey(url)) {
						try {
							BitmapDownloaderTask task = new BitmapDownloaderTask(
									scale, k, z);
							//task.execute(url);
							task.executeOnExecutor(es, url);

							mImageTaskDownloader.put(url, task);
						} catch (Exception e) {

						}
					}
				}
			}
			
		} catch (Exception e) {
			Log.e(DefaultActivity.LOG_TAG, "error", e);
		}
	}

	/**
	 * Resets scales
	 */
	public void resetScale() {
		mMapM.reset();
	}

	/**
	 * Draw map
	 */
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (!detectedViewSizes) {
			loadRequiredTiles(getWidth(), getHeight());
			detectedViewSizes = true;
		}

		try {
			canvas.getMatrix().reset();
			//canvas.setMatrix(null);
		} catch (Exception e) {

		}

		canvas.save();

		Bitmap[][] images = mImageViewReference.get(currentTileScale);

		canvas.setMatrix(mMapM);
		float[] values = new float[9];
		mMapM.getValues(values);

		try {
			// Drawing tiles
			// 11, 12, 13 21, 22, 23 31, 32, 33
			for (int z = 0; z < mData.zoom.get(currentTileScale).x; z++) {
				for (int k = 0; k < mData.zoom.get(currentTileScale).y; k++) {
					if (images[z][k] != null) {
						canvas.drawBitmap(images[z][k], tileSizeX * z,
								tileSizeY * k, new Paint());
					}
				}
			}
		} catch (Exception e) {

		}

		canvas.restore();

		try {
			canvas.setMatrix(new Matrix());
			//canvas.setMatrix(null);
		} catch (Exception e) {

		}		
		
		//Display points
		if (mPoints != null) {
			int z = 0;
			float[][] lines = new float[mPoints.size()][2];
			for (HashMap<String, String> data : mPoints) {
				float x = Float.parseFloat(data.get("point_x"));
				float y = Float.parseFloat(data.get("point_y"));
				if (x != -1 && y != -1) {
					x = x * currentMapScale;
					y = y * currentMapScale;
					float[] p = new float[2];
					p[0] = x;
					p[1] = y;
					mMapM.mapPoints(p);
					canvas.drawBitmap(mPointerBegin, p[0] - 8, p[1] - 8,
							new Paint());
					if (z == mSelectedPoint) {
						canvas.drawBitmap(mPointerEnd, p[0] - 8, p[1] - 8,
								new Paint());
					}
					//Add this point to points array
					lines[z][0] = p[0];
					lines[z][1] = p[1];
				}

				z++;
			}

			//Draw Lines
			Paint linePaint = new Paint();
			linePaint.setStyle(Style.STROKE);
			linePaint.setStrokeWidth(4);
			linePaint.setColor(Color.BLUE);
			for(int i = 0; i < lines.length; ++i) {
				float x1 = lines[i][0];
				float y1 = lines[i][1];
				i++;
				float x2 = lines[i][0];
				float y2 = lines[i][1];
				canvas.drawLine(x1, y1, x2, y2, linePaint);
			}
		}


		//Display first marker

		if (mMarker.x != -1 && mMarker.y != -1) {
			if (mCanBePointed) {
				float x = mMarker.x * currentMapScale;
				float y = mMarker.y * currentMapScale;
				float[] p = new float[2];
				p[0] = x;
				p[1] = y;
				mMapM.mapPoints(p);
				canvas.drawBitmap(mPointerBegin, p[0] - 8, p[1] - 8,
						new Paint());
			}
		}

		//Display second marker
		if (mMarker2.x != -1 && mMarker2.y != -1) {
			if (mCanBePointed) {
				float x = mMarker2.x * currentMapScale;
				float y = mMarker2.y * currentMapScale;
				float[] p = new float[2];
				p[0] = x;
				p[1] = y;
				mMapM.mapPoints(p);
				canvas.drawBitmap(mPointerEnd, p[0] - 8, p[1] - 8, new Paint());
			}
		}
	}

	public void cancelLoadingFiles() {
		for (String url : mImageTaskDownloader.keySet()) {
			BitmapDownloaderTask task = mImageTaskDownloader.get(url);
			task.cancel(true);
		}
	}
}
