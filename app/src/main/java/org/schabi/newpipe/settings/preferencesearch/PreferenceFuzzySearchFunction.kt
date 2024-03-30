package org.schabi.newpipe.settings.preferencesearch

import android.text.TextUtils
import android.util.Pair
import org.apache.commons.text.similarity.FuzzyScore
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchConfiguration.PreferenceSearchFunction
import java.util.Locale
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.ToDoubleFunction
import java.util.stream.Collectors
import java.util.stream.Stream

class PreferenceFuzzySearchFunction() : PreferenceSearchFunction {
    public override fun search(
            allAvailable: Stream<PreferenceSearchItem?>,
            keyword: String
    ): Stream<PreferenceSearchItem?> {
        val maxScore: Int = (keyword.length + 1) * 3 - 2 // First can't get +2 bonus score
        return allAvailable // General search
                // Check all fields if anyone contains something that kind of matches the keyword
                .map(Function({ item: PreferenceSearchItem? -> FuzzySearchGeneralDTO(item, keyword) }))
                .filter(Predicate({ dto: FuzzySearchGeneralDTO -> dto.getScore() / maxScore >= 0.3f }))
                .map(Function({ obj: FuzzySearchGeneralDTO -> obj.getItem() })) // Specific search - Used for determining order of search results
                // Calculate a score based on specific search fields
                .map(Function({ item: PreferenceSearchItem? -> FuzzySearchSpecificDTO(item, keyword) }))
                .sorted(Comparator.comparingDouble(ToDoubleFunction({ obj: FuzzySearchSpecificDTO -> obj.getScore() })).reversed())
                .map(Function({ obj: FuzzySearchSpecificDTO -> obj.getItem() })) // Limit the amount of search results
                .limit(20)
    }

    internal class FuzzySearchGeneralDTO(
            private val item: PreferenceSearchItem?,
            keyword: String?) {
        private val score: Float

        init {
            score = FUZZY_SCORE.fuzzyScore(
                    TextUtils.join(";", item!!.getAllRelevantSearchFields()),
                    keyword).toFloat()
        }

        fun getItem(): PreferenceSearchItem? {
            return item
        }

        fun getScore(): Float {
            return score
        }
    }

    internal class FuzzySearchSpecificDTO(private val item: PreferenceSearchItem?, keyword: String?) {
        private val score: Double

        init {
            score = WEIGHT_MAP.entries.stream()
                    .map(Function({ entry: Map.Entry<Function<PreferenceSearchItem?, String>, Float> -> Pair(entry.key.apply(item), entry.value) }))
                    .filter(Predicate({ pair: Pair<String, Float> -> !pair.first.isEmpty() }))
                    .collect(Collectors.averagingDouble(ToDoubleFunction({ pair: Pair<String, Float> -> (FUZZY_SCORE.fuzzyScore(pair.first, keyword) * pair.second).toDouble() })))
        }

        fun getItem(): PreferenceSearchItem? {
            return item
        }

        fun getScore(): Double {
            return score
        }

        companion object {
            private val WEIGHT_MAP: Map<Function<PreferenceSearchItem?, String>, Float> = java.util.Map.of(Function({ obj: PreferenceSearchItem? -> obj!!.getTitle() }), 1.5f, Function({ obj: PreferenceSearchItem? -> obj!!.getSummary() }), 1f, Function({ obj: PreferenceSearchItem? -> obj!!.getEntries() }), 1f
            )
        }
    }

    companion object {
        private val FUZZY_SCORE: FuzzyScore = FuzzyScore(Locale.ROOT)
    }
}
