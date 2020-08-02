package org.schabi.newpipe.settings.extensions;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ManageExtensionsFragment extends Fragment {
    private static final int REQUEST_ADD_EXTENSION = 32945;

    private final List<Extension> extensionList = new ArrayList<>();
    private InstalledExtensionsAdapter installedExtensionsAdapter = null;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        updateExtensionList();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_extensions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        final RecyclerView listInstalledExtensions
                = rootView.findViewById(R.id.installed_extensions);
        listInstalledExtensions.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listInstalledExtensions);

        installedExtensionsAdapter
                = new InstalledExtensionsAdapter(requireContext(), itemTouchHelper);
        listInstalledExtensions.setAdapter(installedExtensionsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_ADD_EXTENSION
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
            final StoredFileHelper file = new StoredFileHelper(getContext(), data.getData(),
                    "application/zip");
            String name = null;
            String author = null;
            // check if file is supported
            try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                    new SharpInputStream(file.getStream())))) {
                boolean hasDex = false;
                boolean hasIcon = false;
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    switch (zipEntry.getName()) {
                        case "about.json":
                            final JsonObject jsonObject
                                    = JsonParser.object().from(zipInputStream);
                            if (!jsonObject.getString("version")
                                    .equals(BuildConfig.VERSION_NAME)) {
                                throw new IOException(
                                        "Extension is for different NewPipe version");
                            }
                            if (jsonObject.has("replaces")
                                    && jsonObject.getInt("replaces")
                                    >= ServiceList.builtinServices) {
                                throw new IOException(
                                        "Extension replaces not existing service");
                            }
                            name = jsonObject.getString("name");
                            author = jsonObject.getString("author");
                            break;
                        case "classes.dex":
                            hasDex = true;
                            break;
                        case "icon.png":
                            hasIcon = true;
                            break;
                    }
                    zipInputStream.closeEntry();
                }
                if (!hasDex || !hasIcon || name == null || author == null) {
                    throw new IOException("Invalid zip");
                }
            } catch (IOException | JsonParserException e) {
                Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                        .show();
            }
            final String nname = name; // lambda args need to be final
            final android.app.AlertDialog.Builder builder
                    = new android.app.AlertDialog.Builder(getActivity());
            builder.setMessage(getString(R.string.add_extension_dialog, name, author))
                    .setPositiveButton(R.string.finish,
                            (DialogInterface d, int id) -> addExtension(file, nname))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface d, int id) -> d.cancel());
            builder.create().show();
        }
    }

    private void addExtension(final StoredFileHelper file, final String name) {
        final String path = getActivity().getApplicationInfo().dataDir + "/extensions/" + name
                + "/";

        final File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            final File tmpFile = new File(path + "tmp.jar");
            tmpFile.createNewFile();

            final InputStream inFile = new SharpInputStream(file.getStream());
            final FileOutputStream outFile = new FileOutputStream(tmpFile);
            final byte[] data = new byte[2048];
            int count;
            while ((count = inFile.read(data)) != -1) {
                outFile.write(data, 0, count);
            }
            outFile.close();

            final JarFile jarFile = new JarFile(tmpFile);
            final JarEntry jarEntry = jarFile.getJarEntry("classes.dex");
            final InputStream entryStream = jarFile.getInputStream(jarEntry);
            while (entryStream.read(data) != -1) {
                // Stream needs to be fully read or else the signing certificate will be null
                continue;
            }
            final X509Certificate certificate = getSigningCertFromJar(jarEntry);

            final File fingerprintFile = new File(path + "fingerprint.txt");
            if (fingerprintFile.exists()) {
                final BufferedReader reader = new BufferedReader(new FileReader(fingerprintFile));
                final String prevFingerprint = reader.readLine();
                reader.close();
                verifySigningCertificate(certificate, prevFingerprint);
            } else {
                fingerprintFile.createNewFile();
                final BufferedWriter writer = new BufferedWriter(new FileWriter(fingerprintFile));
                writer.write(calcFingerprint(certificate.getEncoded()));
                writer.close();
            }

            tmpFile.delete();

            ZipHelper.extractFileFromZip(file, path + "about.json", "about.json");
            ZipHelper.extractFileFromZip(file, path + "classes.dex", "classes.dex");
            ZipHelper.extractFileFromZip(file, path + "icon.png", "icon.png");

            Toast.makeText(getContext(), R.string.add_extension_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.add_extension_fail, Toast.LENGTH_SHORT).show();
        }

        updateExtensionList();
    }

    private static X509Certificate getSigningCertFromJar(final JarEntry jarEntry) throws Exception {
        final CodeSigner[] codeSigners = jarEntry.getCodeSigners();
        if (codeSigners == null || codeSigners.length == 0) {
            throw new Exception("No signature found in extension");
        }
        // We could in theory support more than 1, but as of now we do not
        if (codeSigners.length > 1) {
            throw new Exception("Extension must be signed by a single code signer");
        }
        final List<? extends Certificate> certs
                = codeSigners[0].getSignerCertPath().getCertificates();
        if (certs.size() != 1) {
            throw new Exception("Extension code signers must only have a single certificate");
        }
        return (X509Certificate) certs.get(0);
    }

    private void verifySigningCertificate(final X509Certificate rawCertFromJar,
                                          final String previousFingerprint) throws Exception {
        final byte[] encodedCert;
        try {
            encodedCert = rawCertFromJar.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new Exception("Certificate encoding is invalid", e);
        }
        if (encodedCert == null || encodedCert.length == 0) {
            throw new Exception("Could not find a signing certificate");
        }

        final String fingerprintFromJar = calcFingerprint(encodedCert);
        if (!previousFingerprint.equalsIgnoreCase(fingerprintFromJar)) {
            throw new Exception("Supplied certificate fingerprint does not match");
        }
    }

    private static String calcFingerprint(final byte[] key) throws Exception {
        if (key == null) {
            throw new Exception("Key is null");
        }
        if (key.length < 256) {
            throw new Exception("Key was shorter than 256 bytes (" + key.length + "), "
                    + "cannot be valid!");
        }
        try {
            // keytool -list -v gives you the SHA-256 fingerprint
            final MessageDigest digest = MessageDigest.getInstance("sha256");
            digest.update(key);
            final byte[] fingerprint = digest.digest();
            final Formatter formatter = new Formatter(new StringBuilder());
            for (final byte aFingerprint : fingerprint) {
                formatter.format("%02X", aFingerprint);
            }
            final String ret = formatter.toString();
            formatter.close();
            return ret;
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("Unable to get certificate fingerprint", e);
        }
    }

    private void updateExtensionList() {
        extensionList.clear();

        final String path = getActivity().getApplicationInfo().dataDir + "/extensions/";
        final File dir = new File(path);
        if (!dir.exists()) {
            return;
        }

        for (final String extension : dir.list()) {
            try {
                final FileInputStream aboutStream = new FileInputStream(new File(
                        path + extension + "/about.json"));
                final JsonObject about = JsonParser.object().from(aboutStream);
                final Drawable icon = new BitmapDrawable(getResources(),
                        path + extension + "/icon.png");
                extensionList.add(new Extension(path + extension, about.getString("name"),
                        about.getString("author"), icon));
            } catch (Exception ignored) { }
        }

        if (installedExtensionsAdapter != null) {
            installedExtensionsAdapter.notifyDataSetChanged();
        }
    }

    public static void removeExtension(final String path) {
        final File extensionDir = new File(path);
        for (final File file : extensionDir.listFiles()) {
            file.delete();
        }

        extensionDir.delete();
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.manage_extensions_title);
            }
        }
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.install_extension_button);
        fab.setOnClickListener(v -> startActivityForResult(StoredFileHelper.getPicker(getContext()),
                REQUEST_ADD_EXTENSION));
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public int interpolateOutOfBoundsScroll(final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder source,
                                  final RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
                final int position = viewHolder.getAdapterPosition();
                removeExtension(extensionList.get(position).path);
                Toast.makeText(getContext(), R.string.remove_extension_success, Toast.LENGTH_SHORT)
                        .show();
                extensionList.remove(position);
                installedExtensionsAdapter.notifyItemRemoved(position);
            }
        };
    }

    private static class Extension {
        final String path;
        final String name;
        final String author;
        final Drawable icon;

        Extension(final String path, final String name, final String author, final Drawable icon) {
            this.path = path;
            this.name = name;
            this.author = author;
            this.icon = icon;
        }
    }

    private class InstalledExtensionsAdapter
            extends RecyclerView.Adapter<InstalledExtensionsAdapter.ExtensionViewHolder> {
        private final LayoutInflater inflater;
        private ItemTouchHelper itemTouchHelper;

        InstalledExtensionsAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public ExtensionViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int viewType) {
            final View view = inflater.inflate(R.layout.list_manage_extensions, parent, false);
            return new ExtensionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ExtensionViewHolder holder,
                                     final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return extensionList.size();
        }

        class ExtensionViewHolder extends RecyclerView.ViewHolder {
            private ImageView extensionIconView;
            private TextView extensionNameView;
            private TextView extensionAuthorView;
            private ImageView handle;

            ExtensionViewHolder(final View itemView) {
                super(itemView);

                extensionIconView = itemView.findViewById(R.id.extension_icon);
                extensionNameView = itemView.findViewById(R.id.extension_name);
                extensionAuthorView = itemView.findViewById(R.id.extension_author);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final ExtensionViewHolder holder) {
                extensionIconView.setImageDrawable(extensionList.get(position).icon);
                extensionNameView.setText(extensionList.get(position).name);
                extensionAuthorView.setText(getString(R.string.video_detail_by,
                        extensionList.get(position).author));
                handle.setOnTouchListener(getOnTouchListener(holder));
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(item);
                            return true;
                        }
                    }
                    return false;
                };
            }
        }
    }
}
