package org.schabi.newpipe;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Madiyar on 23.02.2016.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * SuggestionListAdapter.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SuggestionListAdapter extends CursorAdapter {

    private String[] columns = new String[]{"_id", "title"};

    public SuggestionListAdapter(Context context) {
        super(context, null, false);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        ViewHolder viewHolder;

        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        viewHolder = new ViewHolder();
        viewHolder.suggestionTitle = (TextView) view.findViewById(android.R.id.text1);
        view.setTag(viewHolder);


        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();
        viewHolder.suggestionTitle.setText(cursor.getString(1));
    }


    public void updateAdapter(ArrayList<String> suggestions) {
        MatrixCursor cursor = new MatrixCursor(columns);
        int i = 0;
        for (String s : suggestions) {
            String[] temp = new String[2];
            temp[0] = Integer.toString(i);
            temp[1] = s;
            i++;
            cursor.addRow(temp);
        }
        changeCursor(cursor);
    }

    public String getSuggestion(int position) {
        return ((Cursor) getItem(position)).getString(1);
    }

    private class ViewHolder {
        public TextView suggestionTitle;
    }
}
