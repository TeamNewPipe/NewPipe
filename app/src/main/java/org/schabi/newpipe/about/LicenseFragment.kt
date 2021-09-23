package org.schabi.newpipe.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.about.LicenseFragmentHelper.showLicense
import org.schabi.newpipe.databinding.FragmentLicensesBinding
import org.schabi.newpipe.databinding.ItemSoftwareComponentBinding

/**
 * Fragment containing the software licenses.
 */
class LicenseFragment : Fragment() {
    private lateinit var softwareComponents: Array<SoftwareComponent>
    private var activeLicense: License? = null
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softwareComponents = arguments?.getParcelableArray(ARG_COMPONENTS) as Array<SoftwareComponent>
        activeLicense = savedInstanceState?.getSerializable(LICENSE_KEY) as? License
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
            activeLicense = StandardLicenses.GPL3
            compositeDisposable.add(
                showLicense(activity, StandardLicenses.GPL3)
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
                activeLicense = component.license
                compositeDisposable.add(
                    showLicense(activity, component)
                )
            }
            binding.licensesSoftwareComponents.addView(root)
            registerForContextMenu(root)
        }
        activeLicense?.let { compositeDisposable.add(showLicense(activity, it)) }
        return binding.root
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        activeLicense?.let { savedInstanceState.putSerializable(LICENSE_KEY, it) }
    }

    companion object {
        private const val ARG_COMPONENTS = "components"
        private const val LICENSE_KEY = "ACTIVE_LICENSE"
        fun newInstance(softwareComponents: Array<SoftwareComponent>): LicenseFragment {
            val fragment = LicenseFragment()
            fragment.arguments = bundleOf(ARG_COMPONENTS to softwareComponents)
            return fragment
        }
    }
}
