package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.kt.apps.video.viewmodel.ITubeAppViewModel;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.util.ITubeUtils;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import kotlin.Unit;

public class DefaultKioskFragment extends KioskFragment {
    private ITubeAppViewModel iTubeAppViewModel;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (serviceId < 0) {
            updateSelectedDefaultKiosk();
        }
    }

    @Override
    public void onViewCreated(final @NonNull View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        ITubeUtils.addSafeAreaPadding(
                itemsList,
                WindowInsetsCompat.Type.systemBars(),
                false,
                true,
                false,
                true,
                true);
        iTubeAppViewModel = new ViewModelProvider(requireActivity(),
                ITubeAppViewModel.Companion.getFactory()).get(ITubeAppViewModel.class);
        ITubeUtils.collect(this, iTubeAppViewModel.getHintNewVersion(), hintNewVersion -> {
            Log.d("hintNewVersion", "available =" + (hintNewVersion != null));
            if (hintNewVersion != null) {
                infoListAdapter.setHeaderSupplier(() ->
                        ITubeUtils.createNewVersionHeader(
                                rootView.getContext(),
                                hintNewVersion,
                                () -> {
                                    iTubeAppViewModel.onTapNewVersionHintCloseAction(
                                            hintNewVersion
                                    );
                                    return null;
                                },
                                () -> {
                                    iTubeAppViewModel.onTapNewVersionHintAction(
                                            hintNewVersion
                                    );
                                    return null;
                                }
                        ));
            } else {
                infoListAdapter.setHeaderSupplier(null);
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceId != ServiceHelper.getSelectedServiceId(requireContext())) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
            reloadContent();
        }
    }

    private void updateSelectedDefaultKiosk() {
        try {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());

            final KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
            kioskId = kioskList.getDefaultKioskId();
            url = kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl();

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }
}
