package services;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.file.FilePrinter;
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
import danielfilho.ufc.br.com.predetect.managers.NetworkManager;
import interfaces.ServerResponseListener;
import io.realm.Realm;
import io.realm.RealmResults;
import models.Presence;
import models.Team;
import presence.PresenceReceiver;
import presence.StartObservingReceiver;
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
    private Intent myIntent;

    private NetworkManager networkManager;

    private Realm realm;

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
        try {
            XLog.init(LogLevel.ALL, new FilePrinter.Builder(PredectConstants.LOG_PATH+"presence").build());
        }catch (Exception e){};

        XLog.d(System.currentTimeMillis()+"|  ----------------- SEND PRESENCE SERVICE STARTED ----------------");
        Log.d("PRESENCE LOG", "----------------- SEND PRESENCE SERVICE STARTED ----------------");

        this.myIntent = intent;

        wiFiBundle = intent.getBundleExtra(PredectConstants.WIFI_BUNDLE);
        if(wiFiBundle != null){

            this.networkManager = NetworkManager.getInstance();
            this.networkManager.holdWifiLock(this);

            this.preferences = AppPreferences.getInstance(getApplicationContext());
            this.presenceResponseListener = new PresenceResponseListener();
            this.request = new ServerRequest(getApplicationContext(), presenceResponseListener);
            new Thread(this).start();

        }else{
            XLog.e(System.currentTimeMillis()+"|  ----------------- WIFI BUNDLE IS NULL ----------------");
            Log.e("PRESENCE LOG", "----------------- WIFI BUNDLE IS NULL ----------------");
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(networkManager != null)
            this.networkManager.releaseWifiLock();

        StartObservingReceiver.completeWakefulIntent(myIntent);
        PresenceReceiver.completeWakefulIntent(myIntent);
    }

    @Override
    public void run() {

        Realm.init(getApplicationContext());
        Realm realm = Realm.getDefaultInstance();
        List<WiFiData> wiFiDataList = wiFiBundle.getParcelableArrayList(PredectConstants.WIFI_SCANNED);
        realm.beginTransaction();

        for(WiFiData wifi : wiFiDataList){
            RealmResults<Team> teams = realm.where(Team.class).equalTo("macAP", wifi.getMAC()).findAll();

            String idTrainee = preferences.getString(Constants.USER_ID_KEY);

            PresenceBundle presenceBundle = new PresenceBundle(teams.get(0).getId(), idTrainee, wifi.getPercent());

            XLog.d(System.currentTimeMillis()+" ==== TRAINEE : "+idTrainee+" MAC : "+wifi.getMAC()+" PERCENT : "+wifi.getPercent());

            request.post(ServerRequest.PRESENCE_TRAINEE, presenceBundle);

        }
        realm.commitTransaction();
    }

    class PresenceResponseListener implements ServerResponseListener{

        public PresenceResponseListener() {
            try {
                XLog.init(LogLevel.ALL, new FilePrinter.Builder(PredectConstants.LOG_PATH+"presence").build());
            }catch (Exception e){};

        }

        @Override
        public void onSuccess(Response response, String requestUrl) {
            Log.d("LOG", "SUCCESS SEND PRESENCE ----> "+requestUrl);
            XLog.d(System.currentTimeMillis()+"|  SUCCESS SEND PRESENCE ----> "+requestUrl);
            String message = "";
            if(requestUrl.equals(ServerRequest.PRESENCE_TRAINEE)){
                if(response.getResult()){
                    LinkedTreeMap<String, Object> json = (LinkedTreeMap<String, Object>) response.getData();
                    Presence presence = new Presence();
                    presence.setValid((Boolean)json.get("valid"));
                    presence.setLastCheck((Boolean)json.get("lastCheck"));
                    presence.setChecks(Integer.parseInt(json.get("checks").toString().charAt(0)+""));

                    if(presence.isLastCheck() && presence.isValid()){
                        message = "Sua presença foi confirmada!";
                    }else if(presence.isLastCheck() && !presence.isValid()){
                        message = "Sua média de presença não atingiu o mínimo especificado para a turma!";
                    }else if(presence.isLastCheck() && response.getData() == null){
                        message = "Ops, Não foi possível confirmar a sua presença!";
                    }else if(!presence.isLastCheck()){
                        message = presence.getChecks()+"º checagem de presença concluída!";
                    }
                    NotificationCreator.create(SendPresenceService.this, message, R.drawable.wifilogo);
                }
            }else{
                XLog.d(System.currentTimeMillis()+"|  OTHER URL ----> "+requestUrl);
                Log.d("LOG", "URL ----> "+requestUrl);
            }
        }
        @Override
        public void onFailure(Response response, String requestUrl) {
            Log.d("LOG", "ERRO SEND PRESENCE ----> "+response.getData());
            XLog.d(System.currentTimeMillis()+"|  ERRO SEND PRESENCE ----> "+response.getData());
            String message = "Ops, Não foi possível marcar a sua presença!";
            NotificationCreator.create(SendPresenceService.this.getApplicationContext(), message, R.drawable.wifilogo);
        }

    }

}
