package net.strudelline.pebblemon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: james
 * Date: 10/2/13
 * Time: 10:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class PebblemonReceiver extends BroadcastReceiver {
    private static final String TAG = "PebbLemonReceiver";

    public void onReceive(Context context, Intent intent) {
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        Bundle extras = intent.getExtras();

        String messageType = gcm.getMessageType(intent);

        if (GoogleCloudMessaging.
                MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
            Log.i(TAG, "Send error: " + extras.toString());
        } else if (GoogleCloudMessaging.
                MESSAGE_TYPE_DELETED.equals(messageType)) {
            Log.i(TAG, "Deleted messages on server: " +
                    extras.toString());
            // If it's a regular GCM message, do some work.
        } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
            // This loop represents the service doing some work
            Log.i(TAG, "Title:   " + extras.getString("title"));
            Log.i(TAG, "Message: " + extras.getString("message"));

            sendAlertToPebble(context, extras.getString("title"), extras.getString("message"));
        }
        setResultCode(Activity.RESULT_OK);
    }

    private void sendAlertToPebble(Context context, String title, String message) {
        // this must be called every <time interval> in order toe nsure
        // that the paused state is checked.
        final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map data = new HashMap();
        data.put("title", title);
        data.put("body", message);
        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();

        i.putExtra("messageType", "PEBBLE_ALERT");
        i.putExtra("sender", "pebblepush");
        i.putExtra("notificationData", notificationData);

        Log.d("pebblepush", "About to send a modal alert to Pebble: " + notificationData);
        if (PebblemonPauser.isPaused()) {
            Log.i("pebblepush", "PebblePush is paused.  Storing intent instead.");
            addIntentToQueue(i);
        } else {
            for (Intent backedUpIntent : getQueuedIntents()) {
                Log.i("pebblepush", "PebblePush is unpaused.  Sending stored intent");
                context.sendBroadcast(i);
            }
            clearQueuedIntents();
            context.sendBroadcast(i);
        }
    }


    // Stubs for adding support to queue messages while the pebble is not near the phone.
    private void addIntentToQueue(Intent i) {
        // stub for now.
    }

    private void clearQueuedIntents() {
        // stub for now.
    }

    private Intent[] getQueuedIntents() {
        // stub for now.
        return new Intent[0];
    }

}
