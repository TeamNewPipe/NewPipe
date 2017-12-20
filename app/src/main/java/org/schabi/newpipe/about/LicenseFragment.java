package org.schabi.newpipe.about;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.*;
import android.widget.TextView;
import org.schabi.newpipe.R;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Fragment containing the software licenses
 */
public class LicenseFragment extends Fragment {

    private static final String ARG_COMPONENTS = "components";
    private SoftwareComponent[] softwareComponents;
    private SoftwareComponent mComponentForContextMenu;

    public static LicenseFragment newInstance(SoftwareComponent[] softwareComponents) {
        if(softwareComponents == null) {
            throw new NullPointerException("softwareComponents is null");
        }
        LicenseFragment fragment = new LicenseFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelableArray(ARG_COMPONENTS, softwareComponents);
        fragment.setArguments(bundle);
        return fragment;
    }

    /**
     * Shows a popup containing the license
     * @param context the context to use
     * @param license the license to show
     */
    public static void showLicense(Context context, License license) {
        new LicenseFragmentHelper().execute(context, license);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        softwareComponents = (SoftwareComponent[]) getArguments().getParcelableArray(ARG_COMPONENTS);

        // Sort components by name
        Arrays.sort(softwareComponents, new Comparator<SoftwareComponent>() {
            @Override
            public int compare(SoftwareComponent o1, SoftwareComponent o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_licenses, container, false);
        ViewGroup softwareComponentsView = rootView.findViewById(R.id.software_components);

        View licenseLink = rootView.findViewById(R.id.app_read_license);
        licenseLink.setOnClickListener(new OnReadFullLicenseClickListener());

        for (final SoftwareComponent component : softwareComponents) {
            View componentView = inflater.inflate(R.layout.item_software_component, container, false);
            TextView softwareName = componentView.findViewById(R.id.name);
            TextView copyright = componentView.findViewById(R.id.copyright);
            softwareName.setText(component.getName());
            copyright.setText(getContext().getString(R.string.copyright,
                    component.getYears(),
                    component.getCopyrightOwner(),
                    component.getLicense().getAbbreviation()));

            componentView.setTag(component);
            componentView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = v.getContext();
                    if (context != null) {
                        showLicense(context, component.getLicense());
                    }
                }
            });
            softwareComponentsView.addView(componentView);
            registerForContextMenu(componentView);
        }
        return rootView;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        SoftwareComponent component = (SoftwareComponent) v.getTag();
        menu.setHeaderTitle(component.getName());
        inflater.inflate(R.menu.software_component, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
        mComponentForContextMenu = (SoftwareComponent) v.getTag();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // item.getMenuInfo() is null so we use the tag of the view
        final SoftwareComponent component = mComponentForContextMenu;
        if (component == null) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_website:
                openWebsite(component.getLink());
                return true;
            case R.id.action_show_license:
                showLicense(getContext(), component.getLicense());
        }
        return false;
    }

    private void openWebsite(String componentLink) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(componentLink));
        startActivity(browserIntent);
    }

    private static class OnReadFullLicenseClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            LicenseFragment.showLicense(v.getContext(), StandardLicenses.GPL3);
        }
    }
}
