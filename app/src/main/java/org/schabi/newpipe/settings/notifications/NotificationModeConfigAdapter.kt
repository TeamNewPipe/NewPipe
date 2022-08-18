package org.schabi.newpipe.settings.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.ItemNotificationConfigBinding
import org.schabi.newpipe.settings.notifications.NotificationModeConfigAdapter.SubscriptionHolder

/**
 * This [RecyclerView.Adapter] is used in the [NotificationModeConfigFragment].
 * The adapter holds all subscribed channels and their [NotificationMode]s
 * and provides the needed data structures and methods for this task.
 */
class NotificationModeConfigAdapter(
    private val listener: ModeToggleListener
) : ListAdapter<SubscriptionItem, SubscriptionHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): SubscriptionHolder {
        return SubscriptionHolder(
            ItemNotificationConfigBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SubscriptionHolder, position: Int) {
        holder.bind(currentList[position])
    }

    fun update(newData: List<SubscriptionEntity>) {
        val items = newData.map {
            SubscriptionItem(it.uid, it.name, it.notificationMode, it.serviceId, it.url)
        }
        submitList(items)
    }

    inner class SubscriptionHolder(
        private val itemBinding: ItemNotificationConfigBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        init {
            itemView.setOnClickListener {
                val mode = if (itemBinding.root.isChecked) {
                    NotificationMode.DISABLED
                } else {
                    NotificationMode.ENABLED
                }
                listener.onModeChange(bindingAdapterPosition, mode)
            }
        }

        fun bind(data: SubscriptionItem) {
            itemBinding.root.text = data.title
            itemBinding.root.isChecked = data.notificationMode != NotificationMode.DISABLED
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SubscriptionItem>() {
        override fun areItemsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: SubscriptionItem, newItem: SubscriptionItem): Any? {
            return if (oldItem.notificationMode != newItem.notificationMode) {
                newItem.notificationMode
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }

    fun interface ModeToggleListener {
        /**
         * Triggered when the UI representation of a notification mode is changed.
         */
        fun onModeChange(position: Int, @NotificationMode mode: Int)
    }
}

data class SubscriptionItem(
    val id: Long,
    val title: String,
    @NotificationMode
    val notificationMode: Int,
    val serviceId: Int,
    val url: String
)
