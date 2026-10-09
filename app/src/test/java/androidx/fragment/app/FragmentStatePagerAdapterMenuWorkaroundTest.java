/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package androidx.fragment.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.util.Log;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Set;

public class FragmentStatePagerAdapterMenuWorkaroundTest {

    @Test
    public void testRestoreStateWithMissingFragmentDoesNotThrow() {
        final FragmentManager fragmentManager = mock(FragmentManager.class);
        final Bundle bundle = mock(Bundle.class);
        final Set<String> keys = Collections.singleton("f2");

        when(bundle.keySet()).thenReturn(keys);
        when(fragmentManager.getFragment(bundle, "f2"))
                .thenThrow(new IllegalStateException("Fragment no longer exists for key f2"));

        final FragmentStatePagerAdapterMenuWorkaround adapter =
                new FragmentStatePagerAdapterMenuWorkaround(fragmentManager,
                        FragmentStatePagerAdapterMenuWorkaround
                                .BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                    @Override
                    public Fragment getItem(final int position) {
                        return mock(Fragment.class);
                    }

                    @Override
                    public int getCount() {
                        return 3;
                    }
                };

        try (MockedStatic<Log> mockedLog = mockStatic(Log.class)) {
            adapter.restoreState(bundle, getClass().getClassLoader());

            mockedLog.verify(() -> Log.w(
                    eq("FragmentStatePagerAdapt"),
                    eq("Bad fragment at key f2"),
                    any(IllegalStateException.class)
            ));
        }
    }
}
