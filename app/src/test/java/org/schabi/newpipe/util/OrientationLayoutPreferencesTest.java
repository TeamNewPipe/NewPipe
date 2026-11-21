package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemViewMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OrientationLayoutPreferencesTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void rotationSelectsIndependentLayoutAndColumnsOnPhone() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.list_view_mode_portrait_key), "grid")
                .putString(context.getString(R.string.grid_columns_portrait_key), "2")
                .putString(context.getString(R.string.list_view_mode_landscape_key), "card")
                .putString(context.getString(R.string.grid_columns_landscape_key), "3")
                .commit();
        RuntimeEnvironment.setQualifiers("port");
        assertEquals(ItemViewMode.GRID, ThemeHelper.getItemViewMode(context));
        assertEquals(2, GridLayoutManagerHelper.getPreferredSpanCount(context));
        RuntimeEnvironment.setQualifiers("land");
        assertEquals(ItemViewMode.CARD, ThemeHelper.getItemViewMode(context));
        assertEquals(3, GridLayoutManagerHelper.getPreferredSpanCount(context));
    }

    @Test
    public void autoLayoutUsesCompactListOnPhoneLandscapeWithoutOverridingExplicitChoices() {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        RuntimeEnvironment.setQualifiers("normal-port");
        assertEquals(ItemViewMode.CARD, ThemeHelper.getItemViewMode(context));

        RuntimeEnvironment.setQualifiers("normal-land");
        assertEquals(ItemViewMode.LIST, ThemeHelper.getItemViewMode(context));

        preferences.edit()
                .putString(context.getString(R.string.list_view_mode_landscape_key), "card")
                .commit();
        assertEquals(ItemViewMode.CARD, ThemeHelper.getItemViewMode(context));

        preferences.edit()
                .putString(context.getString(R.string.list_view_mode_landscape_key), "grid")
                .commit();
        assertEquals(ItemViewMode.GRID, ThemeHelper.getItemViewMode(context));
    }

    @Test
    public void defaultRetainsLegacyPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.list_view_mode_key), "grid")
                .putString(context.getString(R.string.grid_columns_key), "4")
                .putString(context.getString(R.string.list_view_mode_landscape_key), "default")
                .commit();
        RuntimeEnvironment.setQualifiers("land");
        assertEquals(ItemViewMode.GRID, ThemeHelper.getItemViewMode(context));
        assertEquals(4, GridLayoutManagerHelper.getPreferredSpanCount(context));
    }
    @Test
    public void switchingLayoutsRetainsTheVisibleItem() {
        final RecyclerView list = new RecyclerView(context);
        final LinearLayoutManager portrait = new LinearLayoutManager(context);
        list.setLayoutManager(portrait);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                              final int viewType) {
                final TextView item = new TextView(parent.getContext());
                item.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 80));
                return new RecyclerView.ViewHolder(item) { };
            }

            @Override
            public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                         final int position) {
                ((TextView) holder.itemView).setText(Integer.toString(position));
            }

            @Override
            public int getItemCount() {
                return 30;
            }
        });
        layout(list);
        portrait.scrollToPositionWithOffset(12, 0);
        layout(list);
        assertEquals(12, portrait.findFirstVisibleItemPosition());
        final GridLayoutManager landscape = new GridLayoutManager(context, 2);
        GridLayoutManagerHelper.preserveScrollPosition(list,
                () -> list.setLayoutManager(landscape));
        layout(list);
        assertEquals(12, landscape.findFirstVisibleItemPosition());
        final LinearLayoutManager restored = new LinearLayoutManager(context);
        GridLayoutManagerHelper.preserveScrollPosition(list,
                () -> list.setLayoutManager(restored));
        layout(list);
        assertEquals(12, restored.findFirstVisibleItemPosition());
    }

    private static void layout(final RecyclerView list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 320, 400);
    }

}
