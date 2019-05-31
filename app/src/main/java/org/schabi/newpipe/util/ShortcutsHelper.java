package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ShortcutsHelper {

	public static final String ACTION_OPEN_SHORTCUT = "org.schabi.newpipe.action.OPEN_SHORTCUT";

	private ShortcutsHelper() {
	}

	@SuppressLint("CheckResult")
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void addShortcut(@Nullable final Context context, @NonNull final ChannelInfoItem data) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || context == null) {
			return;
		}
		final ShortcutManager manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
		if (manager == null) {
			return;
		}
		Single.fromCallable(() -> getIcon(context, data.getThumbnailUrl(), manager.getIconMaxWidth(), manager.getIconMaxHeight()))
				.subscribeOn(Schedulers.computation())
				.map(icon -> new ShortcutInfo.Builder(context, getShortcutId(data))
						.setShortLabel(data.getName())
						.setLongLabel(data.getName())
						.setIcon(icon)
						.setIntent(createIntent(context, data)))
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(builder -> {
					addOrUpdate(manager, builder, getShortcutId(data));
				}, e -> {
					if (BuildConfig.DEBUG) {
						e.printStackTrace();
					}
				});
	}

	@RequiresApi(api = Build.VERSION_CODES.N_MR1)
	private static void addOrUpdate(ShortcutManager manager, ShortcutInfo.Builder builder, String id) {
		final List<ShortcutInfo> shortcuts = manager.getDynamicShortcuts();
		for (int i = 0; i < shortcuts.size(); i++) {
			final ShortcutInfo shortcut = shortcuts.get(i);
			if (id.equals(shortcut.getId())) {
				builder.setRank(shortcut.getRank() + 1);
				manager.updateShortcuts(Collections.singletonList(builder.build()));
				return;
			}
		}
		builder.setRank(1);
		manager.addDynamicShortcuts(Collections.singletonList(builder.build()));
	}

	@NonNull
	private static Intent createIntent(@NonNull Context context, @NonNull ChannelInfoItem channel) {
		final Intent intent = new Intent(context, MainActivity.class);
		intent.setAction(ACTION_OPEN_SHORTCUT);
		intent.putExtra(Constants.KEY_URL, channel.getUrl());
		intent.putExtra(Constants.KEY_SERVICE_ID, channel.getServiceId());
		intent.putExtra(Constants.KEY_TITLE, channel.getName());
		return intent;
	}

	@NonNull
	private static String getShortcutId(@NonNull ChannelInfoItem channel) {
		return "s_" + channel.getUrl().hashCode();
	}

	@NonNull
	@RequiresApi(api = Build.VERSION_CODES.M)
	private static Icon getIcon(@NonNull Context context, final String url, int width, int height) {
		Bitmap bitmap = null;
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

				final Bitmap thumb = ThumbnailUtils.extractThumbnail(bitmap, width, height);
				final Icon icon = Icon.createWithBitmap(createCircleBitmap(thumb));
				thumb.recycle();
				return icon;
			} else {
				return Icon.createWithResource(context, R.drawable.ic_newpipe_triangle_white);
			}
		} catch (Exception e) {
			return Icon.createWithResource(context, R.drawable.ic_newpipe_triangle_white);
		} finally {
			if (bitmap != null) {
				bitmap.recycle();
			}
		}
	}

	@NonNull
	private static Bitmap createCircleBitmap(@NonNull final Bitmap bitmap) {
		final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
				bitmap.getHeight(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
				bitmap.getWidth() / 2, paint);
		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(bitmap, rect, rect, paint);
		return output;
	}
}
