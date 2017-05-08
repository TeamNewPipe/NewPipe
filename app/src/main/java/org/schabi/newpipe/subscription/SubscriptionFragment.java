package org.schabi.newpipe.subscription;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;


public class SubscriptionFragment extends Fragment {

    public SubscriptionFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("xyko", "xyko");
        View v = inflater.inflate(R.layout.subscription_list, container);
        Context context = v.getContext();
        RecyclerView recyclerView = (RecyclerView) v.findViewById(R.id.subscription_list);
        SubscriptionAdapter adapter = new SubscriptionAdapter(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        return v;
    }
}
