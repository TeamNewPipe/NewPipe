package org.schabi.newpipe.util.text

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.TextView.BufferType
import androidx.core.text.HtmlCompat
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.function.Consumer
import java.util.regex.Pattern

object TextLinkifier {
    val TAG = TextLinkifier::class.java.getSimpleName()

    // Looks for hashtags with characters from any language (\p{L}), numbers, or underscores
    private val HASHTAGS_PATTERN = Pattern.compile("(#[\\p{L}0-9_]+)")
    val SET_LINK_MOVEMENT_METHOD = Consumer<TextView> { v: TextView -> v.movementMethod = LongPressLinkMovementMethod.Companion.getInstance() }

    /**
     * Create links for contents with an [Description] in the various possible formats.
     *
     *
     * This will call one of these three functions based on the format: [.fromHtml],
     * [.fromMarkdown] or [.fromPlainText].
     *
     * @param textView           the TextView to set the htmlBlock linked
     * @param description        the htmlBlock to be linked
     * @param htmlCompatFlag     the int flag to be set if [HtmlCompat.fromHtml]
     * will be called (not used for formats different than HTML)
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     * service
     * @param relatedStreamUrl   if given, used alongside `relatedInfoService` to handle
     * timestamps to open the stream in the popup player at the specific
     * time
     * @param disposables        disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use [                           ][.SET_LINK_MOVEMENT_METHOD] to make links clickable and focusable
     */
    fun fromDescription(textView: TextView,
                        description: Description,
                        htmlCompatFlag: Int,
                        relatedInfoService: StreamingService?,
                        relatedStreamUrl: String?,
                        disposables: CompositeDisposable,
                        onCompletion: Consumer<TextView?>?) {
        when (description.type) {
            Description.HTML -> fromHtml(textView, description.content, htmlCompatFlag,
                    relatedInfoService, relatedStreamUrl, disposables, onCompletion)

            Description.MARKDOWN -> fromMarkdown(textView, description.content,
                    relatedInfoService, relatedStreamUrl, disposables, onCompletion)

            Description.PLAIN_TEXT -> fromPlainText(textView, description.content,
                    relatedInfoService, relatedStreamUrl, disposables, onCompletion)

            else -> fromPlainText(textView, description.content,
                    relatedInfoService, relatedStreamUrl, disposables, onCompletion)
        }
    }

    /**
     * Create links for contents with an HTML description.
     *
     *
     *
     * This method will call [.changeLinkIntents] after having linked the URLs with
     * [HtmlCompat.fromHtml].
     *
     *
     * @param textView           the [TextView] to set the HTML string block linked
     * @param htmlBlock          the HTML string block to be linked
     * @param htmlCompatFlag     the int flag to be set when [HtmlCompat.fromHtml] will be called
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     * service
     * @param relatedStreamUrl   if given, used alongside `relatedInfoService` to handle
     * timestamps to open the stream in the popup player at the specific
     * time
     * @param disposables        disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use [                           ][.SET_LINK_MOVEMENT_METHOD] to make links clickable and focusable
     */
    fun fromHtml(textView: TextView,
                 htmlBlock: String,
                 htmlCompatFlag: Int,
                 relatedInfoService: StreamingService?,
                 relatedStreamUrl: String?,
                 disposables: CompositeDisposable,
                 onCompletion: Consumer<TextView?>?) {
        changeLinkIntents(
                textView, HtmlCompat.fromHtml(htmlBlock, htmlCompatFlag), relatedInfoService,
                relatedStreamUrl, disposables, onCompletion)
    }

    /**
     * Create links for contents with a plain text description.
     *
     *
     *
     * This method will call [.changeLinkIntents] after having linked the URLs with
     * [TextView.setAutoLinkMask] and
     * [TextView.setText].
     *
     *
     * @param textView           the [TextView] to set the plain text block linked
     * @param plainTextBlock     the block of plain text to be linked
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     * service
     * @param relatedStreamUrl   if given, used alongside `relatedInfoService` to handle
     * timestamps to open the stream in the popup player at the specific
     * time
     * @param disposables        disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use [                           ][.SET_LINK_MOVEMENT_METHOD] to make links clickable and focusable
     */
    fun fromPlainText(textView: TextView,
                      plainTextBlock: String,
                      relatedInfoService: StreamingService?,
                      relatedStreamUrl: String?,
                      disposables: CompositeDisposable,
                      onCompletion: Consumer<TextView?>?) {
        textView.autoLinkMask = Linkify.WEB_URLS
        textView.setText(plainTextBlock, BufferType.SPANNABLE)
        changeLinkIntents(textView, textView.getText(), relatedInfoService,
                relatedStreamUrl, disposables, onCompletion)
    }

    /**
     * Create links for contents with a markdown description.
     *
     *
     *
     * This method will call [.changeLinkIntents] after creating a [Markwon] object and using
     * [Markwon.setMarkdown].
     *
     *
     * @param textView           the [TextView] to set the plain text block linked
     * @param markdownBlock      the block of markdown text to be linked
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     * service
     * @param relatedStreamUrl   if given, used alongside `relatedInfoService` to handle
     * timestamps to open the stream in the popup player at the specific
     * time
     * @param disposables        disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use [                           ][.SET_LINK_MOVEMENT_METHOD] to make links clickable and focusable
     */
    fun fromMarkdown(textView: TextView,
                     markdownBlock: String,
                     relatedInfoService: StreamingService?,
                     relatedStreamUrl: String?,
                     disposables: CompositeDisposable,
                     onCompletion: Consumer<TextView?>?) {
        val markwon = Markwon.builder(textView.context)
                .usePlugin(LinkifyPlugin.create()).build()
        changeLinkIntents(textView, markwon.toMarkdown(markdownBlock),
                relatedInfoService, relatedStreamUrl, disposables, onCompletion)
    }

    /**
     * Change links generated by libraries in the description of a content to a custom link action
     * and add click listeners on timestamps in this description.
     *
     *
     *
     * Instead of using an [android.content.Intent.ACTION_VIEW] intent in the description of
     * a content, this method will parse the [CharSequence] and replace all current web links
     * with [ShareUtils.openUrlInBrowser].
     *
     *
     *
     *
     * This method will also add click listeners on timestamps in this description, which will play
     * the content in the popup player at the time indicated in the timestamp, by using
     * [TextLinkifier.addClickListenersOnTimestamps] method and click listeners on hashtags, by
     * using [TextLinkifier.addClickListenersOnHashtags], which will open a search on the current service with the hashtag.
     *
     *
     *
     *
     * This method is required in order to intercept links and e.g. show a confirmation dialog
     * before opening a web link.
     *
     *
     * @param textView           the [TextView] to which the converted [CharSequence]
     * will be applied
     * @param chars              the [CharSequence] to be parsed
     * @param relatedInfoService if given, handle hashtags to search for the term in the correct
     * service
     * @param relatedStreamUrl   if given, used alongside `relatedInfoService` to handle
     * timestamps to open the stream in the popup player at the specific
     * time
     * @param disposables        disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     * @param onCompletion       will be run when setting text to the textView completes; use [                           ][.SET_LINK_MOVEMENT_METHOD] to make links clickable and focusable
     */
    private fun changeLinkIntents(textView: TextView,
                                  chars: CharSequence,
                                  relatedInfoService: StreamingService?,
                                  relatedStreamUrl: String?,
                                  disposables: CompositeDisposable,
                                  onCompletion: Consumer<TextView?>?) {
        disposables.add(Single.fromCallable {
            val context = textView.context

            // add custom click actions on web links
            val textBlockLinked = SpannableStringBuilder(chars)
            val urls = textBlockLinked.getSpans(0, chars.length,
                    URLSpan::class.java)
            for (span in urls) {
                val url = span.url
                val longPressClickableSpan: LongPressClickableSpan = UrlLongPressClickableSpan(context, disposables, url)
                textBlockLinked.setSpan(longPressClickableSpan,
                        textBlockLinked.getSpanStart(span),
                        textBlockLinked.getSpanEnd(span),
                        textBlockLinked.getSpanFlags(span))
                textBlockLinked.removeSpan(span)
            }

            // add click actions on plain text timestamps only for description of contents,
            // unneeded for meta-info or other TextViews
            if (relatedInfoService != null) {
                if (relatedStreamUrl != null) {
                    addClickListenersOnTimestamps(context, textBlockLinked,
                            relatedInfoService, relatedStreamUrl, disposables)
                }
                addClickListenersOnHashtags(context, textBlockLinked, relatedInfoService)
            }
            textBlockLinked
        }.subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { textBlockLinked: SpannableStringBuilder? -> setTextViewCharSequence(textView, textBlockLinked, onCompletion) }
                ) { throwable: Throwable? ->
                    Log.e(TAG, "Unable to linkify text", throwable)
                    // this should never happen, but if it does, just fallback to it
                    setTextViewCharSequence(textView, chars, onCompletion)
                })
    }

    /**
     * Add click listeners which opens a search on hashtags in a plain text.
     *
     *
     *
     * This method finds all timestamps in the [SpannableStringBuilder] of the description
     * using a regular expression, adds for each a [LongPressClickableSpan] which opens
     * [NavigationHelper.openSearch] and makes a search on the hashtag,
     * in the service of the content when pressed, and copy the hashtag to clipboard when
     * long-pressed, if allowed by the caller method (parameter `addLongClickCopyListener`).
     *
     *
     * @param context              the [Context] to use
     * @param spannableDescription the [SpannableStringBuilder] with the text of the
     * content description
     * @param relatedInfoService   used to search for the term in the correct service
     */
    private fun addClickListenersOnHashtags(
            context: Context,
            spannableDescription: SpannableStringBuilder,
            relatedInfoService: StreamingService) {
        val descriptionText = spannableDescription.toString()
        val hashtagsMatches = HASHTAGS_PATTERN.matcher(descriptionText)
        while (hashtagsMatches.find()) {
            val hashtagStart = hashtagsMatches.start(1)
            val hashtagEnd = hashtagsMatches.end(1)
            val parsedHashtag = descriptionText.substring(hashtagStart, hashtagEnd)

            // Don't add a LongPressClickableSpan if there is already one, which should be a part
            // of an URL, already parsed before
            if (spannableDescription.getSpans<LongPressClickableSpan>(hashtagStart, hashtagEnd,
                            LongPressClickableSpan::class.java).size == 0) {
                val serviceId = relatedInfoService.serviceId
                spannableDescription.setSpan(
                        HashtagLongPressClickableSpan(context, parsedHashtag, serviceId),
                        hashtagStart, hashtagEnd, 0)
            }
        }
    }

    /**
     * Add click listeners which opens the popup player on timestamps in a plain text.
     *
     *
     *
     * This method finds all timestamps in the [SpannableStringBuilder] of the description
     * using a regular expression, adds for each a [LongPressClickableSpan] which opens the
     * popup player at the time indicated in the timestamps and copy the timestamp in clipboard
     * when long-pressed.
     *
     *
     * @param context              the [Context] to use
     * @param spannableDescription the [SpannableStringBuilder] with the text of the
     * content description
     * @param relatedInfoService   the service of the `relatedStreamUrl`
     * @param relatedStreamUrl     what to open in the popup player when timestamps are clicked
     * @param disposables          disposables created by the method are added here and their
     * lifecycle should be handled by the calling class
     */
    private fun addClickListenersOnTimestamps(
            context: Context,
            spannableDescription: SpannableStringBuilder,
            relatedInfoService: StreamingService,
            relatedStreamUrl: String,
            disposables: CompositeDisposable) {
        val descriptionText = spannableDescription.toString()
        val timestampsMatches = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(
                descriptionText)
        while (timestampsMatches.find()) {
            val timestampMatchDTO = TimestampExtractor.getTimestampFromMatcher(timestampsMatches, descriptionText)
                    ?: continue
            spannableDescription.setSpan(
                    TimestampLongPressClickableSpan(context, descriptionText, disposables,
                            relatedInfoService, relatedStreamUrl, timestampMatchDTO),
                    timestampMatchDTO.timestampStart(),
                    timestampMatchDTO.timestampEnd(),
                    0)
        }
    }

    private fun setTextViewCharSequence(textView: TextView,
                                        charSequence: CharSequence?,
                                        onCompletion: Consumer<TextView?>?) {
        textView.text = charSequence
        textView.visibility = View.VISIBLE
        onCompletion?.accept(textView)
    }
}
