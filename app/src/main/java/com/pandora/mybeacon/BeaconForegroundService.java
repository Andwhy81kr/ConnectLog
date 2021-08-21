package com.pandora.mybeacon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BeaconForegroundService extends Service implements BeaconConsumer {
    protected static final String TAG = "BeaconForegroundService";
    public static final String CHANNEL_ID = "BeaconForegroundServiceChannel";
    private String[] uuidArray = new String[]{"10f86430-1346-11e4-9191-0800200c9a66"};
    private BeaconManager beaconManager;
    private SharedPreferences preferences;
    private BeaconParser beaconParser;
    private NearBeaconLogList userLog;

    private static final String UUID_INDEX = "uuidIndex";
    private static final String MAJOR_ID = "majorID";
    private static final String MINOR_ID = "minorID";

    String uuidIndex;
    String majorID;
    String minorID;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        super.onCreate();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String jsonData = preferences.getString("LogJsonData", "");
        //String jsonData = LoadData("");
        uuidIndex = preferences.getString(UUID_INDEX, "");
        majorID = preferences.getString(MAJOR_ID, "-1");
        minorID = preferences.getString(MINOR_ID, "-1");
        if (jsonData == "") {
            userLog = new NearBeaconLogList();
        } else {
            userLog = new Gson().fromJson(jsonData, NearBeaconLogList.class);
        }
        beaconParser = new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25");
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(beaconParser);
        beaconManager.bind(this);

        Log.i(TAG,majorID+":"+minorID);

        Beacon broadcastBeacon = new Beacon.Builder().setId1(uuidArray[0])
                .setId2(majorID)
                .setId3(minorID)
                .setManufacturer(0x004c)
                .setTxPower(-59)
                .setBluetoothName("Pandora")
                .setDataFields(Arrays.asList(new Long[]{0l})) // Remove this for beacon layouts without d: fields
                .build();

        BeaconTransmitter beaconTransmitter = new BeaconTransmitter(getApplicationContext(), beaconParser);
        beaconTransmitter.startAdvertising(broadcastBeacon, new AdvertiseCallback() {

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertisement start failed with code: " + errorCode);
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Advertisement start succeeded.");
            }
        });
        SendLogToServer();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    public BeaconForegroundService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flag, int startID) {
        String input = intent.getStringExtra("inputExtra");
        input = input == null ? "NULL" : input;
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText("Scanning...").setSmallIcon(R.drawable.ic_beaconservice)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setContentIntent(pendingIntent).build();

        startForeground(1, notification);

        // beaconManager

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Forground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        RangeNotifier notifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                if (collection.size() > 0) {

                    Log.d(TAG, "Detacting [" + collection.size() + "]  Beacons");
                    Log.d(TAG, "==============================================");
                    boolean needUpdate = false;
                    for (Beacon beacon : collection) {
                        if (CheckUUID(beacon.getId1())) {
                            Log.d(TAG, "uuid : " + beacon.getId1() + "\nDIstance : " + beacon.getDistance());
                            userLog.AddLogData(beacon);
                            needUpdate = true;

                        }

                    }

                    if (needUpdate) {
                        preferences.edit().putString("LogJsonData", new Gson().toJson(userLog)).commit();
                        preferences.edit().apply();

                        //Log.d(TAG,LoadData("----"));
                    }
                    Log.d(TAG, "==============================================");
                }

            }
        };
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRang", null, null, null));
            beaconManager.addRangeNotifier(notifier);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    private boolean CheckUUID(Identifier uuid) {
        for (String id : uuidArray) {
            if (uuid.toString().equals(id)) return true;
        }
        return false;
    }


    private void SendLogToServer() {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://postman-echo.com/post";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.i(TAG, "onResponse\n" + response);

                        //if Success
                        //preferences.edit().remove("LogJsonData").commit();
                        //preferences.edit().apply();
                        //userLog = new NearBeaconLogList();

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(TAG, "onErrorResponse\n" + error.toString());
            }

        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError{
                Map<String, String> params = new HashMap<String, String>();
                params.put("log", preferences.getString("LogJsonData", ""));
                return params;
            }
        };


        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }
}
class NearBeaconLogList
{
    public HashMap<String, ArrayList <NearBeaconInfo>> logList;
    public NearBeaconLogList()
    {
        logList = new HashMap<String, ArrayList <NearBeaconInfo>>();
    }
    public void AddLogData(Beacon beacon)
    {
        String userKey ="0:"+beacon.getId2()+":"+beacon.getId3();

        if(logList.containsKey(userKey))
        {
            logList.get(userKey).add(new NearBeaconInfo(beacon.getDistance()));
        }
        else
        {
            ArrayList<NearBeaconInfo> infolist = new ArrayList <NearBeaconInfo>();
            infolist.add(new NearBeaconInfo(beacon.getDistance()));
            logList.put(userKey, infolist);
        }
    }
}
class NearBeaconInfo
{
    public Date logDate;
    public double distance;
    public NearBeaconInfo(double distance){
        logDate = new Date();
        this.distance = distance;
    };
}