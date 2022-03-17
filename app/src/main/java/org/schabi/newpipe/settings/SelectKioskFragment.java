package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 09.10.17.
 * SelectKioskFragment.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class SelectKioskFragment extends DialogFragment {
    private SelectKioskAdapter selectKioskAdapter = null;

    private OnSelectedListener onSelectedListener = null;

    public void setOnSelectedListener(final OnSelectedListener listener) {
        onSelectedListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()));
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.select_kiosk_fragment, container, false);
        final RecyclerView recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        try {
            selectKioskAdapter = new SelectKioskAdapter();
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Selecting kiosk", e);
        }
        recyclerView.setAdapter(selectKioskAdapter);

        return v;
    }

   /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    private void clickedItem(final SelectKioskAdapter.Entry entry) {
        if (onSelectedListener != null) {
            onSelectedListener.onKioskSelected(entry.serviceId, entry.kioskId, entry.kioskName);
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedListener {
        void onKioskSelected(int serviceId, String kioskId, String kioskName);
    }

    private class SelectKioskAdapter
            extends RecyclerView.Adapter<SelectKioskAdapter.SelectKioskItemHolder> {
        private final List<Entry> kioskList = new Vector<>();

        SelectKioskAdapter() throws Exception {
            for (final StreamingService service : NewPipe.getServices()) {
                for (final String kioskId : service.getKioskList().getAvailableKiosks()) {
                    final String name = String.format(getString(R.string.service_kiosk_string),
                            service.getServiceInfo().getName(),
                            KioskTranslator.getTranslatedKioskName(kioskId, getContext()));
                    kioskList.add(new Entry(ServiceHelper.getIcon(service.getServiceId()),
                            service.getServiceId(), kioskId, name));
                }
            }
        }

        public int getItemCount() {
            return kioskList.size();
        }

        @NonNull
        public SelectKioskItemHolder onCreateViewHolder(final ViewGroup parent, final int type) {
            final View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_kiosk_item, parent, false);
            return new SelectKioskItemHolder(item);
        }

        public void onBindViewHolder(final SelectKioskItemHolder holder, final int position) {
            final Entry entry = kioskList.get(position);
            holder.titleView.setText(entry.kioskName);
            holder.thumbnailView
                    .setImageDrawable(AppCompatResources.getDrawable(requireContext(), entry.icon));
            holder.view.setOnClickListener(view -> clickedItem(entry));
        }

        class Entry {
            final int icon;
            final int serviceId;
            final String kioskId;
            final String kioskName;

            Entry(final int i, final int si, final String ki, final String kn) {
                icon = i;
                serviceId = si;
                kioskId = ki;
                kioskName = kn;
            }
        }

        public class SelectKioskItemHolder extends RecyclerView.ViewHolder {
            public final View view;
            final ImageView thumbnailView;
            final TextView titleView;

            SelectKioskItemHolder(final View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
        }
    }
}
