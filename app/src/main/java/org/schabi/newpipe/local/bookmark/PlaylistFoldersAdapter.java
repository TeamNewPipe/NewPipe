package org.schabi.newpipe.local.bookmark;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.model.PlaylistFolderEntity;

import java.util.ArrayList;
import java.util.List;

public class PlaylistFoldersAdapter extends RecyclerView.Adapter<PlaylistFoldersAdapter.ViewHolder> {

    public interface Listener {
        void onFolderSelected(Long folderId);
        void onFolderLongPressed(Long folderId, String name, int position);
    }

    private final List<PlaylistFolderEntity> folders = new ArrayList<>();
    private final Listener listener;
    private int selectedPosition = -1;

    public PlaylistFoldersAdapter(final Listener listener) {
        this.listener = listener;
    }

    public void setFolders(final List<PlaylistFolderEntity> list) {
        folders.clear();
        if (list != null) folders.addAll(list);
        notifyDataSetChanged();
    }

    public Long getSelectedFolderId() {
        if (selectedPosition >= 0 && selectedPosition < folders.size()) {
            return folders.get(selectedPosition).getUid();
        }
        return null;
    }

    public List<PlaylistFolderEntity> getFolders() {
        return folders;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_folder_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final PlaylistFolderEntity folder = folders.get(position);
        holder.name.setText(folder.getName());
        final Long id = folder.getUid();
        holder.itemView.setSelected(position == selectedPosition);
        holder.itemView.setOnClickListener(v -> {
            selectedPosition = position;
            notifyDataSetChanged();
            listener.onFolderSelected(id);
        });
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFolderLongPressed(id, folder.getName(), position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.folder_name);
        }
    }
}
