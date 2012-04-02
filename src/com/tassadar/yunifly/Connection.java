package com.tassadar.yunifly;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;

public class Connection
{
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3; 
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5; 
    public static final int CONNECTION_FAILED = 1;
    public static final int CONNECTION_LOST = 2;
    
    public static final byte CONNECTION_STATE = 1;
    public static final byte CONNECTION_DATA  = 2;
    
    private Connection(Handler handler, BluetoothDevice device)
    {
        mHandler = handler;
        mChatService = new BluetoothService(mBThandler);
        mChatService.start();
        mChatService.connect(device);
        m_protocol = null;
    }
    
    public static Connection initInstance(Handler handler, BluetoothDevice device)
    {    
        instance = new Connection(handler, device);
        return instance;
    }
    
    public static Connection getInst()
    {
        return instance;
    }
    
    public static void Destroy()
    {
        if(instance != null)
            instance.cancel();
        instance = null;
    }
    
    public void cancel()
    {
    	if(m_protocol != null)
    		m_protocol.stopGetter();
        mChatService.stop();
    }
    
    public void setProtocol(Protocol protocol)
    {
    	m_protocol = protocol;
    }
    
    public void write(byte[] data)
    {
        mChatService.write(data);
    }
    
    private final Handler mBThandler = new Handler() {
        @Override
        public void handleMessage(Message msg)
        {
            int state = YuniFlyclientActivity.getState();
            if((state & YuniFlyclientActivity.STATE_CONNECTED) == 0 && msg.what != MESSAGE_STATE_CHANGE && msg.what != MESSAGE_TOAST)
                return;
                
            switch (msg.what)
            {
                case MESSAGE_STATE_CHANGE:
                    if(msg.arg1 == BluetoothService.STATE_CONNECTED)
                    {
                        mHandler.obtainMessage(CONNECTION_STATE,
                                BluetoothService.STATE_CONNECTED, -1).sendToTarget();
                    }
                    break;
                case MESSAGE_TOAST:
                    if(msg.arg1 == CONNECTION_LOST)
                        mHandler.obtainMessage(CONNECTION_STATE, CONNECTION_LOST, -1).sendToTarget();
                    else if(msg.arg1 == CONNECTION_FAILED)
                        mHandler.obtainMessage(CONNECTION_STATE, CONNECTION_FAILED, -1).sendToTarget();
                    break;
                case MESSAGE_READ:
                {
                     if(msg.obj == null || m_protocol == null)
                         break;
                     
                     m_protocol.dataRead((byte[])msg.obj);
                     break;
                }
            }
        }
    };
    
    private static Connection instance;
    private Handler mHandler;
    private BluetoothService mChatService;
    private Protocol m_protocol;
};
