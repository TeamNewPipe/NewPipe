package org.schabi.newpipe.learning;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.learning.dao.LearningSessionDAO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Lightweight Learning Mode dashboard for PipePlayClient.
 *
 * This intentionally ports the first, low-risk dashboard slice: study-time statistics backed by
 * the local learning session table. Rich WizeStream dashboard sections (learning content marking,
 * continue-learning lists, recent notes, and playlist cards) are left as follow-up slices because
 * they depend on additional source tables and player/detail UI hooks.
 */
public class LearningDashboardFragment extends Fragment {
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView statusView;
    private TextView todayView;
    private TextView weekView;
    private TextView totalView;
    private ProgressBar weekProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final ScrollView scrollView = new ScrollView(requireContext());
        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(padding, padding, padding, padding);

        final LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView title = text(R.string.learning_dashboard_title, 22, true);
        content.addView(title);

        statusView = text(R.string.learning_dashboard_empty, 14, false);
        statusView.setPadding(0, padding / 2, 0, padding);
        content.addView(statusView);

        todayView = text("", 18, true);
        weekView = text("", 18, true);
        totalView = text("", 18, true);
        weekProgress = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        weekProgress.setMax(100);

        content.addView(todayView);
        content.addView(weekView);
        content.addView(totalView);
        content.addView(weekProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView hint = text(R.string.learning_dashboard_foundation_hint, 14, false);
        hint.setPadding(0, padding, 0, 0);
        content.addView(hint);

        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        observeStatistics();
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        statusView = null;
        todayView = null;
        weekView = null;
        totalView = null;
        weekProgress = null;
        super.onDestroyView();
    }

    private TextView text(final int stringRes, final int sp, final boolean bold) {
        return text(getString(stringRes), sp, bold);
    }

    private TextView text(final String value, final int sp, final boolean bold) {
        final TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setGravity(Gravity.START);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private void observeStatistics() {
        final LearningSessionDAO dao = NewPipeDatabase.getInstance(requireContext()).learningSessionDAO();
        final LocalDate today = LocalDate.now();
        final LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        disposables.add(Flowable.combineLatest(
                        dao.observeWatchMillisForDate(today.toString()),
                        dao.observeWatchMillisBetween(weekStart.toString(), today.toString()),
                        dao.observeTotalWatchMillis(),
                        LearningStats::new)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showStats, throwable -> {
                    if (statusView != null) {
                        statusView.setText(R.string.learning_dashboard_error);
                    }
                }));
    }

    private void showStats(final LearningStats stats) {
        if (statusView == null) {
            return;
        }
        final boolean hasActivity = stats.todayMillis > 0 || stats.weekMillis > 0
                || stats.totalMillis > 0;
        statusView.setText(hasActivity
                ? R.string.learning_dashboard_summary_title
                : R.string.learning_dashboard_empty);
        todayView.setText(getString(R.string.learning_statistics_today,
                formatDuration(stats.todayMillis)));
        weekView.setText(getString(R.string.learning_statistics_week,
                formatDuration(stats.weekMillis)));
        totalView.setText(getString(R.string.learning_statistics_all_time,
                formatDuration(stats.totalMillis)));
        final long weeklyGoalMillis = TimeUnit.HOURS.toMillis(5);
        weekProgress.setProgress((int) Math.min(100, stats.weekMillis * 100 / weeklyGoalMillis));
    }

    private static String formatDuration(final long millis) {
        final long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0, millis));
        final long hours = totalMinutes / 60;
        final long minutes = totalMinutes % 60;
        return String.format(Locale.getDefault(), "%d:%02d", hours, minutes);
    }

    private static final class LearningStats {
        final long todayMillis;
        final long weekMillis;
        final long totalMillis;

        LearningStats(final long todayMillis, final long weekMillis, final long totalMillis) {
            this.todayMillis = todayMillis;
            this.weekMillis = weekMillis;
            this.totalMillis = totalMillis;
        }
    }
}
