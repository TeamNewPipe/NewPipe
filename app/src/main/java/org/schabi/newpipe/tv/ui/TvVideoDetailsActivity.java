/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.tv.util.TvNavigationHelper;

public class TvVideoDetailsActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        if (savedInstanceState == null) {
            final int serviceId = getIntent().getIntExtra(TvNavigationHelper.KEY_SERVICE_ID, 0);
            final String url = getIntent().getStringExtra(TvNavigationHelper.KEY_URL);
            final String title = getIntent().getStringExtra(TvNavigationHelper.KEY_TITLE);

            final TvVideoDetailsFragment fragment =
                    TvVideoDetailsFragment.newInstance(serviceId, url, title);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tv_main_container, fragment)
                    .commit();
        }
    }
}
