package com.RombieSoft.whisper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.RombieSoft.whisper.util.ScreenUtils;

/**
 * Created by IntelliJ IDEA.
 * User: morven@livemail.tw
 * Date: 2/14/12
 * Time: 9:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class WifiConnectivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ScreenUtils.initScreenUtil(context);

        if (WhisperUtility.isConnectedIntent(context, intent)) {
            WhisperUtility.tryConnect(context, false);
        }
        else if (WhisperUtility.isDisconnectedIntent(intent)) {
            WhisperUtility.cleanNotification(context);
        }

    }
}
