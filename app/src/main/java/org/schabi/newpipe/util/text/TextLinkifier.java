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

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.function.Consumer;
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

    public static final Consumer<TextView> SET_LINK_MOVEMENT_METHOD =
            v -> v.setMovementMethod(LongPressLinkMovementMethod.getInstance());

    private TextLinkifier() {
    }

    /**
     * Create links for contents with an {@link Description} in the various possible formats.
     * <p>
     * This will call one of these three functions based on the format: {@link #fromHtml},
     * {@link #fromMarkdown} or {@link #fromPlainText}.
     *
     * @param textView           the TextView to set the htmlBlock linked
     * @param description        the htmlBlock to be linked
     * @param htmlCompatFlag     the int flag to be set if {@link HtmlCompat#fromHtml(String, int)}
     *                           will be called (not used for formats different than HTML)
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     *                           service
     * @param relatedStreamUrl   if given, used alongside {@code relatedInfoService} to handle
     *                           timestamps to open the stream in the popup player at the specific
     *                           time
     * @param disposables        disposables created by the method are added here and their
     *                           lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use {@link
     *                           #SET_LINK_MOVEMENT_METHOD} to make links clickable and focusable
     */
    public static void fromDescription(@NonNull final TextView textView,
                                       @NonNull final Description description,
                                       final int htmlCompatFlag,
                                       @Nullable final StreamingService relatedInfoService,
                                       @Nullable final String relatedStreamUrl,
                                       @NonNull final CompositeDisposable disposables,
                                       @Nullable final Consumer<TextView> onCompletion) {
        switch (description.getType()) {
            case Description.HTML:
                TextLinkifier.fromHtml(textView, description.getContent(), htmlCompatFlag,
                        relatedInfoService, relatedStreamUrl, disposables, onCompletion);
                break;
            case Description.MARKDOWN:
                TextLinkifier.fromMarkdown(textView, description.getContent(),
                        relatedInfoService, relatedStreamUrl, disposables, onCompletion);
                break;
            case Description.PLAIN_TEXT: default:
                TextLinkifier.fromPlainText(textView, description.getContent(),
                        relatedInfoService, relatedStreamUrl, disposables, onCompletion);
                break;
        }
    }

    /**
     * Create links for contents with an HTML description.
     *
     * <p>
     * This method will call {@link #changeLinkIntents(TextView, CharSequence, StreamingService,
     * String, CompositeDisposable, Consumer)} after having linked the URLs with
     * {@link HtmlCompat#fromHtml(String, int)}.
     * </p>
     *
     * @param textView           the {@link TextView} to set the the HTML string block linked
     * @param htmlBlock          the HTML string block to be linked
     * @param htmlCompatFlag     the int flag to be set when {@link HtmlCompat#fromHtml(String,
     *                           int)} will be called
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     *                           service
     * @param relatedStreamUrl   if given, used alongside {@code relatedInfoService} to handle
     *                           timestamps to open the stream in the popup player at the specific
     *                           time
     * @param disposables        disposables created by the method are added here and their
     *                           lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use {@link
     *                           #SET_LINK_MOVEMENT_METHOD} to make links clickable and focusable
     */
    public static void fromHtml(@NonNull final TextView textView,
                                @NonNull final String htmlBlock,
                                final int htmlCompatFlag,
                                @Nullable final StreamingService relatedInfoService,
                                @Nullable final String relatedStreamUrl,
                                @NonNull final CompositeDisposable disposables,
                                @Nullable final Consumer<TextView> onCompletion) {
        changeLinkIntents(
                textView, HtmlCompat.fromHtml(htmlBlock, htmlCompatFlag), relatedInfoService,
                relatedStreamUrl, disposables, onCompletion);
    }

    /**
     * Create links for contents with a plain text description.
     *
     * <p>
     * This method will call {@link #changeLinkIntents(TextView, CharSequence, StreamingService,
     * String, CompositeDisposable, Consumer)} after having linked the URLs with
     * {@link TextView#setAutoLinkMask(int)} and
     * {@link TextView#setText(CharSequence, TextView.BufferType)}.
     * </p>
     *
     * @param textView           the {@link TextView} to set the plain text block linked
     * @param plainTextBlock     the block of plain text to be linked
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     *                           service
     * @param relatedStreamUrl   if given, used alongside {@code relatedInfoService} to handle
     *                           timestamps to open the stream in the popup player at the specific
     *                           time
     * @param disposables        disposables created by the method are added here and their
     *                           lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use {@link
     *                           #SET_LINK_MOVEMENT_METHOD} to make links clickable and focusable
     */
    public static void fromPlainText(@NonNull final TextView textView,
                                     @NonNull final String plainTextBlock,
                                     @Nullable final StreamingService relatedInfoService,
                                     @Nullable final String relatedStreamUrl,
                                     @NonNull final CompositeDisposable disposables,
                                     @Nullable final Consumer<TextView> onCompletion) {
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setText(plainTextBlock, TextView.BufferType.SPANNABLE);
        changeLinkIntents(textView, textView.getText(), relatedInfoService,
                relatedStreamUrl, disposables, onCompletion);
    }

    /**
     * Create links for contents with a markdown description.
     *
     * <p>
     * This method will call {@link #changeLinkIntents(TextView, CharSequence, StreamingService,
     * String, CompositeDisposable, Consumer)} after creating a {@link Markwon} object and using
     * {@link Markwon#setMarkdown(TextView, String)}.
     * </p>
     *
     * @param textView           the {@link TextView} to set the plain text block linked
     * @param markdownBlock      the block of markdown text to be linked
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     *                           service
     * @param relatedStreamUrl   if given, used alongside {@code relatedInfoService} to handle
     *                           timestamps to open the stream in the popup player at the specific
     *                           time
     * @param disposables        disposables created by the method are added here and their
     *                           lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use {@link
     *                           #SET_LINK_MOVEMENT_METHOD} to make links clickable and focusable
     */
    public static void fromMarkdown(@NonNull final TextView textView,
                                    @NonNull final String markdownBlock,
                                    @Nullable final StreamingService relatedInfoService,
                                    @Nullable final String relatedStreamUrl,
                                    @NonNull final CompositeDisposable disposables,
                                    @Nullable final Consumer<TextView> onCompletion) {
        final Markwon markwon = Markwon.builder(textView.getContext())
                .usePlugin(LinkifyPlugin.create()).build();
        changeLinkIntents(textView, markwon.toMarkdown(markdownBlock),
                relatedInfoService, relatedStreamUrl, disposables, onCompletion);
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
     * StreamingService, String, CompositeDisposable)} method and click listeners on hashtags, by
     * using {@link TextLinkifier#addClickListenersOnHashtags(Context, SpannableStringBuilder,
     * StreamingService)}, which will open a search on the current service with the hashtag.
     * </p>
     *
     * <p>
     * This method is required in order to intercept links and e.g. show a confirmation dialog
     * before opening a web link.
     * </p>
     *
     * @param textView           the {@link TextView} to which the converted {@link CharSequence}
     *                           will be applied
     * @param chars              the {@link CharSequence} to be parsed
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     *                           service
     * @param relatedStreamUrl   if given, used alongside {@code relatedInfoService} to handle
     *                           timestamps to open the stream in the popup player at the specific
     *                           time
     * @param disposables        disposables created by the method are added here and their
     *                           lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use {@link
     *                           #SET_LINK_MOVEMENT_METHOD} to make links clickable and focusable
     */
    private static void changeLinkIntents(@NonNull final TextView textView,
                                          @NonNull final CharSequence chars,
                                          @Nullable final StreamingService relatedInfoService,
                                          @Nullable final String relatedStreamUrl,
                                          @NonNull final CompositeDisposable disposables,
                                          @Nullable final Consumer<TextView> onCompletion) {
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

                    // add click actions on plain text timestamps only for description of contents,
                    // unneeded for meta-info or other TextViews
                    if (relatedInfoService != null) {
                        if (relatedStreamUrl != null) {
                            addClickListenersOnTimestamps(context, textBlockLinked,
                                    relatedInfoService, relatedStreamUrl, disposables);
                        }
                        addClickListenersOnHashtags(context, textBlockLinked, relatedInfoService);
                    }

                    return textBlockLinked;
                }).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        textBlockLinked ->
                                setTextViewCharSequence(textView, textBlockLinked, onCompletion),
                        throwable -> {
                            Log.e(TAG, "Unable to linkify text", throwable);
                            // this should never happen, but if it does, just fallback to it
                            setTextViewCharSequence(textView, chars, onCompletion);
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
     * @param relatedInfoService   used to search for the term in the correct service
     */
    private static void addClickListenersOnHashtags(
            @NonNull final Context context,
            @NonNull final SpannableStringBuilder spannableDescription,
            @NonNull final StreamingService relatedInfoService) {
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
                final int serviceId = relatedInfoService.getServiceId();
                spannableDescription.setSpan(
                        new HashtagLongPressClickableSpan(context, parsedHashtag, serviceId),
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
     * @param relatedInfoService   the service of the {@code relatedStreamUrl}
     * @param relatedStreamUrl     what to open in the popup player when timestamps are clicked
     * @param disposables          disposables created by the method are added here and their
     *                             lifecycle should be handled by the calling class
     */
    private static void addClickListenersOnTimestamps(
            @NonNull final Context context,
            @NonNull final SpannableStringBuilder spannableDescription,
            @NonNull final StreamingService relatedInfoService,
            @NonNull final String relatedStreamUrl,
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

            spannableDescription.setSpan(
                    new TimestampLongPressClickableSpan(context, descriptionText, disposables,
                            relatedInfoService, relatedStreamUrl, timestampMatchDTO),
                    timestampMatchDTO.timestampStart(),
                    timestampMatchDTO.timestampEnd(),
                    0);
        }
    }

    private static void setTextViewCharSequence(@NonNull final TextView textView,
                                                @Nullable final CharSequence charSequence,
                                                @Nullable final Consumer<TextView> onCompletion) {
        textView.setText(charSequence);
        textView.setVisibility(View.VISIBLE);
        if (onCompletion != null) {
            onCompletion.accept(textView);
        }
    }
}
