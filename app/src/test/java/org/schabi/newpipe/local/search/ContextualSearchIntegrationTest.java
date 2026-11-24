package org.schabi.newpipe.local.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.local.bookmark.PlaylistCategories;

import java.nio.file.Files;
import java.nio.file.Path;

public class ContextualSearchIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void requestedLocalTabsImplementContextualSearchContract() throws Exception {
        assertSourceContains("org/schabi/newpipe/local/history/StatisticsPlaylistFragment.java",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/feed/FeedFragment.kt",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/subscription/SubscriptionFragment.kt",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/bookmark/BookmarkFragment.java",
                "ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/download/DownloadsTabFragment.java",
                "implements ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/subscription/SubscriptionViewModel.kt",
                "getSubscriptionsForScope(");
        assertSourceContains("org/schabi/newpipe/local/subscription/SubscriptionViewModel.kt",
                "filterQuery = query");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "fragment instanceof MainFragment");
    }

    @Test
    public void subscriptionNoResultsUsesDedicatedStaticLayout() throws Exception {
        final String item = read(
                "org/schabi/newpipe/local/subscription/item/"
                        + "SearchNoResultsPlaceholderItem.kt");
        final String layout = readResource("layout/list_search_no_results.xml");

        assertTrue(item.contains("R.layout.list_search_no_results"));
        assertFalse(item.contains("ListEmptyViewBinding"));
        assertTrue(layout.contains("android:text=\"@string/search_no_results\""));
        assertFalse(layout.contains("android:id="));
    }

    @Test
    public void bookmarkFilteringProtectsCanonicalOrdering() {
        assertTrue(PlaylistCategories.allowsReordering(false, PlaylistCategories.ALL));
        assertFalse(PlaylistCategories.allowsReordering(true, PlaylistCategories.ALL));
        assertFalse(PlaylistCategories.allowsReordering(false, "a-category"));
        assertFalse(PlaylistCategories.allowsReordering(true, "a-category"));
        assertFalse(PlaylistCategories.allowsReordering(false, PlaylistCategories.UNCATEGORIZED));
    }

    @Test
    public void contextualToolbarOwnsNavigationSpaceOnlyWhileSearchIsOpen() throws Exception {
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "setActivityContextualSearchToolbarActive(contextualSearchOpen)");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "toolbarLayoutBinding.toolbar.setNavigationIcon(null)");
        assertSourceContains("org/schabi/newpipe/MainActivity.java",
                "toggle.syncState()");
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "binding.contextualGlobalSearchFab.setText(globalSearchLabel)");
        assertSourceContains("org/schabi/newpipe/fragments/MainFragment.java",
                "updateGlobalSearchFabPosition(showBottomNavigation");
    }

    @Test
    public void contextualSearchUsesServiceFabAndDoneKeepsSearchLocal() throws Exception {
        final String mainFragment = read("org/schabi/newpipe/fragments/MainFragment.java");
        final String toolbar = readResource("layout/toolbar_contextual_search_layout.xml");

        assertTrue(mainFragment.contains(
                "binding.contextualGlobalSearchFab.setOnClickListener("));
        assertTrue(mainFragment.contains(
                "getString(R.string.search_with_service_name, serviceName)"));
        assertTrue(mainFragment.contains("actionId == EditorInfo.IME_ACTION_DONE"));
        assertTrue(mainFragment.contains(
                "KeyboardUtil.hideKeyboard(activity, contextualSearchEditText)"));
        assertTrue(toolbar.contains(
                "android:imeOptions=\"actionDone|flagNoFullscreen\""));
        assertFalse(toolbar.contains("contextual_global_search_button"));
    }

    @Test
    public void contextualSearchFabStaysAboveTheKeyboard() throws Exception {
        final String mainFragment = read("org/schabi/newpipe/fragments/MainFragment.java");
        final String manifest = Files.readString(
                resourceDirectory.resolve("../AndroidManifest.xml").normalize());

        assertTrue(mainFragment.contains("WindowInsetsCompat.Type.ime()"));
        assertTrue(mainFragment.contains(
                "imeInsets.bottom - navigationBarInsets.bottom"));
        assertTrue(mainFragment.contains(
                "contextualSearchFabBaseBottomMargin + contextualSearchImeBottomInset"));
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""));
    }

    @Test
    public void downloadsSearchIsBufferedFilteredAndRefreshSafe() throws Exception {
        final String mainFragment = read("org/schabi/newpipe/fragments/MainFragment.java");
        assertTrue(mainFragment.contains("searchItem.setVisible(isConfiguredSearchAvailable()"));
        assertFalse(mainFragment.contains("isCurrentDownloadsTab()"));

        assertSourceContains("org/schabi/newpipe/download/DownloadsTabFragment.java",
                "missionsFragment.setSearchQuery(contextualSearchQuery)");
        assertSourceContains("us/shandian/giga/ui/fragment/MissionsFragment.java",
                "mAdapter.setSearchQuery(mSearchQuery)");

        final String manager = read("us/shandian/giga/service/DownloadManager.java");
        assertTrue(manager.contains("pending.removeIf(mission -> !matchesSearchQuery(mission))"));
        assertTrue(manager.contains("finished.removeIf(mission -> !matchesSearchQuery(mission))"));
        assertTrue(manager.contains("ContextualSearchHelper.matches(searchQuery"));
        assertTrue(manager.contains("ArrayList<Mission> hidden"));
        assertTrue(manager.indexOf("pending.removeIf") < manager.indexOf("int fakeTotal"));
        assertTrue(manager.indexOf("finished.removeIf") < manager.indexOf("int fakeTotal"));

        final String adapter = read("us/shandian/giga/ui/adapter/MissionAdapter.java");
        assertTrue(adapter.contains("R.string.search_no_results"));
        assertTrue(adapter.contains("!mIterator.isSearchActive()"));
        assertTrue(adapter.contains("mIterator.start()"));
        assertTrue(adapter.contains("mIterator.end()"));
    }

    @Test
    public void standaloneDownloadsSearchRestoresAndControlsItsToolbar() throws Exception {
        final String activity = read("org/schabi/newpipe/download/DownloadActivity.java");
        assertTrue(activity.contains("findFragmentByTag(MISSIONS_FRAGMENT_TAG)"));
        assertTrue(activity.contains("currentFragment instanceof MissionsFragment"));

        final String fragment = read("us/shandian/giga/ui/fragment/MissionsFragment.java");
        assertTrue(fragment.contains("requireActivity() instanceof DownloadActivity"));
        assertTrue(fragment.contains("STATE_STANDALONE_SEARCH_EXPANDED"));
        assertTrue(fragment.contains("mSearchView.setQuery(mSearchQuery, false)"));
        assertTrue(fragment.contains("setSearchQuery(\"\")"));
        assertTrue(fragment.contains("mSwitch.setVisible(!mStandaloneSearchExpanded)"));
        assertTrue(fragment.contains(
                "mAdapter.setMenuActionsSuppressed(mStandaloneSearchExpanded)"));

        final String adapter = read("us/shandian/giga/ui/adapter/MissionAdapter.java");
        assertTrue(adapter.contains("mMenuActionsSuppressed || mIterator.isSearchActive()"));

        final String menu = readResource("menu/download_menu.xml");
        assertTrue(menu.contains("android:id=\"@+id/search_downloads\""));
        assertTrue(menu.contains(
                "app:actionViewClass=\"androidx.appcompat.widget.SearchView\""));
        assertTrue(menu.contains("app:showAsAction=\"always|collapseActionView\""));
    }

    @Test
    public void channelsAndPlaylistsExposeInPageSearch() throws Exception {
        assertSourceContains("org/schabi/newpipe/fragments/list/channel/ChannelTabFragment.java",
                "implements PlaylistControlViewHolder, ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/fragments/list/playlist/PlaylistFragment.java",
                "implements PlaylistControlViewHolder, ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/local/playlist/LocalPlaylistFragment.java",
                "DebounceSavable, ContextualSearchable");
        assertSourceContains("org/schabi/newpipe/fragments/list/channel/ChannelFragment.java",
                "applySearchToCurrentTab()");

        assertSearchMenu("menu/menu_channel.xml");
        assertSearchMenu("menu/menu_playlist.xml");
        assertSearchMenu("menu/menu_local_playlist.xml");
    }

    @Test
    public void channelContextualSearchSuppressesTheUnderlyingGlobalAction() throws Exception {
        final String channelFragment = read(
                "org/schabi/newpipe/fragments/list/channel/ChannelFragment.java");

        assertTrue(channelFragment.contains(
                "final MenuItem globalSearchItem = menu.findItem(R.id.action_search);"));
        assertTrue(channelFragment.contains("globalSearchItem.setVisible(false);"));
    }

    @Test
    public void remoteSearchWaitsForInitialResultBeforeFiltering() throws Exception {
        assertSourceContains("org/schabi/newpipe/fragments/list/channel/ChannelTabFragment.java",
                "|| currentInfo == null");
        assertSourceContains("org/schabi/newpipe/fragments/list/playlist/PlaylistFragment.java",
                "infoListAdapter == null || currentInfo == null"
                        + " || playlistControlBinding == null");
    }

    private void assertSearchMenu(final String relativePath) throws Exception {
        final String menu = readResource(relativePath);
        assertTrue(menu.contains("android:id=\"@+id/menu_item_search_content\""));
        assertTrue(menu.contains(
                "app:actionViewClass=\"androidx.appcompat.widget.SearchView\""));
        assertTrue(menu.contains("app:showAsAction=\"always|collapseActionView\""));
    }

    private void assertSourceContains(final String relativePath, final String expected)
            throws Exception {
        assertTrue(relativePath, read(relativePath).contains(expected));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }

    private String readResource(final String relativePath) throws Exception {
        return Files.readString(resourceDirectory.resolve(relativePath));
    }
}
