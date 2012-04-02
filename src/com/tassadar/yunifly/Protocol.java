package com.tassadar.yunifly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.Handler;

public class Protocol
{
    private static final int TIMEOUT = 3000/50;
    
    private static final short DEVICE = 0x00;
    
    private static final short SMSG_GET_DEVICE_INFO = 0xFF;
    private static final short CMSG_DEVICE_INFO     = 0xFE;
    private static final short SMSG_GET_DATA        = 0xFD;
    private static final short CMSG_POT             = 0xFC;    
    private static final short CMSG_BUTTONS         = 0xFB;
    private static final short CMSG_TRISTATE        = 0xFA;
    private static final short CMSG_BOARD_VOLTAGE   = 0x00;
    
    public static final short DATA_GLOBAL_INFO     = 0;
    public static final short DATA_POT_DATA        = 1;
    public static final short DATA_BTN_DATA        = 2;
    public static final short DATA_TRISTATE_DATA   = 3;
    public static final short DATA_VOLTAGE         = 4;
    
    public Protocol(Handler protocolHandler)
    {
        m_cur_packet = new Packet();
        m_wait_opcode = -1;
        m_wait_packet = null;
        m_getter = null;
        m_cur_device = DEVICE;

        m_dataHandler = protocolHandler;
    }
    
    public void stopGetter()
    {
        if(m_getter == null)
            return;
        m_getter.stopThread();
        try {
            m_getter.join();
        } catch (InterruptedException e) { }
    }
    
    public short getDevice()
    {
        return m_cur_device;
    }
    
    public GlobalInfo getInfo()
    {
        return m_info;
    }
    
    public void setDevice(short dev)
    {
        m_cur_device = dev;

        m_getter.stopThread();
        try {
            m_getter.join();
        } catch (InterruptedException e) { }
        
        m_getter = new GetterThread();
        m_getter.start();
    }
    
    public void readDeviceInfo()
    {
        new Thread(new Runnable() {
            public void run() {

                Packet pkt = new Packet(DEVICE, SMSG_GET_DEVICE_INFO, new short[0]);
                pkt = waitForPacket(pkt, CMSG_DEVICE_INFO);
                
                if(pkt == null)
                {
                    m_dataHandler.obtainMessage(DATA_GLOBAL_INFO).sendToTarget();
                    return;
                }
                
                m_info = new GlobalInfo();
                
                m_info.deviceName = pkt.readString();
                m_info.boardCount = pkt.readByte();
                m_info.boardInfos = new BoardInfo[m_info.boardCount];
                for(short i = 0; i < m_info.boardCount; ++i)
                {
                    m_info.boardInfos[i].name = pkt.readString();
                    m_info.boardInfos[i].potCount = pkt.readByte();
                    m_info.boardInfos[i].btnCount = pkt.readByte();
                    m_info.boardInfos[i].triStateCount = pkt.readByte();
                }
                
                m_dataHandler.obtainMessage(DATA_GLOBAL_INFO, m_info).sendToTarget();

                m_getter = new GetterThread();
                m_getter.start();
            }
        }).start();
    }
    
    public void dataRead(byte[] data)
    {
        List<Packet> received = new ArrayList<Packet>();

        int curRead = 1;
        int read = 0;
        int len = data.length;
        
        while(read < len)
        {
            if(curRead == 0)
            {
                m_cur_packet.clear();
                
                for(; read < len; ++read)
                    if(data[read] == (byte)0xFF)
                        break;
                
                if(read == len)
                    break;
            }
            
            curRead = m_cur_packet.addData(data, read);
            read += curRead;
            
            if(m_cur_packet.isValid())
            {
                received.add(m_cur_packet);
                m_cur_packet = new Packet();
            }
        }
        
        for(Iterator<Packet> itr = received.iterator(); itr.hasNext();)
            handlePacket(itr.next());
    }
    
    private Packet waitForPacket(Packet toSend, short opcode)
    {
        synchronized(this) {
            assert(m_wait_opcode == -1);
            m_wait_opcode = opcode;
        }
        
        Connection.getInst().write(toSend.getSendData());
        m_wait_packet = null;
        
        for(int timeout = 0; timeout < TIMEOUT; ++timeout)
        {
            synchronized(this) {
                if(m_wait_packet != null)
                    break;
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        }
        
        return m_wait_packet;
    }
    
    private void handlePacket(Packet pkt)
    {
        synchronized(this)
        {
            if(m_wait_opcode != -1 && pkt.getOpcode() == m_wait_opcode)
            {
                m_wait_opcode = -1;
                m_wait_packet = pkt;
                return;
            }
        }
        
        switch(pkt.getOpcode())
        {
            case CMSG_POT:
            {
                short[] vals = new short[m_info.boardInfos[m_cur_device].potCount];
                for(int i = 0; i < vals.length; ++i)
                    vals[i] = pkt.readInt16();

                m_dataHandler.obtainMessage(DATA_POT_DATA, vals).sendToTarget();
                break;
            }
            case CMSG_BUTTONS:
            {
                boolean[] vals = new boolean[m_info.boardInfos[m_cur_device].btnCount];
                int vals_itr = 0;

                for(short i = 0; i < pkt.getLen(); ++i)
                {
                    short val = pkt.readByte();

                    for(;vals_itr > vals.length && vals_itr < (i+1)*8; ++vals_itr)
                        vals[vals_itr] = (val & (1 << vals_itr%8)) != 0;
                }
                m_dataHandler.obtainMessage(DATA_BTN_DATA, vals).sendToTarget();
                break;
            }
            case CMSG_TRISTATE:
            {
                short[] vals = new short[m_info.boardInfos[m_cur_device].triStateCount];
                for(int i = 0; i < vals.length; ++i)
                    vals[i] = pkt.readByte();
                m_dataHandler.obtainMessage(DATA_TRISTATE_DATA, vals).sendToTarget();
                break;
            }
            case CMSG_BOARD_VOLTAGE:
            {
                int val = pkt.readUInt16();
                m_dataHandler.obtainMessage(DATA_VOLTAGE, val, 0).sendToTarget();
                break;
            }
        }
    }

    private class GetterThread extends Thread
    {
        public GetterThread()
        {
            m_run = true;
        }
        
        public void stopThread()
        {
            m_run = false;
        }
        
        public void run()
        {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { }
            
            short[] reqData = { (0x01 | 0x02 | 0x04 | 0x08 ) }; 
            Packet pkt = new Packet(m_cur_device, SMSG_GET_DATA, reqData);
            byte[] data = pkt.getSendData();
            while(m_run)
            {
                Connection.getInst().write(data);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) { }
            }
        }
        
        volatile boolean m_run;
    }
    
    private GetterThread m_getter;
    private volatile short m_cur_device;
    private Packet m_cur_packet;
    private short m_wait_opcode;
    private Packet m_wait_packet;
    private Handler m_dataHandler;
    private GlobalInfo m_info;
};
