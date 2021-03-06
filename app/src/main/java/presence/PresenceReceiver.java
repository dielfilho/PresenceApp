package presence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import danielfilho.ufc.br.com.predetect.constants.PredectConstants;
import danielfilho.ufc.br.com.predetect.datas.WiFiData;
import models.CheckPresence;
import services.SendPresenceService;

/**
 * Created by Daniel Filho on 10/25/16.
 */

public class PresenceReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("LOG", "PRESENCE RECEIVER -------------");
        Bundle bundle = intent.getBundleExtra(PredectConstants.BUNDLE_FINISH_OBSERVING);
        if(bundle != null){
            Log.d("LOG", "TRYING TO START THE SERVICE.....");
            Intent intentService = new Intent(context, SendPresenceService.class);
            intentService.putExtra(PredectConstants.WIFI_BUNDLE, bundle);
            context.startService(intentService);
        }else{
            Log.d("LOG", "PRESENCE RECEIVER BUNDLE NULL -------------");
        }
    }
}
