package org.schabi.newpipe.about;

import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ShareUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Fragment containing the software licenses.
 */
public class LicenseFragment extends Fragment {
    private static final String ARG_COMPONENTS = "components";
    private static final String LICENSE_KEY = "ACTIVE_LICENSE";

    private SoftwareComponent[] softwareComponents;
    private SoftwareComponent componentForContextMenu;
    private License activeLicense;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public static LicenseFragment newInstance(final SoftwareComponent[] softwareComponents) {
        final Bundle bundle = new Bundle();
        bundle.putParcelableArray(ARG_COMPONENTS, Objects.requireNonNull(softwareComponents));
        final LicenseFragment fragment = new LicenseFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        softwareComponents = (SoftwareComponent[]) getArguments()
                .getParcelableArray(ARG_COMPONENTS);

        if (savedInstanceState != null) {
            final Serializable license = savedInstanceState.getSerializable(LICENSE_KEY);
            if (license != null) {
                activeLicense = (License) license;
            }
        }
        // Sort components by name
        Arrays.sort(softwareComponents, Comparator.comparing(SoftwareComponent::getName));
    }

    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_licenses, container, false);
        final ViewGroup softwareComponentsView = rootView.findViewById(R.id.software_components);

        final View licenseLink = rootView.findViewById(R.id.app_read_license);
        licenseLink.setOnClickListener(v -> {
            activeLicense = StandardLicenses.GPL3;
            compositeDisposable.add(LicenseFragmentHelper.showLicense(getActivity(),
                    StandardLicenses.GPL3));
        });

        for (final SoftwareComponent component : softwareComponents) {
            final View componentView = inflater
                    .inflate(R.layout.item_software_component, container, false);
            final TextView softwareName = componentView.findViewById(R.id.name);
            final TextView copyright = componentView.findViewById(R.id.copyright);
            softwareName.setText(component.getName());
            copyright.setText(getString(R.string.copyright,
                    component.getYears(),
                    component.getCopyrightOwner(),
                    component.getLicense().getAbbreviation()));

            componentView.setTag(component);
            componentView.setOnClickListener(v -> {
                activeLicense = component.getLicense();
                compositeDisposable.add(LicenseFragmentHelper.showLicense(getActivity(),
                        component.getLicense()));
            });
            softwareComponentsView.addView(componentView);
            registerForContextMenu(componentView);
        }
        if (activeLicense != null) {
            compositeDisposable.add(LicenseFragmentHelper.showLicense(getActivity(),
                    activeLicense));
        }
        return rootView;
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        final MenuInflater inflater = getActivity().getMenuInflater();
        final SoftwareComponent component = (SoftwareComponent) v.getTag();
        menu.setHeaderTitle(component.getName());
        inflater.inflate(R.menu.software_component, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
        componentForContextMenu = (SoftwareComponent) v.getTag();
    }

    @Override
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        // item.getMenuInfo() is null so we use the tag of the view
        final SoftwareComponent component = componentForContextMenu;
        if (component == null) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_website:
                ShareUtils.openUrlInBrowser(getActivity(), component.getLink());
                return true;
            case R.id.action_show_license:
                compositeDisposable.add(LicenseFragmentHelper.showLicense(getActivity(),
                        component.getLicense()));
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (activeLicense != null) {
            savedInstanceState.putSerializable(LICENSE_KEY, activeLicense);
        }
    }
}
