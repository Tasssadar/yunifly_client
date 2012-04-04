package com.tassadar.yunifly;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;


public class InfoActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.info);
        
        m_adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item);
        m_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = (Spinner)findViewById(R.id.cur_board);
        spinner.setAdapter(m_adapter);
        spinner.setOnItemSelectedListener(boardSelected);

        m_protocol = new Protocol(protocolHandler);
        Connection.getInst().setProtocol(m_protocol);

        m_progress = new ProgressDialog(this);
        m_progress.setTitle(R.string.getting_info);
        m_progress.setMessage(getResources().getString(R.string.getting_info_sum));
        m_progress.show();
        
        m_protocol.readDeviceInfo();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        // need to reload layout, because it is different
        int axes = m_axes.size();
        int btns = m_buttons.size();
        int tristate = m_tristate.size();
        int board = m_protocol.getDevice();

        m_axes.clear();
        m_buttons.clear();
        m_tristate.clear();

        setContentView(R.layout.info);

        Spinner spinner = (Spinner)findViewById(R.id.cur_board);
        spinner.setAdapter(m_adapter);
        spinner.setOnItemSelectedListener(boardSelected);
        spinner.setSelection(board);
        
        if(m_protocol.getInfo() != null)
            ((TextView)findViewById(R.id.title_label)).setText(m_protocol.getInfo().deviceName);

        setAxisCount(axes);
        setButtonCount(btns);
        setTristateCount(tristate);
    }

    public void setAxisCount(int count)
    {
        if(m_axes.size() == count)
            return;

        int ax_size = m_axes.size();
        LinearLayout axis_layout = (LinearLayout)findViewById(R.id.bar_layout);

        while(ax_size != count)
        {
            if(ax_size < count)
            {
                RelativeLayout l = (RelativeLayout)getLayoutInflater()
                        .inflate(R.layout.axis_bar, axis_layout, false);

                ((TextView)l.findViewById(R.id.bar_text)).
                setText(getResources().getString(R.string.pot) + " " + ax_size);

                axis_layout.addView(l);
                m_axes.add(l);
                ++ax_size;
            }
            else
            {
                --ax_size;
                axis_layout.removeView(m_axes.remove(ax_size));
            }
        }
    }

    public void setButtonCount(int count)
    {
        if(m_buttons.size() == count)
            return;

        int size = m_buttons.size();
        LinearLayout btn_layout = (LinearLayout)findViewById(R.id.button_layout);

        while(size != count)
        {
            if(size < count)
            {
                RelativeLayout l = (RelativeLayout)getLayoutInflater()
                        .inflate(R.layout.button_item, btn_layout, false);

                ((TextView)l.findViewById(R.id.button_text)).setText(String.valueOf(size));

                btn_layout.addView(l);
                m_buttons.add(l);
                ++size;
            }
            else
            {
                --size;
                btn_layout.removeView(m_buttons.remove(size));
            }
        }
    }
    
    public void setTristateCount(int count)
    {
        if(m_tristate.size() == count)
            return;

        int size = m_tristate.size();
        LinearLayout btn_layout = (LinearLayout)findViewById(R.id.tristate_layout);

        while(size != count)
        {
            if(size < count)
            {
                RelativeLayout l = (RelativeLayout)getLayoutInflater()
                        .inflate(R.layout.tristate_item, btn_layout, false);

                ((TextView)l.findViewById(R.id.tristate_text)).setText(String.valueOf(size));

                btn_layout.addView(l);
                m_tristate.add(l);
                ++size;
            }
            else
            {
                --size;
                btn_layout.removeView(m_tristate.remove(size));
            }
        }
    }
    
    private void setItemsForDevice(short device, GlobalInfo info)
    {
        setAxisCount(info.boardInfos[device].potCount);
        setButtonCount(info.boardInfos[device].btnCount);
        setTristateCount(info.boardInfos[device].triStateCount);
    }
    
    private final AdapterView.OnItemSelectedListener boardSelected = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int pos,
                long arg3) {
            
            m_protocol.stopGetter();
            
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) { }
            
            setItemsForDevice((short)pos, m_protocol.getInfo());
            m_protocol.setDevice((short)pos);
            
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            
        }
    };
    
    private final Handler protocolHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            switch(msg.what)
            {
                case Protocol.DATA_GLOBAL_INFO:
                {
                    m_progress.dismiss();

                    if(msg.obj == null)
                    {
                        YuniFlyclientActivity.ShowAlert(R.string.timeout, InfoActivity.this);
                        break;
                    }

                    GlobalInfo info = (GlobalInfo)msg.obj;
                    ((TextView)findViewById(R.id.title_label)).setText(info.deviceName);
                    
                    for(short i = 0; i < info.boardCount; ++i)
                        m_adapter.add(info.boardInfos[i].name);
                    break;
                }
                case Protocol.DATA_POT_DATA:
                {
                    if(m_protocol.getInfo() == null)
                        return;
                    
                    short[] vals = (short[])msg.obj;
                    
                    if(vals.length != m_axes.size())
                        return;
                    
                    for(int i = 0; i < vals.length; ++i)
                    {
                        RelativeLayout l = m_axes.get(i);
                        ProgressBar bar = (ProgressBar)l.findViewById(R.id.progress_bar);
                        bar.setProgress((int)vals[i] + 32768);
                    }
                    break;
                }
                case Protocol.DATA_BTN_DATA:
                {
                    if(m_protocol.getInfo() == null)
                        return;
                    
                    boolean[] vals = (boolean[])msg.obj;
                    
                    if(vals.length != m_buttons.size())
                        return;
                    
                    for(int i = 0; i < vals.length; ++i)
                    {
                        RelativeLayout l = m_buttons.get(i);
                        RadioButton btn = (RadioButton)l.findViewById(R.id.button_radio);
                        btn.setChecked(vals[i]);
                    }
                    break;
                }
                case Protocol.DATA_TRISTATE_DATA:
                {
                    if(m_protocol.getInfo() == null)
                        return;

                    short[] vals = (short[])msg.obj;
                    
                    if(vals.length != m_tristate.size())
                        return;
                    
                    for(int i = 0; i < vals.length; ++i)
                    {
                        RelativeLayout l = m_tristate.get(i);
                        Button btn = (Button)l.findViewById(R.id.tristate_button);
                        btn.setText(String.valueOf(vals[i]));
                    }
                    break;
                }
                case Protocol.DATA_VOLTAGE:
                {
                    float val = ((float)(msg.arg1/100))/10;
                    TextView voltage = (TextView)findViewById(R.id.cur_voltage);
                    voltage.setText(String.valueOf(val) + " V");
                    break;
                }
            }
        }
    };

    private List<RelativeLayout> m_axes = new ArrayList<RelativeLayout>();
    private List<RelativeLayout> m_buttons = new ArrayList<RelativeLayout>();
    private List<RelativeLayout> m_tristate = new ArrayList<RelativeLayout>();
    
    private Protocol m_protocol;
    private ProgressDialog m_progress;
    private ArrayAdapter<CharSequence> m_adapter;
};
