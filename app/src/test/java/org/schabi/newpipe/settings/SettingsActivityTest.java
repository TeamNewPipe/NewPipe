package org.schabi.newpipe.settings;

import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchFragment;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class SettingsActivityTest {

    private SettingsActivity activity;
    private FragmentManager fragmentManager;

    @Before
    public void setUp() {
        activity = Mockito.spy(new SettingsActivity());
        fragmentManager = Mockito.mock(FragmentManager.class);
        Mockito.doReturn(fragmentManager).when(activity).getSupportFragmentManager();
        // Mock the searchEditText
        final EditText editText = Mockito.mock(EditText.class);
        activity.setMockEditText(editText);
    }

    @Test
    public void mainSettingsFragmentDisplayedWhenSearchInactive() {
        when(fragmentManager.findFragmentById(anyInt()))
                .thenReturn(new MainSettingsFragment());

        activity.setSearchActive(false);

        final Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentById(SettingsActivity.getFragmentHolderId());
        assertTrue(fragment instanceof MainSettingsFragment);
    }

    @Test
    public void preferenceSearchFragmentDisplayedWhenSearchActiveAndTextNotEmpty() {
        when(fragmentManager.findFragmentById(anyInt()))
                .thenReturn(new PreferenceSearchFragment());

        activity.setSearchActive(true);
        Mockito.when(activity.getSearchEditText().getText())
                .thenReturn(new android.text.SpannableStringBuilder("query"));

        final Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentById(SettingsActivity.getFragmentHolderId());
        assertTrue(fragment instanceof PreferenceSearchFragment);
    }

    @Test
    public void mainSettingsFragmentDisplayedWhenSearchActiveButTextEmpty() {
        when(fragmentManager.findFragmentById(anyInt()))
                .thenReturn(new MainSettingsFragment());

        activity.setSearchActive(true);
        Mockito.when(activity.getSearchEditText().getText())
                .thenReturn(new android.text.SpannableStringBuilder(""));

        final Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentById(SettingsActivity.getFragmentHolderId());
        assertTrue(fragment instanceof MainSettingsFragment);
    }
}
