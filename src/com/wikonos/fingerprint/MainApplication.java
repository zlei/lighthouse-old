/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */
package com.wikonos.fingerprint;

import android.app.Application;

/**
 * Main application activity
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id: MainActivity.java 281 2012-03-12 05:43:04Z dzhakipov $
 */
public class MainApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		//crash logger
/*		if(getString(R.string.crash_reports_enable).equals("true"))
			ACRA.init(this);*/
	}
}