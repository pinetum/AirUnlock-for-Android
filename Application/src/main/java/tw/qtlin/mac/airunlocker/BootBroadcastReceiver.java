package tw.qtlin.mac.airunlocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

/**
 * Created by QT on 2016/7/8.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Boolean bStart = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.start_at_startup_key),
                        false);


        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            if(bStart){
                Intent startServiceIntent = new Intent(context, BLEService.class);
                context.startService(startServiceIntent);
            }

        }


    }
}