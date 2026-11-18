package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.video.PlayerVideoSurface;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.views.PlaybackDimmingLayout;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoPlayerUiTvSeekTest {
    @Test
    public void repeatedSeekingContinuesUntilDirectionalMenuNavigation() throws Exception {
        final Context context = mock(Context.class);
        doReturn("player_controls_timeout").when(context)
                .getString(R.string.player_controls_timeout_key);
        final Player player = mock(Player.class);
        doReturn(mock(SharedPreferences.class)).when(player).getPrefs();
        final VideoPlayerUi ui = mock(VideoPlayerUi.class, CALLS_REAL_METHODS);
        final PlayerBinding binding = mock(PlayerBinding.class);
        doReturn(mock(PlaybackDimmingLayout.class)).when(binding).getRoot();
        setField(PlayerUi.class, ui, "context", context);
        setField(PlayerUi.class, ui, "player", player);
        setField(VideoPlayerUi.class, ui, "binding", binding);
        setField(VideoPlayerUi.class, ui, "videoSurface", mock(PlayerVideoSurface.class));
        doReturn(true).when(ui).isFullscreen();
        doReturn(Player.STATE_PLAYING).when(player).getCurrentState();
        final AtomicBoolean controls = new AtomicBoolean();
        doAnswer(call -> controls.get()).when(ui).isControlsVisible();
        doAnswer(call -> {
            controls.set(true);
            return null;
        }).when(ui).showControlsThenHide();
        doNothing().when(ui).hideControls(anyLong(), anyLong());
        try (MockedStatic<DeviceUtils> devices = mockStatic(DeviceUtils.class)) {
            devices.when(() -> DeviceUtils.isTv(context)).thenReturn(true);
            assertTrue(ui.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT));
            assertTrue(ui.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT));
            assertTrue(ui.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT));
            verify(player, times(2)).fastForward();
            verify(player).fastRewind();

            assertFalse(ui.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN));
            assertFalse(ui.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT));
            verify(player, times(2)).fastForward();
        }
    }

    private static void setField(final Class<?> owner, final Object target,
                                 final String name, final Object value) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
