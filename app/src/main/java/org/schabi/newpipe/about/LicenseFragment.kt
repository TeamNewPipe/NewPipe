package org.schabi.newpipe.about

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentLicensesBinding
import org.schabi.newpipe.databinding.ItemSoftwareComponentBinding
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * Fragment containing the software licenses.
 */
class LicenseFragment : Fragment() {
    private lateinit var softwareComponents: Array<SoftwareComponent>
    private var activeSoftwareComponent: SoftwareComponent? = null
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softwareComponents = arguments?.getParcelableArray(ARG_COMPONENTS) as Array<SoftwareComponent>
        activeSoftwareComponent = savedInstanceState?.getSerializable(SOFTWARE_COMPONENT_KEY) as? SoftwareComponent
        // Sort components by name
        softwareComponents.sortBy { it.name }
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLicensesBinding.inflate(inflater, container, false)
        binding.licensesAppReadLicense.setOnClickListener {
            compositeDisposable.add(
                showLicense(NEWPIPE_SOFTWARE_COMPONENT)
            )
        }
        for (component in softwareComponents) {
            val componentBinding = ItemSoftwareComponentBinding
                .inflate(inflater, container, false)
            componentBinding.name.text = component.name
            componentBinding.copyright.text = getString(
                R.string.copyright,
                component.years,
                component.copyrightOwner,
                component.license.abbreviation
            )
            val root: View = componentBinding.root
            root.tag = component
            root.setOnClickListener {
                compositeDisposable.add(
                    showLicense(component)
                )
            }
            binding.licensesSoftwareComponents.addView(root)
            registerForContextMenu(root)
        }
        activeSoftwareComponent?.let { compositeDisposable.add(showLicense(it)) }
        return binding.root
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        activeSoftwareComponent?.let { savedInstanceState.putSerializable(SOFTWARE_COMPONENT_KEY, it) }
    }

    private fun showLicense(
        softwareComponent: SoftwareComponent
    ): Disposable {
        return if (context == null) {
            Disposable.empty()
        } else {
            val context = requireContext()
            activeSoftwareComponent = softwareComponent
            Observable.fromCallable { getFormattedLicense(context, softwareComponent.license) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { formattedLicense ->
                    val webViewData = Base64.encodeToString(
                        formattedLicense.toByteArray(), Base64.NO_PADDING
                    )
                    val webView = WebView(context)
                    webView.loadData(webViewData, "text/html; charset=UTF-8", "base64")

                    Localization.assureCorrectAppLanguage(context)
                    val builder = AlertDialog.Builder(requireContext())
                        .setTitle(softwareComponent.name)
                        .setView(webView)
                        .setOnCancelListener { activeSoftwareComponent = null }
                        .setOnDismissListener { activeSoftwareComponent = null }
                        .setPositiveButton(R.string.done) { dialog, _ -> dialog.dismiss() }

                    if (softwareComponent != NEWPIPE_SOFTWARE_COMPONENT) {
                        builder.setNeutralButton(R.string.open_website_license) { _, _ ->
                            ShareUtils.openUrlInApp(requireContext(), softwareComponent.link)
                        }
                    }

                    builder.show()
                }
        }
    }

    companion object {
        private const val ARG_COMPONENTS = "components"
        private const val SOFTWARE_COMPONENT_KEY = "ACTIVE_SOFTWARE_COMPONENT"
        private val NEWPIPE_SOFTWARE_COMPONENT = SoftwareComponent(
            "NewPipe",
            "2014-2023",
            "Team NewPipe",
            "https://newpipe.net/",
            StandardLicenses.GPL3,
            BuildConfig.VERSION_NAME
        )
        fun newInstance(softwareComponents: Array<SoftwareComponent>): LicenseFragment {
            val fragment = LicenseFragment()
            fragment.arguments = bundleOf(ARG_COMPONENTS to softwareComponents)
            return fragment
        }
    }
}
