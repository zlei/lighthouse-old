/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */

package com.wikonos.fingerprint.activities;

import com.wikonos.fingerprint.R;
import com.wikonos.models.SimpleListAdapter;
import com.wikonos.network.INetworkTaskStatusListener;
import com.wikonos.network.NetworkManager;
import com.wikonos.network.NetworkResult;
import com.wikonos.network.NetworkTask;
import com.wikonos.utils.AppActivityMediator;
import com.wikonos.utils.DataPersistence;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Select Building/Floor Activity
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class LocationListActivity extends DefaultActivity implements INetworkTaskStatusListener {

  /**
   * Map data
   */
  public class MapData implements Serializable, Comparable {

    private static final long serialVersionUID = -5948875275621573697L;

    public class ZoomInfo implements Serializable {
      private static final long serialVersionUID = 2849412401959020422L;

      public int x, y;

      private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(x);
        out.writeInt(y);
      }

      private void readObject(java.io.ObjectInputStream in) throws IOException,
          ClassNotFoundException {
        x = in.readInt();
        y = in.readInt();
      }
    }

    public HashMap<Integer, ZoomInfo> zoom = new HashMap<Integer, ZoomInfo>();

    public int imageId, floorId, width, height;

    public String img, name;

    public void addZoom(int scaly, int x, int y) {
      ZoomInfo info = new ZoomInfo();
      info.x = x;
      info.y = y;
      zoom.put(scaly, info);
    }

    public String toString() {
      return name;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
      out.writeUTF(img);
      out.writeUTF(name);
      out.writeInt(imageId);
      out.writeInt(floorId);
      out.writeInt(width);
      out.writeInt(height);
      out.writeObject(zoom);
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream in) throws IOException,
        ClassNotFoundException {
      img = in.readUTF();
      name = in.readUTF();
      imageId = in.readInt();
      floorId = in.readInt();
      width = in.readInt();
      height = in.readInt();
      zoom = (HashMap<Integer, ZoomInfo>) in.readObject();
    }

	@Override
	public int compareTo(Object another) {
		return this.name.toLowerCase().compareTo( ((MapData)another).name.toLowerCase() );
	}
  }
  
  private class BuildingData implements Comparable<BuildingData> {

	  public String name;
	  public int building_id;
	  
	@Override
	public int compareTo(BuildingData another) {
		return this.name.toLowerCase().compareTo( another.name.toLowerCase() );
	}
	
	@Override
	public String toString() {
		return name;
	}
  }

  public static final String FLOOR_ID = "FLOOR_ID";
  public static final String FLOOR_NAME = "FLOOR_NAME";

  public static final int GET_BUILDINGS = 1;
  public static final int GET_FLOORS = 2;

  public static final String IMAGE_PATH = "IMAGE_PATH";
  private static final String TAG_KEY = "TAG_KEY";

  /**
   * Current bundle
   */
  protected Bundle mBundle;

  /**
   * Mode for wifi-searcher activity
   */
  protected WifiSearcherActivity.MODE mDestination;

  private SimpleListAdapter mAdapter;

  /**
   * Click listener for building list view item
   */
  private OnItemClickListener mBuildingOnClickListener = new OnItemClickListener() {
    public void onItemClick(AdapterView<?> group, View view, int position, long id) {
    	int building_id = ((BuildingData)mData.get(position)).building_id;
      mActivityMediator.goFloors(Integer.valueOf(building_id).toString(), mDestination);
    }
  };

  private ArrayList mData;

  /**
   * Click listener for floor list view item
   */
  private OnItemClickListener mFloorOnClickListener = new OnItemClickListener() {
    public void onItemClick(AdapterView<?> group, View view, int position, long id) {
      mActivityMediator.goWifiSearcher(mDestination, (MapData) mAdapter.getItem(position));
    }
  };

  private Vector<Integer> mIds = new Vector<Integer>();
  private ListView mList;

  private TextView mListTitle;

  private int modeView;

  @Override
  public void nTaskErr(NetworkResult result) {
    initTitleProgressBar(false);
    
    Exception e = result.getException();
    
    if(result.getResponceCode() == 401 || (e != null && e.getMessage().contains("Received authentication challenge is null"))){
      standardAlertDialog(getString(R.string.msg_error), getString(R.string.msg_session_invalid), null);
    } else {
      standardAlertDialog(getString(R.string.msg_error), getString(R.string.msg_teh_error), null);
      Log.e(LOG_TAG, "error", result.getException());
    }
  }

  @Override
  public void nTaskSucces(NetworkResult result) {
    initTitleProgressBar(false);

    try {
      XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
      
      Log.v(LOG_TAG, new String(result.getData()).toString());
      
      Log.v(LOG_TAG, "url " + result.getTask().getUrl());
      
      parser.setInput(new ByteArrayInputStream(result.getData()), "UTF-8");
      switch ((Integer) (result.getTask().getTag(TAG_KEY))) {
        case GET_BUILDINGS:
          parser.nextTag();
          if (XmlPullParser.START_TAG == parser.getEventType())
            if (parser.getName().equalsIgnoreCase("buildings"))
              while (parser.next() != XmlPullParser.END_DOCUMENT)
                if (parser.getEventType() == XmlPullParser.START_TAG
                    && parser.getName().equalsIgnoreCase("build")) {
                	Log.v(DefaultActivity.LOG_TAG, "build " + parser.getName()+ " ; attribute " + parser.getAttributeValue(null, "building_id"));
                  
                  BuildingData buildingData = new BuildingData();
                  buildingData.name = parser.getAttributeValue(null, "name");
                  buildingData.building_id = Integer.parseInt(parser.getAttributeValue(null, "building_id"));
                  mData.add(buildingData);
                }
          break;
        case GET_FLOORS:
          parser.nextTag();
          if (XmlPullParser.START_TAG == parser.getEventType())
            if (parser.getName().equalsIgnoreCase("images")) {
              MapData mapData = null;
              while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                    && parser.getName().equalsIgnoreCase("data")) {
                  mapData = new MapData();
                  mData.add(mapData);
                } else if (parser.getEventType() == XmlPullParser.START_TAG
                    && parser.getName().equalsIgnoreCase("img")) {
                  mIds.add(Integer.parseInt(parser.getAttributeValue(null, "floor_id")));
                  mapData.name = parser.getAttributeValue(null, "name");
                  mapData.floorId = Integer.parseInt(parser.getAttributeValue(null, "floor_id"));
                  mapData.imageId = Integer.parseInt(parser.getAttributeValue(null, "image_id"));
                  mapData.width = Integer.parseInt(parser.getAttributeValue(null, "width"));
                  mapData.height = Integer.parseInt(parser.getAttributeValue(null, "height"));
                  mapData.img = parser.getAttributeValue(null, "img");
                } else if (parser.getEventType() == XmlPullParser.START_TAG
                    && parser.getName().equalsIgnoreCase("scale")) {
                  int x = Integer.parseInt(parser.getAttributeValue(null, "x"));
                  int y = Integer.parseInt(parser.getAttributeValue(null, "y"));
                  parser.next();
                  int scale = Integer.parseInt(parser.getText());
                  mapData.addZoom(scale, x, y);
                }
              }
            }
          break;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    Collections.sort(mData);
    mAdapter.setData(mData);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.list);

    initTitleProgressBar(true);

    mAdapter = new SimpleListAdapter(this);
    mList = (ListView) findViewById(R.id.list);
    mList.setAdapter(mAdapter);

    mListTitle = (TextView) findViewById(R.id.list_title);

    mBundle = getIntent().getExtras();

    modeView = mBundle.getInt(AppActivityMediator.mode.OPEN.toString());

    mDestination = WifiSearcherActivity.MODE.valueOf(mBundle.getString("dest"));

    switch (modeView) {
      case GET_BUILDINGS:
        mData = new ArrayList<String>();

        setTitle(new StringBuilder().append(R.string.app_name).append(" - Buildings"));

        mListTitle.setText(getString(R.string.txt_buildings));

        mList.setOnItemClickListener(mBuildingOnClickListener);

        /**
         * List Building
         */
        loadBuildings();
        break;
      case GET_FLOORS:
        mData = new ArrayList<MapData>();

        mList.setOnItemClickListener(mFloorOnClickListener);

        mListTitle.setText(getString(R.string.txt_floors));

        /**
         * List Floors
         */
        loadFloors(mBundle.getString(AppActivityMediator.params.BUILDING_ID.toString()));
        break;
    }
  }

  void loadBuildings() {
    Hashtable<String, String> hash = new Hashtable<String, String>(3);
    hash.put("lat", String.format("%.6f", getLocationManager().getLatitude()));
    hash.put("lng", String.format("%.6f", getLocationManager().getLongtitude()));
    Log.v(LOG_TAG, "lat "  + String.format("%.6f", getLocationManager().getLatitude()) );
    Log.v(LOG_TAG, "lng " + String.format("%.6f", getLocationManager().getLongtitude())  );
    hash.put("token", getToken());
    DataPersistence d = new DataPersistence(this);
    NetworkTask task =
        new NetworkTask(this, d.getServerName(), "/logs/pars/getbuildings/", false,
            hash, true);
    task.setTag(TAG_KEY, new Integer(GET_BUILDINGS));
    NetworkManager.getInstance().addTask(task);
  }

  void loadFloors(String buildingId) {
    Hashtable<String, String> hash = new Hashtable<String, String>(3);
    hash.put("buildingId", buildingId);
    hash.put("token", getToken());
    DataPersistence d = new DataPersistence(this);
    NetworkTask task =
        new NetworkTask(this, d.getServerName(), "/logs/pars/getimage/", false, hash,
            true);
    task.setTag(TAG_KEY, new Integer(GET_FLOORS));
    NetworkManager.getInstance().addTask(task);
  }
}