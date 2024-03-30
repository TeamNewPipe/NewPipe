package org.schabi.newpipe.fragments.detail

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.chip.Chip
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentDescriptionBinding
import org.schabi.newpipe.databinding.ItemMetadataBinding
import org.schabi.newpipe.databinding.ItemMetadataTagsBinding
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.text.TextLinkifier
import java.util.function.Consumer

abstract class BaseDescriptionFragment() : BaseFragment() {
    private val descriptionDisposables: CompositeDisposable = CompositeDisposable()
    protected var binding: FragmentDescriptionBinding? = null
    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        binding = FragmentDescriptionBinding.inflate(inflater, container, false)
        setupDescription()
        setupMetadata(inflater, binding!!.detailMetadataLayout)
        addTagsMetadataItem(inflater, binding!!.detailMetadataLayout)
        return binding!!.getRoot()
    }

    public override fun onDestroy() {
        descriptionDisposables.clear()
        super.onDestroy()
    }

    /**
     * Get the description to display.
     * @return description object, if available
     */
    protected abstract fun getDescription(): Description?

    /**
     * Get the streaming service. Used for generating description links.
     * @return streaming service
     */
    protected abstract fun getService(): StreamingService

    /**
     * Get the streaming service ID. Used for tag links.
     * @return service ID
     */
    protected abstract fun getServiceId(): Int

    /**
     * Get the URL of the described video or audio, used to generate description links.
     * @return stream URL
     */
    protected abstract fun getStreamUrl(): String?

    /**
     * Get the list of tags to display below the description.
     * @return tag list
     */
    abstract fun getTags(): List<String?>

    /**
     * Add additional metadata to display.
     * @param inflater LayoutInflater
     * @param layout detailMetadataLayout
     */
    protected abstract fun setupMetadata(inflater: LayoutInflater?, layout: LinearLayout?)
    private fun setupDescription() {
        val description: Description? = getDescription()
        if (((description == null) || TextUtils.isEmpty(description.getContent())
                        || (description === Description.EMPTY_DESCRIPTION))) {
            binding!!.detailDescriptionView.setVisibility(View.GONE)
            binding!!.detailSelectDescriptionButton.setVisibility(View.GONE)
            return
        }

        // start with disabled state. This also loads description content (!)
        disableDescriptionSelection()
        binding!!.detailSelectDescriptionButton.setOnClickListener(View.OnClickListener({ v: View? ->
            if (binding!!.detailDescriptionNoteView.getVisibility() == View.VISIBLE) {
                disableDescriptionSelection()
            } else {
                // enable selection only when button is clicked to prevent flickering
                enableDescriptionSelection()
            }
        }))
    }

    private fun enableDescriptionSelection() {
        binding!!.detailDescriptionNoteView.setVisibility(View.VISIBLE)
        binding!!.detailDescriptionView.setTextIsSelectable(true)
        val buttonLabel: String = getString(R.string.description_select_disable)
        binding!!.detailSelectDescriptionButton.setContentDescription(buttonLabel)
        TooltipCompat.setTooltipText(binding!!.detailSelectDescriptionButton, buttonLabel)
        binding!!.detailSelectDescriptionButton.setImageResource(R.drawable.ic_close)
    }

    private fun disableDescriptionSelection() {
        // show description content again, otherwise some links are not clickable
        val description: Description? = getDescription()
        if (description != null) {
            TextLinkifier.fromDescription(binding!!.detailDescriptionView,
                    description, HtmlCompat.FROM_HTML_MODE_LEGACY,
                    getService(), getStreamUrl(),
                    descriptionDisposables, TextLinkifier.SET_LINK_MOVEMENT_METHOD)
        }
        binding!!.detailDescriptionNoteView.setVisibility(View.GONE)
        binding!!.detailDescriptionView.setTextIsSelectable(false)
        val buttonLabel: String = getString(R.string.description_select_enable)
        binding!!.detailSelectDescriptionButton.setContentDescription(buttonLabel)
        TooltipCompat.setTooltipText(binding!!.detailSelectDescriptionButton, buttonLabel)
        binding!!.detailSelectDescriptionButton.setImageResource(R.drawable.ic_select_all)
    }

    protected fun addMetadataItem(inflater: LayoutInflater?,
                                  layout: LinearLayout,
                                  linkifyContent: Boolean,
                                  @StringRes type: Int,
                                  content: String) {
        if (Utils.isBlank(content)) {
            return
        }
        val itemBinding: ItemMetadataBinding = ItemMetadataBinding.inflate((inflater)!!, layout, false)
        itemBinding.metadataTypeView.setText(type)
        itemBinding.metadataTypeView.setOnLongClickListener(OnLongClickListener({ v: View? ->
            ShareUtils.copyToClipboard(requireContext(), content)
            true
        }))
        if (linkifyContent) {
            TextLinkifier.fromPlainText(itemBinding.metadataContentView, content, null, null,
                    descriptionDisposables, TextLinkifier.SET_LINK_MOVEMENT_METHOD)
        } else {
            itemBinding.metadataContentView.setText(content)
        }
        itemBinding.metadataContentView.setClickable(true)
        layout.addView(itemBinding.getRoot())
    }

    private fun imageSizeToText(heightOrWidth: Int): String {
        if (heightOrWidth < 0) {
            return getString(R.string.question_mark)
        } else {
            return heightOrWidth.toString()
        }
    }

    protected fun addImagesMetadataItem(inflater: LayoutInflater?,
                                        layout: LinearLayout,
                                        @StringRes type: Int,
                                        images: List<Image?>) {
        val preferredImageUrl: String? = ImageStrategy.choosePreferredImage(images)
        if (preferredImageUrl == null) {
            return  // null will be returned in case there is no image
        }
        val itemBinding: ItemMetadataBinding = ItemMetadataBinding.inflate((inflater)!!, layout, false)
        itemBinding.metadataTypeView.setText(type)
        val urls: SpannableStringBuilder = SpannableStringBuilder()
        for (image: Image? in images) {
            if (urls.length != 0) {
                urls.append(", ")
            }
            val entryBegin: Int = urls.length
            if ((image!!.getHeight() != Image.HEIGHT_UNKNOWN
                            ) || (image.getWidth() != Image.WIDTH_UNKNOWN // if even the resolution level is unknown, ?x? will be shown
                            ) || (image.getEstimatedResolutionLevel() == ResolutionLevel.UNKNOWN)) {
                urls.append(imageSizeToText(image.getHeight()))
                urls.append('x')
                urls.append(imageSizeToText(image.getWidth()))
            } else {
                when (image.getEstimatedResolutionLevel()) {
                    ResolutionLevel.LOW -> urls.append(getString(R.string.image_quality_low))
                    ResolutionLevel.MEDIUM -> urls.append(getString(R.string.image_quality_medium))
                    ResolutionLevel.HIGH -> urls.append(getString(R.string.image_quality_high))
                    else -> {}
                }
            }
            urls.setSpan(object : ClickableSpan() {
                public override fun onClick(widget: View) {
                    ShareUtils.openUrlInBrowser(requireContext(), image.getUrl())
                }
            }, entryBegin, urls.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if ((preferredImageUrl == image.getUrl())) {
                urls.setSpan(StyleSpan(Typeface.BOLD), entryBegin, urls.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        itemBinding.metadataContentView.setText(urls)
        itemBinding.metadataContentView.setMovementMethod(LinkMovementMethod.getInstance())
        layout.addView(itemBinding.getRoot())
    }

    private fun addTagsMetadataItem(inflater: LayoutInflater, layout: LinearLayout) {
        val tags: List<String?> = getTags()
        if (!tags.isEmpty()) {
            val itemBinding: ItemMetadataTagsBinding = ItemMetadataTagsBinding.inflate(inflater, layout, false)
            tags.stream().sorted(java.lang.String.CASE_INSENSITIVE_ORDER).forEach(Consumer({ tag: String? ->
                val chip: Chip = inflater.inflate(R.layout.chip,
                        itemBinding.metadataTagsChips, false) as Chip
                chip.setText(tag)
                chip.setOnClickListener(View.OnClickListener({ chip: View -> onTagClick(chip) }))
                chip.setOnLongClickListener(OnLongClickListener({ chip: View -> onTagLongClick(chip) }))
                itemBinding.metadataTagsChips.addView(chip)
            }))
            layout.addView(itemBinding.getRoot())
        }
    }

    private fun onTagClick(chip: View) {
        if (getParentFragment() != null) {
            NavigationHelper.openSearchFragment(getParentFragment()!!.getParentFragmentManager(),
                    getServiceId(), (chip as Chip).getText().toString())
        }
    }

    private fun onTagLongClick(chip: View): Boolean {
        ShareUtils.copyToClipboard(requireContext(), (chip as Chip).getText().toString())
        return true
    }
}
