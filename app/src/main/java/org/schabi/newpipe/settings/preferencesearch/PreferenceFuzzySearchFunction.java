package org.schabi.newpipe.settings.preferencesearch;

import android.text.TextUtils;

import org.apache.commons.text.similarity.FuzzyScore;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class PreferenceFuzzySearchFunction
        implements PreferenceSearchConfiguration.PreferenceSearchFunction {

    private static final FuzzyScore FUZZY_SCORE = new FuzzyScore(Locale.ROOT);

    @Override
    public Stream<PreferenceSearchItem> search(
            final Stream<PreferenceSearchItem> allAvailable,
            final String keyword
    ) {
        final int maxScore = (keyword.length() + 1) * 3 - 2; // First can't get +2 bonus score

        return allAvailable
                // General search
                // Check all fields if anyone contains something that kind of matches the keyword
                .map(item -> new FuzzySearchGeneralDTO(item, keyword))
                .filter(dto -> dto.getScore() / maxScore >= 0.3f)
                .map(FuzzySearchGeneralDTO::getItem)
                // Specific search - Used for determining order of search results
                // Calculate a score based on specific search fields
                .map(item -> new FuzzySearchSpecificDTO(item, keyword))
                .sorted(Comparator.comparing(FuzzySearchSpecificDTO::getScore).reversed())
                .map(FuzzySearchSpecificDTO::getItem)
                // Limit the amount of search results
                .limit(20);
    }

    static class FuzzySearchGeneralDTO {
        private final PreferenceSearchItem item;
        private final float score;

        FuzzySearchGeneralDTO(
                final PreferenceSearchItem item,
                final String keyword) {
            this.item = item;
            this.score = FUZZY_SCORE.fuzzyScore(
                    TextUtils.join(";", item.getAllRelevantSearchFields()),
                    keyword);
        }

        public PreferenceSearchItem getItem() {
            return item;
        }

        public float getScore() {
            return score;
        }
    }

    static class FuzzySearchSpecificDTO {
        private static final Map<Function<PreferenceSearchItem, String>, Float> WEIGHT_MAP = Map.of(
                // The user will most likely look for the title -> prioritize it
                PreferenceSearchItem::getTitle, 1.5f,
                // The summary is also important as it usually contains a larger desc
                // Example: Searching for '4k' → 'show higher resolution' is shown
                PreferenceSearchItem::getSummary, 1f,
                // Entries are also important as they provide all known/possible values
                // Example: Searching where the resolution can be changed to 720p
                PreferenceSearchItem::getEntries, 1f
        );

        private final PreferenceSearchItem item;
        private final float score;

        FuzzySearchSpecificDTO(
                final PreferenceSearchItem item,
                final String keyword) {
            this.item = item;

            float attributeScoreSum = 0;
            int countOfAttributesWithScore = 0;
            for (final Map.Entry<Function<PreferenceSearchItem, String>, Float> we
                    : WEIGHT_MAP.entrySet()) {
                final String valueToProcess = we.getKey().apply(item);
                if (valueToProcess.isEmpty()) {
                    continue;
                }

                attributeScoreSum +=
                        FUZZY_SCORE.fuzzyScore(valueToProcess, keyword) * we.getValue();
                countOfAttributesWithScore++;
            }

            if (countOfAttributesWithScore != 0) {
                this.score = attributeScoreSum / countOfAttributesWithScore;
            } else {
                this.score = 0;
            }
        }

        public PreferenceSearchItem getItem() {
            return item;
        }

        public float getScore() {
            return score;
        }
    }
}
