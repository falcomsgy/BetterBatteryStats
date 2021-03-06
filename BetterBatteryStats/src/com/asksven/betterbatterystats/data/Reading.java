/*
 * Copyright (C) 2011-2013 asksven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asksven.betterbatterystats.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

//import com.asksven.andoid.common.contrib.Shell;
//import com.asksven.andoid.common.contrib.Shell.SU;
import com.asksven.android.common.RootShell;
import com.asksven.android.common.kernelutils.NativeKernelWakelock;
import com.asksven.android.common.kernelutils.State;
import com.asksven.android.common.kernelutils.Wakelocks;
import com.asksven.android.common.privateapiproxies.Alarm;
import com.asksven.android.common.privateapiproxies.Misc;
import com.asksven.android.common.privateapiproxies.NetworkUsage;
import com.asksven.android.common.privateapiproxies.StatElement;
import com.asksven.android.common.privateapiproxies.Wakelock;
import com.asksven.android.common.privateapiproxies.Process;
import com.asksven.android.common.utils.DataStorage;
import com.asksven.android.common.utils.DateUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A reading represents the data that was collected on a device between two references.
 * This class is designed to be dumped as text or as JSON representation
 * @author sven
 *
 */
public class Reading implements Serializable
{
	
	static final String TAG = "Reading";
	String bbsVersion;
	String creationDate;
	String statType;
	String duration;
	String buildVersionRelease;
	String buildBrand;
	String buildDevice;
	String buildManufacturer;
	String buildModel;
	String osVersion;
	String buildBootloader;
	String buildHardware;
	String buildFingerprint;
	String buildId;
	String buildTags;
	String buildUser;
	String buildProduct;
	String buildRadio;
	boolean rooted;
	int batteryLevelLost;
	int batteryVoltageLost;
	String batteryLevelLostText;
	String batteryVoltageLostText;
	
	
	ArrayList<StatElement> otherStats;
	ArrayList<StatElement> kernelWakelockStats;
	ArrayList<StatElement> partialWakelockStats;
	ArrayList<StatElement> alarmStats;
	ArrayList<StatElement> networkStats;
	ArrayList<StatElement> cpuStateStats;
	ArrayList<StatElement> processStats;
	
	@SuppressLint({ "InlinedApi", "NewApi" })
	public Reading(Context context, Reference refFrom, Reference refTo)
	{
		otherStats 				= new ArrayList<StatElement>();
		kernelWakelockStats 	= new ArrayList<StatElement>();
		partialWakelockStats 	= new ArrayList<StatElement>();
		alarmStats 				= new ArrayList<StatElement>();
		networkStats 			= new ArrayList<StatElement>();
		cpuStateStats 			= new ArrayList<StatElement>();
		processStats 			= new ArrayList<StatElement>();

		try
		{
			PackageInfo pinfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			bbsVersion 			= pinfo.versionName;
		}
		catch (Exception e)
		{
			// do nothing
			bbsVersion = "unknown";
		}
				
		creationDate 		= DateUtils.now();
		statType 			= refFrom.getLabel() + " to "+ refTo.getLabel();
		duration 			= DateUtils.formatDuration(StatsProvider.getInstance(context).getSince(refFrom, refTo));
		buildVersionRelease = Build.VERSION.RELEASE;
		buildBrand 			= Build.BRAND;
		buildDevice 		= Build.DEVICE;
		buildManufacturer 	= Build.MANUFACTURER;
		buildModel 			= Build.MODEL;
		osVersion 			= System.getProperty("os.version");

		if (Build.VERSION.SDK_INT >= 8)
		{
			buildBootloader = Build.BOOTLOADER;
			buildHardware 	= Build.HARDWARE;
		}
		buildFingerprint 	= Build.FINGERPRINT;
		buildId 			= Build.ID;
		buildTags 			= Build.TAGS;
		buildUser 			= Build.USER;
		buildProduct 		= Build.PRODUCT;
		buildRadio 			= "";

		// from API14
		if (Build.VERSION.SDK_INT >= 14)
		{
			buildRadio 		= Build.getRadioVersion();
		}
		else if (Build.VERSION.SDK_INT >= 8)
		{
			buildRadio 		= Build.RADIO;
		}

		rooted 				= RootShell.getInstance().rooted(); //Shell.SU.available();
		
		batteryLevelLost 		= StatsProvider.getInstance(context).getBatteryLevelStat(refFrom, refTo);
		batteryVoltageLost 		= StatsProvider.getInstance(context).getBatteryVoltageStat(refFrom, refTo);
		batteryLevelLostText 	= StatsProvider.getInstance(context).getBatteryLevelFromTo(refFrom, refTo);
		batteryVoltageLostText 	= StatsProvider.getInstance(context).getBatteryVoltageFromTo(refFrom, refTo);

		// populate the stats
		SharedPreferences sharedPrefs 	= PreferenceManager.getDefaultSharedPreferences(context);
		boolean bFilterStats 			= sharedPrefs.getBoolean("filter_data", true);
		int iPctType 					= Integer.valueOf(sharedPrefs.getString("default_wl_ref", "0"));
		int iSort 						= 0;
		
		try
		{
			ArrayList<StatElement> tempStats = StatsProvider.getInstance(context).getOtherUsageStatList(bFilterStats, refFrom, false, false, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);
				
				if (tempStats.get(i) instanceof Misc)
				{
					otherStats.add((Misc) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type Misc");
				}
			}
			
			tempStats = StatsProvider.getInstance(context).getWakelockStatList(bFilterStats, refFrom, iPctType, iSort, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof Wakelock)
				{
					partialWakelockStats.add((Wakelock) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type Wakelock");
				}
			}

			tempStats = StatsProvider.getInstance(context).getNativeKernelWakelockStatList(bFilterStats, refFrom, iPctType, iSort, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof NativeKernelWakelock)
				{
					kernelWakelockStats.add((NativeKernelWakelock) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type NativeKernelWakelock");
				}
			}
			
			tempStats = StatsProvider.getInstance(context).getProcessStatList(bFilterStats, refFrom, iSort, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof Process)
				{
					processStats.add((Process) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type Process");
				}
			}
			
			tempStats = StatsProvider.getInstance(context).getAlarmsStatList(bFilterStats, refFrom, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof Alarm)
				{
					alarmStats.add((Alarm) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type Alarm");
				}
			}
			
			tempStats = StatsProvider.getInstance(context).getNativeNetworkUsageStatList(bFilterStats, refFrom, refTo);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof NetworkUsage)
				{
					networkStats.add((NetworkUsage) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type NetworkUsage");
				}
			}
			
			tempStats = StatsProvider.getInstance(context).getCpuStateList(refFrom, refTo, bFilterStats);
			for (int i = 0; i < tempStats.size(); i++)
			{
				// make sure to load all data (even the lazy loaded one)
				tempStats.get(i).getFqn(context);

				if (tempStats.get(i) instanceof State)
				{
					cpuStateStats.add((State) tempStats.get(i));
				}
				else
				{
					Log.e(TAG, tempStats.get(i).toString() + " is not of type State");
				}
			}		
		}
		catch (Exception e)
		{
			Log.e(TAG, "An exception occured: " + e.getMessage());
		}
	}
	
	private String toJson()
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
		String json = gson.toJson(this);
		
		return json;
	}
	

	public String toStringText(Context context)
	{
		StringWriter out = new StringWriter();
		SharedPreferences sharedPrefs 	= PreferenceManager.getDefaultSharedPreferences(context);


		// write header
		out.write("===================\n");
		out.write("General Information\n");
		out.write("===================\n");
		out.write("BetterBatteryStats version: " + bbsVersion + "\n");
		out.write("Creation Date: " + creationDate + "\n");
		out.write("Statistic Type: " + statType + "\n");
		out.write("Since " + duration + "\n");
		out.write("VERSION.RELEASE: " + buildVersionRelease + "\n");
		out.write("BRAND: " + buildBrand + "\n");
		out.write("DEVICE: " + buildDevice + "\n");
		out.write("MANUFACTURER: " + buildManufacturer + "\n");
		out.write("MODEL: " + buildModel + "\n");
		out.write("OS.VERSION: " + osVersion + "\n");

		if (Build.VERSION.SDK_INT >= 8)
		{

			out.write("BOOTLOADER: " + buildBootloader + "\n");
			out.write("HARDWARE: " + buildHardware + "\n");
		}
		out.write("FINGERPRINT: " + buildFingerprint + "\n");
		out.write("ID: " + buildId + "\n");
		out.write("TAGS: " + buildTags + "\n");
		out.write("USER: " + buildUser + "\n");
		out.write("PRODUCT: " + buildProduct + "\n");
		out.write("RADIO: " + buildRadio + "\n");
		out.write("Rooted: " + rooted + "\n");

		out.write("============\n");
		out.write("Battery Info\n");
		out.write("============\n");
		out.write("Level lost [%]: " + batteryLevelLostText + "\n");
		out.write("Voltage lost [mV]: " + batteryVoltageLostText + "\n");

		// write timing info
		boolean bDumpChapter = sharedPrefs.getBoolean("show_other",
				true);
		if (bDumpChapter)
		{
			out.write("===========\n");
			out.write("Other Usage\n");
			out.write("===========\n");
			dumpList(context, otherStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_pwl", true);
		if (bDumpChapter)
		{
			// write wakelock info
			out.write("=========\n");
			out.write("Wakelocks\n");
			out.write("=========\n");
			dumpList(context, partialWakelockStats, out);
			
		}

		bDumpChapter = sharedPrefs.getBoolean("show_kwl", true);
		if (bDumpChapter)
		{
			String addendum = "";
			if (Wakelocks.isDiscreteKwlPatch())
			{
				addendum = "!!! Discrete !!!";
			}
			if (!Wakelocks.fileExists())
			{
				addendum = " !!! wakeup_sources !!!";
			}

			// write kernel wakelock info
			out.write("================\n");
			out.write("Kernel Wakelocks " + addendum + "\n");
			out.write("================\n");

			dumpList(context, kernelWakelockStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_proc", false);
		if (bDumpChapter)
		{
			// write process info
			out.write("=========\n");
			out.write("Processes\n");
			out.write("=========\n");
			dumpList(context, processStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_alarm", true);
		if (bDumpChapter)
		{
			// write alarms info
			out.write("======================\n");
			out.write("Alarms (requires root)\n");
			out.write("======================\n");
			dumpList(context, alarmStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_network", true);
		if (bDumpChapter)
		{
			// write alarms info
			out.write("======================\n");
			out.write("Network (requires root)\n");
			out.write("======================\n");
			dumpList(context, networkStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_cpustates", true);
		if (bDumpChapter)
		{
			// write alarms info
			out.write("==========\n");
			out.write("CPU States\n");
			out.write("==========\n");
			dumpList(context, cpuStateStats, out);
		}

		bDumpChapter = sharedPrefs.getBoolean("show_serv", false);
		if (bDumpChapter)
		{
			out.write("========\n");
			out.write("Services\n");
			out.write("========\n");
			out.write("Active since: The time when the service was first made active, either by someone starting or binding to it.\n");
			out.write("Last activity: The time when there was last activity in the service (either explicit requests to start it or clients binding to it)\n");
			out.write("See http://developer.android.com/reference/android/app/ActivityManager.RunningServiceInfo.html\n");
			ActivityManager am = (ActivityManager) context
					.getSystemService(context.ACTIVITY_SERVICE);
			List<ActivityManager.RunningServiceInfo> rs = am
					.getRunningServices(50);

			for (int i = 0; i < rs.size(); i++)
			{
				ActivityManager.RunningServiceInfo rsi = rs.get(i);
				out.write(rsi.process + " ("
						+ rsi.service.getClassName() + ")\n");
				out.write("  Active since: "
						+ DateUtils.formatDuration(rsi.activeSince)
						+ "\n");
				out.write("  Last activity: "
						+ DateUtils
								.formatDuration(rsi.lastActivityTime)
						+ "\n");
				out.write("  Crash count:" + rsi.crashCount + "\n");
			}
		}

		// add chapter for reference info
		out.write("==================\n");
		out.write("Reference overview\n");
		out.write("==================\n");
		
		for (int i = 0; i < ReferenceStore.getReferenceNames(null, context).size(); i++)
		{
			String name = ReferenceStore.getReferenceNames(null, context).get(i);
			Reference ref = ReferenceStore.getReferenceByName(name, context);
			out.write	(name + ": " + ref.whoAmI() + "\n");
		}

		return out.toString();
	}

	
	@SuppressLint("NewApi") 
	public Uri writeToFileJson(Context context)
	{
		Uri fileUri = null;
		
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (!DataStorage.isExternalStorageWritable())
		{
			Log.e(TAG, "External storage can not be written");
			Toast.makeText(context, "External Storage can not be written",
					Toast.LENGTH_SHORT).show();
		}
		try
		{
			// open file for writing
			File root;
			boolean bSaveToPrivateStorage = sharedPrefs.getBoolean("files_to_private_storage", false);

			if (bSaveToPrivateStorage)
			{
				try
				{
					root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
				}
				catch (Exception e)
				{
					root = Environment.getExternalStorageDirectory();
				}
			}
			else
			{
				root = Environment.getExternalStorageDirectory();
			}

			// check if file can be written
			if (root.canWrite())
			{
				String strFilename = "BetterBatteryStats-"
						+ DateUtils.now("yyyy-MM-dd_HHmmssSSS") + ".json";
				File dumpFile = new File(root, strFilename);
				fileUri = Uri.fromFile(dumpFile);
				FileWriter fw = new FileWriter(dumpFile);
				BufferedWriter out = new BufferedWriter(fw);
				out.write(this.toJson());
				out.close();
				
				// workaround: force mediascanner to run
				DataStorage.forceMediaScanner(context, fileUri);

			}
		}
		catch (Exception e)
		{
			Log.e(TAG, "Exception: " + e.getMessage());
		}
		
		return fileUri;
	}

	@SuppressLint("NewApi") 
	public Uri writeToFileText(Context context)
	{
		Uri fileUri = null;
		
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (!DataStorage.isExternalStorageWritable())
		{
			Log.e(TAG, "External storage can not be written");
			Toast.makeText(context, "External Storage can not be written",
					Toast.LENGTH_SHORT).show();
		}
		try
		{
			// open file for writing
			File root;
			boolean bSaveToPrivateStorage = sharedPrefs.getBoolean("files_to_private_storage", false);

			if (bSaveToPrivateStorage)
			{
				try
				{
					root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
				}
				catch (Exception e)
				{
					root = Environment.getExternalStorageDirectory();
				}
			}
			else
			{
				root = Environment.getExternalStorageDirectory();
			}

			// check if file can be written
			if (root.canWrite())
			{
				String strFilename = "BetterBatteryStats-"
						+ DateUtils.now("yyyy-MM-dd_HHmmssSSS") + ".txt";
				File dumpFile = new File(root, strFilename);
				fileUri = Uri.fromFile(dumpFile);
				FileWriter fw = new FileWriter(dumpFile);
				BufferedWriter out = new BufferedWriter(fw);
				out.write(this.toStringText(context));
				out.close();
				
				// workaround: force mediascanner to run
				DataStorage.forceMediaScanner(context, fileUri);

			}
		}
		catch (Exception e)
		{
			Log.e(TAG, "Exception: " + e.getMessage());
		}
		
		return fileUri;
	}

	@SuppressLint("NewApi") 
	public String toStringJson(Context context)
	{
		return this.toJson();
	}
	
	/**
	 * Dump the elements on one list
	 * 
	 * @param myList
	 *            a list of StatElement
	 */
	private void dumpList(Context context, List<StatElement> myList, Writer out)
	{
		try
		{
			if (myList != null)
			{
				for (int i = 0; i < myList.size(); i++)
				{
					out.write(myList.get(i).getDumpData(context) + "\n");
		
				}
			}
		}
		catch (Exception e)
		{
			Log.e(TAG, "An error occured serializing list: " + e.getMessage());
		}
	}


}
