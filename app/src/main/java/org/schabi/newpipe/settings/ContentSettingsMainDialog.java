package org.schabi.newpipe.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContentSettingsMainDialog extends DialogFragment {


    public AllAdapter allAdapter;

    public String[] allTabs = new String[7];

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_contentsettingsadd, container);
    }


    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        tabNames();

        RecyclerView allTabs = rootView.findViewById(R.id.allTabs);
        allTabs.setLayoutManager(new LinearLayoutManager(getContext()));
        allAdapter = new AllAdapter();
        allTabs.setAdapter(allAdapter);
    }

    private void tabNames() {
        allTabs[0] = getString(R.string.blank_page_summary);
        allTabs[1] = getString(R.string.kiosk_page_summary);
        allTabs[2] = getString(R.string.subscription_page_summary);
        allTabs[3] = getString(R.string.feed_page_summary);
        allTabs[4] = getString(R.string.tab_bookmarks);
        allTabs[5] = getString(R.string.title_activity_history);
        allTabs[6] = getString(R.string.channel_page_summary);
    }

    public interface OnAddListener {
        void onAddListener(int position);
    }
    ContentSettingsMainDialog.OnAddListener onAddListener = null;
    public void setOnAddListener(ContentSettingsMainDialog.OnAddListener listener) {
        onAddListener = listener;
    }

    private void addTab(int position) {
        if(onAddListener != null) {
        onAddListener.onAddListener(position);
    }
        dismiss();
    }

    public class AllAdapter extends RecyclerView.Adapter<AllAdapter.TabViewHolder>{

        @Override
        public TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            LayoutInflater inflater = LayoutInflater.from(getContext());
            View view = inflater.inflate(R.layout.dialog_contentsettingtab, parent, false);
            return new TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return allTabs.length;
        }

        class TabViewHolder extends RecyclerView.ViewHolder {

            TextView text;
            View view;

            public TabViewHolder(View itemView) {
                super(itemView);

                text = itemView.findViewById(R.id.tabName);
                view = itemView.findViewById(R.id.layoutCard);
            }

            void bind(int position) {
                text.setText(allTabs[position]);
                ((CardView) view).setCardElevation(0);
                view.setOnClickListener(v -> {
                    addTab(position);
                });

            }
        }
    }
}
