package com.example.mobibox.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import com.example.mobibox.service.DataService;

/**
 * BroadcastReceiver that listens for network connectivity changes.
 * When network becomes available, it triggers pending uploads in DataService.
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";
    private static NetworkListener listener;

    /**
     * Interface for network state change notifications
     */
    public interface NetworkListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }

    /**
     * Register a listener to be notified when network state changes.
     * @param networkListener The listener to register
     */
    public static void setListener(NetworkListener networkListener) {
        listener = networkListener;
    }

    /**
     * Unregister the current listener.
     */
    public static void clearListener() {
        listener = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        // Check for connectivity change
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            boolean isConnected = isNetworkConnected(context);

            if (isConnected) {
                Log.i(TAG, "✅ Network connection restored - triggering pending uploads");
                if (listener != null) {
                    listener.onNetworkAvailable();
                }
            } else {
                Log.w(TAG, "❌ Network connection lost");
                if (listener != null) {
                    listener.onNetworkLost();
                }
            }
        }
    }

    /**
     * Check if network is currently connected.
     * @param context Application context
     * @return true if network is available, false otherwise
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }

            // Check for various network types
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        } else {
            // Legacy method for older Android versions
            android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnectedOrConnecting();
        }
    }
}