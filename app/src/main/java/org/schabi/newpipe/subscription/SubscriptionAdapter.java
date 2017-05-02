package org.schabi.newpipe.subscription;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.List;

class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.ViewHolder>
                                implements View.OnClickListener{

    private List subInfoList;
    private SubscribedChannelInfo channelInfo;
    private Context context;
    private ImageLoader imageloader = ImageLoader.getInstance();

    SubscriptionAdapter(Context context) {
        SubscriptionDBHelper dbHelper = new SubscriptionDBHelper(context);
        subInfoList = dbHelper.read(context);
        this.context = context;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        ImageView avatarImage;
        LinearLayout container;

        ViewHolder(View itemView) {
            super(itemView);
            container = (LinearLayout) itemView.findViewById(R.id.channel_item);
            nameTextView = (TextView) itemView.findViewById(R.id.channel_name);
            avatarImage = (ImageView) itemView.findViewById(R.id.channel_avatar);
        }
    }

    @Override
    public SubscriptionAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View contactView = inflater.inflate(R.layout.subscription_item, parent, false);

        // Return a new holder instance
        return new ViewHolder(contactView);
    }

    @Override
    public void onBindViewHolder(SubscriptionAdapter.ViewHolder holder, int position) {
        channelInfo = (SubscribedChannelInfo) subInfoList.get(position);
        holder.nameTextView.setText(channelInfo.getName());
        holder.container.setOnClickListener(this);
        try {
            imageloader.displayImage(channelInfo.getAvatar(), holder.avatarImage);
        }
        catch (IllegalArgumentException e){e.printStackTrace();}
    }

    @Override
    public int getItemCount() {
        return subInfoList.size();
    }

    @Override
    public void onClick(View v) {
        NavigationHelper.openChannel(context, channelInfo.getID(), channelInfo.getLink());
    }
}