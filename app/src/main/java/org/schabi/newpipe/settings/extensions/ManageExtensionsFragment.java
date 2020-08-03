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

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
            JsonObject about = null;
            String name = null;
            String author = null;
            final String fingerprint;
            final String path;
            final File tmpFile;
            // check if file is supported
            try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                    new SharpInputStream(file.getStream())))) {
                boolean hasDex = false;
                boolean hasIcon = false;
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    switch (zipEntry.getName()) {
                        case "about.json":
                            about = JsonParser.object().from(zipInputStream);
                            if (!about.getString("version")
                                    .equals(BuildConfig.VERSION_NAME)) {
                                throw new Exception("Extension is for different NewPipe version");
                            }
                            if (about.has("replaces")
                                    && about.getInt("replaces")
                                    >= ServiceList.builtinServices) {
                                throw new Exception("Extension replaces not existing service");
                            }
                            name = about.getString("name");
                            author = about.getString("author");
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

                path = getActivity().getApplicationInfo().dataDir + "/extensions/" + name + "/";

                final File dir = new File(path);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                tmpFile = new File(path + "tmp.jar");
                tmpFile.createNewFile();

                final InputStream inFile = new SharpInputStream(file.getStream());
                final FileOutputStream outFile = new FileOutputStream(tmpFile);
                final byte[] buf = new byte[2048];
                int count;
                while ((count = inFile.read(buf)) != -1) {
                    outFile.write(buf, 0, count);
                }
                outFile.close();

                final JarFile jarFile = new JarFile(tmpFile);

                final JarEntry jarEntry = jarFile.getJarEntry("about.json");
                final InputStream entryStream = jarFile.getInputStream(jarEntry);
                while (entryStream.read(buf) != -1) {
                    // Stream needs to be fully read or else the signing certificate will be null
                    continue;
                }
                final X509Certificate certificate
                        = ExtensionManager.getSigningCertFromJar(jarEntry);
                fingerprint = ExtensionManager.calcFingerprint(certificate.getEncoded());
            } catch (Exception e) {
                Toast.makeText(getContext(), R.string.invalid_extension, Toast.LENGTH_SHORT).show();
                return;
            }

            final boolean upgrade = new File(path + "about.json").exists()
                    && new File(path + "classes.dex").exists()
                    && new File(path + "icon.png").exists();

            final JsonObject aabout = about;
            final android.app.AlertDialog.Builder builder
                    = new android.app.AlertDialog.Builder(getActivity());

            if (upgrade) {
                builder.setMessage(getString(R.string.upgrade_extension_dialog, name, author));
            } else {
                builder.setMessage(getString(R.string.add_extension_dialog, name, author,
                        fingerprint));
            }

            builder.setPositiveButton(R.string.finish, (DialogInterface d, int id) -> {
                try {
                    ExtensionManager.addExtension(getClass().getClassLoader(), path, tmpFile,
                            aabout);
                    Toast.makeText(getContext(), R.string.add_extension_success, Toast.LENGTH_SHORT)
                            .show();
                } catch (IOException | ExtensionManager.NoStreamingServiceClassException
                        | ExtensionManager.NameMismatchException
                        | ExtensionManager.InvalidSignatureException
                        | ExtensionManager.SignatureMismatchException e) {
                    ExtensionManager.removeExtension(path);
                    int string = 0;
                    if (e instanceof IOException) {
                        string = R.string.add_extension_fail_io;
                    } else if (e instanceof ExtensionManager.NameMismatchException) {
                        string = R.string.add_extension_fail_name_mismatch;
                    } else if (e instanceof ExtensionManager.NoStreamingServiceClassException) {
                        string = R.string.add_extension_fail_no_streaming_service_class;
                    } else if (e instanceof ExtensionManager.InvalidSignatureException) {
                        string = R.string.add_extension_fail_invalid_signature;
                    } else if (e instanceof ExtensionManager.SignatureMismatchException) {
                        string = R.string.add_extension_fail_signature_mismatch;
                    }
                    Toast.makeText(getContext(), string, Toast.LENGTH_SHORT).show();
                }
                updateExtensionList();
            }).setNegativeButton(R.string.cancel, (DialogInterface d, int id) -> {
                if (upgrade) {
                    tmpFile.delete();
                } else {
                    ExtensionManager.removeExtension(path);
                }
                d.cancel();
            });
            builder.create().show();
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
                ExtensionManager.removeExtension(extensionList.get(position).path);
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
