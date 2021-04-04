package org.schabi.newpipe.util.external_communication;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.schabi.newpipe.util.external_communication.InternalUrlsHandler.playOnPopup;

public final class TextLinkifier {
    public static final String TAG = TextLinkifier.class.getSimpleName();
    private static final Pattern HASHTAGS_PATTERN = Pattern.compile("(#[A-Za-z0-9_]+)");
    private static final Pattern TIMESTAMPS_PATTERN = Pattern.compile(
            "(?:^|(?!:)\\W)(?:([0-5]?[0-9]):)?([0-5]?[0-9]):([0-5][0-9])(?=$|(?!:)\\W)");

    private TextLinkifier() {
    }

    /**
     * Create web links for contents with an HTML description.
     * <p>
     * This will call
     * {@link TextLinkifier#changeIntentsOfDescriptionLinks(Context, CharSequence, TextView,
     * StreamingService, String)}
     * after having linked the URLs with {@link HtmlCompat#fromHtml(String, int)}.
     *
     * @param context          the context to use
     * @param htmlBlock        the htmlBlock to be linked
     * @param textView         the TextView to set the htmlBlock linked
     * @param streamingService the {@link StreamingService} of the content
     * @param contentUrl       the URL of the content
     * @param htmlCompatFlag   the int flag to be set when {@link HtmlCompat#fromHtml(String, int)}
     *                         will be called
     * @return a disposable to be stored somewhere and disposed when activity/fragment is destroyed
     */
    public static Disposable createLinksFromHtmlBlock(final Context context,
                                                      final String htmlBlock,
                                                      final TextView textView,
                                                      final StreamingService streamingService,
                                                      final String contentUrl,
                                                      final int htmlCompatFlag) {
        return changeIntentsOfDescriptionLinks(context,
                HtmlCompat.fromHtml(htmlBlock, htmlCompatFlag), textView, streamingService,
                contentUrl);
    }

    /**
     * Create web links for contents with a plain text description.
     * <p>
     * This will call
     * {@link TextLinkifier#changeIntentsOfDescriptionLinks(Context, CharSequence, TextView,
     * StreamingService, String)}
     * after having linked the URLs with {@link TextView#setAutoLinkMask(int)} and
     * {@link TextView#setText(CharSequence, TextView.BufferType)}.
     *
     * @param context          the context to use
     * @param plainTextBlock   the block of plain text to be linked
     * @param textView         the TextView to set the plain text block linked
     * @param streamingService the {@link StreamingService} of the content
     * @param contentUrl       the URL of the content
     * @return a disposable to be stored somewhere and disposed when activity/fragment is destroyed
     */
    public static Disposable createLinksFromPlainText(final Context context,
                                                      final String plainTextBlock,
                                                      final TextView textView,
                                                      final StreamingService streamingService,
                                                      final String contentUrl) {
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setText(plainTextBlock, TextView.BufferType.SPANNABLE);
        return changeIntentsOfDescriptionLinks(context, textView.getText(), textView,
                streamingService, contentUrl);
    }

    /**
     * Create web links for contents with a markdown description.
     * <p>
     * This will call
     * {@link TextLinkifier#changeIntentsOfDescriptionLinks(Context, CharSequence, TextView,
     * StreamingService, String)}
     * after creating an {@link Markwon} object and using
     * {@link Markwon#setMarkdown(TextView, String)}.
     *
     * @param context          the context to use
     * @param markdownBlock    the block of markdown text to be linked
     * @param textView         the TextView to set the plain text block linked
     * @param streamingService the {@link StreamingService} of the content
     * @param contentUrl       the URL of the content
     * @return a disposable to be stored somewhere and disposed when activity/fragment is destroyed
     */
    public static Disposable createLinksFromMarkdownText(final Context context,
                                                         final String markdownBlock,
                                                         final TextView textView,
                                                         final StreamingService streamingService,
                                                         final String contentUrl) {
        final Markwon markwon = Markwon.builder(context).usePlugin(LinkifyPlugin.create()).build();
        markwon.setMarkdown(textView, markdownBlock);
        return changeIntentsOfDescriptionLinks(context, textView.getText(), textView,
                streamingService, contentUrl);
    }

    /**
     * Add click listeners which opens a search on hashtags in a plain text.
     * <p>
     * This method finds all timestamps in the {@link SpannableStringBuilder} of the description
     * using a regular expression, adds for each a {@link ClickableSpan} which opens
     * {@link NavigationHelper#openSearch(Context, int, String)} and makes a search on the hashtag,
     * in the service of the content.
     *
     * @param context              the context to use
     * @param spannableDescription the SpannableStringBuilder with the text of the
     *                             content description
     * @param streamingService     the {@link StreamingService} of the content
     */
    private static void addClickListenersOnHashtags(final Context context,
                                                    final SpannableStringBuilder
                                                            spannableDescription,
                                                    final StreamingService streamingService) {
        final String descriptionText = spannableDescription.toString();
        final Matcher hashtagsMatches = HASHTAGS_PATTERN.matcher(descriptionText);

        while (hashtagsMatches.find()) {
            final int hashtagStart = hashtagsMatches.start(1);
            final int hashtagEnd = hashtagsMatches.end(1);
            final String parsedHashtag = descriptionText.substring(hashtagStart, hashtagEnd);

            // don't add a ClickableSpan if there is already one, which should be a part of an URL,
            // already parsed before
            if (spannableDescription.getSpans(hashtagStart, hashtagEnd,
                    ClickableSpan.class).length == 0) {
                spannableDescription.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull final View view) {
                        NavigationHelper.openSearch(context, streamingService.getServiceId(),
                                parsedHashtag);
                    }
                }, hashtagStart, hashtagEnd, 0);
            }
        }
    }

    /**
     * Add click listeners which opens the popup player on timestamps in a plain text.
     * <p>
     * This method finds all timestamps in the {@link SpannableStringBuilder} of the description
     * using a regular expression, adds for each a {@link ClickableSpan} which opens the popup
     * player at the time indicated in the timestamps.
     *
     * @param context              the context to use
     * @param spannableDescription the SpannableStringBuilder with the text of the
     *                             content description
     * @param contentUrl           the URL of the content
     * @param streamingService     the {@link StreamingService} of the content
     */
    private static void addClickListenersOnTimestamps(final Context context,
                                                      final SpannableStringBuilder
                                                              spannableDescription,
                                                      final String contentUrl,
                                                      final StreamingService streamingService) {
        final String descriptionText = spannableDescription.toString();
        final Matcher timestampsMatches = TIMESTAMPS_PATTERN.matcher(descriptionText);

        while (timestampsMatches.find()) {
            final int timestampStart = timestampsMatches.start(2);
            final int timestampEnd = timestampsMatches.end(3);
            final String parsedTimestamp = descriptionText.substring(timestampStart, timestampEnd);
            final String[] timestampParts = parsedTimestamp.split(":");
            final int time;

            if (timestampParts.length == 3) { // timestamp format: XX:XX:XX
                time = Integer.parseInt(timestampParts[0]) * 3600 // hours
                        + Integer.parseInt(timestampParts[1]) * 60 // minutes
                        + Integer.parseInt(timestampParts[2]); // seconds
            } else if (timestampParts.length == 2) { // timestamp format: XX:XX
                time = Integer.parseInt(timestampParts[0]) * 60 // minutes
                        + Integer.parseInt(timestampParts[1]); // seconds
            } else {
                continue;
            }

            spannableDescription.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull final View view) {
                    playOnPopup(new CompositeDisposable(), context, contentUrl, streamingService,
                            time);
                }
            }, timestampStart, timestampEnd, 0);
        }
    }

    /**
     * Change links generated by libraries in the description of a content to a custom link action
     * and add click listeners on timestamps in this description.
     * <p>
     * Instead of using an {@link android.content.Intent#ACTION_VIEW} intent in the description of
     * a content, this method will parse the {@link CharSequence} and replace all current web links
     * with {@link ShareUtils#openUrlInBrowser(Context, String, boolean)}.
     * This method will also add click listeners on timestamps in this description, which will play
     * the content in the popup player at the time indicated in the timestamp, by using
     * {@link TextLinkifier#addClickListenersOnTimestamps(Context, SpannableStringBuilder, String,
     * StreamingService)} method and click listeners on hashtags, which will open a search
     * on the current service with the hashtag.
     * <p>
     * This method is required in order to intercept links and e.g. show a confirmation dialog
     * before opening a web link.
     *
     * @param context          the context to use
     * @param chars            the CharSequence to be parsed
     * @param textView         the TextView in which the converted CharSequence will be applied
     * @param streamingService the {@link StreamingService} of the content
     * @param contentUrl       the URL of the content
     * @return a disposable to be stored somewhere and disposed when activity/fragment is destroyed
     */
    private static Disposable changeIntentsOfDescriptionLinks(final Context context,
                                                              final CharSequence chars,
                                                              final TextView textView,
                                                              final StreamingService
                                                                      streamingService,
                                                              final String contentUrl) {
        return Single.fromCallable(() -> {
            // add custom click actions on web links
            final SpannableStringBuilder textBlockLinked = new SpannableStringBuilder(chars);
            final URLSpan[] urls = textBlockLinked.getSpans(0, chars.length(), URLSpan.class);

            for (final URLSpan span : urls) {
                final String url = span.getURL();
                final ClickableSpan clickableSpan = new ClickableSpan() {
                    public void onClick(@NonNull final View view) {
                        if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(
                                new CompositeDisposable(), context, url)) {
                            ShareUtils.openUrlInBrowser(context, url, false);
                        }
                    }
                };

                textBlockLinked.setSpan(clickableSpan, textBlockLinked.getSpanStart(span),
                        textBlockLinked.getSpanEnd(span), textBlockLinked.getSpanFlags(span));
                textBlockLinked.removeSpan(span);
            }

            // add click actions on plain text timestamps only for description of contents,
            // unneeded for metainfo TextViews
            if (contentUrl != null || streamingService != null) {
                addClickListenersOnTimestamps(context, textBlockLinked, contentUrl,
                        streamingService);
                addClickListenersOnHashtags(context, textBlockLinked, streamingService);
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
                        });
    }

    private static void setTextViewCharSequence(final TextView textView,
                                                final CharSequence charSequence) {
        textView.setText(charSequence);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setVisibility(View.VISIBLE);
    }
}
