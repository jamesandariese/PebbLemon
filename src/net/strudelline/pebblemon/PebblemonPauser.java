package net.strudelline.pebblemon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created with IntelliJ IDEA.
 * User: james
 * Date: 10/6/13
 * Time: 12:08 AM
 * To change this template use File | Settings | File Templates.
 */
public class PebblemonPauser extends BroadcastReceiver {
    private static boolean paused = false;
    final public static String TAG = "PEBBLEMON:PebblemonPauser";

    public static boolean isPaused() {
        return false; // TODO: never pause!
        //return paused;
    }

    public static void setPaused(boolean paused) {
        PebblemonPauser.paused = paused;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.getpebble.action.PEBBLE_CONNECTED")) {
            Log.i(TAG, "Unpausing pebblepush");
            setPaused(false);
        } else if (intent.getAction().equals("com.getpebble.action.PEBBLE_DISCONNECTED")) {
            Log.i(TAG, "Pausing pebblepush");
            setPaused(true);
        } else {
            Log.w(TAG, "Received unexpected intent in PebblePushPauser: " + intent.getAction());
        }
    }
}
