package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.subscription.SubscriptionService;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {

    private static final int SUBSCRIPTION_DEBOUNCE_INTERVAL = 500;
    public final CircleImageView itemThumbnailView;
    public final TextView itemTitleView;
    public final TextView itemAdditionalDetailView;
    public final Button   itemBookmarkBtn;
    private  Flowable<List<SubscriptionEntity>> subscription;
    SubscriptionDAO subscriptionDAO;
    List<SubscriptionEntity> subscriptionEntity;
    AppDatabase db;
    InfoItemBuilder infoItemBuilder;


    ChannelMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemBookmarkBtn = itemView.findViewById(R.id.bookmarkItems);

        db = NewPipeDatabase.getInstance(itemView.getContext());
        subscriptionDAO = db.subscriptionDAO();
    }

    public ChannelMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof ChannelInfoItem)) return;
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;


        itemTitleView.setText(item.getName());
        itemAdditionalDetailView.setText(getDetailLine(item));

        //This code will set bookmark image to respected position of subcription
        if(item.getBookmark() == 1){
            itemBookmarkBtn.setBackground(ContextCompat.getDrawable(itemView.getContext(),R.drawable.bookmarked));
        }else if (item.getBookmark() == 0){
            itemBookmarkBtn.setBackground(ContextCompat.getDrawable(itemView.getContext(),R.drawable.bookmark));
        }

        //bookmark btn
        itemBookmarkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d("ItemNames ::",item.getName());
                Completable.fromAction(() -> {
                    subscriptionEntity = subscriptionDAO.getBookmark();

                    for (int i=0;i<subscriptionEntity.size();i++){
                       Log.d("ListW ::",subscriptionEntity.get(i).getName() + " bookmark :: "+ subscriptionEntity.get(i).getBookmarks());
                        if (item.getName().equals(subscriptionEntity.get(i).getName())){
                            Log.d("ListW ::",subscriptionEntity.get(i).getName() + " "+ subscriptionEntity.get(i).getBookmarks());
                            if(subscriptionEntity.get(i).getBookmarks() == 0){
                                subscriptionEntity.get(i).setBookmarks(1);
                                itemBookmarkBtn.setBackground(ContextCompat.getDrawable(itemView.getContext(),R.drawable.bookmarked));
                                subscriptionDAO.update(subscriptionEntity.get(i));

                            }else if(subscriptionEntity.get(i).getBookmarks() == 1){
                                subscriptionEntity.get(i).setBookmarks(0);
                                itemBookmarkBtn.setBackground(ContextCompat.getDrawable(itemView.getContext(),R.drawable.bookmark));
                                subscriptionDAO.update(subscriptionEntity.get(i));
                            }
                        }

                    }

                }).subscribeOn(Schedulers.io()).subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });

            }
        });



        itemBuilder.getImageLoader()
                .displayImage(item.getThumbnailUrl(),
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        });

    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.getSubscriberCount() >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        }
        return details;
    }

}
