package org.schabi.newpipe.local.subscription

/** The Home host keeps relocated actions on the same routing and availability policy as its menu. */
interface SubscriptionActionsHost {
    fun performConfiguredSearchAction()

    fun getConfiguredSearchLabel(): String

    fun getConfiguredSearchIcon(): Int

    fun isConfiguredSearchAvailable(): Boolean

    fun isContextualSearchOpen(): Boolean

    fun notifySubscriptionActionsChanged()
}

/** Called after Home changes its selected page, search presentation, or navigation layout. */
interface SubscriptionNavigationActions {
    fun onMainNavigationActionsChanged()

    fun usesFloatingSearchAction(): Boolean
}
