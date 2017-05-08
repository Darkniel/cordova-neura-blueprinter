/**
 */
package com.neuraweb;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

import java.util.Date;


import zj.com.command.sdk.Command;
import zj.com.command.sdk.PrinterCommand;
//import zj.com.customize.sdk.Other;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class BluePrinter extends CordovaPlugin {
    // Debugging
    private static final String TAG = "BluePrinter";
    private static final boolean DEBUG = true;
    /******************************************************************************************************/
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;
    public static final int MESSAGE_UNABLE_CONNECT = 7;
    /*******************************************************************************************************/
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int ABANDONED_ACTION = 0;
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;


    public static int BT_NOT_AVAILABLE = 8;
    public static int DEVICE_ADDRESS = 20;
    public static String BT_NOT_ENABLED = "Bluetooth is not enabled";
    private static final boolean IS_AT_LEAST_LOLLIPOP = Build.VERSION.SDK_INT >= 21;

    /*******************************************************************************************************/
    private static final String CHINESE = "GBK";
    /*********************************************************************************/
    /******************************************************************************************************/
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;

    private CallbackContext printerCallbackContext = null;

    private JSONObject responseOjv = null;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        responseOjv = new JSONObject();
        Log.d(TAG, "Initializing BluePrinter");
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if (action.equals("start")) {
            Log.i(TAG, "start");

            if (printerCallbackContext != null) {
                callbackContext.error("Printer listener already running.");
                return true;
            }
            printerCallbackContext = callbackContext;
            Log.i(TAG, "SETEADO EL CONTEXT");
            return true;

        } else if (action.equals("connect")) {
//            if(printerCallbackContext != null){
//                String a = printerCallbackContext.getCallbackId();
//                String b = callbackContext.getCallbackId();
//                Boolean compare = a.equals(b);
//                Log.i(TAG,"CALLBACK IGUALES " + compare.toString()+ " A "+a+"B "+b);
//                Log.i(TAG,"CALLBACK INICIAL ESTA FINALIZADO " + printerCallbackContext.isFinished());
//            }

            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            // If the adapter is null, then Bluetooth is not supported

            if (mBluetoothAdapter == null) {
                Log.i(TAG, "BLUETOOTH ADAPTER UNAVAILABLE");
                try {
                    responseOjv.put("id", BT_NOT_AVAILABLE);
                    responseOjv.put("msg", getStringResource("bt_not_available"));
                    sendUpdate(responseOjv, true);
                    Toast.makeText(this.cordova.getActivity().getApplicationContext(), getStringResource("not_connected"), Toast.LENGTH_SHORT)
                            .show();
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return true;
            }

            Log.i(TAG, "BLUETOOTH ADAPTER AVAILABLE");
            findPrinter();

            return true;
        } else if (action.equals("print")) {
            if(args != null){
                try {
                    String phrase = args.getString(0);
                    // An example of returning data back to the web layer
//                    String msg = "áéíóúñ Hola esto es un test :)\n";
                    SendDataByte(phrase.getBytes());
                    // SendDataByte(msg.getBytes("GBK"))
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return true;

        } else if (action.equals("stop")) {
            printerCallbackContext = null;
            callbackContext.success();
            return true;
        } else if (action.equals("disconnect")) {
            if (mService != null)
                mService.stop();

//            printerCallbackContext = null;
//            callbackContext.success();
            return true;
        }
        return true;
    }

    private void findPrinter() {
        if (DEBUG)
            Log.i(TAG, "FIND PRINTER");
        PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
        r.setKeepCallback(true);
        printerCallbackContext.sendPluginResult(r);

        // If Bluetooth is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);

            cordova.setActivityResultCallback(BluePrinter.this);
            this.cordova.getActivity().startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the session
        } else {
            connect();
        }
    }

    /****************************************************************************************************/
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (DEBUG)
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            try {
                                toastme("p_connected");
                                responseOjv.put("id", msg.arg1);
                                responseOjv.put("msg", getStringResource("p_connected"));
                                sendUpdate(responseOjv, true);
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            try {
                                toastme("p_connecting");
                                responseOjv.put("id", msg.arg1);
                                responseOjv.put("msg", getStringResource("p_connecting"));
                                sendUpdate(responseOjv, true);
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            break;
                        case BluetoothService.STATE_LISTEN:
                            try {
                                toastme("p_listen");
                                responseOjv.put("id", msg.arg1);
                                responseOjv.put("msg", getStringResource("p_listen"));
                                sendUpdate(responseOjv, true);
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                            break;
                        case BluetoothService.STATE_NONE:
                            try {
                                toastme("p_none");
                                responseOjv.put("id", msg.arg1);
                                responseOjv.put("msg", getStringResource("p_none"));
                                sendUpdate(responseOjv, true);
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    Log.i(TAG, "MESSAGE_WRITE");
                    break;
                case MESSAGE_READ:
                    Log.i(TAG, "MESSAGE_READ");
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
//                    Toast.makeText(getApplicationContext(),
//                            "Connected to " + mConnectedDeviceName,
//                            Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "CONNETCTED TO: " + mConnectedDeviceName);
                    break;
                case MESSAGE_TOAST:
                    Log.i(TAG, "MESSAGE_TOAST: " + msg.getData().getString(TOAST));
                    toastme(msg.getData().getString(TOAST));
                    break;
                case MESSAGE_CONNECTION_LOST:    //蓝牙已断开连接
                    Log.i(TAG, "MESSAGE_CONNECTION_LOST");
                    try {
                        // toastme("p_none");
                        responseOjv.put("id", msg.what);
                        responseOjv.put("msg", getStringResource("p_unable_connect"));
                        sendUpdate(responseOjv, true);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    break;
                case MESSAGE_UNABLE_CONNECT:     //无法连接设备
                    Log.i(TAG, "MESSAGE_CONNECTION_LOST");
                    try {
                        //toastme("p_none");
                        responseOjv.put("id", msg.what);
                        responseOjv.put("msg", getStringResource("p_lost_connection"));
                        sendUpdate(responseOjv, true);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    break;
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG)
            Log.d(TAG, "onActivityResult REQUEST:"+requestCode +" RESULT:"+ resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE: {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(
                            DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        BluetoothDevice device = mBluetoothAdapter
                                .getRemoteDevice(address);
                        // Attempt to connect to the device
                        mService.connect(device);
                        try {
                            //toastme("p_none");
                            responseOjv.put("id", DEVICE_ADDRESS);
                            responseOjv.put("msg", address);
                            sendUpdate(responseOjv, true);
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }else{
                    Log.d(TAG, "NO SE TOMO ACCION");

                    if (mService != null)
                        mService.stop();
                    if(printerCallbackContext !=null){
                        try {
                            // toastme("p_none");
                            responseOjv.put("id", 0);
                            responseOjv.put("msg", getStringResource("p_unable_connect"));
                            sendUpdate(responseOjv, true);
                            // printerCallbackContext.success();
//                            printerCallbackContext = null;
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                // When the request to enable Bluetooth returns
                // CallbackContext callbackContext = (IS_AT_LEAST_LOLLIPOP ? cordova.getActivity().getWindow().getContext() : cordova.getActivity().getApplicationContext());
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a session
                    toastme("bt_activated");
                    //callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, "Bluethoot Activado"));
                    connect();
                    // KeyListenerInit();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    toastme("bt_no_activated");
                    // Toast.makeText(callbackContext, BT_NOT_ENABLED, Toast.LENGTH_SHORT).show();
                    //callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Bluethoot No Activado"));
                    // finish();
                }
                break;
            }
        }
    }

    private void connect() {
        if (mService != null) {
            mService.stop();
            mService = null;
        }
        mService = new BluetoothService(this.cordova.getActivity().getApplicationContext(), mHandler);

//        Intent serverIntent = new Intent(this.cordova, DeviceListActivity.class);
//        this.cordova.getActivity().startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
//        Intent intent = new Intent("com.neuraweb.DeviceList", DeviceListActivity.class);
        Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), DeviceListActivity.class);
//        Intent intent = new Intent((CordovaPlugin) this, DeviceListActivity.class);
//        Intent intent = new Intent();
//        intent.setClassName("com.pkg.drs","com.pkg.drs.ActivityToCall");
//        cordova.startActivityForResult(intent);
//        this.cordova.getActivity().startActivityForResult(intent);
        this.cordova.startActivityForResult((CordovaPlugin) this, intent, 1);


    }

    /*
 *SendDataByte
 */
    private void SendDataByte(byte[] data) {

        if (mService == null || mService.getState() != BluetoothService.STATE_CONNECTED) {
            Log.d(TAG, getStringResource("not_connected"));

            Toast.makeText(this.cordova.getActivity().getApplicationContext(), getStringResource("not_connected"), Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        mService.write(data);
    }

    private int getAppResource(String name, String type) {
        String package_name = cordova.getActivity().getPackageName();
        return cordova.getActivity().getResources().getIdentifier(name, type, package_name);
    }

    private String getStringResource(String name) {
        return cordova.getActivity().getString(
                cordova.getActivity().getResources().getIdentifier(
                        name, "string", cordova.getActivity().getPackageName()));
    }

    private void sendUpdate(JSONObject info, boolean keepCallback) {
        if (printerCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            printerCallbackContext.sendPluginResult(result);
        }
    }

    private void toastme(String id) {
        Toast.makeText(this.cordova.getActivity().getApplicationContext(), getStringResource(id), Toast.LENGTH_SHORT).show();
    }
}
