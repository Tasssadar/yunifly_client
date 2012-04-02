package com.tassadar.yunifly;

import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.widget.AdapterView.OnItemClickListener;

public class YuniFlyclientActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_INFO      = 3;

    public static final byte CONNECTION_STATE = 1;
    public static final byte CONNECTION_DATA  = 2;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3; 
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5; 
    public static final int CONNECTION_FAILED = 1;
    public static final int CONNECTION_LOST = 2;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";

    public static final byte STATE_CONNECTED        = 0x01;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.device_list);


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter discoverFinished = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        IntentFilter filterBTChange = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
        registerReceiver(mBTStateChangeReceiver, filterBTChange);
        registerReceiver(mBTStateChangeReceiver, discoverFinished);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null)
            ShowAlert("This device does not have bluetooth adapter");
        else if(!mBluetoothAdapter.isEnabled())
            EnableBT();

        gestureLib = GestureLibraries.fromRawResource(this, R.raw.gestures);
        if (!gestureLib.load()) {
            finish();
        }
        initUi();
    }

    @Override
    public void onDestroy() 
    {
        super.onDestroy();
        if(isFinishing())
        {
            Disconnect(false);
            unregisterReceiver(mReceiver);
            unregisterReceiver(mBTStateChangeReceiver);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode)
        {
        case REQUEST_ENABLE_BT:
        {
            if(resultCode != Activity.RESULT_OK)
            {
                if(btTurnOn != 0)
                    ShowAlert("Bluetooth is disabled!");
                break;
            }

            switch(btTurnOn)
            {
            case 1:
                FindDevices();
                break;
            case 2:
                Connect(connectView);
                break;
            case 0:
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices(); 
                if (pairedDevices.size() > 0) {
                    mPairedDevices.clear();
                    findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
                    for (BluetoothDevice device : pairedDevices) {
                        mPairedDevices.add(device.getName() + "\n" + device.getAddress());
                    }
                }
                break;
            }

            btTurnOn = 0;
            connectView = null;
            break;
        }
        case REQUEST_INFO:
        {
            Connection.Destroy();
            break;
        }
        }

    } 

    private void EnableBT()
    {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    private void Disconnect(boolean resetUI)
    {
        state = 0;
        finishActivity(REQUEST_INFO);
        if(!resetUI)
            mBluetoothAdapter = null;
    }

    private void Connect(View v)
    {
        if(Connection.getInst() != null)
            return;

        EnableConnect(false);

        // Get the device MAC address, which is the last 17 chars in the View
        String info = ((TextView) v).getText().toString();
        String address = info.substring(info.length() - 17);

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device != null)
            Connection.initInstance(connectionHandler, device);
    }

    private void EnableConnect(boolean enable)
    {
        if(!enable)
        {
            dialog= new ProgressDialog(this);
            dialog.setCancelable(true);
            dialog.setMessage(getResources().getString(R.string.connecting));
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);    
            dialog.setMax(0);
            dialog.setProgress(0);
            dialog.setOnCancelListener(new Dialog.OnCancelListener()
            {
                public void onCancel(DialogInterface dia)
                {
                    if(Connection.getInst() != null)
                        Connection.getInst().cancel();
                    Connection.Destroy();
                    EnableConnect(true);
                }
            });
            dialog.show();
        }
        else
        {
            Connection.Destroy();
            dialog.dismiss();
        }
        EnableInteraction(enable);
    }

    private void EnableInteraction(boolean enable)
    {
        Button button = (Button) findViewById(R.id.button_scan);
        button.setEnabled(enable);
        ListView listView = (ListView) findViewById(R.id.paired_devices);
        listView.setEnabled(enable);
    }

    private void ShowAlert(CharSequence text)
    {
        AlertDialog.Builder builder2 = new AlertDialog.Builder(getApplication());
        builder2.setMessage(text)
        .setTitle("Error")
        .setPositiveButton("Dismiss", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder2.create();
        alert.show();
    }

    private void FindDevices()
    {
        if(!mBluetoothAdapter.isEnabled())
        {
            btTurnOn = 1;
            EnableBT();
            return;
        }
        if (mBluetoothAdapter.isDiscovering())
            mBluetoothAdapter.cancelDiscovery();

        mArrayAdapter.clear();
        mPairedDevices.clear();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices(); 
        if (pairedDevices.size() > 0)
        {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices)
                mPairedDevices.add(device.getName() + "\n" + device.getAddress());
        }
        setProgressBarIndeterminateVisibility(true);
        mBluetoothAdapter.startDiscovery();
    } 

    private void initUi()
    {
        setContentView(R.layout.device_list);

        list_part = 1;

        mPairedDevices = new ArrayAdapter<String>(this, R.layout.device_name);
        mArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevices);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices(); 
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
                mPairedDevices.add(device.getName() + "\n" + device.getAddress());
        }

        final Button button = (Button) findViewById(R.id.button_scan);
        button.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter != null)
                    FindDevices();
            }
        });

        GestureOverlayView gestureOverlayView = (GestureOverlayView) findViewById(R.id.gestures_view);
        gestureOverlayView.addOnGesturePerformedListener(new GestureOverlayView.OnGesturePerformedListener()
        {    
            public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture)
            {
                ArrayList<Prediction> predictions = gestureLib.recognize(gesture);
                for (Prediction prediction : predictions)
                {
                    if (prediction.score > 1.0)
                    {
                        ChangeDevicesPart(true, prediction.name.contentEquals("right"));
                        break;
                    }
                }
            }
        });
    }

    private void ChangeDevicesPart(boolean animation, boolean right)
    {
        final ViewFlipper flipper = (ViewFlipper) findViewById(R.id.flipper_devices);
        flipper.setInAnimation(right? inFromLeftAnimation() : inFromRightAnimation());
        flipper.showNext(); 
        final ListView list = (ListView) findViewById(R.id.paired_devices); 
        TextView header = (TextView)findViewById(R.id.title_paired_devices);
        Button button = (Button) findViewById(R.id.button_scan);

        if(list_part == 1)
        {
            list_part = 2;
            button.setVisibility(Button.VISIBLE);
            header.setText(getResources().getString(R.string.new_devices));
            list.setAdapter(mArrayAdapter);
        }
        else
        {
            list_part = 1;
            button.setVisibility(Button.GONE);
            header.setText(getResources().getString(R.string.paired_devices));
            list.setAdapter(mPairedDevices);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action) && mArrayAdapter != null) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    };

    private final BroadcastReceiver mBTStateChangeReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
            {
                int stateBT = intent.getExtras().getInt(BluetoothAdapter.EXTRA_STATE);
                if((state & STATE_CONNECTED) != 0 && stateBT == BluetoothAdapter.STATE_TURNING_OFF)
                    Disconnect(true);
                else if((state & STATE_CONNECTED) == 0 && stateBT == BluetoothAdapter.STATE_ON)
                {
                    mPairedDevices.clear();
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices(); 
                    if (pairedDevices.size() > 0)
                        for (BluetoothDevice device : pairedDevices)
                            mPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                YuniFlyclientActivity.this.setProgressBarIndeterminateVisibility(false);
            }
        }
    };

    private final OnItemClickListener mDeviceClickListener = new OnItemClickListener()
    {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3)
        {
            YuniFlyclientActivity.this.startActivityForResult(
                    new Intent(YuniFlyclientActivity.this, InfoActivity.class), REQUEST_INFO);
            /*
            if(!mBluetoothAdapter.isEnabled())
            {
                btTurnOn = 2;
                connectView = v;
                EnableBT();
                return;
            }
            Connect(v);
            */
        }
    };

    private final Handler connectionHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            switch(msg.what)
            {
            case Connection.CONNECTION_STATE:
            {
                switch(msg.arg1)
                {
                case BluetoothService.STATE_CONNECTED:
                    state |= STATE_CONNECTED;
                    dialog.dismiss();
                    YuniFlyclientActivity.this.startActivityForResult(
                            new Intent(YuniFlyclientActivity.this, InfoActivity.class), REQUEST_INFO);
                    EnableInteraction(true);
                    return;
                case Connection.CONNECTION_FAILED:
                {
                    Toast.makeText(YuniFlyclientActivity.this.getApplication(),
                            getResources().getString(R.string.unable_to_con), 
                            Toast.LENGTH_SHORT).show();
                    EnableConnect(true);
                    return;
                }
                case Connection.CONNECTION_LOST:
                {
                    Toast.makeText(YuniFlyclientActivity.this.getApplication(),
                            getResources().getString(R.string.con_lost),
                            Toast.LENGTH_SHORT).show();
                    Disconnect(true);
                    return;
                }
                }
                return;
            }
            } // switch(msg.what)
        }
    };

    public static int getState()
    {
        return state;
    }

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> mArrayAdapter;
    private ArrayAdapter<String> mPairedDevices;
    private GestureLibrary gestureLib;

    private static int state;
    private byte btTurnOn;
    private byte list_part;
    private View connectView;
    private ProgressDialog dialog;

    private Animation inFromRightAnimation()
    {
        Animation inFromRight = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  +1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
                );
        inFromRight.setDuration(100);
        inFromRight.setInterpolator(new LinearInterpolator());
        return inFromRight;
    }

    private Animation inFromLeftAnimation()
    {
        Animation inFromLeft = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT,  -1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
                Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
                );
        inFromLeft.setDuration(100);
        inFromLeft.setInterpolator(new LinearInterpolator());
        return inFromLeft;
    }
}
