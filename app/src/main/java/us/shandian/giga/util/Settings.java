package us.shandian.giga.util;

import android.content.Context;
import android.content.SharedPreferences;

/*
  Settings Provider
*/
public class Settings
{
	public static final String XML_NAME = "settings";
	
	public static final String DOWNLOAD_DIRECTORY = "download_directory";
	
	public static final String DEFAULT_PATH = "/storage/sdcard0/GigaGet";
	
	private static Settings sInstance;
	
	private SharedPreferences mPrefs;
	
	public static Settings getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new Settings(context);
		}
		
		return sInstance;
	}
	
	private Settings(Context context) {
		mPrefs = context.getSharedPreferences(XML_NAME, Context.MODE_PRIVATE);
	}
	
	public Settings putBoolean(String key, boolean value) {
		mPrefs.edit().putBoolean(key, value).commit();
		return this;
	}
	
	public boolean getBoolean(String key, boolean def) {
		return mPrefs.getBoolean(key, def);
	}
	
	public Settings putInt(String key, int value) {
		mPrefs.edit().putInt(key, value).commit();
		return this;
	}
	
	public int getInt(String key, int defValue) {
		return mPrefs.getInt(key, defValue);
	}

	public Settings putString(String key, String value) {
		mPrefs.edit().putString(key, value).commit();
		return this;
	}

	public String getString(String key, String defValue) {
		return mPrefs.getString(key, defValue);
	}
	
}
