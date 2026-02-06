package pl.tdf.atak.TAKWatch;

import static com.garmin.android.connectiq.IQApp.IQAppStatus.INSTALLED;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import java.util.List;

public class WatchClient {

    private static final String TAG = "TAKWatchWatchClient";
    private static final String COMM_WATCH_ID = "95a20b8f-a30c-47a7-9fc9-1ada1f452ba2";

    private IQApp myApp;
    private IQDevice selectedDevice;
    private static ConnectIQ connectIQ;
    private static ConnectIQ.IQApplicationEventListener mapEventsListener;
    private static boolean isSdkReady = false;
    private final Context context;

    private WatchClient(Context context) {
        this.context = context;
    }

    public static WatchClient initialize(Context context, ConnectIQ.IQApplicationEventListener listener) {
        WatchClient instance = new WatchClient(context);
        mapEventsListener = listener;
        connectIQ = ConnectIQ.getInstance();
        connectIQ.initialize(context, true, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkReady() {
                Log.d(TAG, "onSdkReady");
                if (!isSdkReady) {
                    Log.d(TAG, "SDK not ready - initializing");
                    instance.initializeDevice();
                    instance.initializeApp();
                    instance.registerForAppEvents(mapEventsListener);
                    isSdkReady = true;
                }
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus iqSdkErrorStatus) {
                Log.d(TAG, "SDK initialization error");
                isSdkReady = false;
            }

            @Override
            public void onSdkShutDown() {
                Log.d(TAG, "SDK shut down, will attempt reconnect in 5s");
                isSdkReady = false;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    instance.attemptReconnect();
                }, 5000);
            }
        });
        return instance;
    }

    public void sendMessageToWatch(List<Object> msg) {
        if (selectedDevice == null || myApp == null) {
            Log.w(TAG, "sendMessageToWatch: device or app not initialized, skipping");
            return;
        }
        try {
            Log.d(TAG, "sendMessageToWatch: " + msg.toString());
            connectIQ.sendMessage(selectedDevice, myApp, msg, (iqDevice, iqApp, iqMessageStatus) -> {
                Log.d(TAG, "MessageStatus: " + iqMessageStatus);
                if (iqMessageStatus != ConnectIQ.IQMessageStatus.SUCCESS) {
                    Log.w(TAG, "Message failed: " + iqMessageStatus + ", will retry once");
                    retryMessage(msg);
                }
            });
        } catch (InvalidStateException e) {
            Log.e(TAG, "InvalidState sending message, attempting SDK re-init", e);
            attemptReconnect();
        } catch (ServiceUnavailableException e) {
            Log.e(TAG, "Service unavailable, will retry", e);
            retryMessage(msg);
        }
    }

    private void retryMessage(List<Object> msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (selectedDevice != null && myApp != null) {
                    connectIQ.sendMessage(selectedDevice, myApp, msg, (d, a, s) ->
                        Log.d(TAG, "Retry MessageStatus: " + s));
                }
            } catch (Exception e) {
                Log.e(TAG, "Retry also failed", e);
            }
        }, 2000);
    }

    private void attemptReconnect() {
        Log.d(TAG, "Attempting SDK reconnect...");
        try {
            isSdkReady = false;
            connectIQ.initialize(context, true, new ConnectIQ.ConnectIQListener() {
                @Override
                public void onSdkReady() {
                    Log.d(TAG, "Reconnect: SDK ready");
                    initializeDevice();
                    initializeApp();
                    registerForAppEvents(mapEventsListener);
                    isSdkReady = true;
                }

                @Override
                public void onInitializeError(ConnectIQ.IQSdkErrorStatus status) {
                    Log.e(TAG, "Reconnect failed: " + status);
                }

                @Override
                public void onSdkShutDown() {
                    Log.d(TAG, "Reconnect: SDK shut down again");
                    isSdkReady = false;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Reconnect exception", e);
        }
    }

    public List<IQDevice> getDevices() {
        try {
            return connectIQ.getConnectedDevices();
        } catch (InvalidStateException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    public void reload() {
        this.initializeDevice();
        this.initializeApp();

        this.unregisterEvents();
        this.registerForAppEvents(mapEventsListener);
    }

    public void cleanup() {
        this.unregisterEvents();
        this.shoutDown();
    }

    private void unregisterEvents() {
        try {
            connectIQ.unregisterAllForEvents();
        } catch (InvalidStateException e) {
            // do nothing
        }
    }

    private void shoutDown() {
        try {
            connectIQ.shutdown(context);
        } catch (InvalidStateException e) {
            // do nothing
        }
    }

    private SharedPreferences getSharedPref() {
        return PreferenceManager.getDefaultSharedPreferences(this.context);
    }

    private void initializeDevice() {
        String deviceNameFromPreferences = getSharedPref().getString(PreferenceKeys.PREFERENCE_KEY_DEVICE_NAME, null);
        Log.d(TAG, "Value from preferences: " + deviceNameFromPreferences);

        if (deviceNameFromPreferences != null) {
            for (IQDevice d : this.getDevices()) {
                Log.d(TAG, "Lookup of: '" + d.getFriendlyName() + "' vs '" + deviceNameFromPreferences + "'");
                if (d.getFriendlyName().equals(deviceNameFromPreferences)) {
                    selectedDevice = d;
                    Log.d(TAG, "Device matched with preferences selection: " + selectedDevice.getFriendlyName());
                    return;
                }
            }
            Log.d(TAG, "No device found for name: " + deviceNameFromPreferences);
        }
    }

    private void initializeApp() {
        if (selectedDevice == null) {
            Log.d(TAG, "No devices selected in the preferences - skipping connectIQ initialization");
            return;
        }
        try {
            connectIQ.registerForDeviceEvents(selectedDevice, (iqDevice, iqDeviceStatus) -> {
                Log.d(TAG, "Inside registerForDeviceEvents ");
                Log.d(TAG, "DEVICE: " + iqDeviceStatus.toString());
            });

            myApp = new IQApp(COMM_WATCH_ID);

            boolean openApp = getSharedPref().getBoolean(PreferenceKeys.PREFERENCE_KEY_AUTOLAUNCH, false);

            if (openApp) {
                connectIQ.openApplication(selectedDevice, myApp, (iqDevice, iqApp, iqOpenApplicationStatus) -> Log.d(TAG, "openApplication " + iqDevice.getFriendlyName() + ":" + iqApp.getApplicationId() + ":" + iqOpenApplicationStatus));
            }

            connectIQ.getApplicationInfo(COMM_WATCH_ID, selectedDevice, new ConnectIQ.IQApplicationInfoListener() {
                @Override
                public void onApplicationInfoReceived(IQApp app) {
                    if (app != null && app.getStatus() == INSTALLED) {
                        Log.d(TAG, "Version:" + app.version());
                    }
                }

                @Override
                public void onApplicationNotInstalled(String s) {
                    Log.d(TAG, "Missing Application, Corresponding IQ application not installed");
                }
            });
        } catch (InvalidStateException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }

    }

    private void registerForAppEvents(ConnectIQ.IQApplicationEventListener listener) {
        Log.d(TAG, "registerForAppEvents");
        try {
            if (selectedDevice == null || myApp == null) {
                Log.d(TAG, "Devices not initialized");
                return;
            }
            connectIQ.registerForAppEvents(selectedDevice, myApp, listener);
        } catch (InvalidStateException e) {
            throw new RuntimeException(e);
        }
    }
}
