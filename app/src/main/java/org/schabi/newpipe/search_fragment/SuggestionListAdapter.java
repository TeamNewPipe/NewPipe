package org.schabi.newpipe.search_fragment;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.View;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
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

/**
 * {@link ResourceCursorAdapter} to display suggestions.
 */
public class SuggestionListAdapter extends ResourceCursorAdapter {

    private static final String[] columns = new String[]{"_id", "title"};
    private static final int INDEX_ID = 0;
    private static final int INDEX_TITLE = 1;


    public SuggestionListAdapter(Context context) {
        super(context, android.R.layout.simple_list_item_1, null, 0);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder viewHolder = new ViewHolder(view);
        viewHolder.suggestionTitle.setText(cursor.getString(INDEX_TITLE));
    }

    /**
     * Update the suggestion list
     * @param suggestions the list of suggestions
     */
    public void updateAdapter(List<String> suggestions) {
        MatrixCursor cursor = new MatrixCursor(columns, suggestions.size());
        int i = 0;
        for (String suggestion : suggestions) {
            String[] columnValues = new String[columns.length];
            columnValues[INDEX_TITLE] = suggestion;
            columnValues[INDEX_ID] = Integer.toString(i);
            cursor.addRow(columnValues);
            i++;
        }
        changeCursor(cursor);
    }

    /**
     * Get the suggestion for a position
     * @param position the position of the suggestion
     * @return the suggestion
     */
    public String getSuggestion(int position) {
        return ((Cursor) getItem(position)).getString(INDEX_TITLE);
    }

    private class ViewHolder {
        private final TextView suggestionTitle;
        private ViewHolder(View view) {
            this.suggestionTitle = (TextView) view.findViewById(android.R.id.text1);
        }
    }
}