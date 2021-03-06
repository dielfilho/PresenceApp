package services;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.internal.LinkedTreeMap;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import connection.PresenceBundle;
import connection.Response;
import connection.ServerRequest;
import constants.Constants;
import danielfilho.ufc.br.com.predetect.constants.PredectConstants;
import danielfilho.ufc.br.com.predetect.datas.WiFiData;
import interfaces.ServerResponseListener;
import io.realm.Realm;
import io.realm.RealmResults;
import models.Presence;
import models.Team;
import presenceapp.com.br.ufc.danielfilho.presenceapp.R;
import util.AppPreferences;
import util.NotificationCreator;

/**
 * Created by Daniel Filho on 10/29/16.
 */

public class SendPresenceService extends Service implements Runnable{


    private Bundle wiFiBundle;

    private AppPreferences preferences;
    private ServerRequest request;
    private PresenceResponseListener presenceResponseListener;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("PRESENCE LOG", "----------------- SEND PRESENCE SERVICE STARTED ----------------");
        wiFiBundle = intent.getBundleExtra(PredectConstants.WIFI_BUNDLE);
        if(wiFiBundle != null){

            this.preferences = AppPreferences.getInstance(getApplicationContext());
            this.presenceResponseListener = new PresenceResponseListener();
            this.request = new ServerRequest(getApplicationContext(), presenceResponseListener);
            new Thread(this).start();

        }else{
            Log.e("PRESENCE LOG", "----------------- WIFI BUNDLE IS NULL ----------------");
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void run() {
        Realm.init(getApplicationContext());
        Realm realm = Realm.getDefaultInstance();
        List<WiFiData> wiFiDataList = wiFiBundle.getParcelableArrayList(PredectConstants.WIFI_SCANNED);
        Log.d("LOG", "SEND PRECENSE SERVICE LIST: "+wiFiDataList.size());
        realm.beginTransaction();
        for(WiFiData wifi : wiFiDataList){
            RealmResults<Team> teams = realm.where(Team.class).equalTo("macAP", wifi.getMAC()).findAll();

            String idTrainee = preferences.getString(Constants.USER_ID_KEY);

            PresenceBundle presenceBundle = new PresenceBundle(teams.get(0).getId(), idTrainee, wifi.getPercent());

            request.post(ServerRequest.PRESENCE_TRAINEE, presenceBundle);

        }
        realm.commitTransaction();

    }

    class PresenceResponseListener implements ServerResponseListener{

        @Override
        public void onSuccess(Response response, String requestUrl) {
            Log.d("LOG", "SUCCESS SEND PRESENCE ----> "+response.getData().toString());
            String message = "Ops, Não foi possível marcar a sua presença!";
            if(requestUrl.equals(ServerRequest.PRESENCE_TRAINEE)){
                if(response.getResult()){
                    LinkedTreeMap<String, Object> json = (LinkedTreeMap<String, Object>) response.getData();
                    Presence presence = new Presence();
                    presence.setDate((Date)json.get("date"));
                    presence.setValid((Boolean)json.get("valid"));

//                    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy'T'HH:mm:ss.SSS'Z'");

                    if(presence.isValid()){
                        message = "Sua presença foi confirmada!";
                        NotificationCreator.create(SendPresenceService.this.getApplicationContext(), message, R.drawable.check);
                    }

                }
            }else{
                Log.d("LOG", "URL ----> "+requestUrl);
            }
        }
        @Override
        public void onFailure(Response response, String requestUrl) {
            Log.d("LOG", "ERRO SEND PRESENCE ----> "+response.getData().toString());
            String message = "Ops, Não foi possível marcar a sua presença!";
            NotificationCreator.create(SendPresenceService.this.getApplicationContext(), message, R.drawable.check);
        }

    }

}
