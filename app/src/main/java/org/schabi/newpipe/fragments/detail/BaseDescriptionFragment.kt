package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.lang.String.CASE_INSENSITIVE_ORDER
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.DescriptionText
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ImageStrategy.choosePreferredImage

abstract class BaseDescriptionFragment : BaseFragment() {
    private val descriptionDisposables = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext(), null).apply {
            setContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                AppTheme {
                    Surface(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .nestedScroll(nestedScrollInterop)
                    ) {
                        Column {
                            Description(description, uploadDate ?: "")
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(0.dp, 8.dp)
                            ) {
                                Metadata()
                            }

                            Tags(tags, ::onTagClick, ::onTagLongClick)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        descriptionDisposables.clear()
        super.onDestroy()
    }

    /**
     * Description to display.
     */
    protected abstract val description: Description

    /**
     * Streaming service ID. Used for tag links.
     */
    protected abstract val serviceId: Int

    /**
     * List of tags to display below the description.
     */
    protected abstract val tags: List<String>

    /**
     * Upload date of described piece of data.
     */
    protected abstract val uploadDate: String?

    @Composable
    protected abstract fun Metadata()

    @Composable
    private fun Description(description: Description, uploadDate: String) {
        if (description.content.isNullOrEmpty() || description === Description.EMPTY_DESCRIPTION) {
            return
        }

        var selectable by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uploadDate,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { selectable = !selectable }
                ) {
                    if (!selectable) {
                        Icon(
                            painter = painterResource(R.drawable.ic_select_all),
                            contentDescription = getString(R.string.description_select_enable)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = getString(R.string.description_select_disable)
                        )
                    }
                }
            }
            if (selectable) {
                Text(
                    text = stringResource(R.string.description_select_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
            }
        }

        // TODO: DescriptionText is incomplete (TextLinkifier needs port to AnnotatedString)
        if (!selectable) {
            DescriptionText(
                description = description,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            SelectionContainer {
                DescriptionText(
                    description = description,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @Composable
    protected fun MetadataItem(
        @StringRes type: Int,
        content: String,
        // TODO: linkify text when TextLinkifier ported to AnnotationString
        linkify: Boolean
    ) {
        if (content.isBlank()) {
            return
        }

        MetadataItem(type, AnnotatedString(content))
    }

    private fun imageSizeToText(extent: Int): String {
        return if (extent < 0) getString(R.string.question_mark) else extent.toString()
    }

    @Composable
    protected fun MetadataItem(@StringRes type: Int, images: List<Image>) {
        val preferredImageUrl = choosePreferredImage(images) ?: return
        val itemString = buildAnnotatedString {
            for (image in images) {
                if (this.length > 0) {
                    append(", ")
                }

                val link = LinkAnnotation.Url(
                    image.url,
                    TextLinkStyles(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold.takeIf { preferredImageUrl == image.url }
                        )
                    )
                )

                withLink(link) {
                    if (image.height != Image.HEIGHT_UNKNOWN || image.width != Image.WIDTH_UNKNOWN || // if even the resolution level is unknown, ?x? will be shown
                        image.estimatedResolutionLevel == ResolutionLevel.UNKNOWN
                    ) {
                        append(imageSizeToText(image.height))
                        append('x')
                        append(imageSizeToText(image.width))
                    } else {
                        when (image.estimatedResolutionLevel) {
                            ResolutionLevel.LOW -> append(getString(R.string.image_quality_low))
                            ResolutionLevel.MEDIUM -> append(getString(R.string.image_quality_medium))
                            ResolutionLevel.HIGH -> append(getString(R.string.image_quality_high))
                            else -> {}
                        }
                    }
                }
            }
        }
        MetadataItem(type, itemString)
    }

    private fun onTagClick(tag: String) {
        if (parentFragment != null) {
            NavigationHelper.openSearchFragment(
                parentFragment?.getParentFragmentManager(),
                this.serviceId,
                tag
            )
        }
    }

    private fun onTagLongClick(tag: String): Boolean {
        ShareUtils.copyToClipboard(requireContext(), tag)
        return true
    }
}

@Composable
internal fun MetadataItem(@StringRes type: Int, content: AnnotatedString) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(type).uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 15.sp,
            textAlign = TextAlign.Right,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.requiredWidth(96.dp)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 15.sp
        )
    }
}

@Composable
internal fun Tags(
    tags: List<String>,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.metadata_tags).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (tag in tags.sortedWith(CASE_INSENSITIVE_ORDER)) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = true,
                            interactionSource = null,
                            indication = ripple(false),
                            role = Role.Button,
                            onLongClickLabel = "Copy tag to clipboard",
                            onLongClick = { onLongClick(tag) },
                            onClickLabel = "Search for tag",
                            onClick = { onClick(tag) }
                        )
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp, 4.dp)
                    )
                }
            }
        }
    }
}
