package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.local.subscription.SubscriptionService;

import java.util.List;
import java.util.Vector;

import de.hdodenhof.circleimageview.CircleImageView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by Christian Schabesberger on 26.09.17.
 * SelectChannelFragment.java is part of NewPipe.
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

public class SelectChannelFragment extends DialogFragment {
    private final ImageLoader imageLoader = ImageLoader.getInstance();

    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    private List<SubscriptionEntity> subscriptions = new Vector<>();

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedLisener {
        void onChannelSelected(int serviceId, String url, String name);
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

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.select_channel_fragment, container, false);
        recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SelectChannelAdapter channelAdapter = new SelectChannelAdapter();
        recyclerView.setAdapter(channelAdapter);

        progressBar = v.findViewById(R.id.progressBar);
        emptyView = v.findViewById(R.id.empty_state_view);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);


        SubscriptionService subscriptionService = SubscriptionService.getInstance(getContext());
        subscriptionService.getSubscription().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver());

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

    private void clickedItem(int position) {
        if(onSelectedLisener != null) {
            SubscriptionEntity entry = subscriptions.get(position);
            onSelectedLisener.onChannelSelected(entry.getServiceId(), entry.getUrl(), entry.getName());
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    //////////////////////////////////////////////////////////////////////////*/

    private void displayChannels(List<SubscriptionEntity> subscriptions) {
        this.subscriptions = subscriptions;
        progressBar.setVisibility(View.GONE);
        if(subscriptions.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.VISIBLE);

    }

    private Observer<List<SubscriptionEntity>> getSubscriptionObserver() {
        return new Observer<List<SubscriptionEntity>>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(List<SubscriptionEntity> subscriptions) {
                displayChannels(subscriptions);
            }

            @Override
            public void onError(Throwable exception) {
                SelectChannelFragment.this.onError(exception);
            }

            @Override
            public void onComplete() {
            }
        };
    }

    private class SelectChannelAdapter extends
            RecyclerView.Adapter<SelectChannelAdapter.SelectChannelItemHolder> {

        @Override
        public SelectChannelItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_channel_item, parent, false);
            return new SelectChannelItemHolder(item);
        }

        @Override
        public void onBindViewHolder(SelectChannelItemHolder holder, final int position) {
            SubscriptionEntity entry = subscriptions.get(position);
            holder.titleView.setText(entry.getName());
            holder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickedItem(position);
                }
            });
            imageLoader.displayImage(entry.getAvatarUrl(), holder.thumbnailView, DISPLAY_IMAGE_OPTIONS);
        }

        @Override
        public int getItemCount() {
            return subscriptions.size();
        }

        public class SelectChannelItemHolder extends RecyclerView.ViewHolder {
            public SelectChannelItemHolder(View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
            public final View view;
            public final CircleImageView thumbnailView;
            public final TextView titleView;
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


    /*//////////////////////////////////////////////////////////////////////////
    // ImageLoaderOptions
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Base display options
     */
    public static final DisplayImageOptions DISPLAY_IMAGE_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .build();
}
