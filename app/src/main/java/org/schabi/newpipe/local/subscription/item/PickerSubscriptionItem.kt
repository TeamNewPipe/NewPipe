package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.google.android.material.shape.ShapeAppearanceModel
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.PickerSubscriptionItemBinding
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.util.PicassoHelper

data class PickerSubscriptionItem(
    val subscriptionEntity: SubscriptionEntity,
    var isSelected: Boolean = false
) : BindableItem<PickerSubscriptionItemBinding>() {
    override fun getId(): Long = subscriptionEntity.uid
    override fun getLayout(): Int = R.layout.picker_subscription_item
    override fun getSpanSize(spanCount: Int, position: Int): Int = 1

    override fun bind(viewBinding: PickerSubscriptionItemBinding, position: Int) {
        PicassoHelper.loadAvatar(subscriptionEntity.avatarUrl).into(viewBinding.thumbnailView)
        updateAvatar(viewBinding)
        viewBinding.titleView.text = subscriptionEntity.name
        viewBinding.selectedHighlight.isVisible = isSelected
    }

    override fun unbind(viewHolder: GroupieViewHolder<PickerSubscriptionItemBinding>) {
        super.unbind(viewHolder)

        viewHolder.binding.selectedHighlight.apply {
            animate().setListener(null).cancel()
            isGone = true
            alpha = 1F
        }
    }

    override fun initializeViewBinding(view: View) = PickerSubscriptionItemBinding.bind(view)

    fun updateSelected(containerView: View, isSelected: Boolean) {
        this.isSelected = isSelected
        PickerSubscriptionItemBinding.bind(containerView).selectedHighlight
            .animate(isSelected, 150, AnimationType.LIGHT_SCALE_AND_ALPHA)
    }

    private fun updateAvatar(viewBinding: PickerSubscriptionItemBinding) {
        val avatarMode = PreferenceManager.getDefaultSharedPreferences(viewBinding.root.context)
            .getString(
                viewBinding.root.context.getString(R.string.avatar_mode_key),
                viewBinding.root.context.getString(R.string.avatar_mode_round_key)
            )
        val shapeAppearanceResId: Int = when (avatarMode) {
            viewBinding.root.context
                .getString(R.string.avatar_mode_round_key) -> {
                R.style.CircularImageView
            }
            viewBinding.root.context
                .getString(R.string.avatar_mode_square_key) -> {
                R.style.SquaredImageView
            }
            else -> {
                R.style.RoundedSquaredImageView
            }
        }
        viewBinding.thumbnailView.shapeAppearanceModel = ShapeAppearanceModel
            .builder(
                viewBinding.root.context,
                shapeAppearanceResId, 0
            ).build()
    }
}
