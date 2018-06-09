package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContentSettingsDialog extends DialogFragment {

    List<String> usedTabs = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_contentsettings, container);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        RecyclerView allTabs = rootView.findViewById(R.id.tabs);
        allTabs.setLayoutManager(new LinearLayoutManager(getContext()));
        allTabs.setAdapter(new allAdapter());

        RecyclerView usedTabs = rootView.findViewById(R.id.usedTabs);
        usedTabs.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public class allAdapter extends RecyclerView.Adapter<allAdapter.TabViewHolder>{

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


        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return 5;
        }

        class TabViewHolder extends RecyclerView.ViewHolder {

            TextView text;
            Button add;

            public TabViewHolder(View itemView) {
                super(itemView);

                text = itemView.findViewById(R.id.tabName);
                add = itemView.findViewById(R.id.buttonAddRemove);
            }

            void bind(int position) {
                add.setBackgroundResource(R.drawable.ic_add);
                switch (position) {
                    case 0:
                        text.setText("Test");
                    break;
                    case 1:
                    break;
                    case 2:
                    break;
                    case 3:
                    break;
                    case 4:
                    break;
                    case 5:
                    break;
                    case 6:
                    break;
                }
            }
        }
    }
}
