package com.RombieSoft.whisper.service;

import android.R;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.RombieSoft.whisper.MyActivity;
import com.RombieSoft.whisper.WhisperUtility;
import com.RombieSoft.whisper.pref.ServiceItemList;
import com.RombieSoft.whisper.util.AppLogger;
import com.RombieSoft.whisper.util.HttpUtils;
import com.RombieSoft.whisper.wispr.*;

/**
 * Created by IntelliJ IDEA.
 * User: morven@livemail.tw
 * Date: 2/16/12
 * Time: 8:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class WISPrLoginService extends IntentService {
    private static String TAG = WISPrLoginService.class.getName();

    static AppLogger logger;
    static boolean isDebug = false;

    public static void log(int type, String msg) {
        if (type == Log.DEBUG && !isDebug)
            return;
        if (logger != null)
            logger.log(type, msg);
    }

    public WISPrLoginService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (logger == null) {
            try {
                logger = new AppLogger(this);

                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                isDebug = pref.getBoolean(WhisperUtility.KEY_DEBUG, false);
            }
            catch(Exception e) {
                Log.e("Whisper", "WISPrLogSrv", e);
            }
        }
        
        String action = intent.getAction();
        if (action.equals(WhisperUtility.KEY_ACTION_DEBUG))
            isDebug = intent.getBooleanExtra(WhisperUtility.KEY_DEBUG, false);
        else if (action.equals(WhisperUtility.KEY_ACTION_LOG)) {
            int index = intent.getIntExtra(WhisperUtility.KEY_IDX, -1);
            String user = intent.getStringExtra(WhisperUtility.KEY_USER);
            String pass = intent.getStringExtra(WhisperUtility.KEY_PASS);
            String ssid = intent.getStringExtra(WhisperUtility.KEY_SSID);
            String service = intent.getStringExtra(WhisperUtility.KEY_SERVICE);
            boolean itw_auto = intent.getBooleanExtra(WhisperUtility.KEY_ITW,false);

            ServiceLogger logger = null;

            if (ssid.equalsIgnoreCase("TPE-Free")) {
                logger = new WiFlyLogger();
                log(Log.INFO, "LOGIN " + service + " ssid " + ssid + "(WiFlyLogger)");
            }
            else
            if (ssid.toLowerCase().contains("wifly")) {
                logger = new WiFlyLogger();
                log(Log.INFO, "LOGIN " + service + " ssid " + ssid + "(WiFlyLogger)");
            }
            else if (itw_auto /*ServiceLogger.isTaiwanService(ssid)*/) {
                user = WhisperUtility.reformatTaiwanLogin(service, ssid, user);
                if (user.length() <= 0) {
                    return;
                }
                if (MyActivity.rd_debug) {
                    Toast.makeText(this, user, Toast.LENGTH_LONG).show();
                }
                logger = new TaiwanLogger();
                log(Log.INFO, "LOGIN " + service + " ssid " + ssid + "(TaiwanLogger)");
            }
            else {
                logger = new WISPrLogger();
                log(Log.INFO, "LOGIN " + service + " ssid " + ssid + "(WISPr)");
            }

            if (logger.setServiceSSID(service)) {
                LoggerResult result = logger.login(user, pass);
                notifyConnectionResult(this, result, ssid, service, index);
            }
            else {
                log(Log.WARN, "Not valid service for the Wi-Fi");
                notifyConnectionResult(this,
                        new LoggerResult(WISPrConstants.WISPR_RESPONSE_CODE_INTERNAL_ERROR, ""),
                        ssid, service, index);
            }

        }
        else if (action.equals(WhisperUtility.KEY_ACTION_LOGOFF)) {
            String uri = intent.getStringExtra(WhisperUtility.KEY_URI).trim();
            String service = intent.getStringExtra(WhisperUtility.KEY_SERVICE).trim();
            int index = intent.getIntExtra(WhisperUtility.KEY_IDX, -1);

            if (index != -1 && uri.length() > 0 && service.length() > 0) {
                log(Log.INFO, "LOGOFF " + service + " " + uri);

                //Toast.makeText(this, getString(com.RombieSoft.whisper.R.string.strLogoff) + service, Toast.LENGTH_LONG).show();
                
                try {
                    HttpUtils.getUrl(uri);
                    WhisperUtility.cleanNotification(this);
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                    pref.edit().remove(ServiceItemList.KEY_SERVICE_LOGOFF+index).commit();
                }
                catch (Exception e) {
                    WISPrLoginService.log(Log.ERROR, "Error at logoff " + e.getMessage());
                }
            }
        }
    }

    static private boolean isWisprConnected(String resultDesc) {
        return resultDesc.equals(WISPrConstants.WISPR_RESPONSE_CODE_LOGIN_SUCCEEDED)
                || resultDesc.equals(WISPrConstants.ALREADY_CONNECTED);
    }
    
    private void notifyConnectionResult(Context context, LoggerResult result, String ssid, String service, int index) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        String resultDesc = result.getResult();
        Intent appIntent = null;
        Notification notification = null;
        PendingIntent pendingIntent = null;
        String notificationTitle = null;
        String notificationText = null;

        if (isWisprConnected(resultDesc)) {
            appIntent = new Intent(context, MyActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingIntent = PendingIntent.getActivity(context, 1, appIntent, 0);
            notification = new Notification(R.drawable.ic_media_play, notificationTitle, System.currentTimeMillis());
            notificationTitle = "1st Whisper";
            if (resultDesc.equals(WISPrConstants.ALREADY_CONNECTED)) {
                notificationText = ssid + getString(com.RombieSoft.whisper.R.string.strConnectAlready);
            }
            else {
                notificationText = ssid + getString(com.RombieSoft.whisper.R.string.strConnected);
                notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
            }

            if (index >= 0 && result.getLogOffUrl() != null && result.getLogOffUrl().length() > 0) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);

                SharedPreferences.Editor editor = pref.edit();
                editor.putString(ServiceItemList.KEY_SERVICE_LOGOFF+index, result.getLogOffUrl());
                editor.commit();
            }
            Toast.makeText(context, notificationText, Toast.LENGTH_LONG).show();
        }
        else {
            if (resultDesc.equals(WISPrConstants.WISPR_RESPONSE_CODE_LOGIN_FAILED)) {
                resultDesc = getString(com.RombieSoft.whisper.R.string.strLoginFailure);
            } else if (resultDesc.equals(WISPrConstants.WISPR_NOT_PRESENT)) {
                resultDesc = getString(com.RombieSoft.whisper.R.string.strNoWispr);
            } else if (resultDesc.equals(WISPrConstants.WISPR_RESPONSE_CODE_INTERNAL_ERROR)) {
                resultDesc = getString(com.RombieSoft.whisper.R.string.strInternalError);
            }

            notificationTitle = service + getString(com.RombieSoft.whisper.R.string.strConnectFailed);
            notificationText = ssid + ": " + resultDesc;
            String msg = result.getMsg();
            if (msg.length() > 0)
                notificationText += "(" + msg + ")";
            appIntent = new Intent(context, MyActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            pendingIntent = PendingIntent.getActivity(context, 1, appIntent, 0);
            notification = new Notification(R.drawable.ic_media_pause, notificationTitle, System.currentTimeMillis());
            notification.flags = notification.flags | Notification.FLAG_AUTO_CANCEL;
            Toast.makeText(context, notificationText, Toast.LENGTH_LONG).show();
        }

        if (appIntent != null) {
            notification.setLatestEventInfo(context, notificationTitle, notificationText, pendingIntent);
            notificationManager.notify(1, notification);
        }

    }
}
