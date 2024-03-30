package org.schabi.newpipe.fragments.list.channel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxbinding4.view.clicks
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentChannelBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.detail.TabAdapter
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateBackgroundColor
import org.schabi.newpipe.ktx.animateTextColor
import org.schabi.newpipe.local.feed.notifications.NotificationHelper.Companion.areNewStreamsNotificationsEnabled
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.subscription.SubscriptionManager.updateNotificationMode
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.StateSaver.WriteRead
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Queue
import java.util.concurrent.TimeUnit

class ChannelFragment() : BaseStateFragment<ChannelInfo>(), WriteRead {
    @State
    protected var serviceId: Int = NO_SERVICE_ID

    @State
    protected var name: String? = null

    @State
    protected var url: String? = null
    private var currentInfo: ChannelInfo? = null
    private var currentWorker: Disposable? = null
    private val disposables: CompositeDisposable = CompositeDisposable()
    private var subscribeButtonMonitor: Disposable? = null
    private var subscriptionManager: SubscriptionManager? = null
    private var lastTab: Int = 0
    private var channelContentNotSupported: Boolean = false

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var binding: FragmentChannelBinding? = null
    private var tabAdapter: TabAdapter? = null
    private var menuRssButton: MenuItem? = null
    private var menuNotifyButton: MenuItem? = null
    private var channelSubscription: SubscriptionEntity? = null
    private fun setInitialData(sid: Int, u: String?, title: String?) {
        serviceId = sid
        url = u
        name = if (!TextUtils.isEmpty(title)) title else ""
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    public override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager((activity)!!)
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        binding = FragmentChannelBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    // called from onViewCreated in BaseFragment.onViewCreated
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        tabAdapter = TabAdapter(getChildFragmentManager())
        binding!!.viewPager.setAdapter(tabAdapter)
        binding!!.tabLayout.setupWithViewPager(binding!!.viewPager)
        setTitle(name)
        binding!!.channelTitleView.setText(name)
        if (!ImageStrategy.shouldLoadImages()) {
            // do not waste space for the banner if it is not going to be loaded
            binding!!.channelBannerImage.setImageDrawable(null)
        }
    }

    override fun initListeners() {
        super.initListeners()
        val openSubChannel: View.OnClickListener = View.OnClickListener({ v: View? ->
            if (!TextUtils.isEmpty(currentInfo!!.getParentChannelUrl())) {
                try {
                    NavigationHelper.openChannelFragment(getFM(), currentInfo!!.getServiceId(),
                            currentInfo!!.getParentChannelUrl(),
                            currentInfo!!.getParentChannelName())
                } catch (e: Exception) {
                    showUiErrorSnackbar(this, "Opening channel fragment", e)
                }
            } else if (BaseFragment.Companion.DEBUG) {
                Log.i(TAG, "Can't open parent channel because we got no channel URL")
            }
        })
        binding!!.subChannelAvatarView.setOnClickListener(openSubChannel)
        binding!!.subChannelTitleView.setOnClickListener(openSubChannel)
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        disposables.clear()
        binding = null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_channel, menu)
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
    }

    public override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menuRssButton = menu.findItem(R.id.menu_item_rss)
        menuNotifyButton = menu.findItem(R.id.menu_item_notify)
        updateNotifyButton(channelSubscription)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menu_item_notify -> {
                val value: Boolean = !item.isChecked()
                item.setEnabled(false)
                setNotify(value)
            }

            R.id.action_settings -> NavigationHelper.openSettings(requireContext())
            R.id.menu_item_rss -> if (currentInfo != null) {
                ShareUtils.openUrlInApp(requireContext(), currentInfo!!.getFeedUrl())
            }

            R.id.menu_item_openInBrowser -> if (currentInfo != null) {
                ShareUtils.openUrlInBrowser(requireContext(), currentInfo!!.getOriginalUrl())
            }

            R.id.menu_item_share -> if (currentInfo != null) {
                ShareUtils.shareText(requireContext(), (name)!!, currentInfo!!.getOriginalUrl(),
                        currentInfo!!.getAvatars())
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Channel Subscription
    ////////////////////////////////////////////////////////////////////////// */
    private fun monitorSubscription(info: ChannelInfo) {
        val onError: Consumer<Throwable> = Consumer({ throwable: Throwable? ->
            binding!!.channelSubscribeButton.animate(false, 100)
            showSnackBarError(ErrorInfo((throwable)!!, UserAction.SUBSCRIPTION_GET,
                    "Get subscription status", currentInfo))
        })
        val observable: Observable<List<SubscriptionEntity>> = subscriptionManager
                .subscriptionTable()
                .getSubscriptionFlowable(info.getServiceId(), info.getUrl())
                .toObservable()
        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError))
        disposables.add(observable
                .map(Function<List<SubscriptionEntity?>, Boolean>({ obj: List<SubscriptionEntity?> -> obj.isEmpty() }))
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ isEmpty: Boolean? -> updateSubscribeButton(!isEmpty!!) }), onError))
        disposables.add(observable
                .map(Function<List<SubscriptionEntity?>, Boolean>({ obj: List<SubscriptionEntity?> -> obj.isEmpty() }))
                .distinctUntilChanged()
                .skip(1) // channel has just been opened
                .filter(Predicate({ x: Boolean? -> areNewStreamsNotificationsEnabled(requireContext()) }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        showNotifySnackbar()
                    }
                }), onError))
    }

    private fun mapOnSubscribe(subscription: SubscriptionEntity): Function<Any, Any> {
        return Function({ o: Any ->
            subscriptionManager!!.insertSubscription(subscription)
            o
        })
    }

    private fun mapOnUnsubscribe(subscription: SubscriptionEntity?): Function<Any, Any> {
        return Function({ o: Any ->
            subscriptionManager!!.deleteSubscription((subscription)!!)
            o
        })
    }

    private fun updateSubscription(info: ChannelInfo) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "updateSubscription() called with: info = [" + info + "]")
        }
        val onComplete: Action = Action({
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, "Updated subscription: " + info.getUrl())
            }
        })
        val onError: Consumer<Throwable> = Consumer({ throwable: Throwable ->
            showSnackBarError(ErrorInfo(throwable, UserAction.SUBSCRIPTION_UPDATE,
                    "Updating subscription for " + info.getUrl(), info))
        })
        disposables.add(subscriptionManager!!.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError))
    }

    private fun monitorSubscribeButton(action: Function<Any, Any>): Disposable {
        val onNext: Consumer<Any> = Consumer<Any>({ o: Any ->
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, "Changed subscription status to this channel!")
            }
        })
        val onError: Consumer<Throwable> = Consumer({ throwable: Throwable ->
            showSnackBarError(ErrorInfo(throwable, UserAction.SUBSCRIPTION_CHANGE,
                    "Changing subscription for " + currentInfo!!.getUrl(), currentInfo))
        })

        /* Emit clicks from main thread unto io thread */return binding!!.channelSubscribeButton.clicks()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .debounce(BUTTON_DEBOUNCE_INTERVAL.toLong(), TimeUnit.MILLISECONDS) // Ignore rapid clicks
                .map(action)
                .subscribe(onNext, onError)
    }

    private fun getSubscribeUpdateMonitor(info: ChannelInfo): Consumer<List<SubscriptionEntity>> {
        return Consumer<List<SubscriptionEntity>>({ subscriptionEntities: List<SubscriptionEntity> ->
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, ("subscriptionManager.subscriptionTable.doOnNext() called with: "
                        + "subscriptionEntities = [" + subscriptionEntities + "]"))
            }
            if (subscribeButtonMonitor != null) {
                subscribeButtonMonitor!!.dispose()
            }
            if (subscriptionEntities.isEmpty()) {
                if (BaseFragment.Companion.DEBUG) {
                    Log.d(TAG, "No subscription to this channel!")
                }
                val channel: SubscriptionEntity = SubscriptionEntity()
                channel.setServiceId(info.getServiceId())
                channel.setUrl(info.getUrl())
                channel.setData(info.getName(),
                        ImageStrategy.imageListToDbUrl(info.getAvatars()),
                        info.getDescription(),
                        info.getSubscriberCount())
                channelSubscription = null
                updateNotifyButton(null)
                subscribeButtonMonitor = monitorSubscribeButton(mapOnSubscribe(channel))
            } else {
                if (BaseFragment.Companion.DEBUG) {
                    Log.d(TAG, "Found subscription to this channel!")
                }
                channelSubscription = subscriptionEntities.get(0)
                updateNotifyButton(channelSubscription)
                subscribeButtonMonitor = monitorSubscribeButton(mapOnUnsubscribe(channelSubscription))
            }
        })
    }

    private fun updateSubscribeButton(isSubscribed: Boolean) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("updateSubscribeButton() called with: "
                    + "isSubscribed = [" + isSubscribed + "]"))
        }
        val isButtonVisible: Boolean = (binding!!.channelSubscribeButton.getVisibility()
                == View.VISIBLE)
        val backgroundDuration: Int = if (isButtonVisible) 300 else 0
        val textDuration: Int = if (isButtonVisible) 200 else 0
        val subscribedBackground: Int = ContextCompat
                .getColor((activity)!!, R.color.subscribed_background_color)
        val subscribedText: Int = ContextCompat.getColor((activity)!!, R.color.subscribed_text_color)
        val subscribeBackground: Int = ColorUtils.blendARGB(ThemeHelper.resolveColorFromAttr((activity)!!, R.attr.colorPrimary), subscribedBackground, 0.35f)
        val subscribeText: Int = ContextCompat.getColor((activity)!!, R.color.subscribe_text_color)
        if (isSubscribed) {
            binding!!.channelSubscribeButton.setText(R.string.subscribed_button_title)
            binding!!.channelSubscribeButton.animateBackgroundColor(backgroundDuration.toLong(), subscribeBackground, subscribedBackground)
            binding!!.channelSubscribeButton.animateTextColor(textDuration.toLong(), subscribeText, subscribedText)
        } else {
            binding!!.channelSubscribeButton.setText(R.string.subscribe_button_title)
            binding!!.channelSubscribeButton.animateBackgroundColor(backgroundDuration.toLong(), subscribedBackground, subscribeBackground)
            binding!!.channelSubscribeButton.animateTextColor(textDuration.toLong(), subscribedText, subscribeText)
        }
        binding!!.channelSubscribeButton.animate(true, 100, AnimationType.LIGHT_SCALE_AND_ALPHA)
    }

    private fun updateNotifyButton(subscription: SubscriptionEntity?) {
        if (menuNotifyButton == null) {
            return
        }
        if (subscription != null) {
            menuNotifyButton!!.setEnabled(
                    areNewStreamsNotificationsEnabled(requireContext())
            )
            menuNotifyButton!!.setChecked(
                    subscription.getNotificationMode() == NotificationMode.Companion.ENABLED
            )
        }
        menuNotifyButton!!.setVisible(subscription != null)
    }

    private fun setNotify(isEnabled: Boolean) {
        disposables.add(
                subscriptionManager
                        .updateNotificationMode(
                                currentInfo!!.getServiceId(),
                                currentInfo!!.getUrl(),
                                if (isEnabled) NotificationMode.Companion.ENABLED else NotificationMode.Companion.DISABLED)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
        )
    }

    /**
     * Show a snackbar with the option to enable notifications on new streams for this channel.
     */
    private fun showNotifySnackbar() {
        Snackbar.make(binding!!.getRoot(), R.string.you_successfully_subscribed, Snackbar.LENGTH_LONG)
                .setAction(R.string.get_notified, View.OnClickListener({ v: View? -> setNotify(true) }))
                .setActionTextColor(Color.YELLOW)
                .show()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    private fun updateTabs() {
        tabAdapter!!.clearAllItems()
        if (currentInfo != null && !channelContentNotSupported) {
            val context: Context = requireContext()
            val preferences: SharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context)
            for (linkHandler: ListLinkHandler in currentInfo!!.getTabs()) {
                val tab: String = linkHandler.getContentFilters().get(0)
                if (ChannelTabHelper.showChannelTab(context, preferences, tab)) {
                    val channelTabFragment: ChannelTabFragment = ChannelTabFragment.Companion.getInstance(serviceId, linkHandler, name)
                    channelTabFragment.useAsFrontPage(useAsFrontPage)
                    tabAdapter!!.addFragment(channelTabFragment,
                            context.getString(ChannelTabHelper.getTranslationKey(tab)))
                }
            }
            if (ChannelTabHelper.showChannelTab(
                            context, preferences, R.string.show_channel_tabs_about)) {
                tabAdapter!!.addFragment(
                        ChannelAboutFragment(currentInfo!!),
                        context.getString(R.string.channel_tab_about))
            }
        }
        tabAdapter!!.notifyDataSetUpdate()
        for (i in 0 until tabAdapter!!.getCount()) {
            binding!!.tabLayout.getTabAt(i)!!.setText(tabAdapter!!.getItemTitle(i))
        }

        // Restore previously selected tab
        val ltab: TabLayout.Tab? = binding!!.tabLayout.getTabAt(lastTab)
        if (ltab != null) {
            binding!!.tabLayout.selectTab(ltab)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    ////////////////////////////////////////////////////////////////////////// */
    public override fun generateSuffix(): String? {
        return null
    }

    public override fun writeTo(objectsToSave: Queue<Any?>) {
        objectsToSave.add(currentInfo)
        objectsToSave.add(if (binding == null) 0 else binding!!.tabLayout.getSelectedTabPosition())
    }

    public override fun readFrom(savedObjects: Queue<Any>) {
        currentInfo = savedObjects.poll() as ChannelInfo?
        lastTab = savedObjects.poll() as Int
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (binding != null) {
            outState.putInt("LastTab", binding!!.tabLayout.getSelectedTabPosition())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastTab = savedInstanceState.getInt("LastTab", 0)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    override fun doInitialLoadLogic() {
        if (currentInfo == null) {
            startLoading(false)
        } else {
            handleResult(currentInfo!!)
        }
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        currentInfo = null
        updateTabs()
        if (currentWorker != null) {
            currentWorker!!.dispose()
        }
        runWorker(forceLoad)
    }

    private fun runWorker(forceLoad: Boolean) {
        currentWorker = ExtractorHelper.getChannelInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<ChannelInfo?>({ result: ChannelInfo? ->
                    isLoading.set(false)
                    handleResult((result)!!)
                }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_CHANNEL,
                            (if (url == null) "No URL" else url)!!, serviceId))
                }))
    }

    public override fun showLoading() {
        super.showLoading()
        PicassoHelper.cancelTag(PICASSO_CHANNEL_TAG)
        binding!!.channelSubscribeButton.animate(false, 100)
    }

    public override fun handleResult(result: ChannelInfo) {
        super.handleResult(result)
        currentInfo = result
        setInitialData(result.getServiceId(), result.getOriginalUrl(), result.getName())
        if (ImageStrategy.shouldLoadImages() && !result.getBanners().isEmpty()) {
            PicassoHelper.loadBanner(result.getBanners()).tag(PICASSO_CHANNEL_TAG)
                    .into(binding!!.channelBannerImage)
        } else {
            // do not waste space for the banner, if the user disabled images or there is not one
            binding!!.channelBannerImage.setImageDrawable(null)
        }
        PicassoHelper.loadAvatar(result.getAvatars()).tag(PICASSO_CHANNEL_TAG)
                .into(binding!!.channelAvatarView)
        PicassoHelper.loadAvatar(result.getParentChannelAvatars()).tag(PICASSO_CHANNEL_TAG)
                .into(binding!!.subChannelAvatarView)
        binding!!.channelTitleView.setText(result.getName())
        binding!!.channelSubscriberView.setVisibility(View.VISIBLE)
        if (result.getSubscriberCount() >= 0) {
            binding!!.channelSubscriberView.setText(Localization.shortSubscriberCount((activity)!!, result.getSubscriberCount()))
        } else {
            binding!!.channelSubscriberView.setText(R.string.subscribers_count_not_available)
        }
        if (!TextUtils.isEmpty(currentInfo!!.getParentChannelName())) {
            binding!!.subChannelTitleView.setText(String.format(
                    getString(R.string.channel_created_by),
                    currentInfo!!.getParentChannelName()))
            binding!!.subChannelTitleView.setVisibility(View.VISIBLE)
            binding!!.subChannelAvatarView.setVisibility(View.VISIBLE)
        }
        if (menuRssButton != null) {
            menuRssButton!!.setVisible(!TextUtils.isEmpty(result.getFeedUrl()))
        }
        channelContentNotSupported = false
        for (throwable: Throwable? in result.getErrors()) {
            if (throwable is ContentNotSupportedException) {
                channelContentNotSupported = true
                showContentNotSupportedIfNeeded()
                break
            }
        }
        disposables.clear()
        if (subscribeButtonMonitor != null) {
            subscribeButtonMonitor!!.dispose()
        }
        updateTabs()
        updateSubscription(result)
        monitorSubscription(result)
    }

    private fun showContentNotSupportedIfNeeded() {
        // channelBinding might not be initialized when handleResult() is called
        // (e.g. after rotating the screen, #6696)
        if (!channelContentNotSupported || binding == null) {
            return
        }
        binding!!.errorContentNotSupported.setVisibility(View.VISIBLE)
        binding!!.channelKaomoji.setText("(︶︹︺)")
        binding!!.channelKaomoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 45f)
    }

    companion object {
        private val BUTTON_DEBOUNCE_INTERVAL: Int = 100
        private val PICASSO_CHANNEL_TAG: String = "PICASSO_CHANNEL_TAG"
        fun getInstance(serviceId: Int, url: String?,
                        name: String?): ChannelFragment {
            val instance: ChannelFragment = ChannelFragment()
            instance.setInitialData(serviceId, url, name)
            return instance
        }
    }
}
