package org.schabi.newpipe;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;

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
public class SuggestionListAdapter extends BaseAdapter {

    private final Context context;
    private final SuggestionItemViewCreator viewCreator;
    private ArrayList<String> suggestionList = new ArrayList<>();
    private final ListView listView;

    public SuggestionListAdapter(Context context, VideoItemListFragment suggestionListFragment) {
        viewCreator = new SuggestionItemViewCreator(LayoutInflater.from(context));
        this.listView = suggestionListFragment.getListView();
        this.listView.setDivider(null);
        this.listView.setDividerHeight(0);
        this.context = context;
    }

    public void addSuggestionList(ArrayList<String> suggestionList) {
        this.suggestionList.addAll(suggestionList);
        notifyDataSetChanged();
    }

    public void clearSuggestionList() {
        suggestionList = new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return suggestionList.size();
    }

    @Override
    public Object getItem(int position) {
        return suggestionList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = viewCreator.getViewByVideoInfoItem(convertView, parent, suggestionList.get(position));

        if(listView.isItemChecked(position)) {
            convertView.setBackgroundColor(ContextCompat.getColor(context, R.color.primaryColorYoutube));
        } else {
            convertView.setBackgroundColor(0);
        }

        return convertView;
    }

    public ArrayList<String> getData(){
        return suggestionList;
    }
}
