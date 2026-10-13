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

public class TvSearchActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        if (savedInstanceState == null) {
            final String query = getIntent().getStringExtra(TvNavigationHelper.KEY_SEARCH_QUERY);
            final TvSearchFragment fragment = TvSearchFragment.newInstance(query);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tv_main_container, fragment)
                    .commit();
        }
    }
}
