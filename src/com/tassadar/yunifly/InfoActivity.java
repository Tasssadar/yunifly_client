package com.tassadar.yunifly;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class InfoActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.info);

        // test vals
        setAxisCount(4);
        setButtonCount(10);
        setTristateCount(4);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        // need to reload layout, because it is different
        int axes = m_axes.size();
        int btns = m_buttons.size();
        int tristate = m_tristate.size();

        m_axes.clear();
        m_buttons.clear();
        m_tristate.clear();

        setContentView(R.layout.info);

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

    private List<RelativeLayout> m_axes = new ArrayList<RelativeLayout>();
    private List<RelativeLayout> m_buttons = new ArrayList<RelativeLayout>();
    private List<RelativeLayout> m_tristate = new ArrayList<RelativeLayout>();
};
