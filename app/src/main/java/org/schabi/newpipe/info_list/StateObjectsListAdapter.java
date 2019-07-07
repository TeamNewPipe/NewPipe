package org.schabi.newpipe.info_list;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.SparseArrayUtils;

import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public abstract class StateObjectsListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	private final SparseArray<StreamStateEntity> states;
	private final HistoryRecordManager recordManager;
	private final CompositeDisposable stateLoaders;
	private final Context context;

	public StateObjectsListAdapter(Context context) {
		this.states = new SparseArray<>();
		this.recordManager = new HistoryRecordManager(context);
		this.context = context;
		this.stateLoaders = new CompositeDisposable();
	}

	@Nullable
	public StreamStateEntity getState(int position) {
		return states.get(position);
	}

	protected void clearStates() {
		states.clear();
	}

	private void appendStates(List<StreamStateEntity> statesEntities, int offset) {
		for (int i = 0; i < statesEntities.size(); i++) {
			final StreamStateEntity state = statesEntities.get(i);
			if (state != null) {
				states.append(offset + i, state);
			}
		}
	}

	private void appendState(StreamStateEntity statesEntity, int offset) {
		if (statesEntity != null) {
			states.append(offset, statesEntity);
		}
	}

	protected void removeState(int index) {
		states.remove(index);
	}

	protected void moveState(int from, int to) {
		final StreamStateEntity item = states.get(from);
		if (from < to) {
			SparseArrayUtils.shiftItemsDown(states, from, to);
		} else {
			SparseArrayUtils.shiftItemsUp(states, to, from);
		}
		states.put(to, item);
	}

	protected void loadStates(List<InfoItem> list, int offset, Runnable callback) {
		if (isPlaybackStatesVisible()) {
			stateLoaders.add(
					recordManager.loadStreamStateBatch(list)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(streamStateEntities -> {
								appendStates(streamStateEntities, offset);
								callback.run();
							}, throwable -> {
								if (BuildConfig.DEBUG) throwable.printStackTrace();
								callback.run();
							})
			);
		} else {
			callback.run();
		}
	}

	protected void loadState(InfoItem item, int offset, Runnable callback) {
		if (isPlaybackStatesVisible()) {
			stateLoaders.add(
					recordManager.loadStreamState(item)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(streamStateEntities -> {
								appendState(streamStateEntities[0], offset);
								callback.run();
							}, throwable -> {
								if (BuildConfig.DEBUG) throwable.printStackTrace();
								callback.run();
							})
			);
		} else {
			callback.run();
		}
	}

	protected void loadStatesForLocal(List<? extends LocalItem> list, int offset, Runnable callback) {
		if (isPlaybackStatesVisible()) {
			stateLoaders.add(
					recordManager.loadLocalStreamStateBatch(list)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(streamStateEntities -> {
								appendStates(streamStateEntities, offset);
								callback.run();
							}, throwable -> {
								if (BuildConfig.DEBUG) throwable.printStackTrace();
								callback.run();
							})
			);
		} else {
			callback.run();
		}
	}

	private void processStatesUpdates(List<StreamStateEntity> streamStateEntities) {
		for (int i = 0; i < streamStateEntities.size(); i++) {
			final StreamStateEntity newState = streamStateEntities.get(i);
			if (!Objects.equals(states.get(i), newState)) {
				if (newState == null) {
					states.remove(i);
				} else {
					states.put(i, newState);
				}
				onItemStateChanged(i, newState);
			}
		}
	}

	protected void updateAllStates(List<InfoItem> list) {
		if (isPlaybackStatesVisible()) {
			stateLoaders.add(
					recordManager.loadStreamStateBatch(list)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(this::processStatesUpdates, throwable -> {
								if (BuildConfig.DEBUG) throwable.printStackTrace();
							})
			);
		} else {
			final int[] positions = SparseArrayUtils.getKeys(states);
			states.clear();
			for (int pos : positions) onItemStateChanged(pos, null);
		}
	}

	protected void updateAllLocalStates(List<? extends LocalItem> list) {
		if (isPlaybackStatesVisible()) {
			stateLoaders.add(
					recordManager.loadLocalStreamStateBatch(list)
							.observeOn(AndroidSchedulers.mainThread())
							.subscribe(this::processStatesUpdates, throwable -> {
								if (BuildConfig.DEBUG) throwable.printStackTrace();
							})
			);
		} else {
			final int[] positions = SparseArrayUtils.getKeys(states);
			states.clear();
			for (int pos : positions) onItemStateChanged(pos, null);
		}
	}

	public void dispose() {
		stateLoaders.dispose();
	}

	protected boolean isPlaybackStatesVisible() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)
				&& prefs.getBoolean(context.getString(R.string.enable_playback_resume_key), true)
				&& prefs.getBoolean(context.getString(R.string.enable_playback_state_lists_key), true);
	}

	protected abstract void onItemStateChanged(int position, @Nullable StreamStateEntity state);

}
