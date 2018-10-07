package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 09.10.17.
 * SelectKioskFragment.java is part of NewPipe.
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

public class SelectKioskFragment extends DialogFragment {

    private static final boolean DEBUG = MainActivity.DEBUG;

    RecyclerView recyclerView = null;
    SelectKioskAdapter selectKioskAdapter = null;

     /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedLisener {
        void onKioskSelected(int serviceId, String kioskId, String kioskName);
    }

    OnSelectedLisener onSelectedLisener = null;
    public void setOnSelectedLisener(OnSelectedLisener listener) {
        onSelectedLisener = listener;
    }

    public interface OnCancelListener {
        void onCancel();
    }
    OnCancelListener onCancelListener = null;
    public void setOnCancelListener(OnCancelListener listener) {
        onCancelListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.select_kiosk_fragment, container, false);
        recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        try {
            selectKioskAdapter = new SelectKioskAdapter();
        } catch (Exception e) {
            onError(e);
        }
        recyclerView.setAdapter(selectKioskAdapter);

        return v;
    }

   /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCancel(final DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        if(onCancelListener != null) {
            onCancelListener.onCancel();
        }
    }

    private void clickedItem(SelectKioskAdapter.Entry entry) {
        if(onSelectedLisener != null) {
            onSelectedLisener.onKioskSelected(entry.serviceId, entry.kioskId, entry.kioskName);
        }
        dismiss();
    }

    private class SelectKioskAdapter
            extends RecyclerView.Adapter<SelectKioskAdapter.SelectKioskItemHolder> {
        public class Entry {
            public Entry (int i, int si, String ki, String kn){
                icon = i; serviceId=si; kioskId=ki; kioskName = kn;
            }
            final int icon;
            final int serviceId;
            final String kioskId;
            final String kioskName;
        }

        private final List<Entry> kioskList = new Vector<>();

        public SelectKioskAdapter()
                throws Exception {

            for(StreamingService service : NewPipe.getServices()) {
                //TODO: Multi-service support
                if (service.getServiceId() != ServiceList.YouTube.getServiceId() && !DEBUG) continue;

                for(String kioskId : service.getKioskList().getAvailableKiosks()) {
                    String name = String.format(getString(R.string.service_kiosk_string),
                            service.getServiceInfo().getName(),
                            KioskTranslator.getTranslatedKioskName(kioskId, getContext()));
                    kioskList.add(new Entry(
                            ServiceHelper.getIcon(service.getServiceId()),
                            service.getServiceId(),
                            kioskId,
                            name));
                }
            }
        }

        public int getItemCount() {
            return kioskList.size();
        }

        public SelectKioskItemHolder onCreateViewHolder(ViewGroup parent, int type) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_kiosk_item, parent, false);
            return new SelectKioskItemHolder(item);
        }

        public class SelectKioskItemHolder extends RecyclerView.ViewHolder {
            public SelectKioskItemHolder(View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
            public final View view;
            public final ImageView thumbnailView;
            public final TextView titleView;
        }

        public void onBindViewHolder(SelectKioskItemHolder holder, final int position) {
            final Entry entry = kioskList.get(position);
            holder.titleView.setText(entry.kioskName);
            holder.thumbnailView.setImageDrawable(ContextCompat.getDrawable(getContext(), entry.icon));
            holder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickedItem(entry);
                }
            });
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e,
                activity.getClass(),
                null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash));
    }
}
