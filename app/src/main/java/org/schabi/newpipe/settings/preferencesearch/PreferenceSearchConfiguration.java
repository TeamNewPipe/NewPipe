package org.schabi.newpipe.settings.preferencesearch;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.XmlRes;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class PreferenceSearchConfiguration {
    private final ArrayList<SearchIndexItem> itemsToIndex = new ArrayList<>();

    private BinaryOperator<String> breadcrumbConcat =
            (s1, s2) -> TextUtils.isEmpty(s1) ? s2 : (s1 + " > " + s2);

    private PreferenceSearchFunction searcher = new PreferenceFuzzySearchFunction();

    private final List<String> parserIgnoreElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName());
    private final List<String> parserContainerElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName(),
            PreferenceScreen.class.getSimpleName());


    public void setBreadcrumbConcat(final BinaryOperator<String> breadcrumbConcat) {
        this.breadcrumbConcat = Objects.requireNonNull(breadcrumbConcat);
    }

    public void setSearcher(final PreferenceSearchFunction searcher) {
        this.searcher = Objects.requireNonNull(searcher);
    }

    /**
     * Adds a new file to the index.
     *
     * @param resId The preference file to index
     * @return SearchIndexItem
     */
    public SearchIndexItem index(@XmlRes final int resId) {
        final SearchIndexItem item = new SearchIndexItem(resId, this);
        itemsToIndex.add(item);
        return item;
    }

    public List<SearchIndexItem> getFiles() {
        return itemsToIndex;
    }

    public BinaryOperator<String> getBreadcrumbConcat() {
        return breadcrumbConcat;
    }

    public PreferenceSearchFunction getSearchMatcher() {
        return searcher;
    }

    public List<String> getParserIgnoreElements() {
        return parserIgnoreElements;
    }

    public List<String> getParserContainerElements() {
        return parserContainerElements;
    }

    /**
     * Adds a given R.xml resource to the search index.
     */
    public static final class SearchIndexItem implements Parcelable {
        private String breadcrumb = "";
        @XmlRes
        private final int resId;
        private final PreferenceSearchConfiguration searchConfiguration;

        /**
         * Includes the given R.xml resource in the index.
         *
         * @param resId               The resource to index
         * @param searchConfiguration The configuration for the search
         */
        private SearchIndexItem(
                @XmlRes final int resId,
                final PreferenceSearchConfiguration searchConfiguration
        ) {
            this.resId = resId;
            this.searchConfiguration = searchConfiguration;
        }

        /**
         * Adds a breadcrumb.
         *
         * @param breadcrumb The breadcrumb to add
         * @return For chaining
         */
        @SuppressWarnings("HiddenField")
        public SearchIndexItem withBreadcrumb(final String breadcrumb) {
            this.breadcrumb =
                    searchConfiguration.getBreadcrumbConcat().apply(this.breadcrumb, breadcrumb);
            return this;
        }

        @XmlRes
        int getResId() {
            return resId;
        }

        String getBreadcrumb() {
            return breadcrumb;
        }

        public static final Creator<SearchIndexItem> CREATOR = new Creator<>() {
            @Override
            public SearchIndexItem createFromParcel(final Parcel in) {
                return new SearchIndexItem(in);
            }

            @Override
            public SearchIndexItem[] newArray(final int size) {
                return new SearchIndexItem[size];
            }
        };

        private SearchIndexItem(final Parcel parcel) {
            this.breadcrumb = parcel.readString();
            this.resId = parcel.readInt();
            this.searchConfiguration = null;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(this.breadcrumb);
            dest.writeInt(this.resId);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    @FunctionalInterface
    public interface PreferenceSearchFunction {
        Stream<PreferenceSearchItem> search(
                Stream<PreferenceSearchItem> allAvailable,
                String keyword);
    }
}
