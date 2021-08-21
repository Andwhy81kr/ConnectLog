package com.pandora.mybeacon;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    protected static final String TAG = "MonitoringActivity";
    private BeaconManager beaconManager;
    private List<Beacon> beaconList = new ArrayList<>();
    TextView textView;
    Button startButton;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_BACKGROUND_LOCATION = 2;
    private static final String UUID_INDEX = "uuidIndex";
    private static final String MAJOR_ID = "majorID";
    private static final String MINOR_ID = "minorID";

    private SharedPreferences preferences;
    String uuidIndex;
    String majorID;
    String minorID;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        onCheckRequestParmissions();

        textView = (TextView)findViewById(R.id.Textview);
        startButton= findViewById(R.id.button);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        uuidIndex = preferences.getString(UUID_INDEX, "");
        majorID = preferences.getString(MAJOR_ID, "");
        minorID = preferences.getString(MINOR_ID, "");
        checkAndStart();
        updateServiceBtn();
    }
    private void checkAndStart()
    {
        if(!majorID.isEmpty()&&!minorID.isEmpty())
        {
            startService();
        }
    }
    private void updateServiceBtn()
    {
        if(uuidIndex.isEmpty()||majorID.isEmpty()||minorID.isEmpty())
        {
            textView.setText("Not Yet Setting Beacon");
            startButton.setText("StartBeacon");
            startButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    uuidIndex = "0";
                    majorID = String.valueOf(new Random().nextInt(2<<15));
                    minorID = String.valueOf(new Random().nextInt(2<<15));
                    preferences.edit().putString(UUID_INDEX, uuidIndex).commit();
                    preferences.edit().putString(MAJOR_ID, majorID).commit();
                    preferences.edit().putString(MINOR_ID, minorID).commit();
                    preferences.edit().apply();

                    startService();
                    updateServiceBtn();
                }
            });

        }
        else
        {
            textView.setText("Already Setting Beacon");
            textView.append("\nmajorID : "+majorID);
            textView.append("\nminorID : "+minorID);
            startButton.setText("StopBeacon");
            startButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){

                    majorID = "";
                    minorID = "";
                    preferences.edit().remove(MAJOR_ID).commit();
                    preferences.edit().remove(MINOR_ID).commit();
                    preferences.edit().apply();

                    stopService();
                    updateServiceBtn();
                }
            });
        }
    }
    private void onCheckRequestParmissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("This app needs background location access");
                        builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                            @TargetApi(23)
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                        PERMISSION_REQUEST_BACKGROUND_LOCATION);
                            }

                        });
                        builder.show();
                    }
                    else {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("경고");
                        builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                        PERMISSION_REQUEST_BACKGROUND_LOCATION);
                            }

                        });
                        builder.show();
                    }

                }
            } else {
                if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            PERMISSION_REQUEST_FINE_LOCATION);
                }
                else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location access to this app.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    PERMISSION_REQUEST_FINE_LOCATION);
                        }

                    });
                    builder.show();
                }

            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "fine location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
            case PERMISSION_REQUEST_BACKGROUND_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "background location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    public void CreateLogFile(View view)
    {
        Log.i(TAG, "CreateLogFile   "+Environment.getExternalStorageDirectory());
        ClipboardManager clipboardManager = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("Log", preferences.getString("LogJsonData", ""));
        clipboardManager.setPrimaryClip(clipData);
        Log.i(TAG, "===="+preferences.getString("LogJsonData", ""));
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        //beaconManager.unbind(this);
    }
    @Override
    public void onBeaconServiceConnect() {

        RangeNotifier notifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                if(collection.size()>0)
                {

                    Log.d(TAG, "Detacting ["+ collection.size()+"]  Beacons");
                    Log.d(TAG,"==============================================");
                    for (Beacon beacon : collection) {
                        Log.d(TAG,"uuid : "+beacon.getId1()+"\nDIstance : "+beacon.getDistance());
                    }
                    Log.d(TAG,"==============================================");
                }
            }
        };
        try{
            beaconManager.startRangingBeaconsInRegion(new Region("myRang", null, null, null));
            beaconManager.addRangeNotifier(notifier);
        }
        catch (RemoteException e)
        {}
    }

    public void startService()
    {
        Intent serviceIntent = new Intent(this, BeaconForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    public void stopService()
    {
        Intent serviceIntent = new Intent(this, BeaconForegroundService.class);
        stopService(serviceIntent);
    }

}
