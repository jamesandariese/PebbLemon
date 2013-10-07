package net.strudelline.pebblemon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.apache.http.HttpConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PebblemonSetup extends Activity {
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String PROPERTY_REG_ID = "registration_id";
    public static final String PROPERTY_REST_UUID = "rest_uuid";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String TAG = "PEBBLEMON";
    private static final String SENDER_ID = "153016106242";
    private static final String PEBBLEMON_BASE_URL = "https://pebblemon.appspot.com";
    private static final String PEBBLEMON_REGISTER_PATH = "/register";
    private static final String PEBBLEMON_UNREGISTER_PATH = "/unregister";

    private GoogleCloudMessaging gcm;

    boolean restRegistrationStatus = false;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Context context = getApplicationContext();

        // Check device for Play Services APK.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            String regId = getRegistrationId(context);

            if (regId == null || regId.isEmpty()) {
                registerInBackground();
            } else {
                sendRegistrationIdToBackendInBackground();
            }
        } else {
            Log.i(TAG, "Play Services APK not found or not available");
        }
        updateDisplay();

        getButtonById(R.id.reregister).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                registerInBackground();
            }
        });
        getButtonById(R.id.resetRestUUID).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                rekeyInBackground();
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(PebblemonSetup.this);
                builder.setMessage("This will rekey your account and you will have to reset the key in any apps pushing to PebbLemon currently.  Are you sure?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });
    }

    private Button getButtonById(int id) {
        return (Button)findViewById(id);
    }

    private TextView getTextViewById(int id) {
        return (TextView)findViewById(id);
    }

    private void updateDisplay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getTextViewById(R.id.restUuid).setText(getRestUUID(PebblemonSetup.this));
                TextView registrationStatus = getTextViewById(R.id.registrationStatus);
                String regId = getRegistrationId(getApplicationContext());
                if (regId != null && (!regId.isEmpty())) {
                    if (restRegistrationStatus) {
                        registrationStatus.setText("Status: Registered");
                    } else {
                        registrationStatus.setText("Status: Contacting PebbLemon");
                    }
                } else {
                    registrationStatus.setText("Status: Connecting to Google");
                }
            }
        });
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    private String resetRestUUID(Context context) {
        saveRestUUID(context, "");
        return getRestUUID(context);
    }

    private String getRestUUID(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String uuid = prefs.getString(PROPERTY_REST_UUID, "");
        if (uuid == null || uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString();
            saveRestUUID(context, uuid);
        }
        return uuid;
    }

    private void saveRestUUID(Context context, String uuid) {
        final SharedPreferences prefs = getGCMPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REST_UUID, uuid);
        editor.commit();
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId == null || registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        Log.i(TAG, "Found registration ID: " + registrationId);
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences("net.strudelline.pebblemon.GCM", Context.MODE_PRIVATE);
    }


    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }


    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    Context context = getApplicationContext();
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    String regId = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regId;
                    storeRegistrationId(context, regId);

                    updateDisplay();

                    sendRegistrationIdToBackend();

                    updateDisplay();
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return null;
            }
        }.execute();
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    private JSONObject readStreamAsJson(InputStream in) throws JSONException {
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
        String json = s.hasNext() ? s.next() : "";
        JSONObject object = (JSONObject) new JSONTokener(json).nextValue();
        return object;
    }

    private InputStream httpPostJSON(String url_in, JSONObject postData) throws IOException {
        URL url = new URL(url_in);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        byte[] postBytes = postData.toString().getBytes("UTF-8");
        conn.setRequestProperty("Content-Length", String.format("%d", postBytes.length));
        OutputStream postStream = conn.getOutputStream();
        postStream.write(postBytes);
        postStream.close();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        return in;
    }

    private JSONObject httpGetJSON(String url_in, String...args) throws IOException, JSONException {
        Uri.Builder uriBuilder = Uri.parse(url_in).buildUpon();
        String argName = null;
        for (String arg : args) {
            if (argName == null) {
                argName = arg;
            } else {
                uriBuilder.appendQueryParameter(argName, arg);
                argName = null;
            }
        }
        Log.i(TAG, "Attempting to GET: " + uriBuilder.toString());
        URL url = new URL(uriBuilder.toString());
        URLConnection conn = url.openConnection();
        InputStream in = new BufferedInputStream(conn.getInputStream());
        JSONObject json = readStreamAsJson(in);
        return json;
    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP
     * or CCS to send messages to your app. Not needed for this demo since the
     * device sends upstream messages to a server that echoes back the message
     * using the 'from' address in the message.
     */
    private void sendRegistrationIdToBackend() {
        String regId = getRegistrationId(getApplicationContext());
        if (regId == null || regId.isEmpty()) {
            Log.i(TAG, "attempted to send registration ID to backend when it is empty.  Aborted");
            return;
        }
        restRegistrationStatus = false;
        updateDisplay();
        Log.i(TAG, String.format("Sending Registration ID <%s> and RestAuthToken <%s> to backend", regId, this.getRestUUID(this)));

        JSONObject registration = new JSONObject();
        try {
            registration.put("auth", getRestUUID(this));
            registration.put("regid", getRegistrationId(this));
        } catch (JSONException e) {
            // this is clearly ridiculous and impossible
            Log.e(TAG, "Couldn't encode registration ID or auth token as JSON");
        }
        try {
            httpPostJSON(PEBBLEMON_BASE_URL + PEBBLEMON_REGISTER_PATH, registration);
        } catch (IOException e) {
            Log.e(TAG, "Could not contact pebblemon servers!");
            return;
        }
        restRegistrationStatus = true;
        updateDisplay();
    }

    private void unregisterWithBackend()  {
        String regId = getRegistrationId(getApplicationContext());
        if (regId == null || regId.isEmpty()) {
            // can't be registered if we're not registered with GCM.  no-op.
            return;
        }
        restRegistrationStatus = false;
        Log.i(TAG, String.format("Sending Registration ID <%s> and RestAuthToken <%s> to backend", regId, this.getRestUUID(this)));

        JSONObject registration = new JSONObject();
        try {
            registration.put("regid", getRegistrationId(this));
        } catch (JSONException e) {
            // this is clearly ridiculous and impossible
            Log.e(TAG, "Couldn't encode registration ID as JSON");
        }
        try {
            httpPostJSON(PEBBLEMON_BASE_URL + PEBBLEMON_UNREGISTER_PATH, registration);
        } catch (IOException e) {
            return;
        }
        restRegistrationStatus = false;
    }

    private void sendRegistrationIdToBackendInBackground() {
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                sendRegistrationIdToBackend();
                updateDisplay();
                return null;
            }
        }.execute();
    }

    private void rekeyInBackground() {
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                unregisterWithBackend();
                updateDisplay();
                resetRestUUID(PebblemonSetup.this);
                updateDisplay();
                sendRegistrationIdToBackend();
                updateDisplay();
                return null;
            }

            @Override
            protected void onPostExecute(Void o) {
                super.onPostExecute(o);
                updateDisplay();
            }
        }.execute();
    }
}
