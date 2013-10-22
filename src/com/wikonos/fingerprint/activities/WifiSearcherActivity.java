/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */

package com.wikonos.fingerprint.activities;

import com.wikonos.fingerprint.R;
import com.wikonos.fingerprint.activities.LocationListActivity.MapData;
import com.wikonos.logs.ErrorLog;
import com.wikonos.logs.LogWriter;
import com.wikonos.logs.LogWriterSensors;
import com.wikonos.network.HttpLogSender;
import com.wikonos.network.INetworkTaskStatusListener;
import com.wikonos.network.NetworkManager;
import com.wikonos.network.NetworkResult;
import com.wikonos.network.NetworkTask;
import com.wikonos.network.WifiScanResult;
import com.wikonos.network.WifiSearcherAdapter;
import com.wikonos.network.WifiSnifferService;
import com.wikonos.utils.DataPersistence;
import com.wikonos.utils.Rotate3dAnimation;
import com.wikonos.views.MapView;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * WifiSearcher activity
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class WifiSearcherActivity extends DefaultActivity implements INetworkTaskStatusListener {

	/**
	 * View Modes
	 */
	public enum MODE {
		MAP, MAP_POINTS, SUMMARY
	}

	/**
	 * This class listens for the end of the first half of the animation. It then posts a new action
	 * that effectively swaps the views when the container is rotated 90 degrees and thus invisible.
	 */
	private final class DisplayNextView implements Animation.AnimationListener {
		private final int mPosition;

		private DisplayNextView(int position) {
			mPosition = position;
		}

		public void onAnimationEnd(Animation animation) {
			mViewport.post(new SwapViews(mPosition));
		}

		public void onAnimationRepeat(Animation animation) {
		}

		public void onAnimationStart(Animation animation) {
		}
	}

	/**
	 * This class is responsible for swapping the views and start the second half of the animation.
	 */
	private final class SwapViews implements Runnable {
		private final int mPosition;

		public SwapViews(int position) {
			mPosition = position;
		}

		public void run() {
			final float centerX = mViewport.getWidth() / 2.0f;
			final float centerY = mViewport.getHeight() / 2.0f;
			Rotate3dAnimation rotation;

			if (mPosition > -1) {
				mViewSummary.setVisibility(View.GONE);
				mViewMap.setVisibility(View.VISIBLE);
				mViewMap.requestFocus();

				rotation = new Rotate3dAnimation(90, 0, centerX, centerY, 310.0f, false);
			} else {
				mViewMap.setVisibility(View.VISIBLE);
				mViewSummary.setVisibility(View.VISIBLE);
				mViewSummary.requestFocus();

				rotation = new Rotate3dAnimation(90, 0, centerX, centerY, 310.0f, false);
			}

			rotation.setDuration(500);
			rotation.setFillAfter(true);
			rotation.setInterpolator(new DecelerateInterpolator());

			mViewport.startAnimation(rotation);
		}
	}

	/**
	 * Tag of submit logs to a server
	 */
	private static final int TAG_GET_POINTS = 2;

	/**
	 * network tag key
	 */
	private static final String TAG_KEY = "TAG";

	/**
	 * Tag of submit logs to a server
	 */
	private static final int TAG_LOG_SUBMIT = 1;

	/**
	 * Current mode
	 */
	public MODE mModeEnabled;

	public boolean mShowPoints = false;

	public TextView mTimelapsedTv, mNumberReadingsTv, mLocationTv, mMapTv, mApCountTv;

	/**
	 * Connection to Wi-Fi sniffer
	 */
	protected ServiceConnection connection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder binder) {
			mService = ((WifiSnifferService.ServiceBinder) binder).getService();
			mService.setDelegate(WifiSearcherActivity.this);
			mService.mResults = new Vector<WifiScanResult>();

			mAdapter.setData(mService == null ? null : mService.mResults);
			mAdapter.setTryCount(mService == null ? 0 : mService.mCount);

		}

		public void onServiceDisconnected(ComponentName className) {
			mService.stopScan();
			mService.unregisterReceiver(mService.getOnScan());
			mService = null;
		}
	};

	/**
	 * Views
	 */

	protected Button mBtnStartScan, mBtnSaveScan, mBtnClearScan, mBtnSubmitScan, mBtnMode;

	protected String mFilename;

	protected boolean mIsMapLoaded = false;

	protected ListView mLSearchResult;

	protected MapData mMapData;

	protected MapView mMapView;

	/**
	 * state of wifi access point scan completion
	 */
	protected boolean mScanCompleted = false;

	protected SensorManager mSensorManager;

	protected LinearLayout mViewport, mViewMap, mViewSummary;

	private WifiSearcherAdapter mAdapter;

	private Bundle mBundle;

	private ConnectivityManager mConManager;

	/**
	 * stores state of Mobile Internet connection before closing one
	 */
	private boolean mConnectionMobileEnabled;

	/**
	 * stores state of Wifi connection before closing one
	 */
	private boolean mConnectionWifiEnabled;

	private boolean mFlagScan = false;

	private SensorEventListener mSensorListener = new SensorEventListener() {

		private static final int X = 0;

		private static final int Y = 1;

		private static final int Z = 2;
		private Float azimuth = null;
		private HashMap<Integer, ArrayList<Object[]>> data =
				new HashMap<Integer, ArrayList<Object[]>>();

		private float[] mOldValues = null;

		private Long timestamp;

		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		public void onSensorChanged(SensorEvent event) {
			if (timestamp == null) {
				timestamp = System.currentTimeMillis();
			}

			if (!data.containsKey(event.sensor.getType())) {
				data.put(event.sensor.getType(), new ArrayList<Object[]>());
			}

			long sensorMilis = System.currentTimeMillis();

			if (Sensor.TYPE_MAGNETIC_FIELD == event.sensor.getType() && azimuth != null) {
				GeomagneticField field =
						new GeomagneticField(Double.valueOf(getLocationManager().getLatitude()).floatValue(),
								Double.valueOf(getLocationManager().getLongtitude()).floatValue(), Double.valueOf(
										getLocationManager().getAltitude()).floatValue(), System.currentTimeMillis());

				double trueHeading = azimuth + field.getDeclination();

				data.get(event.sensor.getType())
				.add(
						new Object[] {
								sensorMilis, event.values[0], event.values[1], event.values[2],
								trueHeading});
			} else if (Sensor.TYPE_ORIENTATION != event.sensor.getType()
					&& event.sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) {
				data.get(event.sensor.getType()).add(
						new Object[] {sensorMilis, event.values[0], event.values[1], event.values[2]});
			} else if (Sensor.TYPE_ORIENTATION == event.sensor.getType()
					&& event.sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) {
				azimuth = event.values[0];
				for (int i = 0; i < event.values.length; i++) {
					event.values[i] = (float) ((event.values[i] * Math.PI) / 180.0d);
				}
				event.values[X] -= 2 * Math.PI;
				float[] deltaRotationVector = null;

				if (mOldValues != null) {
					float axisX = event.values[X];
					float axisY = event.values[Y];
					float axisZ = event.values[Z];
					float dx = mOldValues[X] - axisX;
					float dy = mOldValues[Y] - axisY;
					float dz = mOldValues[Z] - axisZ;
					float omegaMagnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
					dx /= omegaMagnitude;
					dy /= omegaMagnitude;
					dz /= omegaMagnitude;
					deltaRotationVector = new float[4];
					float thetaOverTwo = omegaMagnitude / 2;
					float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
					float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
					deltaRotationVector[0] = sinThetaOverTwo * dx;
					deltaRotationVector[1] = sinThetaOverTwo * dy;
					deltaRotationVector[2] = sinThetaOverTwo * dz;
					deltaRotationVector[3] = cosThetaOverTwo;
					// is nan check
					deltaRotationVector[0] = Float.isNaN(deltaRotationVector[0]) ? 0 : deltaRotationVector[0];
					deltaRotationVector[1] = Float.isNaN(deltaRotationVector[1]) ? 0 : deltaRotationVector[1];
					deltaRotationVector[2] = Float.isNaN(deltaRotationVector[2]) ? 0 : deltaRotationVector[2];
					deltaRotationVector[3] = Float.isNaN(deltaRotationVector[3]) ? 0 : deltaRotationVector[3];
				}
				mOldValues = Arrays.copyOf(event.values, event.values.length);

				if (deltaRotationVector != null) {
					data.get(event.sensor.getType()).add(
							new Object[] {
									sensorMilis, event.values[0], event.values[1], event.values[2],
									deltaRotationVector[0], deltaRotationVector[1], deltaRotationVector[2],
									deltaRotationVector[3]});
				}

			}

			if (sensorMilis - timestamp > 1000) {
				writeToLogAndClearList();
			}
		}

		protected void writeToLogAndClearList() {

			timestamp = System.currentTimeMillis();
			LogWriterSensors.instance().write(data);
			data.clear();
		}
	};

	private WifiSnifferService mService;

	private int mWifiActiveNetwork = -1;

	private WifiManager mWifiManager;

	/**
	 * Shows alert to turn active wifi/mobile connections off
	 */
	public void alertActiveConnectionsTurnOff(final Runnable r) {

		disableAllWifiNetworks();
		try {
			setMobileDataEnabled(this,  false);
		} catch (Exception e1) {
			e1.printStackTrace();
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_operation_failed), null);
		}
		
		if(r != null) r.run();
		
//		standardConfirmDialog(getString(R.string.msg_alert),
//				getString(R.string.msg_connections_turnoff), new OnClickListener() {
//
//			@Override
//			public void onClick(DialogInterface dialog, int which) {
//				try {
//					setMobileDataEnabled(WifiSearcherActivity.this, false);
//					disableAllWifiNetworks();
//					if (r != null) {
//						r.run();
//					}
//				} catch (Exception e) {
//					standardAlertDialog(getString(R.string.msg_alert),
//							getString(R.string.msg_operation_failed), null);
//				}
//			}
//		}, new OnClickListener() {
//
//			@Override
//			public void onClick(DialogInterface dialog, int which) {
//				if (r != null) {
//					r.run();
//				}
//			}
//		}, false);
	}

	/**
	 * Shows alert to turn active connections on
	 */
	public void alertActiveConnectionsTurnOn() {
		//Re enable data
		try {
//			if (mConnectionMobileEnabled)
			setMobileDataEnabled(WifiSearcherActivity.this, true);

//			if (mConnectionWifiEnabled) 
//				toggleWifiActiveConnection();

			mConnectionMobileEnabled = false;
			mConnectionWifiEnabled = false;

		} catch (Exception e) {
			standardAlertDialog(getString(R.string.msg_alert),
			getString(R.string.msg_operation_failed), null);
			Log.e(LOG_TAG, "error", e);
		}

//		standardConfirmDialog(getString(R.string.msg_alert),
//				getString(R.string.msg_connections_turnon), new OnClickListener() {
//
//			@Override
//			public void onClick(DialogInterface dialog, int which) {
//				try {
//					if (mConnectionMobileEnabled)
//						setMobileDataEnabled(WifiSearcherActivity.this, true);
//
//					if (mConnectionWifiEnabled)
//						toggleWifiActiveConnection();
//
//					mConnectionMobileEnabled = false;
//					mConnectionWifiEnabled = false;
//
//				} catch (Exception e) {
//					standardAlertDialog(getString(R.string.msg_alert),
//							getString(R.string.msg_operation_failed), null);
//					Log.e(LOG_TAG, "error", e);
//				}
//			}
//		}, null);
	}

	/**
	 * Clears scan
	 */
	public void clearScan() {
		/**
		 * Button controls states
		 */
		mBtnStartScan.setEnabled(true);
		enableButtonsAfterScan(false);

		mAdapter.reset();

		mFilename = null;

		/**
		 * Reset info
		 */
		setScanInfo(0, 0, false);

		mTimelapsedTv.setText("Time elapsed 0s");

		LogWriter.reset();
		LogWriterSensors.reset();

		mMapView.resetMarkers();

		mMapView.setFirstMarker();

		mFlagScan = false;

		mScanCompleted = false;
	}

	public void completeLogs() {
		if (!mScanCompleted) {
			mService.stopScan(mMapData.imageId, mMapData.floorId, mMapView.mMarker, mMapView.mMarker2);
			mScanCompleted = true;
		}
	}

	/**
	 * Buttons : Save, clear, submit scan
	 * 
	 * @param b
	 */
	public void enableButtonsAfterScan(boolean b) {
		mBtnSaveScan.setEnabled(b);
		mBtnClearScan.setEnabled(b);
		mBtnSubmitScan.setEnabled(b);
	}

	public WifiSearcherAdapter getAdapter() {
		return mAdapter;
	}

	/**
	 * Loads map
	 */
	public void loadMap() {
		if (!mIsMapLoaded) {
			mMapView.setData(mMapData);

			if (mModeEnabled == MODE.MAP_POINTS) {
				/**
				 * Loading points
				 */
				Hashtable<String, String> hash = new Hashtable<String, String>(3);
				hash.put("imageId", Integer.valueOf(mMapData.imageId).toString());
				hash.put("token", getToken());
				DataPersistence d = new DataPersistence(this);
				NetworkTask task =
						new NetworkTask(this, d.getServerName(), "/logs/pars/getpoint", false, hash,
								true);
				task.setTag(TAG_KEY, new Integer(TAG_GET_POINTS));
				NetworkManager.getInstance().addTask(task);

				mShowPoints = true;
			}
		}
		mIsMapLoaded = true;
	}

	public void loadSummary() {
		mLSearchResult = (ListView) findViewById(R.id.lSearchResultListView);
		mLSearchResult.setAdapter(mAdapter);

		/**
		 * TextViews
		 */
		mTimelapsedTv = (TextView) findViewById(R.id.tTimeElapsed);
		mNumberReadingsTv = (TextView) findViewById(R.id.tReadings);
		mLocationTv = (TextView) findViewById(R.id.tLocation);
		mMapTv = (TextView) findViewById(R.id.tMap);
		mApCountTv = (TextView) findViewById(R.id.tApCount);

		/**
		 * Static info
		 */
		mMapTv.setText(new StringBuilder().append("Map: ").append(mMapData.name));
		mLocationTv.setText(new StringBuilder().append("Location: ")
				.append(Double.valueOf(getLocationManager().getLatitude()).toString().substring(0, 8))
				.append(",")
				.append(Double.valueOf(getLocationManager().getLongtitude()).toString().substring(0, 8)));
	}

	/**
	 * network task error
	 */
	public void nTaskErr(NetworkResult result) {
		initTitleProgressBar(false);

		if (result.getResponceCode() == 401) {
			standardAlertDialog(getString(R.string.msg_error), getString(R.string.msg_session_invalid),
					null);
		} else {
			standardAlertDialog(getString(R.string.msg_error), getString(R.string.msg_teh_error), null);
		}
	}

	/**
	 * network task success
	 */
	public void nTaskSucces(NetworkResult result) {
		try {
			XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
			parser.setInput(new ByteArrayInputStream(result.getData()), "UTF-8");
			switch (((Integer) result.getTask().getTag(TAG_KEY)).intValue()) {
			case TAG_LOG_SUBMIT:
				break;
			case TAG_GET_POINTS:
				parser.nextTag();
				ArrayList<HashMap<String, String>> points = new ArrayList<HashMap<String, String>>();
				if (XmlPullParser.START_TAG == parser.getEventType()) {
					if (parser.getName().equalsIgnoreCase("images")) {
						while (parser.next() != XmlPullParser.END_DOCUMENT)
							if (parser.getEventType() == XmlPullParser.START_TAG
							&& parser.getName().equalsIgnoreCase("img")) {
								HashMap<String, String> data = new HashMap<String, String>();
								data.put("point_id", parser.getAttributeValue(null, "scan_point_id"));
								data.put("point_name", parser.getAttributeValue(null, "scan_point_name"));
								data.put("point_x", parser.getAttributeValue(null, "point_x"));
								data.put("point_y", parser.getAttributeValue(null, "point_y"));
								points.add(data);
							}
					}
				}

				if (mShowPoints) {
					mMapView.setPoints(points);
				}

				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onBackPressed() {
		mMapView.cancelLoadingFiles();
		super.onBackPressed();
	}

	@Override
	public void onClickFeature(View v) {
		switch (v.getId()) {
		case R.id.start_scan:
			if (!mFlagScan) {
				startScan();
			} else {
				stopScan();
			}
			break;
		case R.id.mode:
			/**
			 * Changing modes between summary and map
			 */
			if (mModeEnabled == MODE.SUMMARY)
				initMode(MODE.MAP, false);
			else if (mModeEnabled == MODE.MAP)
				initMode(MODE.SUMMARY, false);
			break;
		case R.id.clear_scan:
			completeLogs();
			clearScan();
			break;
		case R.id.save_scan:
			if (mMapView.mMarker2.x != -1 && mMapView.mMarker2.y != -1) {
				completeLogs();
				saveScan();
			} else {
				standardAlertDialog(getString(R.string.msg_alert),
						getString(R.string.msg_map_2points_required), null);
			}
			break;
		case R.id.submit_scan:
			if (mMapView.mMarker2.x != -1 && mMapView.mMarker2.y != -1) {
				completeLogs();
				submitScan();
			} else {
				standardAlertDialog(getString(R.string.msg_alert),
						getString(R.string.msg_map_2points_required), null);
			}
			break;
		default:
			super.onClickFeature(v);
		}
	}

	/**
	 * Saving scan to sd card
	 */
	public void saveScan() {
		AlertDialog alert = segmentNameDailog("Save Scan", this, mFilename, this, null, null, 0);
		alert.setCanceledOnTouchOutside(false);
		alert.show();
	}

	/**
	 * Summary Info
	 * 
	 * @param time
	 * @param readings
	 * @param x
	 * @param y
	 * @param map
	 * @param apCount
	 */
	public void setScanInfo(int readings, int apCount, boolean incTryCount) {
		mNumberReadingsTv.setText(new StringBuilder().append("Readings: ").append(readings));
		mApCountTv.setText(new StringBuilder().append("Total AP: ").append(apCount));

		if (incTryCount)
			mAdapter.incTryCount();
	}

	public void startSensors() {
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager
		.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
				SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(mSensorListener,
				mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	public void stopSensors() {
		try {
			mSensorManager.unregisterListener(mSensorListener);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Submits scan to sd card
	 */
	public void submitScan() {
		/**
		 * Submitting scan
		 */
		ArrayList<String> fileList = new ArrayList<String>();

		if (mFilename != null) {
			fileList.add(LogWriter.APPEND_PATH + mFilename + ".log");
		} else {
			fileList.add(LogWriter.APPEND_PATH + LogWriter.DEFAULT_NAME);
		}

		DataPersistence d = new DataPersistence(this);
		new HttpLogSender(this, d.getServerName() + getString(R.string.submit_log_url), fileList).setToken(getToken())
		.execute();
	}

	/**
	 * Inits view mode
	 * 
	 * @param m
	 * @param firstRun
	 */
	protected void initMode(MODE m, boolean firstRun) {
		mModeEnabled = m;

		switch (m) {
		case MAP_POINTS:
			loadMap();
			if (firstRun) {
				mViewMap.setVisibility(View.VISIBLE);
			} else {
				applyRotation(0, 180, 90);
			}
			mBtnStartScan.setEnabled(false);
			mBtnMode.setVisibility(View.GONE);
			break;
		case MAP:
			loadMap();
			mMapView.canBePointed(true);
			if (firstRun) {
				mViewMap.setVisibility(View.VISIBLE);
			} else {
				applyRotation(0, 180, 90);
			}
			mBtnMode.setText(getString(R.string.button_summary));
			break;
		case SUMMARY:
			loadSummary();
			if (firstRun) {
				mViewSummary.setVisibility(View.GONE);
			} else {
				applyRotation(-1, 180, 90);
			}
			mBtnMode.setText(getString(R.string.button_map));
			break;

		}
	}

	/**
	 * Checks is mobile network connected
	 * 
	 * @return
	 */
	protected boolean isMobileInternetConnected() {
		NetworkInfo info = mConManager.getActiveNetworkInfo();
		return info != null && info.isConnectedOrConnecting()
				&& info.getType() == ConnectivityManager.TYPE_MOBILE;
	}

	/**
	 * Checks is wifi network connected
	 * 
	 * @return
	 */
	protected boolean isWiFiInternetConnected() {
		NetworkInfo info = mConManager.getActiveNetworkInfo();
		return info != null && info.isConnectedOrConnecting()
				&& info.getType() == ConnectivityManager.TYPE_WIFI;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.wifisearcher);

		/**
		 * Buttons
		 */
		mBtnStartScan = (Button) findViewById(R.id.start_scan);
		mBtnSaveScan = (Button) findViewById(R.id.save_scan);
		mBtnClearScan = (Button) findViewById(R.id.clear_scan);
		mBtnSubmitScan = (Button) findViewById(R.id.submit_scan);
		mBtnMode = (Button) findViewById(R.id.mode);
		mViewport = (LinearLayout) findViewById(R.id.viewport);

		/**
		 * Layouts
		 */
		mViewMap = (LinearLayout) findViewById(R.id.viewport_map);
		mViewSummary = (LinearLayout) findViewById(R.id.viewport_summary);

		mMapView = (MapView) findViewById(R.id.map_view);

		mAdapter = new WifiSearcherAdapter(this);

		mBundle = getIntent().getExtras();

		mMapData = (MapData) mBundle.get("data");

		initMode(MODE.valueOf(mBundle.getString("mode")), true);

		/**
		 * Service
		 */
		if (mService == null) {
			if (mModeEnabled != MODE.MAP_POINTS) {
				startService(new Intent(this, WifiSnifferService.class));
				bindService(new Intent(this, WifiSnifferService.class), connection, 0);
			}
		}

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		mConManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

	}

	@Override
	protected void onDestroy() {
		stopServices();
		super.onDestroy();
	}

	/**
	 * Starts scan
	 */
	protected void startScan() {
		//forgetAllWifiNetworks();
		disableAllWifiNetworks();

		// enables scan
		if (isWifiAvailable()) {
			if (mMapView.mMarker.x != -1 && mMapView.mMarker.y != -1) {
				Runnable startScan = new Runnable() {

					@Override
					public void run() {
						if (mModeEnabled != MODE.SUMMARY)
							initMode(MODE.SUMMARY, false);

						mService.startScan();
						mBtnStartScan.setText(getString(R.string.button_stop));
						mFlagScan = true;
					}
				};

				if (isWiFiInternetConnected()) {
					mConnectionWifiEnabled = true;
					mWifiActiveNetwork = mWifiManager.getConnectionInfo().getNetworkId();
				}

				if (isMobileInternetConnected()) {
					mConnectionMobileEnabled = true;
				}

				if (mConnectionMobileEnabled || mConnectionWifiEnabled) {
					alertActiveConnectionsTurnOff(startScan);
				} else {
					startScan.run();
				}
			} else {
				standardAlertDialog(getString(R.string.msg_alert), getString(R.string.msg_market_is_null),
						null);
			}
		} else {
			standardAlertDialog(getString(R.string.msg_alert_wifi_title), getString(R.string.msg_wifi_not_enabled),
					null);
		}

	}

	/**
	 * Stops scan
	 */
	protected void stopScan() {
		// stops scanning
		if (mModeEnabled == MODE.SUMMARY) {
			if (mBundle.getString("mode").equals("MAP")) {
				initMode(MODE.MAP, false);
			} else if (mBundle.getString("mode").equals("MAP_POINTS")) {
				initMode(MODE.MAP_POINTS, false);
			}
		}
		stopSensors();
		mService.pauseScan();

		Toast.makeText(this, R.string.msg_map_notification_points, 3000);
		mMapView.setLastMarker();

		mBtnStartScan.setText(getString(R.string.button_start));
		mBtnStartScan.setEnabled(false);
		enableButtonsAfterScan(true);

		if ((mConnectionMobileEnabled || mConnectionWifiEnabled)
				&& (mConManager.getActiveNetworkInfo() == null || !mConManager.getActiveNetworkInfo()
				.isConnectedOrConnecting())) {
			alertActiveConnectionsTurnOn();
		}
	}

	/**
	 * Stop service
	 */
	protected void stopServices() {
		try {
			if (mService != null) {
				mService.stopScan();
				mService.deInitWifi();
			}
			unbindService(connection);
			stopService(new Intent(this, WifiSnifferService.class));
		} catch (Exception e) {
			e.printStackTrace();
		}
		stopSensors();
	}

	/**
	 * Setup a new 3D rotation on the container view.
	 * 
	 * @param position the item that was clicked to show a picture, or -1 to show the list
	 * @param start the start angle at which the rotation must begin
	 * @param end the end angle of the rotation
	 */
	private void applyRotation(int position, float start, float end) {
		// Find the center of the container
		final float centerX = mViewport.getWidth() / 2.0f;
		final float centerY = mViewport.getHeight() / 2.0f;

		// Create a new 3D rotation with the supplied parameter
		// The animation listener is used to trigger the next animation
		final Rotate3dAnimation rotation =
				new Rotate3dAnimation(start, end, centerX, centerY, 310.0f, true);
		rotation.setDuration(500);
		rotation.setFillAfter(true);
		rotation.setInterpolator(new AccelerateInterpolator());
		rotation.setAnimationListener(new DisplayNextView(position));

		mViewport.startAnimation(rotation);
	}

	/**
	 * Disables auto-connect to configured networks
	 */
	private void disableAllWifiNetworks() {
		for (WifiConfiguration conf : mWifiManager.getConfiguredNetworks()) {
			mWifiManager.disableNetwork(conf.networkId);
		}
	}

	private void forgetAllWifiNetworks() {
		for (WifiConfiguration conf : mWifiManager.getConfiguredNetworks()) {
			mWifiManager.removeNetwork(conf.networkId);
		}
	}

	/**
	 * Turn on/off mobile internet
	 * 
	 * @param context
	 * @param enabled
	 */
	private void setMobileDataEnabled(Context context, boolean enabled) throws Exception {
		final ConnectivityManager conman =
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		final Class<?> conmanClass = Class.forName(conman.getClass().getName());
		final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
		iConnectivityManagerField.setAccessible(true);
		final Object iConnectivityManager = iConnectivityManagerField.get(conman);
		final Class<?> iConnectivityManagerClass =
				Class.forName(iConnectivityManager.getClass().getName());
		final Method setMobileDataEnabledMethod =
				iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
		setMobileDataEnabledMethod.setAccessible(true);

		setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
	}

	/**
	 * Toggle active connection of Wifi
	 */
	private void toggleWifiActiveConnection() {
		WifiInfo connection = mWifiManager.getConnectionInfo();
		if (connection != null && mConManager.getActiveNetworkInfo() != null
				&& mConManager.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI
				&& mConManager.getActiveNetworkInfo().isConnectedOrConnecting()) {
			// turn wifi connection off
			mWifiActiveNetwork = mWifiManager.getConnectionInfo().getNetworkId();
			mWifiManager.disableNetwork(mWifiActiveNetwork);
			Log.v(LOG_TAG, "disabled all configured networks");
		} else {
			// turn wifi connection on
			if (mWifiActiveNetwork != -1) {
				mWifiManager.enableNetwork(mWifiActiveNetwork, true);
				Log.v(LOG_TAG, "enabled wi-fi connection ");
			} else {
				Log.v(LOG_TAG, "nothing to enable ");
			}
		}
	}
}
