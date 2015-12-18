package org.schabi.newpipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by shekhar on 10/12/15.
 *
 * SuggestionItemViewCreator.java is part of NewPipe.
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

public class SuggestionItemViewCreator {
    private final LayoutInflater inflater;

    public SuggestionItemViewCreator(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public View getViewByVideoInfoItem(View convertView, ViewGroup parent, String suggestion) {
        ViewHolder holder;
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.suggestion_item, parent, false);
            holder = new ViewHolder();
            holder.suggestionTitle = (TextView) convertView.findViewById(R.id.suggestionTitle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.suggestionTitle.setText(suggestion);

        return convertView;
    }

    private class ViewHolder {
        public TextView suggestionTitle;
    }

}

