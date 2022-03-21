package org.schabi.newpipe.util.external_communication;

import static org.schabi.newpipe.util.external_communication.InternalUrlsHandler.playOnPopup;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
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
import org.schabi.newpipe.views.LongPressClickableSpan;
import org.schabi.newpipe.views.LongPressLinkMovementMethod;

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
    private static final Pattern HASHTAGS_PATTERN =
            Pattern.compile("(#[\\p{L}0-9_]+)");

    private TextLinkifier() {
    }

    /**
     * Create web links for contents with an HTML description.
     *
     * <p>
     * This method will call {@link TextLinkifier#changeIntentsOfDescriptionLinks(TextView,
     * CharSequence, Info, boolean, CompositeDisposable)} after having linked the URLs with
     * {@link HtmlCompat#fromHtml(String, int)}.
     * </p>
     *
     * @param textView                    the TextView to set the htmlBlock linked
     * @param htmlBlock                   the htmlBlock to be linked
     * @param htmlCompatFlag              the int flag to be set when
     *                                    {@link HtmlCompat#fromHtml(String, int)} will be called
     * @param relatedInfo                 if given, handle timestamps to open the stream in the
     *                                    popup player at the specific time, and hashtags to search
     *                                    for the term in the correct service
     * @param addLongPressListenerToLinks whether to set a long click listener which will open an
     *                                    alert dialog when links and/or hashtags are clicked
     * @param disposables                 disposables created by the method are added here and
     *                                    their lifecycle should be handled by the calling class
     */
    public static void createLinksFromHtmlBlock(@NonNull final TextView textView,
                                                final String htmlBlock,
                                                final int htmlCompatFlag,
                                                @Nullable final Info relatedInfo,
                                                final boolean addLongPressListenerToLinks,
                                                final CompositeDisposable disposables) {
        changeIntentsOfDescriptionLinks(textView, HtmlCompat.fromHtml(htmlBlock, htmlCompatFlag),
                relatedInfo, addLongPressListenerToLinks, disposables);
    }

    /**
     * Create web links for contents with a plain text description.
     *
     * <p>
     * This method will call {@link TextLinkifier#changeIntentsOfDescriptionLinks(TextView,
     * CharSequence, Info, boolean, CompositeDisposable)} after having linked the URLs with
     * {@link TextView#setAutoLinkMask(int)} and {@link TextView#setText(CharSequence,
     * TextView.BufferType)}.
     * </p>
     *
     * @param textView                    the TextView to set the plain text block linked
     * @param plainTextBlock              the block of plain text to be linked
     * @param relatedInfo                 if given, handle timestamps to open the stream in the
     *                                    popup player at the specific time, and hashtags to search
     *                                    for the term in the correct service
     * @param addLongPressListenerToLinks whether to set a long click listener which will open an
     *                                    alert dialog when links and/or hashtags are clicked
     * @param disposables                 disposables created by the method are added here and
     *                                    their lifecycle should be handled by the calling class
     */
    public static void createLinksFromPlainText(@NonNull final TextView textView,
                                                final String plainTextBlock,
                                                @Nullable final Info relatedInfo,
                                                final boolean addLongPressListenerToLinks,
                                                final CompositeDisposable disposables) {
        textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setText(plainTextBlock, TextView.BufferType.SPANNABLE);
        changeIntentsOfDescriptionLinks(textView, textView.getText(), relatedInfo,
                addLongPressListenerToLinks, disposables);
    }

    /**
     * Create web links for contents with a markdown description.
     *
     * <p>
     * This method will call {@link TextLinkifier#changeIntentsOfDescriptionLinks(TextView,
     * CharSequence, Info, boolean, CompositeDisposable)} after creating an {@link Markwon} object
     * and using {@link Markwon#setMarkdown(TextView, String)}.
     * </p>
     *
     * @param textView                    the TextView to set the plain text block linked
     * @param markdownBlock               the block of markdown text to be linked
     * @param relatedInfo                 if given, handle timestamps to open the stream in the
     *                                    popup player at the specific time, and hashtags to search
     *                                    for the term in the correct service
     * @param addLongPressListenerToLinks whether to set a long click listener which will open an
     *                                    alert dialog when links and/or hashtags are clicked
     * @param disposables                 disposables created by the method are added here and
     *                                    their lifecycle should be handled by the calling class
     */
    public static void createLinksFromMarkdownText(@NonNull final TextView textView,
                                                   final String markdownBlock,
                                                   @Nullable final Info relatedInfo,
                                                   final boolean addLongPressListenerToLinks,
                                                   final CompositeDisposable disposables) {
        final Markwon markwon = Markwon.builder(textView.getContext())
                .usePlugin(LinkifyPlugin.create()).build();
        changeIntentsOfDescriptionLinks(textView, markwon.toMarkdown(markdownBlock), relatedInfo,
                addLongPressListenerToLinks, disposables);
    }

    /**
     * Add click listeners which opens a search on hashtags in a plain text.
     * <p>
     * This method finds all timestamps in the {@link SpannableStringBuilder} of the description
     * using a regular expression, adds for each a {@link ClickableSpan} which opens
     * {@link NavigationHelper#openSearch(Context, int, String)} and makes a search on the hashtag,
     * in the service of the content.
     *
     * @param context                     the context to use
     * @param spannableDescription        the SpannableStringBuilder with the text of the
     *                                    content description
     * @param relatedInfo                 used to search for the term in the correct service
     * @param addLongPressListenerToLinks whether to set a long click listener which will open an
     *                                    alert dialog when hashtags are clicked
     */
    private static void addClickListenersOnHashtags(
            final Context context,
            @NonNull final SpannableStringBuilder spannableDescription,
            final Info relatedInfo,
            final boolean addLongPressListenerToLinks) {
        final String descriptionText = spannableDescription.toString();
        final Matcher hashtagsMatches = HASHTAGS_PATTERN.matcher(descriptionText);

        while (hashtagsMatches.find()) {
            final int hashtagStart = hashtagsMatches.start(1);
            final int hashtagEnd = hashtagsMatches.end(1);
            final String parsedHashtag = descriptionText.substring(hashtagStart, hashtagEnd);

            // don't add a ClickableSpan if there is already one, which should be a part of an URL,
            // already parsed before
            final Class<? extends ClickableSpan> clickableSpanClass = addLongPressListenerToLinks
                    ? LongPressClickableSpan.class : ClickableSpan.class;
            if (spannableDescription.getSpans(hashtagStart, hashtagEnd,
                    clickableSpanClass).length == 0) {
                if (addLongPressListenerToLinks) {
                    spannableDescription.setSpan(new LongPressClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View view) {
                            NavigationHelper.openSearch(context, relatedInfo.getServiceId(),
                                    parsedHashtag);
                        }

                        @Override
                        public void onLongClick(@NonNull final View view) {
                            ShareUtils.copyToClipboard(context, parsedHashtag);
                        }
                    }, hashtagStart, hashtagEnd, 0);
                } else {
                    spannableDescription.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View view) {
                            NavigationHelper.openSearch(context, relatedInfo.getServiceId(),
                                    parsedHashtag);
                        }
                    }, hashtagStart, hashtagEnd, 0);
                }
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
     * @param relatedInfo          what to open in the popup player when timestamps are clicked
     * @param disposables          disposables created by the method are added here and their
     *                             lifecycle should be handled by the calling class
     */
    private static void addClickListenersOnTimestamps(final Context context,
                                                      @NonNull final SpannableStringBuilder
                                                              spannableDescription,
                                                      final Info relatedInfo,
                                                      final CompositeDisposable disposables) {
        final String descriptionText = spannableDescription.toString();
        final Matcher timestampsMatches =
                TimestampExtractor.TIMESTAMPS_PATTERN.matcher(descriptionText);

        while (timestampsMatches.find()) {
            final TimestampExtractor.TimestampMatchDTO timestampMatchDTO =
                    TimestampExtractor.getTimestampFromMatcher(
                            timestampsMatches,
                            descriptionText);

            if (timestampMatchDTO == null) {
                continue;
            }

            spannableDescription.setSpan(
                    new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View view) {
                            playOnPopup(context, relatedInfo.getUrl(), relatedInfo.getService(),
                                    timestampMatchDTO.seconds(), disposables);
                        }
                    },
                    timestampMatchDTO.timestampStart(),
                    timestampMatchDTO.timestampEnd(),
                    0);
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
     * {@link TextLinkifier#addClickListenersOnTimestamps(Context, SpannableStringBuilder, Info,
     * CompositeDisposable)} method and click listeners on hashtags, by using
     * {@link TextLinkifier#addClickListenersOnHashtags(Context, SpannableStringBuilder, Info,
     * boolean)})}, which will open a search on the current service with the hashtag.
     * <p>
     * This method is required in order to intercept links and e.g. show a confirmation dialog
     * before opening a web link.
     *
     * @param textView                    the TextView in which the converted CharSequence will be
     *                                    applied
     * @param chars                       the CharSequence to be parsed
     * @param relatedInfo                 if given, handle timestamps to open the stream in the
     *                                    popup player at the specific time, and hashtags to search
     *                                    for the term in the correct service
     * @param addLongPressListenerToLinks whether to set a long click listener which will open an
     *                                    alert dialog when links are clicked
     * @param disposables                 disposables created by the method are added here and
     *                                    their lifecycle should be handled by the calling class
     */
    private static void changeIntentsOfDescriptionLinks(
            @NonNull final TextView textView,
            final CharSequence chars,
            @Nullable final Info relatedInfo,
            final boolean addLongPressListenerToLinks,
            @NonNull final CompositeDisposable disposables) {
        textView.setMovementMethod(LongPressLinkMovementMethod.getInstance());
        disposables.add(Single.fromCallable(() -> {
            final Context context = textView.getContext();

            // add custom click actions on web links
            final SpannableStringBuilder textBlockLinked = new SpannableStringBuilder(chars);
            final URLSpan[] urls = textBlockLinked.getSpans(0, chars.length(), URLSpan.class);

            for (final URLSpan span : urls) {
                final String url = span.getURL();
                final ClickableSpan clickableSpan;
                if (addLongPressListenerToLinks) {
                    clickableSpan = new LongPressClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View view) {
                            if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(
                                    new CompositeDisposable(), context, url)) {
                                ShareUtils.openUrlInBrowser(context, url, false);
                            }
                        }

                        @Override
                        public void onLongClick(@NonNull final View view) {
                            ShareUtils.copyToClipboard(context, url);
                        }
                    };
                } else {
                    clickableSpan = new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View widget) {
                            if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(
                                    new CompositeDisposable(), context, url)) {
                                ShareUtils.openUrlInBrowser(context, url, false);
                            }
                        }
                    };
                }

                textBlockLinked.setSpan(clickableSpan, textBlockLinked.getSpanStart(span),
                        textBlockLinked.getSpanEnd(span), textBlockLinked.getSpanFlags(span));
                textBlockLinked.removeSpan(span);
            }

            // add click actions on plain text timestamps only for description of contents,
            // unneeded for meta-info or other TextViews
            if (relatedInfo != null) {
                if (relatedInfo instanceof StreamInfo) {
                    addClickListenersOnTimestamps(context, textBlockLinked, relatedInfo,
                            disposables);
                }
                addClickListenersOnHashtags(context, textBlockLinked, relatedInfo,
                        addLongPressListenerToLinks);
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

    private static void setTextViewCharSequence(@NonNull final TextView textView,
                                                final CharSequence charSequence) {
        textView.setText(charSequence);
        textView.setVisibility(View.VISIBLE);
    }
}
