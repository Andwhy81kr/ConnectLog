package com.pandora.mybeacon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class RebootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction()))
        {
            Intent rebootIntent = new Intent(context, BeaconForegroundService.class);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            {
                context.startForegroundService(rebootIntent);
            }
            else
            {
                context.startService(rebootIntent);
            }
        }
    }
}
