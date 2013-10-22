/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */

package com.wikonos.utils;

import com.wikonos.fingerprint.activities.HomeActivity;
import com.wikonos.fingerprint.activities.LocationListActivity;
import com.wikonos.fingerprint.activities.WifiSearcherActivity;
import com.wikonos.fingerprint.activities.LocationListActivity.MapData;
import com.wikonos.logs.ErrorLog;

import android.app.Activity;
import android.os.Bundle;

/**
 * Application Activity Mediator
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class AppActivityMediator extends ActivityMediator {

  protected Bundle bundle = new Bundle();

  public enum mode {
    OPEN
  }

  public enum params {
    BUILDING_ID, MAP_NAME
  }

  public AppActivityMediator(Activity activity) {
    super(activity);
  }

  public void goHome() {
    startActivity(HomeActivity.class);
  }

  public void goBuildings(Enum<WifiSearcherActivity.MODE> dest) {
    bundle.putInt(mode.OPEN.toString(), LocationListActivity.GET_BUILDINGS);
    bundle.putString("dest", dest.toString());
    startActivity(LocationListActivity.class, bundle);
  }

  public void goFloors(String buildingId, Enum<WifiSearcherActivity.MODE> dest) {
    bundle.putInt(mode.OPEN.toString(), LocationListActivity.GET_FLOORS);
    bundle.putString(params.BUILDING_ID.toString(), buildingId);
    bundle.putString("dest", dest.toString());
    startActivity(LocationListActivity.class, bundle);
  }

  public void goWifiSearcher(Enum<WifiSearcherActivity.MODE> mode, MapData data) {
    try {
      bundle.putString("mode", mode.toString());
      bundle.putSerializable("data", data);
      startActivity(WifiSearcherActivity.class, bundle);
    } catch (Exception e) {
      e.printStackTrace();
      ErrorLog.e(e);
    }
  }

}
