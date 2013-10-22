package com.wikonos.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.Environment;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

public class BitmapCache {

	private int scale = 0;
	private String imageName = "";
	private DiskLruCache mDiskLruCache;
	private static final int DISK_CACHE_SIZE = 1024 * 1024 * 64; // 64MB
	private static final int IO_BUFFER_SIZE = 8 * 1024;
	private static final String DISK_CACHE_NAME = "tilecache";
	private final CompressFormat mCompressFormat = CompressFormat.JPEG;
	private final int mCompressQuality = 100;
	private SharedPreferences prefs;
	private final Object mDiskCacheLock = new Object();

	public BitmapCache(Context context, int scale, String imageName) {
		Log.d("Fingerprint", "Creating Bitmap Cache Instance");
		try {
			synchronized(mDiskCacheLock) {
				final File diskCacheDir = getDiskCacheDir(context, DISK_CACHE_NAME);
				mDiskLruCache = DiskLruCache.open( diskCacheDir, 1, 1, DISK_CACHE_SIZE);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		this.scale = scale;
		this.imageName = imageName;
		
		prefs = context.getSharedPreferences("BITMAP_CACHE", Context.MODE_PRIVATE);
		int lastScale = prefs.getInt("scale", -1);
		String lastImageName = prefs.getString("imageName", "");
		if(lastScale != scale || !imageName.equals(lastImageName)) {
			clearCache(context);
		}
		prefs.edit().putString("imageName", imageName).commit();
		prefs.edit().putInt("scale", scale).commit();
	}
	
	private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor) throws IOException, FileNotFoundException {
		if(bitmap == null) return false;
		OutputStream os = null;
		try {
			os = new BufferedOutputStream(editor.newOutputStream(0), IO_BUFFER_SIZE);
			return bitmap.compress(mCompressFormat, mCompressQuality, os);
		}finally{
			if (os != null) os.close();
		}
	}

	public Bitmap get(int x, int y) {
		String key = x + "-" + y;
		Bitmap bitmap = null;
		DiskLruCache.Snapshot snap = null;
		try {
			synchronized(mDiskCacheLock) {
				snap = mDiskLruCache.get(key);
				if( snap == null ) {
					return null;
				}
				final InputStream is = snap.getInputStream(0);
				if ( is != null ) {
					final BufferedInputStream bis = new BufferedInputStream( is, IO_BUFFER_SIZE );
					bitmap = BitmapFactory.decodeStream(bis);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			return null;
		} finally {
			if (snap != null) {
				snap.close();
			}
		}
		return bitmap;
	}

	public void set(int x, int y, Bitmap tile) {
		String key = x + "-" + y;
		Log.d("Fingerprint", "Setting " + key);
		DiskLruCache.Editor editor = null;
		try {
			synchronized(mDiskCacheLock) {
				editor = mDiskLruCache.edit(key);
				//if (editor == null) return;
				if (writeBitmapToFile(tile, editor)) {
					mDiskLruCache.flush();
					editor.commit();
				} else {
					editor.abort();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			try {
				if (editor != null) editor.abort();
			}catch (IOException ee) {
				e.printStackTrace();
			}
		} catch (IllegalStateException e) {

		}
	}

	public String getImageName() {
		return imageName;
	}
	
	public void setImageName(String imageName) {
		this.imageName = imageName;
	}
	
	public int getScale() {
		return scale;
	}
	
	public void setScale(int scale) {
		this.scale = scale;
	}
	
	private File getDiskCacheDir(Context context, String name) {
		String cachePath = "";
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())	|| !Environment.isExternalStorageRemovable()) {
			cachePath = context.getExternalCacheDir().getPath();
		} else {
			cachePath = context.getCacheDir().getPath();
		}
		
		return new File(cachePath + File.separator + name);
	}
	
	public void clearCache(Context context) {
		Log.d("Fingerprint", "Clearing Bitmap Cache");
		try {
			synchronized(mDiskCacheLock) {
				mDiskLruCache.delete();
				final File diskCacheDir = getDiskCacheDir(context, DISK_CACHE_NAME);
				mDiskLruCache = DiskLruCache.open( diskCacheDir, 1, 1, DISK_CACHE_SIZE);
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
}
