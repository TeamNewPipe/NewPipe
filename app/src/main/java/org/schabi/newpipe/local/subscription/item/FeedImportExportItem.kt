package org.schabi.newpipe.local.subscription.item

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.feed_import_export_group.export_to_options
import kotlinx.android.synthetic.main.feed_import_export_group.import_export
import kotlinx.android.synthetic.main.feed_import_export_group.import_export_expand_icon
import kotlinx.android.synthetic.main.feed_import_export_group.import_export_options
import kotlinx.android.synthetic.main.feed_import_export_group.import_from_options
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.util.AnimationUtils
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.CollapsibleView

class FeedImportExportItem(
    val onImportPreviousSelected: () -> Unit,
    val onImportFromServiceSelected: (Int) -> Unit,
    val onExportSelected: () -> Unit,
    var isExpanded: Boolean = false
) : Item() {
    companion object {
        const val REFRESH_EXPANDED_STATUS = 123
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(REFRESH_EXPANDED_STATUS)) {
            viewHolder.import_export_options.apply { if (isExpanded) expand() else collapse() }
            return
        }

        super.bind(viewHolder, position, payloads)
    }

    override fun getLayout(): Int = R.layout.feed_import_export_group

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        if (viewHolder.import_from_options.childCount == 0) setupImportFromItems(viewHolder.import_from_options)
        if (viewHolder.export_to_options.childCount == 0) setupExportToItems(viewHolder.export_to_options)

        expandIconListener?.let { viewHolder.import_export_options.removeListener(it) }
        expandIconListener = CollapsibleView.StateListener { newState ->
            AnimationUtils.animateRotation(viewHolder.import_export_expand_icon,
                    250, if (newState == CollapsibleView.COLLAPSED) 0 else 180)
        }

        viewHolder.import_export_options.currentState = if (isExpanded) CollapsibleView.EXPANDED else CollapsibleView.COLLAPSED
        viewHolder.import_export_expand_icon.rotation = if (isExpanded) 180F else 0F
        viewHolder.import_export_options.ready()

        viewHolder.import_export_options.addListener(expandIconListener)
        viewHolder.import_export.setOnClickListener {
            viewHolder.import_export_options.switchState()
            isExpanded = viewHolder.import_export_options.currentState == CollapsibleView.EXPANDED
        }
    }

    override fun unbind(viewHolder: GroupieViewHolder) {
        super.unbind(viewHolder)
        expandIconListener?.let { viewHolder.import_export_options.removeListener(it) }
        expandIconListener = null
    }

    private var expandIconListener: CollapsibleView.StateListener? = null

    private fun addItemView(title: String, @DrawableRes icon: Int, container: ViewGroup): View {
        val itemRoot = View.inflate(container.context, R.layout.subscription_import_export_item, null)
        val titleView = itemRoot.findViewById<TextView>(android.R.id.text1)
        val iconView = itemRoot.findViewById<ImageView>(android.R.id.icon1)

        titleView.text = title
        iconView.setImageResource(icon)

        container.addView(itemRoot)
        return itemRoot
    }

    private fun setupImportFromItems(listHolder: ViewGroup) {
        val previousBackupItem = addItemView(listHolder.context.getString(R.string.previous_export),
                ThemeHelper.resolveResourceIdFromAttr(listHolder.context, R.attr.ic_backup), listHolder)
        previousBackupItem.setOnClickListener { onImportPreviousSelected() }

        val iconColor = if (ThemeHelper.isLightThemeSelected(listHolder.context)) Color.BLACK else Color.WHITE
        val services = listHolder.context.resources.getStringArray(R.array.service_list)
        for (serviceName in services) {
            try {
                val service = NewPipe.getService(serviceName)

                val subscriptionExtractor = service.subscriptionExtractor ?: continue

                val supportedSources = subscriptionExtractor.supportedSources
                if (supportedSources.isEmpty()) continue

                val itemView = addItemView(serviceName, ServiceHelper.getIcon(service.serviceId), listHolder)
                val iconView = itemView.findViewById<ImageView>(android.R.id.icon1)
                iconView.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

                itemView.setOnClickListener { onImportFromServiceSelected(service.serviceId) }
            } catch (e: ExtractionException) {
                throw RuntimeException("Services array contains an entry that it's not a valid service name ($serviceName)", e)
            }
        }
    }

    private fun setupExportToItems(listHolder: ViewGroup) {
        val previousBackupItem = addItemView(listHolder.context.getString(R.string.file),
                ThemeHelper.resolveResourceIdFromAttr(listHolder.context, R.attr.ic_save), listHolder)
        previousBackupItem.setOnClickListener { onExportSelected() }
    }
}
