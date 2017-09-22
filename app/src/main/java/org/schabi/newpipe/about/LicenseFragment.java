package org.schabi.newpipe.about;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
        if(context == null) {
            throw new NullPointerException("context is null");
        }
        if(license == null) {
            throw new NullPointerException("license is null");
        }
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(license.getName());

        WebView wv = new WebView(context);
        String licenseContent = "";
        String webViewData;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(context.getAssets().open(license.getFilename()), "UTF-8"));
            String str;
            while ((str = in.readLine()) != null) {
                licenseContent += str;
            }
            in.close();

            // split the HTML file and insert the stylesheet into the HEAD of the file
            String[] insert = licenseContent.split("</head>");
            webViewData = insert[0] + "<style type=\"text/css\">"
                    + getLicenseStylesheet(context) + "</style></head>"
                    + insert[1];
        } catch (Exception e) {
            throw new NullPointerException("could not get license file:" + getLicenseStylesheet(context));
        }
        wv.loadData(webViewData, "text/html", "utf-8");

        alert.setView(wv);
        alert.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public static String getLicenseStylesheet(Context context) {
        return "body{padding:12px 15px;margin:0;background:#"
        + getHexRGBColor(context,(ThemeHelper.isLightThemeSelected(context))
                ? R.color.light_license_background_color
                : R.color.dark_license_background_color)
        + ";color:#"
        + getHexRGBColor(context,(ThemeHelper.isLightThemeSelected(context))
                ? R.color.light_license_text_color
                : R.color.dark_license_text_color) + ";}"
        + "a[href]{color:#"
        + getHexRGBColor(context,(ThemeHelper.isLightThemeSelected(context))
                ? R.color.light_youtube_primary_color
                : R.color.dark_youtube_primary_color) + ";}"
        + "pre{white-space: pre-wrap;}";
    }

    /**
     * Cast R.color to a hexadecimal color value
     * @param context the context to use
     * @param color the color number from R.color
     * @return a six characters long String with hexadecimal RGB values
     */
    public static String getHexRGBColor(Context context, int color) {
        return context.getResources().getString(color).substring(3);
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
}
