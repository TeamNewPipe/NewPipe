package us.shandian.giga.ui.adapter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import android.support.v7.widget.RecyclerView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.schabi.newpipe.R;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.common.ProgressDrawable;
import us.shandian.giga.util.Utility;

public class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.ViewHolder>
{
	private static final Map<Integer, String> ALGORITHMS = new HashMap<>();
	
	static {
		ALGORITHMS.put(R.id.md5, "MD5");
		ALGORITHMS.put(R.id.sha1, "SHA1");
	}
	
	private Context mContext;
	private LayoutInflater mInflater;
	private DownloadManager mManager;
	private DownloadManagerService.DMBinder mBinder;
	private int mLayout;
	
	public MissionAdapter(Context context, DownloadManagerService.DMBinder binder, DownloadManager manager, boolean isLinear) {
		mContext = context;
		mManager = manager;
		mBinder = binder;
		
		mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		mLayout = isLinear ? R.layout.mission_item_linear : R.layout.mission_item;
	}

	@Override
	public MissionAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		final ViewHolder h =  new ViewHolder(mInflater.inflate(mLayout, parent, false));
		
		h.menu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				buildPopup(h);
			}
		});
		
		/*h.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showDetail(h);
			}
		});*/
		
		return h;
	}

	@Override
	public void onViewRecycled(MissionAdapter.ViewHolder h) {
		super.onViewRecycled(h);
		h.mission.removeListener(h.observer);
		h.mission = null;
		h.observer = null;
		h.progress = null;
		h.position = -1;
		h.lastTimeStamp = -1;
		h.lastDone = -1;
		h.colorId = 0;
	}

	@Override
	public void onBindViewHolder(MissionAdapter.ViewHolder h, int pos) {
		DownloadMission ms = mManager.getMission(pos);
		h.mission = ms;
		h.position = pos;
		
		Utility.FileType type = Utility.getFileType(ms.name);
		
		h.icon.setImageResource(Utility.getIconForFileType(type));
		h.name.setText(ms.name);
		h.size.setText(Utility.formatBytes(ms.length));
		
		h.progress = new ProgressDrawable(mContext, Utility.getBackgroundForFileType(type), Utility.getForegroundForFileType(type));
		h.bkg.setBackgroundDrawable(h.progress);
		
		h.observer = new MissionObserver(this, h);
		ms.addListener(h.observer);
		
		updateProgress(h);
	}

	@Override
	public int getItemCount() {
		return mManager.getCount();
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	private void updateProgress(ViewHolder h) {
		updateProgress(h, false);
	}
	
	private void updateProgress(ViewHolder h, boolean finished) {
		if (h.mission == null) return;
		
		long now = System.currentTimeMillis();
		
		if (h.lastTimeStamp == -1) {
			h.lastTimeStamp = now;
		}
		
		if (h.lastDone == -1) {
			h.lastDone = h.mission.done;
		}
		
		long deltaTime = now - h.lastTimeStamp;
		long deltaDone = h.mission.done - h.lastDone;
		
		if (deltaTime == 0 || deltaTime > 1000 || finished) {
			if (h.mission.errCode > 0) {
				h.status.setText(R.string.msg_error);
			} else {
				float progress = (float) h.mission.done / h.mission.length;
				h.status.setText(String.format("%.2f%%", progress * 100));
				h.progress.setProgress(progress);
			
			}
		}
		
		if (deltaTime > 1000 && deltaDone > 0) {
			float speed = (float) deltaDone / deltaTime;
			String speedStr = Utility.formatSpeed(speed * 1000);
			String sizeStr = Utility.formatBytes(h.mission.length);
			
			h.size.setText(sizeStr + " " + speedStr);
			
			h.lastTimeStamp = now;
			h.lastDone = h.mission.done;
		}
	}
	

	private void buildPopup(final ViewHolder h) {
		PopupMenu popup = new PopupMenu(mContext, h.menu);
		popup.inflate(R.menu.mission);
		
		Menu menu = popup.getMenu();
		MenuItem start = menu.findItem(R.id.start);
		MenuItem pause = menu.findItem(R.id.pause);
		MenuItem view = menu.findItem(R.id.view);
		MenuItem delete = menu.findItem(R.id.delete);
		MenuItem checksum = menu.findItem(R.id.checksum);
		
		// Set to false first
		start.setVisible(false);
		pause.setVisible(false);
		view.setVisible(false);
		delete.setVisible(false);
		checksum.setVisible(false);
		
		if (!h.mission.finished) {
			if (!h.mission.running) {
				if (h.mission.errCode == -1) {
					start.setVisible(true);
				}
				
				delete.setVisible(true);
			} else {
				pause.setVisible(true);
			}
		} else {
			view.setVisible(true);
			delete.setVisible(true);
			checksum.setVisible(true);
		}
		
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				int id = item.getItemId();
				switch (id) {
					case R.id.start:
						mManager.resumeMission(h.position);
						mBinder.onMissionAdded(mManager.getMission(h.position));
						return true;
					case R.id.pause:
						mManager.pauseMission(h.position);
						mBinder.onMissionRemoved(mManager.getMission(h.position));
						h.lastTimeStamp = -1;
						h.lastDone = -1;
						return true;
					case R.id.view:
						Intent i = new Intent();
						i.setAction(Intent.ACTION_VIEW);
						File f = new File(h.mission.location + "/" + h.mission.name);
						String ext = Utility.getFileExt(h.mission.name);
						
						if (ext == null) return false;
						
						String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.substring(1));
						
						if (f.exists()) {
							i.setDataAndType(Uri.fromFile(f), mime);
							
							try {
								mContext.startActivity(i);
							} catch (Exception e) {
								
							}
						}
						
						return true;
					case R.id.delete:
						mManager.deleteMission(h.position);
						notifyDataSetChanged();
						return true;
					case R.id.md5:
					case R.id.sha1:
						DownloadMission mission = mManager.getMission(h.position);
						new ChecksumTask().execute(mission.location + "/" + mission.name, ALGORITHMS.get(id));
						return true;
					default:
						return false;
				}
			}
		});
		
		popup.show();
	}
	
	private class ChecksumTask extends AsyncTask<String, Void, String> {
		ProgressDialog prog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			// Create dialog
			prog = new ProgressDialog(mContext);
			prog.setCancelable(false);
			prog.setMessage(mContext.getString(R.string.msg_wait));
			prog.show();
		}

		@Override
		protected String doInBackground(String... params) {
			return Utility.checksum(params[0], params[1]);
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			prog.dismiss();
			Utility.copyToClipboard(mContext, result);
		}
	}
	
	static class ViewHolder extends RecyclerView.ViewHolder {
		public DownloadMission mission;
		public int position;
		
		public TextView status;
		public ImageView icon;
		public TextView name;
		public TextView size;
		public View bkg;
		public ImageView menu;
		public ProgressDrawable progress;
		public MissionObserver observer;
		
		public long lastTimeStamp = -1;
		public long lastDone = -1;
		public int colorId;
		
		public ViewHolder(View v) {
			super(v);
			
			status = Utility.findViewById(v, R.id.item_status);
			icon = Utility.findViewById(v, R.id.item_icon);
			name = Utility.findViewById(v, R.id.item_name);
			size = Utility.findViewById(v, R.id.item_size);
			bkg = Utility.findViewById(v, R.id.item_bkg);
			menu = Utility.findViewById(v, R.id.item_more);
		}
	}
	
	static class MissionObserver implements DownloadMission.MissionListener {
		private MissionAdapter mAdapter;
		private ViewHolder mHolder;
		
		public MissionObserver(MissionAdapter adapter, ViewHolder holder) {
			mAdapter = adapter;
			mHolder = holder;
		}
		
		@Override
		public void onProgressUpdate(long done, long total) {
			mAdapter.updateProgress(mHolder);
		}

		@Override
		public void onFinish() {
			//mAdapter.mManager.deleteMission(mHolder.position);
			// TODO Notification
			//mAdapter.notifyDataSetChanged();
			if (mHolder.mission != null) {
				mHolder.size.setText(Utility.formatBytes(mHolder.mission.length));
				mAdapter.updateProgress(mHolder, true);
			}
		}

		@Override
		public void onError(int errCode) {
			mAdapter.updateProgress(mHolder);
		}
		
	}
}
