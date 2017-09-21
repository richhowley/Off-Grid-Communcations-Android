package com.example.android.offgrid;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

/**
 *  Custom array adapter for message list
 */

public class CoversationAdaptor<String> extends ArrayAdapter<String> {

    public CoversationAdaptor(Context context,  int textViewResourceId) {
        super (context,  textViewResourceId);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        // different background color on alternae rows
        if (position % 2 == 1) {
            view.setBackgroundColor(Color.WHITE);
        } else {
            view.setBackgroundColor(Color.CYAN);
            view.getBackground().setAlpha(25);
        }

        return view;
    }

}
