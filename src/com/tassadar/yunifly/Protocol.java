package com.tassadar.yunifly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.Handler;
import android.os.Message;

public class Protocol
{
    private static int TIMEOUT = 1000/50;
    
    private static short DEVICE = 0x00;
    
    private static short SMSG_GET_DEVICE_INFO = 0xFF;
    private static short CMSG_DEVICE_INFO     = 0xFE;
    
    private static short SMSG_GET_DATA        = 0xFD;
    
    private static short CMSG_POT             = 0xFC;    
    private static short CMSG_BUTTONS         = 0xFB;
    private static short CMSG_TRISTATE        = 0xFA;
    private static short CMSG_BOARD_VOLTAGE   = 0x00;
    
    public Protocol()
    {
        m_cur_packet = new Packet();
        m_wait_opcode = -1;
        m_wait_packet = null;
        m_getter = null;
        m_cur_device = DEVICE;
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
    
    public void readDeviceInfo()
    {
        new Thread(new Runnable() {
            public void run() {

                Packet pkt = new Packet(DEVICE, SMSG_GET_DEVICE_INFO, new short[0]);
                pkt = waitForPacket(pkt, CMSG_DEVICE_INFO);
                
                

                m_getter = new GetterThread();
                m_getter.start();
            }
        }).start();
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
            
        }
    }

    private final Handler dataRead = new Handler()
    {
        public void handleMessage(Message msg)
        {
            List<Packet> received = new ArrayList<Packet>();
            
            byte[] data = (byte[])msg.obj;
            
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
    };
    
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
            Packet pkt = new Packet(m_cur_device, SMSG_GET_DATA, new short[0]);
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
    private short m_cur_device;
    private Packet m_cur_packet;
    private short m_wait_opcode;
    private Packet m_wait_packet;
};