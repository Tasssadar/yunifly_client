package com.tassadar.yunifly;

public class Packet
{
    public Packet()
    {
        clear();
    }
    
    public Packet(short device, short opcode, short[] data)
    {
        m_device = device;
        m_opcode = opcode;
        m_data = data.clone();
        m_read_itr = 0;
    }
    
    public void clear()
    {
        m_itr = 0;
        m_opcode = 0;
        m_device = 0;
        m_data = null;
        m_read_itr = 0;
    }
    
    public short readByte()
    {
        if(m_read_itr >= m_data.length)
            return 0;

        return (short)((int)m_data[m_read_itr++] & 0xFF);
    }

    public int readUInt16()
    {
        if(m_read_itr+1 >= m_data.length)
            return 0;
        
        int firstByte = (0xFF & ((int)m_data[m_read_itr++]));
        int secondByte = (0xFF & ((int)m_data[m_read_itr++]));

        return  ((firstByte << 8) | secondByte);
    }
    
    public short readInt16()
    {
        return (short)readUInt16();
    }
    
    public String readString()
    {
        StringBuffer sb = new StringBuffer();
        for(;m_read_itr < m_data.length; ++m_read_itr)
        {
            if(m_data[m_read_itr] == 0)
            {
                ++m_read_itr;
                break;
            }
            sb.append((char)m_data[m_read_itr]);
        }
        return sb.toString();
    }
    
    public byte[] getSendData()
    {
        if(m_data == null)
            return null;
        
        byte[] data = new byte[4+m_data.length];
        data[0] = (byte)0xFF;
        data[1] = (byte)m_device;
        data[2] = (byte)(m_data.length+1);
        data[3] = (byte)m_opcode;
        
        for(short i = 0; i < m_data.length; ++i)
            data[i+4] = (byte)m_data[i];
        
        return data;
    }
    
    public int addData(byte[] data, int offset)
    {
        int len = data.length - offset;
        int read = 0;
        
        while(read < len)
        {
            short val = (short)(0xFF & ((int)m_data[offset+read]));
            switch(m_itr)
            {
                case 0:
                {
                    if(val == 0xFF)
                    {
                        ++m_itr;
                        ++read;
                    }
                    else
                        return read;
                    break;
                }
                case 1:
                {
                    m_device = val;
                    ++m_itr;
                    ++read;
                    break;
                }
                case 2:
                {
                    m_data = new short[val-1];
                    ++m_itr;
                    ++read;
                    break;
                }
                case 3:
                {
                    m_opcode = val;
                    ++m_itr;
                    ++read;
                    break;
                }
                default:
                {
                    if(m_itr-4 >= m_data.length)
                        return read;
                    m_data[m_itr-4] = val;
                    ++m_itr;
                    ++read;
                    break;
                }
            }
        }
            
        return read;
    }
    
    public boolean isValid()
    {
        return (m_itr == 4+m_data.length);
    }
    
    public short getOpcode()
    {
        return m_opcode;
    }

    public int getLen()
    {
        return m_data.length;
    }
    
    private short m_read_itr;
    private short m_itr;
    
    private short m_opcode;
    private short m_device;
    private short[] m_data;
}
