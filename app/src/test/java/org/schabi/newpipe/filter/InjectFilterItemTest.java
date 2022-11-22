package org.schabi.newpipe.filter;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.filter.FilterContainer;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import org.schabi.newpipe.fragments.list.search.filter.InjectFilterItem;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InjectFilterItemTest {

    static final String SERVICE_NAME = "YouTube";

    @Test
    public void injectIntoFilterGroupTest() throws ExtractionException {
        final FilterContainer filterContainer = NewPipe.getService(SERVICE_NAME)
                .getSearchQHFactory().getAvailableContentFilter();

        final AtomicInteger itemCount = new AtomicInteger();
        assertFalse(getInjectedFilterItem(filterContainer, itemCount).isPresent());

        InjectDividerTestClass.run(SERVICE_NAME);

        final int expectedInjectedItemPosition = 5;
        final AtomicInteger injectedItemPosition = new AtomicInteger();
        assertTrue(getInjectedFilterItem(filterContainer, injectedItemPosition).isPresent());
        assertTrue(itemCount.get() > injectedItemPosition.get());
        assertEquals(expectedInjectedItemPosition, injectedItemPosition.get());
    }

    @NonNull
    private Optional<FilterItem> getInjectedFilterItem(
            @NonNull final FilterContainer filterContainer,
            @NonNull final AtomicInteger itemCount) {

        return filterContainer.getFilterGroups().stream()
                .map(FilterGroup::getFilterItems)
                .flatMap(Collection::stream)
                .filter(item -> {
                    itemCount.getAndIncrement();
                    return item instanceof InjectFilterItem.DividerItem;
                })
                .findAny();
    }

    public static class InjectDividerTestClass extends InjectFilterItem {

        private static boolean isDividerInjected = false;

        protected InjectDividerTestClass(@NonNull final String serviceName) {
            super(serviceName,
                    YoutubeFilters.ID_CF_MAIN_PLAYLISTS,
                    new DividerItem(0)
            );
        }

        public static void run(final String serviceName) {
            if (!isDividerInjected) {
                new InjectDividerTestClass(serviceName);
            }
        }

        @Override
        protected boolean isAlreadyInjected() {
            return isDividerInjected;
        }

        @Override
        protected void setAsInjected() {
            isDividerInjected = true;
        }
    }
}
