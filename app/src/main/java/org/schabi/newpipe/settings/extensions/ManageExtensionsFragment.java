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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.R;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ManageExtensionsFragment extends Fragment {
    private static final int MENU_ITEM_FINGERPRINTS = 32944;
    private static final int REQUEST_ADD_EXTENSION = 32945;

    private final List<Extension> extensionList = new ArrayList<>();
    private InstalledExtensionsAdapter installedExtensionsAdapter = null;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        updateExtensionList();

        setHasOptionsMenu(true);
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
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem fingerprintItem = menu.add(Menu.NONE, MENU_ITEM_FINGERPRINTS, Menu.NONE,
                R.string.manage_fingerprints_title);
        fingerprintItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        final int restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(),
                R.attr.ic_fingerprint);
        fingerprintItem.setIcon(AppCompatResources.getDrawable(requireContext(), restoreIcon));
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == MENU_ITEM_FINGERPRINTS) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_holder, new ManageFingerprintsFragment())
                    .addToBackStack(null)
                    .commit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_ADD_EXTENSION
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
            final StoredFileHelper file = new StoredFileHelper(getContext(), data.getData(),
                    "application/zip");
            final ExtensionManager.ExtensionInfo extension;
            try {
                extension = ExtensionManager.checkExtension(getContext(), file);
            } catch (IOException | ExtensionManager.UnknownSignatureException
                    | ExtensionManager.InvalidSignatureException
                    | ExtensionManager.VersionMismatchException
                    | ExtensionManager.InvalidReplacementException
                    | ExtensionManager.InvalidExtensionException
                    | ExtensionManager.SignatureMismatchException e) {
                final int string;
                if (e instanceof IOException) {
                    string = R.string.add_extension_fail_io;
                } else if (e instanceof ExtensionManager.InvalidSignatureException) {
                    string = R.string.add_extension_fail_invalid_signature;
                } else if (e instanceof  ExtensionManager.SignatureMismatchException) {
                    string = R.string.add_extension_fail_signature_mismatch;
                } else if (e instanceof ExtensionManager.UnknownSignatureException) {
                    string = R.string.add_extension_fail_unknown_signature;
                } else if (e instanceof ExtensionManager.VersionMismatchException) {
                    string = R.string.add_extension_fail_version_mismatch;
                } else if (e instanceof ExtensionManager.InvalidReplacementException) {
                    string = R.string.add_extension_fail_invalid_replacement;
                } else {
                    string = R.string.add_extension_fail_invalid_extension;
                }
                Toast.makeText(getContext(), string, Toast.LENGTH_SHORT).show();
                return;
            }

            final android.app.AlertDialog.Builder builder
                    = new android.app.AlertDialog.Builder(getActivity());

            if (extension.upgrade) {
                builder.setMessage(getString(R.string.upgrade_extension_dialog, extension.name,
                        extension.author));
            } else if (extension.replaces != -1) {
                builder.setMessage(getString(R.string.replace_extension_dialog, extension.name,
                        extension.author, extension.fingerprint));
            } else {
                builder.setMessage(getString(R.string.add_extension_dialog, extension.name,
                        extension.author, extension.fingerprint));
            }

            builder.setPositiveButton(R.string.finish, (DialogInterface d, int id) -> {
                try {
                    ExtensionManager.addExtension(getClass().getClassLoader(), getContext(),
                            extension);
                    Toast.makeText(getContext(), R.string.add_extension_success, Toast.LENGTH_SHORT)
                            .show();
                } catch (IOException | ExtensionManager.NameMismatchException
                        | ExtensionManager.InvalidSignatureException
                        | ExtensionManager.SignatureMismatchException
                        | ExtensionManager.InvalidExtensionException e) {
                    ExtensionManager.removeExtension(ExtensionManager.getPathForExtensionName(
                            requireContext(), extension.name));
                    final int string;
                    if (e instanceof IOException) {
                        string = R.string.add_extension_fail_io;
                    } else if (e instanceof ExtensionManager.NameMismatchException) {
                        string = R.string.add_extension_fail_name_mismatch;
                    } else if (e instanceof ExtensionManager.InvalidSignatureException) {
                        string = R.string.add_extension_fail_invalid_signature;
                    } else if (e instanceof ExtensionManager.SignatureMismatchException) {
                        string = R.string.add_extension_fail_signature_mismatch;
                    } else {
                        string = R.string.add_extension_fail_invalid_extension;
                    }
                    Toast.makeText(getContext(), string, Toast.LENGTH_SHORT).show();
                }
                updateExtensionList();
            }).setNegativeButton(R.string.cancel, (DialogInterface d, int id) -> {
                if (extension.upgrade) {
                    ExtensionManager.getTmpFileForExtensionName(getContext(), extension.name)
                            .delete();
                } else {
                    ExtensionManager.removeExtension(ExtensionManager.getPathForExtensionName(
                            requireContext(), extension.name));
                }
                d.cancel();
            });
            builder.create().show();
        }
    }

    private void updateExtensionList() {
        extensionList.clear();

        final String path = requireContext().getApplicationInfo().dataDir + "/extensions/";
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
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
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
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {
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
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
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
