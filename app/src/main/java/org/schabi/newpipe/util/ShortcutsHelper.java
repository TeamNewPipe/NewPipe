package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

import java.io.InputStream;
import java.util.LinkedList;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ShortcutsHelper {

	private static final int SHORTCUTS_COUNT = 3;

	private ShortcutsHelper() {
	}

	@SuppressLint("CheckResult")
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void addShortcut(@Nullable final Context context, ChannelInfoItem data) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || context == null) {
			return;
		}
		final ShortcutManager manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
		if (manager == null) {
			return;
		}
		final int count = Math.max(SHORTCUTS_COUNT, manager.getMaxShortcutCountPerActivity());
		final LinkedList<ShortcutInfo> shortcuts = new LinkedList<>(manager.getDynamicShortcuts());
		Single.fromCallable(() -> getIcon(context, data.getThumbnailUrl(), manager.getIconMaxWidth(), manager.getIconMaxHeight()))
				.subscribeOn(Schedulers.computation())
				.map(icon -> new ShortcutInfo.Builder(context, getShortcutId(data))
						.setShortLabel(data.getName())
						.setLongLabel(data.getName())
						.setIcon(icon)
						.setIntent(NavigationHelper.getChannelIntent(context, data.getServiceId(), data.getUrl(), data.getName())
								.setAction(Intent.ACTION_VIEW))
						.build())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(shortcut -> {
					shortcuts.addFirst(shortcut);
					while (shortcuts.size() > count) {
						shortcuts.removeLast();
					}
					manager.setDynamicShortcuts(shortcuts);
				}, e -> {
					if (BuildConfig.DEBUG) {
						e.printStackTrace();
					}
				});
	}

	private static String getShortcutId(ChannelInfoItem channel) {
		return "s_" + channel.getUrl().hashCode();
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	private static Icon getIcon(Context context, String url, int width, int height) {
		Bitmap bitmap = null;
		Bitmap thumb = null;
		try {
			final OkHttpClient client = new OkHttpClient();
			final Request request = new Request.Builder()
					.url(url)
					.get()
					.build();
			final Response response = client.newCall(request).execute();
			if (response.isSuccessful()) {
				final InputStream inputStream = response.body().byteStream();
				bitmap = BitmapFactory.decodeStream(inputStream);
				thumb = ThumbnailUtils.extractThumbnail(bitmap, width, height);
				return Icon.createWithBitmap(thumb);
			} else {
				return Icon.createWithResource(context, R.drawable.ic_newpipe_triangle_white);
			}
		} catch (Exception e) {
			return Icon.createWithResource(context, R.drawable.ic_newpipe_triangle_white);
		} finally {
			if (bitmap != null) {
				bitmap.recycle();
			}
			if (thumb != null) {
				thumb.recycle();
			}
		}
	}
}
