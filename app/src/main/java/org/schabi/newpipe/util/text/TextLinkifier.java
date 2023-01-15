package org.schabi.newpipe.util.text;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class TextLinkifier {
    public static final String TAG = TextLinkifier.class.getSimpleName();

    // Looks for hashtags with characters from any language (\p{L}), numbers, or underscores
    private static final Pattern HASHTAGS_PATTERN = Pattern.compile("(#[\\p{L}0-9_]+)");

    private TextLinkifier() {
    }

    /**
     * Create links for contents with an HTML description.
     *
     * <p>
     * This method will call {@link #changeIntentsOfDescriptionLinks(TextView, CharSequence, Info,
     * CompositeDisposable)} after having linked the URLs with
     * {@link HtmlCompat#fromHtml(String, int)}.
     * </p>
     *
     * @param textView       the {@link TextView} to set the the HTML string block linked
     * @param htmlBlock      the HTML string block to be linked
     * @param htmlCompatFlag the int flag to be set when {@link HtmlCompat#fromHtml(String, int)}
     *                       will be called
     * @param relatedInfo    if given, handle timestamps to open the stream in the popup player at
     *                       the specific time, and hashtags to search for the term in the correct
     *                       service
     * @param disposables    disposables created by the method are added here and their lifecycle
     *                       should be handled by the calling class
     */
    public static void createLinksFromHtmlBlock(@NonNull final TextView textView,
                                                @NonNull final String htmlBlock,
                                                final int htmlCompatFlag,
                                                @Nullable final Info relatedInfo,
                                                @NonNull final CompositeDisposable disposables) {
        changeIntentsOfDescriptionLinks(textView, HtmlCompat.fromHtml(htmlBlock, htmlCompatFlag),
                relatedInfo, disposables);
    }

    /**
     * Create links for contents with a plain text description.
     *
     * <p>
     * This method will call {@link #changeIntentsOfDescriptionLinks(TextView, CharSequence, Info,
     * CompositeDisposable)} after having linked the URLs with {@link TextView#setAutoLinkMask(int)}
     * and {@link TextView#setText(CharSequence, TextView.BufferType)}.
     * </p>
     *
     * @param textView       the {@link TextView} to set the plain text block linked
     * @param plainTextBlock the block of plain text to be linked
     * @param relatedInfo    if given, handle timestamps to open the stream in the popup player, at
     *                       the specified time, and hashtags to search for the term in the correct
     *                       service
     * @param disposables    disposables created by the method are added here and their lifecycle
     *                       should be handled by the calling class
     */
    public static void createLinksFromPlainText(@NonNull final TextView textView,
                                                @NonNull final String plainTextBlock,
                                                @Nullable final Info relatedInfo,
                                                @NonNull final CompositeDisposable disposables) {
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setText(plainTextBlock, TextView.BufferType.SPANNABLE);
        changeIntentsOfDescriptionLinks(textView, textView.getText(), relatedInfo, disposables);
    }

    /**
     * Create links for contents with a markdown description.
     *
     * <p>
     * This method will call {@link #changeIntentsOfDescriptionLinks(TextView, CharSequence, Info,
     * CompositeDisposable)} after creating a {@link Markwon} object and using
     * {@link Markwon#setMarkdown(TextView, String)}.
     * </p>
     *
     * @param textView      the {@link TextView} to set the plain text block linked
     * @param markdownBlock the block of markdown text to be linked
     * @param relatedInfo   if given, handle timestamps to open the stream in the popup player at
     *                      the specific time, and hashtags to search for the term in the correct
     *                      service
     * @param disposables   disposables created by the method are added here and their lifecycle
     *                      should be handled by the calling class
     */
    public static void createLinksFromMarkdownText(@NonNull final TextView textView,
                                                   final String markdownBlock,
                                                   @Nullable final Info relatedInfo,
                                                   final CompositeDisposable disposables) {
        final Markwon markwon = Markwon.builder(textView.getContext())
                .usePlugin(LinkifyPlugin.create()).build();
        changeIntentsOfDescriptionLinks(textView, markwon.toMarkdown(markdownBlock), relatedInfo,
                disposables);
    }

    /**
     * Change links generated by libraries in the description of a content to a custom link action
     * and add click listeners on timestamps in this description.
     *
     * <p>
     * Instead of using an {@link android.content.Intent#ACTION_VIEW} intent in the description of
     * a content, this method will parse the {@link CharSequence} and replace all current web links
     * with {@link ShareUtils#openUrlInBrowser(Context, String, boolean)}.
     * </p>
     *
     * <p>
     * This method will also add click listeners on timestamps in this description, which will play
     * the content in the popup player at the time indicated in the timestamp, by using
     * {@link TextLinkifier#addClickListenersOnTimestamps(Context, SpannableStringBuilder,
     * StreamInfo, CompositeDisposable)} method and click listeners on hashtags, by using
     * {@link TextLinkifier#addClickListenersOnHashtags(Context, SpannableStringBuilder, Info)})},
     * which will open a search on the current service with the hashtag.
     * </p>
     *
     * <p>
     * This method is required in order to intercept links and e.g. show a confirmation dialog
     * before opening a web link.
     * </p>
     *
     * @param textView    the {@link TextView} in which the converted {@link CharSequence} will be
     *                    applied
     * @param chars       the {@link CharSequence} to be parsed
     * @param relatedInfo if given, handle timestamps to open the stream in the popup player at the
     *                    specific time, and hashtags to search for the term in the correct service
     * @param disposables disposables created by the method are added here and their lifecycle
     *                    should be handled by the calling class
     */
    private static void changeIntentsOfDescriptionLinks(
            @NonNull final TextView textView,
            @NonNull final CharSequence chars,
            @Nullable final Info relatedInfo,
            @NonNull final CompositeDisposable disposables) {
        textView.setMovementMethod(LongPressLinkMovementMethod.getInstance());
        disposables.add(Single.fromCallable(() -> {
                    final Context context = textView.getContext();

                    // add custom click actions on web links
                    final SpannableStringBuilder textBlockLinked =
                            new SpannableStringBuilder(chars);
                    final URLSpan[] urls = textBlockLinked.getSpans(0, chars.length(),
                            URLSpan.class);

                    for (final URLSpan span : urls) {
                        final String url = span.getURL();
                        final LongPressClickableSpan longPressClickableSpan =
                                new UrlLongPressClickableSpan(context, disposables, url);

                        textBlockLinked.setSpan(longPressClickableSpan,
                                textBlockLinked.getSpanStart(span),
                                textBlockLinked.getSpanEnd(span),
                                textBlockLinked.getSpanFlags(span));
                        textBlockLinked.removeSpan(span);
                    }

                    if (relatedInfo != null) {
                        // add click actions on plain text timestamps only for description of
                        // contents, unneeded for meta-info or other TextViews
                        if (relatedInfo instanceof StreamInfo) {
                            addClickListenersOnTimestamps(context, textBlockLinked,
                                    (StreamInfo) relatedInfo, disposables);
                        }

                        addClickListenersOnHashtags(context, textBlockLinked, relatedInfo);
                    }

                    return textBlockLinked;
                }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        textBlockLinked -> setTextViewCharSequence(textView, textBlockLinked),
                        throwable -> {
                            Log.e(TAG, "Unable to linkify text", throwable);
                            // this should never happen, but if it does, just fallback to it
                            setTextViewCharSequence(textView, chars);
                        }));
    }

    /**
     * Add click listeners which opens a search on hashtags in a plain text.
     *
     * <p>
     * This method finds all timestamps in the {@link SpannableStringBuilder} of the description
     * using a regular expression, adds for each a {@link LongPressClickableSpan} which opens
     * {@link NavigationHelper#openSearch(Context, int, String)} and makes a search on the hashtag,
     * in the service of the content when pressed, and copy the hashtag to clipboard when
     * long-pressed, if allowed by the caller method (parameter {@code addLongClickCopyListener}).
     * </p>
     *
     * @param context              the {@link Context} to use
     * @param spannableDescription the {@link SpannableStringBuilder} with the text of the
     *                             content description
     * @param relatedInfo          used to search for the term in the correct service
     */
    private static void addClickListenersOnHashtags(
            @NonNull final Context context,
            @NonNull final SpannableStringBuilder spannableDescription,
            @NonNull final Info relatedInfo) {
        final String descriptionText = spannableDescription.toString();
        final Matcher hashtagsMatches = HASHTAGS_PATTERN.matcher(descriptionText);

        while (hashtagsMatches.find()) {
            final int hashtagStart = hashtagsMatches.start(1);
            final int hashtagEnd = hashtagsMatches.end(1);
            final String parsedHashtag = descriptionText.substring(hashtagStart, hashtagEnd);

            // Don't add a LongPressClickableSpan if there is already one, which should be a part
            // of an URL, already parsed before
            if (spannableDescription.getSpans(hashtagStart, hashtagEnd,
                    LongPressClickableSpan.class).length == 0) {
                spannableDescription.setSpan(
                        new HashtagLongPressClickableSpan(context, parsedHashtag, relatedInfo),
                        hashtagStart, hashtagEnd, 0);
            }
        }
    }

    /**
     * Add click listeners which opens the popup player on timestamps in a plain text.
     *
     * <p>
     * This method finds all timestamps in the {@link SpannableStringBuilder} of the description
     * using a regular expression, adds for each a {@link LongPressClickableSpan} which opens the
     * popup player at the time indicated in the timestamps and copy the timestamp in clipboard
     * when long-pressed.
     * </p>
     *
     * @param context              the {@link Context} to use
     * @param spannableDescription the {@link SpannableStringBuilder} with the text of the
     *                             content description
     * @param streamInfo           what to open in the popup player when timestamps are clicked
     * @param disposables          disposables created by the method are added here and their
     *                             lifecycle should be handled by the calling class
     */
    private static void addClickListenersOnTimestamps(
            @NonNull final Context context,
            @NonNull final SpannableStringBuilder spannableDescription,
            @NonNull final StreamInfo streamInfo,
            @NonNull final CompositeDisposable disposables) {
        final String descriptionText = spannableDescription.toString();
        final Matcher timestampsMatches = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(
                descriptionText);

        while (timestampsMatches.find()) {
            final TimestampExtractor.TimestampMatchDTO timestampMatchDTO =
                    TimestampExtractor.getTimestampFromMatcher(timestampsMatches, descriptionText);

            if (timestampMatchDTO == null) {
                continue;
            }

            spannableDescription.setSpan(new TimestampLongPressClickableSpan(
                    context, descriptionText, disposables, streamInfo, timestampMatchDTO),
                    timestampMatchDTO.timestampStart(),
                    timestampMatchDTO.timestampEnd(),
                    0);
        }
    }

    private static void setTextViewCharSequence(@NonNull final TextView textView,
                                                @Nullable final CharSequence charSequence) {
        textView.setText(charSequence);
        textView.setVisibility(View.VISIBLE);
    }
}
