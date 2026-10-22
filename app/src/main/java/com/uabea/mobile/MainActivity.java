package com.uabea.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.uabea.mobile.core.Asset;
import com.uabea.mobile.core.AssetExtractor;
import com.uabea.mobile.core.AssetIndex;
import com.uabea.mobile.core.DataDump;
import com.uabea.mobile.core.Fsb5;
import com.uabea.mobile.core.Png;
import com.uabea.mobile.core.TextureDecoder;
import com.uabea.mobile.core.UnityBundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Layar utama bergaya UABEA: menu, daftar aset, aksi, dan jendela info. */
public final class MainActivity extends Activity {
    private static final int REQ_OPEN = 1;
    private static final int REQ_EXPORT = 2;
    private static final int REQ_REPLACE = 3;
    private static final int REQ_SAVE = 4;
    private static final int REQ_EXPORT_ALL = 5;
    private static final int REQ_IMPORT_ALL = 6;
    private static final String PREFS = "uabea";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private Ui ui;
    private boolean dark;
    private SharedPreferences prefs;

    private LinearLayout root;
    private LinearLayout menuBar;
    private LinearLayout fileHeader;
    private LinearLayout fileCombo;
    private LinearLayout searchArea;
    private LinearLayout tableHeader;
    private LinearLayout statusBar;
    private TextView menuFile;
    private TextView menuView;
    private TextView menuTools;
    private TextView menuHelp;
    private TextView menuBrand;
    private View menuDivider;
    private View headerDividerTop;
    private View headerDividerBottom;
    private TextView titleView;
    private TextView fileSub;
    private TextView fileComboLabel;
    private TextView fileComboValue;
    private TextView fileComboArrow;
    private ImageView searchIcon;
    private TextView btnClear;
    private TextView btnFilter;
    private TextView hdrName;
    private TextView hdrType;
    private TextView hdrSize;
    private TextView emptyView;
    private TextView statusView;
    private TextView statusRight;
    private EditText searchBox;
    private ListView listView;
    private ProgressBar progress;
    private Button btnExport;
    private Button btnExportAll;
    private Button btnRename;
    private Button btnImport;
    private Button btnImportAll;
    private Button btnRemove;
    private Button btnInfo;

    private AssetAdapter adapter;
    private AssetIndex index;
    private Uri bundleUri;
    private String bundleName = "";
    private long bundleSize;
    private Asset selected;
    private int fileFilter = -1;
    private final HashSet<String> typeFilter = new HashSet<>();
    private int sortMode;
    private boolean busy;
    private PopupWindow openPopup;

    private final ArrayList<Asset> shown = new ArrayList<>();
    private final ArrayList<String> fileLabels = new ArrayList<>();

    private byte[] pendingExport;
    private String pendingExportName;
    private String pendingExportMime;
    private byte[] pendingSave;
    private Asset pendingReplace;
    private MediaPlayer player;
    private volatile int audioToken;

    private interface Job {
        void run() throws Exception;
    }

    private interface MenuAction {
        void on(int index);
    }

    private static final class ExportFile {
        byte[] data;
        String name;
        String mime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, 0);
        dark = prefs.contains("dark") ? prefs.getBoolean("dark", true) : systemIsDark();
        ui = new Ui(dark);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        setContentView(R.layout.activity_main);
        bindViews();
        applyTheme();
        adapter = new AssetAdapter(this, ui);
        listView.setAdapter(adapter);
        wireListeners();
        updateFileHeader();
        updateCombo();
        applyFilter();

        Uri fromIntent = getIntent() != null ? getIntent().getData() : null;
        if (fromIntent != null) {
            bundleUri = fromIntent;
            openBundle(fromIntent);
        }
    }

    private boolean systemIsDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        menuBar = findViewById(R.id.menuBar);
        fileHeader = findViewById(R.id.fileHeader);
        fileCombo = findViewById(R.id.fileCombo);
        searchArea = findViewById(R.id.searchArea);
        tableHeader = findViewById(R.id.tableHeader);
        statusBar = findViewById(R.id.statusBar);
        menuFile = findViewById(R.id.menuFile);
        menuView = findViewById(R.id.menuView);
        menuTools = findViewById(R.id.menuTools);
        menuHelp = findViewById(R.id.menuHelp);
        menuBrand = findViewById(R.id.menuBrand);
        menuDivider = findViewById(R.id.menuDivider);
        headerDividerTop = findViewById(R.id.headerDividerTop);
        headerDividerBottom = findViewById(R.id.headerDividerBottom);
        titleView = findViewById(R.id.title);
        fileSub = findViewById(R.id.fileSub);
        fileComboLabel = findViewById(R.id.fileComboLabel);
        fileComboValue = findViewById(R.id.fileComboValue);
        fileComboArrow = findViewById(R.id.fileComboArrow);
        searchIcon = findViewById(R.id.searchIcon);
        btnClear = findViewById(R.id.btnClear);
        btnFilter = findViewById(R.id.btnFilter);
        hdrName = findViewById(R.id.hdrName);
        hdrType = findViewById(R.id.hdrType);
        hdrSize = findViewById(R.id.hdrSize);
        emptyView = findViewById(R.id.empty);
        statusView = findViewById(R.id.status);
        statusRight = findViewById(R.id.statusRight);
        searchBox = findViewById(R.id.search);
        listView = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        btnExport = findViewById(R.id.btnExport);
        btnExportAll = findViewById(R.id.btnExportAll);
        btnRename = findViewById(R.id.btnRename);
        btnImport = findViewById(R.id.btnImport);
        btnImportAll = findViewById(R.id.btnImportAll);
        btnRemove = findViewById(R.id.btnRemove);
        btnInfo = findViewById(R.id.btnInfo);
    }

    // ---------------------------------------------------------------- tema

    private void applyTheme() {
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(ui.bg));
        int statusColor = ui.panel;
        int navColor = ui.panel;
        if (!ui.dark && Build.VERSION.SDK_INT < 23) {
            statusColor = 0xFF202020;
        }
        if (!ui.dark && Build.VERSION.SDK_INT < 26) {
            navColor = 0xFF202020;
        }
        w.setStatusBarColor(statusColor);
        w.setNavigationBarColor(navColor);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (ui.dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);

        root.setBackgroundColor(ui.bg);
        menuBar.setBackgroundColor(ui.panel);
        menuDivider.setBackgroundColor(ui.border);
        styleMenuText(menuFile);
        styleMenuText(menuView);
        styleMenuText(menuTools);
        styleMenuText(menuHelp);
        menuBrand.setTextColor(ui.muted);
        menuBrand.setTypeface(ui.sans);

        fileHeader.setBackgroundColor(ui.bg);
        titleView.setTextColor(ui.text);
        titleView.setTypeface(ui.sansMedium);
        fileSub.setTextColor(ui.muted);
        fileSub.setTypeface(ui.sans);

        fileCombo.setBackground(ui.ripple(this, ui.panelAlt, ui.border,
                ui.alpha(ui.accent, 0x22), 2));
        fileComboLabel.setTextColor(ui.muted);
        fileComboValue.setTextColor(ui.text);
        fileComboArrow.setTextColor(ui.muted);

        searchArea.setBackgroundColor(ui.bg);
        searchIcon.setColorFilter(ui.muted, PorterDuff.Mode.SRC_IN);
        btnClear.setTextColor(ui.muted);
        btnClear.setBackground(ui.ripple(this, Color.TRANSPARENT, 0, ui.alpha(ui.accent, 0x22), 0));
        ui.input(searchBox);
        styleFilterButton();

        tableHeader.setBackgroundColor(ui.header);
        hdrName.setTextColor(ui.muted);
        hdrType.setTextColor(ui.muted);
        hdrSize.setTextColor(ui.muted);
        hdrName.setTypeface(ui.sansMedium);
        hdrType.setTypeface(ui.sansMedium);
        hdrSize.setTypeface(ui.sansMedium);
        headerDividerTop.setBackgroundColor(ui.border);
        headerDividerBottom.setBackgroundColor(ui.border);

        listView.setBackgroundColor(ui.bg);
        listView.setDivider(new ColorDrawable(ui.border));
        listView.setDividerHeight(dp(1));
        emptyView.setTextColor(ui.muted);

        statusBar.setBackgroundColor(ui.statusBg);
        statusView.setTextColor(ui.muted);
        statusRight.setTextColor(ui.muted);
        ui.tint(progress);

        ui.actionButton(btnExport, false);
        ui.actionButton(btnExportAll, false);
        ui.actionButton(btnRename, false);
        ui.actionButton(btnImport, false);
        ui.actionButton(btnImportAll, false);
        ui.actionButton(btnRemove, false);
        ui.actionButton(btnInfo, false);
        btnExportAll.setTextSize(10.5f);
        btnImportAll.setTextSize(10.5f);
        btnRename.setTextSize(10.5f);
        btnInfo.setTextSize(13.5f);
        btnInfo.setTypeface(ui.sansMedium);
        updateButtonStates();
    }

    private void styleMenuText(TextView tv) {
        tv.setTextColor(ui.text);
        tv.setTypeface(ui.sans);
        tv.setBackground(ui.ripple(this, Color.TRANSPARENT, 0, ui.alpha(ui.accent, 0x22), 0));
        tv.setClickable(true);
        tv.setFocusable(true);
    }

    private void styleFilterButton() {
        boolean active = !typeFilter.isEmpty();
        btnFilter.setText(active ? "Filter (" + typeFilter.size() + ")" : "Filter");
        btnFilter.setTextColor(active ? ui.accent : ui.muted);
        btnFilter.setTypeface(ui.sansMedium);
        btnFilter.setBackground(ui.ripple(this, Color.TRANSPARENT, 0, ui.alpha(ui.accent, 0x22), 2));
    }

    private void toggleTheme() {
        dark = !dark;
        prefs.edit().putBoolean("dark", dark).apply();
        dismissPopup();
        ui = new Ui(dark);
        if (adapter != null) {
            adapter.setUi(ui);
        }
        applyTheme();
        updateFileHeader();
        updateCombo();
        applyFilter();
    }

    // ------------------------------------------------------------- listener

    private void wireListeners() {
        menuFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileMenu();
            }
        });
        menuView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showViewMenu();
            }
        });
        menuTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToolsMenu();
            }
        });
        menuHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelpMenu();
            }
        });
        titleView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showAbout();
                return true;
            }
        });
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selected == null) {
                    toast("Pilih aset terlebih dahulu.");
                } else {
                    exportAsset(selected);
                }
            }
        });
        btnExportAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportAllPick();
            }
        });
        btnRename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selected == null) {
                    toast("Pilih aset terlebih dahulu.");
                } else {
                    showRenameDialog(selected);
                }
            }
        });
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selected == null) {
                    toast("Pilih aset terlebih dahulu.");
                } else {
                    startReplace(selected);
                }
            }
        });
        btnImportAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importAllPick();
            }
        });
        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("Hapus aset belum didukung di versi Android ini.");
            }
        });
        btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selected == null) {
                    toast("Pilih aset terlebih dahulu.");
                } else {
                    showInfo(selected);
                }
            }
        });
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (busy) {
                    toast("Tunggu proses selesai terlebih dahulu.");
                    return;
                }
                Asset a = adapter.getItem(position);
                if (a == selected) {
                    selectAsset(a, true);
                } else {
                    selectAsset(a, false);
                }
            }
        });
        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (busy) {
                    toast("Tunggu proses selesai terlebih dahulu.");
                    return true;
                }
                Asset a = adapter.getItem(position);
                selectAsset(a, false);
                showAssetMenu(view, a);
                return true;
            }
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchBox.setText("");
            }
        });
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog();
            }
        });
        fileCombo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileDialog();
            }
        });
        hdrName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = (sortMode == 0) ? 1 : 0;
                applyFilter();
            }
        });
        hdrType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = (sortMode == 2) ? 3 : 2;
                applyFilter();
            }
        });
        hdrSize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = (sortMode == 4) ? 5 : 4;
                applyFilter();
            }
        });
    }

    // ---------------------------------------------------------------- menu

    private void showFileMenu() {
        String[] items = {"Buka…", "Tutup", null, "Simpan", "Simpan sebagai…", null, "Keluar"};
        boolean[] en = {!busy, index != null && !busy, true, index != null && !busy,
                index != null && !busy, true, !busy};
        showPopup(menuFile, items, en, null, new MenuAction() {
            @Override
            public void on(int i) {
                switch (i) {
                    case 0:
                        pickBundle();
                        break;
                    case 1:
                        closeBundle();
                        break;
                    case 3:
                        saveBundle(true);
                        break;
                    case 4:
                        saveBundle(false);
                        break;
                    case 6:
                        confirmExit();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void showViewMenu() {
        String[] items = {"Cari berdasarkan nama", "Lanjutkan pencarian", null,
                "Filter tipe…", "Tema gelap"};
        boolean[] en = {index != null && !busy, index != null && !busy, true,
                index != null && !busy, !busy};
        boolean[] checked = {false, false, false, !typeFilter.isEmpty(), dark};
        showPopup(menuView, items, en, checked, new MenuAction() {
            @Override
            public void on(int i) {
                switch (i) {
                    case 0:
                        focusSearch();
                        break;
                    case 1:
                        findNext();
                        break;
                    case 3:
                        showFilterDialog();
                        break;
                    case 4:
                        toggleTheme();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void showToolsMenu() {
        String[] items = {"Info aset", "Lihat data", "Pohon tipe", null,
                "Ekspor semua…", "Impor semua…"};
        boolean[] en = {selected != null && !busy, selected != null && !busy,
                selected != null && !busy, true, index != null && !busy,
                index != null && !busy};
        showPopup(menuTools, items, en, null, new MenuAction() {
            @Override
            public void on(int i) {
                switch (i) {
                    case 0:
                        showInfo(selected);
                        break;
                    case 1:
                        showData(selected);
                        break;
                    case 2:
                        showTypeTree(selected);
                        break;
                    case 4:
                        exportAllPick();
                        break;
                    case 5:
                        importAllPick();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void showHelpMenu() {
        showPopup(menuHelp, new String[]{"Tentang UABEA Mobile"}, new boolean[]{true}, null, new MenuAction() {
            @Override
            public void on(int i) {
                showAbout();
            }
        });
    }

    private void showPopup(View anchor, String[] items, boolean[] enabled, boolean[] checked,
                           final MenuAction action) {
        dismissPopup();
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(ui.box(this, ui.panel, ui.border, 2));
        box.setPadding(0, dp(3), 0, dp(3));
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                View d = new View(this);
                d.setBackgroundColor(ui.border);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                dlp.topMargin = dp(3);
                dlp.bottomMargin = dp(3);
                box.addView(d, dlp);
                continue;
            }
            final int which = i;
            TextView tv = new TextView(this);
            boolean on = enabled == null || enabled[i];
            String prefix = checked != null && checked[i] ? "\u2713  " : "      ";
            tv.setText(prefix + items[i]);
            ui.menuItem(tv);
            tv.setAlpha(on ? 1f : 0.4f);
            tv.setEnabled(on);
            if (on) {
                tv.setBackground(ui.ripple(this, Color.TRANSPARENT, 0, ui.alpha(ui.accent, 0x22), 0));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismissPopup();
                        action.on(which);
                    }
                });
            }
            box.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));
        }
        final PopupWindow pw = new PopupWindow(box, dp(226), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(10));
        pw.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (openPopup == pw) {
                    openPopup = null;
                }
            }
        });
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int xoff = 0;
        if (loc[0] + dp(226) > screenW) {
            xoff = Math.min(0, anchor.getWidth() - dp(226));
        }
        pw.showAsDropDown(anchor, xoff, dp(1));
        openPopup = pw;
    }

    private void dismissPopup() {
        if (openPopup != null) {
            try {
                openPopup.dismiss();
            } catch (Exception ignored) {
                // sudah ditutup
            }
            openPopup = null;
        }
    }

    private void focusSearch() {
        searchBox.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void findNext() {
        if (shown.isEmpty()) {
            return;
        }
        int start = 0;
        if (selected != null) {
            int pos = shown.indexOf(selected);
            if (pos >= 0) {
                start = pos + 1;
            }
        }
        Asset next = shown.get(start % shown.size());
        selectAsset(next, false);
        listView.setSelection(shown.indexOf(next));
    }

    private void showAssetMenu(View anchor, final Asset a) {
        String[] items = {"Info", "Ekspor", "Impor", "Ganti nama", null,
                "Lihat data", "Pohon tipe", null, "Hapus"};
        boolean[] en = {true, true, a.classId == 49 || a.classId == 28, true, true, true, true,
                true, true};
        showPopup(anchor, items, en, null, new MenuAction() {
            @Override
            public void on(int i) {
                switch (i) {
                    case 0:
                        showInfo(a);
                        break;
                    case 1:
                        exportAsset(a);
                        break;
                    case 2:
                        startReplace(a);
                        break;
                    case 3:
                        showRenameDialog(a);
                        break;
                    case 5:
                        showData(a);
                        break;
                    case 6:
                        showTypeTree(a);
                        break;
                    case 8:
                        toast("Hapus aset belum didukung di versi Android ini.");
                        break;
                    default:
                        break;
                }
            }
        });
    }

    // -------------------------------------------------------------- file/buka

    private void pickBundle() {
        if (busy) {
            return;
        }
        if (index != null && hasUnsaved()) {
            AlertDialog d = themed().setTitle("Buka file")
                    .setMessage("Ada perubahan yang belum disimpan. Buka file lain tanpa menyimpan?")
                    .setPositiveButton("Buka", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            launchOpenPicker();
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .create();
            showThemed(d);
        } else {
            launchOpenPicker();
        }
    }

    private void launchOpenPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    private void openBundle(final Uri uri) {
        job("Membaca bundle…", new Job() {
            @Override
            public void run() throws Exception {
                final byte[] data = readAll(uri);
                final AssetIndex idx = AssetIndex.open(data);
                final String name = queryName(uri);
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        index = idx;
                        bundleUri = uri;
                        bundleName = name;
                        bundleSize = data.length;
                        selected = null;
                        fileFilter = -1;
                        typeFilter.clear();
                        sortMode = 0;
                        searchBox.setText("");
                        buildFileLabels();
                        adapter.setSelected(null);
                        updateFileHeader();
                        updateCombo();
                        applyFilter();
                        setBusy(null);
                        setStatusOnly(idx.assets.size() + " aset dimuat dari " + name);
                        toast("Bundle berhasil dibuka.");
                    }
                });
            }
        });
    }

    private void buildFileLabels() {
        fileLabels.clear();
        if (index == null) {
            return;
        }
        for (UnityBundle.Node n : index.bundle.nodes) {
            if (n.sf == null) {
                continue;
            }
            String name;
            if (index.bundle.standalone) {
                name = "SerializedFile";
            } else {
                name = n.path == null || n.path.isEmpty() ? "data" : n.fileName();
            }
            fileLabels.add(name + "  (" + n.sf.objects.size() + " aset)");
        }
    }

    private void closeBundle() {
        if (index == null) {
            return;
        }
        if (hasUnsaved()) {
            AlertDialog d = themed().setTitle("Tutup file")
                    .setMessage("Ada perubahan yang belum disimpan. Tutup tanpa menyimpan?")
                    .setPositiveButton("Tutup", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            doCloseBundle();
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .create();
            showThemed(d);
        } else {
            doCloseBundle();
        }
    }

    private void doCloseBundle() {
        stopAudio();
        index = null;
        bundleUri = null;
        bundleName = "";
        bundleSize = 0;
        selected = null;
        fileFilter = -1;
        typeFilter.clear();
        searchBox.setText("");
        fileLabels.clear();
        shown.clear();
        adapter.setSelected(null);
        adapter.setAssets(new ArrayList<Asset>());
        updateFileHeader();
        updateCombo();
        applyFilter();
        setStatusOnly("Siap");
    }

    private String queryName(Uri uri) {
        String result = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    result = c.getString(i);
                }
            }
        } catch (Exception ignored) {
            // pakai nama dari uri
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (result == null || result.isEmpty()) {
            result = uri.getLastPathSegment();
        }
        return result == null ? "bundle" : result;
    }

    private byte[] readAll(Uri uri) throws Exception {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IllegalStateException("Tidak bisa membuka file: " + uri);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 512 * 1024 * 1024) {
                    throw new IllegalStateException("File terlalu besar (>512 MB)");
                }
            }
            return out.toByteArray();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void writeUri(Uri uri, byte[] data) throws Exception {
        OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                throw new IllegalStateException("Tidak bisa membuka tujuan tulis");
            }
            out.write(data);
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    // ------------------------------------------------------------- daftar

    private void applyFilter() {
        if (index == null) {
            shown.clear();
            adapter.setAssets(shown);
            emptyView.setText("Belum ada file dibuka.\n\nGunakan File > Buka… untuk memilih bundle.");
            emptyView.setVisibility(View.VISIBLE);
            if (!busy) {
                setStatusOnly("Siap");
                statusRight.setText("v1.0");
            }
            updateFileHeader();
            updateCombo();
            updateButtonStates();
            return;
        }
        String q = searchBox.getText().toString();
        ArrayList<Asset> result = new ArrayList<>();
        for (Asset a : index.search(q)) {
            if (fileFilter >= 0 && a.fileId != fileFilter) {
                continue;
            }
            if (!typeFilter.isEmpty() && !typeFilter.contains(a.typeName)) {
                continue;
            }
            result.add(a);
        }
        sortAssets(result);
        shown.clear();
        shown.addAll(result);
        adapter.setAssets(result);
        if (selected != null && !result.contains(selected)) {
            selected = null;
            adapter.setSelected(null);
        }
        if (result.isEmpty()) {
            emptyView.setText(index.assets.isEmpty()
                    ? "Tidak ada aset di file ini."
                    : "Tidak ada aset yang cocok dengan pencarian / filter.");
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
        if (!busy) {
            setStatusOnly(result.size() + " aset" + (result.size() == index.assets.size()
                    ? "" : " dari " + index.assets.size())
                    + (typeFilter.isEmpty() ? "" : " \u00b7 filter aktif"));
        }
        updateFileHeader();
        updateCombo();
        updateButtonStates();
        updateHeaderArrows();
    }

    private void sortAssets(ArrayList<Asset> list) {
        Comparator<Asset> cmp;
        switch (sortMode) {
            case 1:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        return b.displayName().compareToIgnoreCase(a.displayName());
                    }
                };
                break;
            case 2:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        int c = a.typeName.compareToIgnoreCase(b.typeName);
                        return c != 0 ? c : a.displayName().compareToIgnoreCase(b.displayName());
                    }
                };
                break;
            case 3:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        int c = b.typeName.compareToIgnoreCase(a.typeName);
                        return c != 0 ? c : a.displayName().compareToIgnoreCase(b.displayName());
                    }
                };
                break;
            case 4:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        return Long.compare(b.obj.byteSize, a.obj.byteSize);
                    }
                };
                break;
            case 5:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        return Long.compare(a.obj.byteSize, b.obj.byteSize);
                    }
                };
                break;
            default:
                cmp = new Comparator<Asset>() {
                    @Override
                    public int compare(Asset a, Asset b) {
                        return a.displayName().compareToIgnoreCase(b.displayName());
                    }
                };
        }
        Collections.sort(list, cmp);
    }

    private void updateHeaderArrows() {
        hdrName.setText("Nama" + (sortMode == 0 ? "  \u25B2" : sortMode == 1 ? "  \u25BC" : ""));
        hdrType.setText("Tipe" + (sortMode == 2 ? "  \u25B2" : sortMode == 3 ? "  \u25BC" : ""));
        hdrSize.setText("Ukuran" + (sortMode == 4 ? "  \u25BC" : sortMode == 5 ? "  \u25B2" : ""));
    }

    private void selectAsset(Asset a, boolean openInfo) {
        selected = a;
        adapter.setSelected(a);
        updateButtonStates();
        if (!busy) {
            if (a == null) {
                setStatusOnly("Tidak ada aset dipilih");
            } else {
                setStatusOnly("Dipilih: " + a.displayName() + " \u00b7 " + a.typeName);
            }
        }
        if (openInfo && a != null) {
            showInfo(a);
        }
    }

    private void updateButtonStates() {
        boolean has = selected != null;
        boolean hasIndex = index != null;
        setEnabled(btnExport, has && !busy);
        setEnabled(btnExportAll, hasIndex && !busy);
        setEnabled(btnRename, has && !busy);
        boolean canImport = has && (selected.classId == 49 || selected.classId == 28);
        setEnabled(btnImport, canImport && !busy);
        setEnabled(btnImportAll, hasIndex && !busy);
        setEnabled(btnRemove, has && !busy);
        setEnabled(btnInfo, has && !busy);
        fileCombo.setAlpha(hasIndex ? 1f : 0.5f);
        styleFilterButton();
    }

    private void setEnabled(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.42f);
    }

    private void updateFileHeader() {
        if (index == null) {
            titleView.setText("Tidak ada file dibuka.");
            fileSub.setText("File > Buka… untuk memilih bundle.");
            statusRight.setText("v1.0");
            return;
        }
        String t = (bundleName == null || bundleName.isEmpty() ? "bundle" : bundleName)
                + (hasUnsaved() ? "  *" : "");
        if (hasUnsaved()) {
            SpannableString sp = new SpannableString(t);
            sp.setSpan(new ForegroundColorSpan(ui.accent), t.length() - 1, t.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            titleView.setText(sp);
        } else {
            titleView.setText(t);
        }
        int files = fileLabels.size();
        fileSub.setText(index.assets.size() + " aset \u00b7 " + files + " berkas data \u00b7 "
                + Util.formatSize(bundleSize) + (hasUnsaved() ? " \u00b7 belum disimpan" : ""));
        statusRight.setText(files + " berkas \u00b7 " + Util.formatSize(bundleSize));
    }

    private void updateCombo() {
        if (index == null) {
            fileComboValue.setText("—");
            return;
        }
        if (fileFilter < 0 || fileFilter >= fileLabels.size()) {
            fileComboValue.setText("Semua berkas data  (" + index.assets.size() + " aset)");
        } else {
            fileComboValue.setText(fileLabels.get(fileFilter));
        }
    }

    private void showFileDialog() {
        if (index == null) {
            return;
        }
        final ArrayList<String> choices = new ArrayList<>();
        choices.add("Semua berkas data");
        choices.addAll(fileLabels);
        int sel = fileFilter + 1;
        AlertDialog d = themed().setTitle("Berkas data")
                .setSingleChoiceItems(choices.toArray(new String[0]), sel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fileFilter = which - 1;
                                dialog.dismiss();
                                applyFilter();
                            }
                        })
                .setNegativeButton("Batal", null)
                .create();
        showThemed(d);
    }

    private void showFilterDialog() {
        if (index == null) {
            return;
        }
        final ArrayList<String> types = new ArrayList<>();
        final HashMap<String, Integer> counts = new HashMap<>();
        for (Asset a : index.assets) {
            Integer c = counts.get(a.typeName);
            counts.put(a.typeName, c == null ? 1 : c + 1);
        }
        types.addAll(counts.keySet());
        Collections.sort(types, String.CASE_INSENSITIVE_ORDER);
        final String[] labels = new String[types.size()];
        final boolean[] checked = new boolean[types.size()];
        for (int i = 0; i < types.size(); i++) {
            labels[i] = types.get(i) + "  (" + counts.get(types.get(i)) + ")";
            checked[i] = typeFilter.contains(types.get(i));
        }
        final boolean[] sel = checked.clone();
        AlertDialog d = themed().setTitle("Filter tipe aset")
                .setMultiChoiceItems(labels, sel, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        sel[which] = isChecked;
                    }
                })
                .setPositiveButton("Terapkan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        typeFilter.clear();
                        for (int i = 0; i < sel.length; i++) {
                            if (sel[i]) {
                                typeFilter.add(types.get(i));
                            }
                        }
                        applyFilter();
                    }
                })
                .setNeutralButton("Bersihkan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        typeFilter.clear();
                        applyFilter();
                    }
                })
                .setNegativeButton("Batal", null)
                .create();
        showThemed(d);
    }

    // -------------------------------------------------------------- simpan

    private void saveBundle(final boolean overwrite) {
        if (index == null) {
            return;
        }
        job("Menyusun bundle…", new Job() {
            @Override
            public void run() throws Exception {
                final byte[] out = index.bundle.save();
                if (overwrite) {
                    try {
                        writeUri(bundleUri, out);
                    } catch (final Exception e) {
                        pendingSave = out;
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setBusy(null);
                                toast("Tidak bisa menimpa file asli: " + Util.errorMessage(e)
                                        + "\nPilih lokasi file baru.");
                                createDocument(suggestedBundleName(), "application/octet-stream", REQ_SAVE);
                            }
                        });
                        return;
                    }
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(null);
                            bundleSize = out.length;
                            clearModified();
                            updateFileHeader();
                            adapter.notifyDataSetChanged();
                            setStatusOnly("Bundle ditimpa \u00b7 " + Util.formatSize(out.length));
                            toast("Bundle berhasil disimpan.");
                        }
                    });
                } else {
                    pendingSave = out;
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(null);
                            createDocument(suggestedBundleName(), "application/octet-stream", REQ_SAVE);
                        }
                    });
                }
            }
        });
    }

    private String suggestedBundleName() {
        String base = bundleName == null || bundleName.isEmpty() ? "bundle" : bundleName;
        if (base.contains(".")) {
            int i = base.lastIndexOf('.');
            return Util.sanitizeFileName(base.substring(0, i)) + "_mod." + base.substring(i + 1);
        }
        return Util.sanitizeFileName(base) + "_mod.bundle";
    }

    private void clearModified() {
        if (index == null) {
            return;
        }
        for (Asset a : index.assets) {
            a.modified = false;
        }
        for (UnityBundle.Node n : index.bundle.nodes) {
            n.modified = false;
        }
    }

    private boolean hasUnsaved() {
        if (index == null) {
            return false;
        }
        for (Asset a : index.assets) {
            if (a.modified) {
                return true;
            }
        }
        return false;
    }

    private void createDocument(String name, String mime, int request) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, request);
        } catch (Exception e) {
            toast("Tidak bisa membuka pemilih berkas: " + Util.errorMessage(e));
        }
    }

    // ------------------------------------------------------------ ekspor

    private ExportFile buildExport(Asset a) throws Exception {
        ExportFile e = new ExportFile();
        String base = Util.sanitizeFileName(a.displayName());
        if (a.classId == 28) {
            AssetExtractor.Texture t = AssetExtractor.texture(a);
            int[] px = t.pixels;
            if (px == null) {
                px = GpuTextureDecoder.decode(t.format, t.width, t.height, t.rawData);
            }
            if (px != null) {
                e.data = Png.encode(px, t.width, t.height);
                e.name = base + ".png";
                e.mime = "image/png";
            } else {
                e.data = t.rawData;
                e.name = base + ".tex";
                e.mime = "application/octet-stream";
            }
        } else if (a.classId == 49) {
            e.data = AssetExtractor.textBytes(a);
            e.name = base + ".txt";
            e.mime = "text/plain";
        } else if (a.classId == 83) {
            Fsb5.Result r = AssetExtractor.audio(a);
            e.data = r.data;
            e.name = base + "." + r.extension;
            e.mime = r.extension.equals("wav") ? "audio/wav"
                    : (r.extension.equals("mp3") ? "audio/mpeg"
                    : (r.extension.equals("ogg") ? "audio/ogg" : "application/octet-stream"));
        } else {
            e.data = a.raw();
            e.name = base + ".bin";
            e.mime = "application/octet-stream";
        }
        return e;
    }

    private void exportAsset(final Asset a) {
        job("Menyiapkan ekspor…", new Job() {
            @Override
            public void run() throws Exception {
                final ExportFile e = buildExport(a);
                pendingExport = e.data;
                pendingExportName = e.name;
                pendingExportMime = e.mime;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(null);
                        setStatusOnly("Ekspor " + e.name + " \u00b7 " + Util.formatSize(e.data.length));
                        createDocument(e.name, e.mime, REQ_EXPORT);
                    }
                });
            }
        });
    }

    private ArrayList<Asset> currentFileAssets() {
        ArrayList<Asset> list = new ArrayList<>();
        if (index == null) {
            return list;
        }
        for (Asset a : index.assets) {
            if (fileFilter < 0 || a.fileId == fileFilter) {
                list.add(a);
            }
        }
        return list;
    }

    private void exportAllPick() {
        if (index == null || busy) {
            return;
        }
        if (currentFileAssets().isEmpty()) {
            toast("Tidak ada aset untuk diekspor.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_EXPORT_ALL);
    }

    private void exportAll(final Uri tree) {
        final ArrayList<Asset> list = currentFileAssets();
        job("Mengekspor semua…", new Job() {
            @Override
            public void run() throws Exception {
                final int total = list.size();
                final HashMap<String, Integer> names = new HashMap<>();
                for (Asset a : list) {
                    String n = Util.sanitizeFileName(a.displayName());
                    Integer c = names.get(n);
                    names.put(n, c == null ? 1 : c + 1);
                }
                int ok = 0;
                int fail = 0;
                for (int i = 0; i < total; i++) {
                    Asset a = list.get(i);
                    try {
                        ExportFile e = buildExport(a);
                        if (names.get(Util.sanitizeFileName(a.displayName())) > 1) {
                            e.name = addSuffix(e.name, "_" + a.pathId);
                        }
                        writeDocument(tree, e.name, e.mime, e.data);
                        ok++;
                    } catch (Throwable t) {
                        fail++;
                    }
                    final int done = i + 1;
                    final int okNow = ok;
                    final int failNow = fail;
                    if (done == total || done % 5 == 0) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setStatusOnly("Mengekspor " + done + "/" + total + "…");
                            }
                        });
                    }
                }
                final int okFinal = ok;
                final int failFinal = fail;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(null);
                        setStatusOnly("Ekspor semua: " + okFinal + " berhasil"
                                + (failFinal > 0 ? ", " + failFinal + " gagal" : ""));
                        toast("Ekspor semua selesai: " + okFinal + " berhasil"
                                + (failFinal > 0 ? ", " + failFinal + " gagal" : "") + ".");
                    }
                });
            }
        });
    }

    private static String addSuffix(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot) + suffix + name.substring(dot);
        }
        return name + suffix;
    }

    private void writeDocument(Uri tree, String name, String mime, byte[] data) throws Exception {
        Uri doc = DocumentsContract.createDocument(getContentResolver(), tree, mime, name);
        if (doc == null) {
            throw new IOException("Gagal membuat dokumen " + name);
        }
        OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(doc, "w");
            if (out == null) {
                throw new IOException("Gagal membuka " + name);
            }
            out.write(data);
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    // -------------------------------------------------------------- impor

    private void importAllPick() {
        if (index == null || busy) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_IMPORT_ALL);
    }

    private void importAll(final Uri tree) {
        final ArrayList<Asset> list = currentFileAssets();
        job("Membaca folder…", new Job() {
            @Override
            public void run() throws Exception {
                final Map<String, Uri> files = listTreeFiles(tree);
                int ok = 0;
                int fail = 0;
                int skip = 0;
                for (Asset a : list) {
                    if (a.classId != 49 && a.classId != 28) {
                        skip++;
                        continue;
                    }
                    String base = Util.sanitizeFileName(a.displayName()).toLowerCase(Locale.ROOT);
                    Uri u = null;
                    if (a.classId == 49) {
                        u = files.get(base + ".txt");
                    } else if (a.classId == 28) {
                        u = files.get(base + ".png");
                        if (u == null) {
                            u = files.get(base + ".jpg");
                        }
                        if (u == null) {
                            u = files.get(base + ".jpeg");
                        }
                    }
                    if (u == null) {
                        u = files.get(base);
                    }
                    if (u == null) {
                        u = files.get(base + "_" + a.pathId);
                    }
                    if (u == null) {
                        skip++;
                        continue;
                    }
                    try {
                        applyReplaceSync(a, readAll(u));
                        ok++;
                    } catch (Throwable t) {
                        fail++;
                    }
                }
                final int okFinal = ok;
                final int failFinal = fail;
                final int skipFinal = skip;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(null);
                        updateFileHeader();
                        adapter.notifyDataSetChanged();
                        applyFilter();
                        setStatusOnly("Impor semua: " + okFinal + " diganti"
                                + (failFinal > 0 ? ", " + failFinal + " gagal" : "")
                                + (skipFinal > 0 ? ", " + skipFinal + " dilewati" : ""));
                        toast("Impor semua selesai: " + okFinal + " aset diganti.");
                    }
                });
            }
        });
    }

    private Map<String, Uri> listTreeFiles(Uri tree) throws Exception {
        HashMap<String, Uri> map = new HashMap<>();
        String docId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = null;
        try {
            c = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    String id = c.getString(1);
                    String mime = c.getString(2);
                    if (name == null || id == null || DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        continue;
                    }
                    Uri u = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    String lower = name.toLowerCase(Locale.ROOT);
                    String base = stripExtension(lower);
                    if (!map.containsKey(base)) {
                        map.put(base, u);
                    }
                    if (!map.containsKey(lower)) {
                        map.put(lower, u);
                    }
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return map;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void startReplace(Asset a) {
        if (a == null) {
            return;
        }
        if (a.classId != 49 && a.classId != 28) {
            toast("Penggantian isi baru didukung untuk TextAsset dan Texture2D.");
            return;
        }
        pendingReplace = a;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(a.classId == 28 ? "image/*" : "*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_REPLACE);
    }

    private void applyReplace(final Asset a, final byte[] bytes) {
        job("Mengganti aset…", new Job() {
            @Override
            public void run() throws Exception {
                applyReplaceSync(a, bytes);
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(null);
                        adapter.notifyDataSetChanged();
                        applyFilter();
                        updateFileHeader();
                        setStatusOnly("Aset diganti. Gunakan File > Simpan untuk menulis bundle.");
                        toast("Aset berhasil diganti (belum disimpan).");
                    }
                });
            }
        });
    }

    private void applyReplaceSync(Asset a, byte[] bytes) throws Exception {
        if (a.classId == 49) {
            AssetExtractor.replaceText(a, bytes);
        } else if (a.classId == 28) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                throw new IllegalArgumentException("Gambar tidak bisa dibaca");
            }
            if ((long) o.outWidth * o.outHeight > 16L * 1024 * 1024) {
                throw new IllegalArgumentException("Gambar terlalu besar (maks. 16 megapiksel)");
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) {
                throw new IllegalArgumentException("Gambar tidak bisa dibaca");
            }
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 16L * 1024 * 1024) {
                bmp.recycle();
                throw new IllegalArgumentException("Gambar terlalu besar (maks. 16 megapiksel)");
            }
            int[] argb = new int[w * h];
            bmp.getPixels(argb, 0, w, 0, 0, w, h);
            bmp.recycle();
            byte[] rgba = new byte[w * h * 4];
            for (int i = 0; i < argb.length; i++) {
                int col = argb[i];
                rgba[i * 4] = (byte) (col >> 16);
                rgba[i * 4 + 1] = (byte) (col >> 8);
                rgba[i * 4 + 2] = (byte) col;
                rgba[i * 4 + 3] = (byte) (col >>> 24);
            }
            AssetExtractor.replaceTexture(a, w, h, rgba);
        } else {
            throw new IllegalArgumentException("Tipe aset ini belum bisa diganti");
        }
        a.modified = true;
    }

    // ------------------------------------------------------------- ganti nama

    private void showRenameDialog(final Asset a) {
        if (a == null) {
            return;
        }
        final EditText input = new EditText(this);
        ui.input(input);
        input.setText(a.name == null ? "" : a.name);
        input.setSelection(input.getText().length());
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = new TextView(this);
        hint.setText("Nama baru untuk " + a.typeName + " (field m_Name):");
        hint.setTextColor(ui.muted);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(6));
        wrap.addView(hint);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog d = themed().setTitle("Ganti Nama")
                .setView(wrap)
                .setPositiveButton("Ganti", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            toast("Nama tidak boleh kosong.");
                            return;
                        }
                        if (name.equals(a.name)) {
                            toast("Nama tidak berubah.");
                            return;
                        }
                        job("Mengganti nama…", new Job() {
                            @Override
                            public void run() throws Exception {
                                AssetExtractor.rename(a, name);
                                uiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        setBusy(null);
                                        adapter.notifyDataSetChanged();
                                        applyFilter();
                                        updateFileHeader();
                                        setStatusOnly("Nama diganti menjadi " + name
                                                + ". Gunakan File > Simpan untuk menulis bundle.");
                                        toast("Nama berhasil diganti (belum disimpan).");
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .create();
        showThemed(d);
    }

    // ------------------------------------------------------------- info/data

    private void showInfo(final Asset a) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.panel);

        root.addView(dialogTitleBar("Info Aset", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        }), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(ui.divider(this));

        ScrollView sc = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(8));
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        String label = Ui.typeLabel(a.typeName);
        int color = Ui.typeColor(a.typeName);
        badge.setText(label);
        badge.setTextColor(color);
        badge.setTextSize(10);
        badge.setTypeface(ui.sansMedium);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ui.box(this, ui.alpha(color, ui.dark ? 0x33 : 0x22), 0, 3));
        header.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(8), 0, 0, 0);
        TextView nm = new TextView(this);
        nm.setText(a.displayName());
        nm.setTextColor(ui.text);
        nm.setTextSize(16);
        nm.setTypeface(ui.sansMedium);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        names.addView(nm);
        TextView ty = new TextView(this);
        ty.setText(a.typeName + " \u00b7 classId " + a.classId);
        ty.setTextColor(ui.accent);
        ty.setTextSize(11);
        names.addView(ty);
        header.addView(names, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout props = new LinearLayout(this);
        props.setOrientation(LinearLayout.VERTICAL);
        props.setBackground(ui.box(this, ui.panelAlt, ui.border, 2));
        LinearLayout.LayoutParams propsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        propsLp.topMargin = dp(10);
        content.addView(props, propsLp);
        addProp(props, "Nama", a.displayName());
        addProp(props, "Container", a.container == null || a.container.isEmpty() ? "—" : a.container);
        addProp(props, "Tipe", a.typeName);
        String fileInfo = String.valueOf(a.fileId + 1);
        if (a.fileId >= 0 && a.fileId < fileLabels.size()) {
            fileInfo += "  " + fileLabels.get(a.fileId);
        }
        addProp(props, "Berkas", fileInfo);
        addProp(props, "Path ID", String.valueOf(a.pathId));
        addProp(props, "Ukuran", Util.formatSize(a.obj.byteSize) + " (" + a.obj.byteSize + " byte)");
        addProp(props, "Diubah", a.modified ? "Ya (belum disimpan)" : "Tidak");
        addProp(props, "Class ID", String.valueOf(a.classId));

        TextView prevLabel = new TextView(this);
        prevLabel.setText("Pratinjau");
        prevLabel.setTextColor(ui.muted);
        prevLabel.setTextSize(11);
        prevLabel.setTypeface(ui.sansMedium);
        prevLabel.setPadding(0, dp(12), 0, dp(4));
        content.addView(prevLabel);

        FrameLayout previewBox = new FrameLayout(this);
        content.addView(previewBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView note = new TextView(this);
        note.setTextColor(ui.muted);
        note.setTextSize(11);
        note.setPadding(0, dp(6), 0, dp(4));
        content.addView(note);

        if (a.classId == 28) {
            loadTexturePreview(a, previewBox, note);
        } else if (a.classId == 49) {
            loadTextPreview(a, previewBox, note);
        } else if (a.classId == 83) {
            loadAudioPreview(a, previewBox, note);
        } else {
            note.setText("Tipe ini belum punya pratinjau khusus. Data mentah tetap bisa diekspor.");
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(ui.panelAlt);
        footer.setPadding(dp(7), dp(6), dp(7), dp(6));
        LinearLayout row1 = dialogRow();
        addDialogButton(row1, "Lihat Data", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showData(a);
            }
        });
        addDialogButton(row1, "Ekspor", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportAsset(a);
            }
        });
        footer.addView(row1);
        LinearLayout row2 = dialogRow();
        Button impBtn = addDialogButton(row2, "Impor", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                startReplace(a);
            }
        });
        if (a.classId != 49 && a.classId != 28) {
            impBtn.setEnabled(false);
            impBtn.setAlpha(0.42f);
        }
        addDialogButton(row2, "Ganti Nama", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                showRenameDialog(a);
            }
        });
        footer.addView(row2);
        LinearLayout row3 = dialogRow();
        addDialogButton(row3, "Hapus", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("Hapus aset belum didukung di versi Android ini.");
            }
        });
        addDialogButton(row3, "Plugin", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("Sistem plugin belum tersedia di versi Android.");
            }
        });
        footer.addView(row3);
        root.addView(ui.divider(this));
        root.addView(footer);

        d.setContentView(root);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                stopAudio();
            }
        });
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(ui.panel));
        }
        d.show();
        w = d.getWindow();
        if (w != null) {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min((int) (sw * 0.94f), dp(820)),
                    Math.min((int) (sh * 0.88f), dp(720)));
        }
    }

    private LinearLayout dialogTitleBar(String text, View.OnClickListener close) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(ui.panelAlt);
        bar.setPadding(dp(12), dp(8), dp(6), dp(8));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(ui.text);
        t.setTextSize(14);
        t.setTypeface(ui.sansMedium);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        bar.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(ui.muted);
        x.setTextSize(16);
        x.setGravity(Gravity.CENTER);
        x.setPadding(dp(10), 0, dp(10), 0);
        x.setBackground(ui.ripple(this, Color.TRANSPARENT, 0, ui.alpha(ui.accent, 0x22), 2));
        x.setOnClickListener(close);
        bar.addView(x);
        return bar;
    }

    private void addProp(LinearLayout parent, String label, String value) {
        if (parent.getChildCount() > 0) {
            parent.addView(ui.divider(this));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(5), dp(8), dp(5));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(ui.muted);
        l.setTextSize(11);
        row.addView(l, new LinearLayout.LayoutParams(dp(88),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView v = new TextView(this);
        v.setText(value == null ? "" : value);
        v.setTextColor(ui.text);
        v.setTextSize(11.5f);
        v.setTextIsSelectable(true);
        row.addView(v, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
    }

    private LinearLayout dialogRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        lp.topMargin = dp(3);
        r.setLayoutParams(lp);
        return r;
    }

    private Button addDialogButton(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        ui.actionButton(b, false);
        b.setTextSize(11.5f);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        row.addView(b, lp);
        return b;
    }

    private void loadTexturePreview(final Asset a, final FrameLayout box, final TextView note) {
        final TextView loading = new TextView(this);
        loading.setText("Memuat pratinjau tekstur…");
        loading.setTextColor(ui.muted);
        loading.setTextSize(12);
        loading.setPadding(0, dp(8), 0, dp(8));
        box.addView(loading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final AssetExtractor.Texture t = AssetExtractor.texture(a);
                    int[] px = t.pixels;
                    if (px == null) {
                        px = GpuTextureDecoder.decode(t.format, t.width, t.height, t.rawData);
                    }
                    if (px == null) {
                        final String msg = t.formatName + " belum bisa didekode di perangkat ini.";
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                loading.setText(msg);
                                note.setText(t.note.isEmpty() ? msg : t.note);
                            }
                        });
                        return;
                    }
                    Bitmap bmp = Bitmap.createBitmap(px, t.width, t.height, Bitmap.Config.ARGB_8888);
                    int maxW = 960;
                    if (t.width > maxW) {
                        int nh = Math.max(1, (int) ((long) t.height * maxW / t.width));
                        Bitmap scaled = Bitmap.createScaledBitmap(bmp, maxW, nh, true);
                        bmp.recycle();
                        bmp = scaled;
                    }
                    final Bitmap finalBmp = bmp;
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ImageView iv = new ImageView(MainActivity.this);
                            iv.setAdjustViewBounds(true);
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            iv.setImageBitmap(finalBmp);
                            iv.setBackgroundColor(ui.inputBg);
                            box.removeAllViews();
                            box.addView(iv, new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT));
                            note.setText("Tekstur: " + t.width + "x" + t.height + " " + t.formatName
                                    + (t.streamed ? " (stream " + t.source + ")" : "")
                                    + (t.note.isEmpty() ? "" : " \u00b7 " + t.note));
                        }
                    });
                } catch (final Throwable t) {
                    final String msg = "Gagal memuat pratinjau: " + Util.errorMessage(t);
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading.setText(msg);
                            note.setText(msg);
                        }
                    });
                }
            }
        }, "uabea-preview").start();
    }

    private void loadTextPreview(Asset a, FrameLayout box, TextView note) {
        try {
            String text = AssetExtractor.textPreview(a, 8000);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextColor(ui.text);
            tv.setTextSize(11);
            tv.setTypeface(ui.mono);
            tv.setBackground(ui.box(this, ui.inputBg, ui.border, 2));
            tv.setPadding(dp(10), dp(10), dp(10), dp(10));
            tv.setTextIsSelectable(true);
            ScrollView sc = new ScrollView(this);
            sc.addView(tv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            box.addView(sc, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(300)));
            note.setText("Pratinjau maksimal 8000 karakter.");
        } catch (Throwable t) {
            note.setText("Gagal membaca teks: " + Util.errorMessage(t));
        }
    }

    private void loadAudioPreview(final Asset a, FrameLayout box, final TextView note) {
        try {
            LinearLayout audio = new LinearLayout(this);
            audio.setOrientation(LinearLayout.VERTICAL);
            TextView tv = new TextView(this);
            tv.setText(AssetExtractor.audioSummary(a));
            tv.setTextColor(ui.text);
            tv.setTextSize(12);
            tv.setPadding(0, dp(2), 0, dp(8));
            audio.addView(tv);
            final Button play = new Button(this);
            ui.actionButton(play, false);
            bindPlayButton(play, a, note);
            audio.addView(play, new LinearLayout.LayoutParams(dp(110), dp(34)));
            box.addView(audio, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            note.setText("Audio diekspor sebagai WAV/MP3/OGG/FSB sesuai format aslinya.");
        } catch (Throwable t) {
            note.setText("Gagal membaca audio: " + Util.errorMessage(t));
        }
    }

    private void bindPlayButton(final Button play, final Asset a, final TextView note) {
        play.setText("Putar");
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAudio(a, note, play);
            }
        });
    }

    private void bindStopButton(final Button play, final Asset a, final TextView note) {
        play.setText("Berhenti");
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAudio();
                bindPlayButton(play, a, note);
            }
        });
    }

    private void playAudio(final Asset a, final TextView note, final Button play) {
        stopAudio();
        final int token = ++audioToken;
        job("Menyiapkan audio…", new Job() {
            @Override
            public void run() throws Exception {
                final Fsb5.Result r = AssetExtractor.audio(a);
                File f = new File(getCacheDir(), "preview." + r.extension);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(r.data);
                fos.close();
                final MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(f.getAbsolutePath());
                mp.prepare();
                if (token != audioToken || isFinishing()) {
                    mp.release();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(null);
                        }
                    });
                    return;
                }
                player = mp;
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer m) {
                        if (player == m) {
                            player = null;
                            m.release();
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    bindPlayButton(play, a, note);
                                }
                            });
                        }
                    }
                });
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != audioToken) {
                            if (player == mp) {
                                player = null;
                                mp.release();
                            }
                            setBusy(null);
                            return;
                        }
                        setBusy(null);
                        bindStopButton(play, a, note);
                        if (!r.note.isEmpty()) {
                            note.setText(r.note);
                        }
                        try {
                            mp.start();
                        } catch (Exception e) {
                            if (player == mp) {
                                player = null;
                            }
                            mp.release();
                            bindPlayButton(play, a, note);
                            note.setText("Gagal memutar audio: " + Util.errorMessage(e));
                        }
                    }
                });
            }
        });
    }

    private void stopAudio() {
        audioToken++;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
                // sudah berhenti
            }
            player.release();
            player = null;
        }
    }

    private void showData(final Asset a) {
        if (a == null) {
            return;
        }
        job("Membaca data aset…", new Job() {
            @Override
            public void run() throws Exception {
                final String dump = DataDump.fields(a);
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(null);
                        showTextViewer("Data — " + a.displayName(), dump);
                    }
                });
            }
        });
    }

    private void showTypeTree(final Asset a) {
        if (a == null) {
            return;
        }
        try {
            String dump = DataDump.typeTree(a);
            showTextViewer("Pohon Tipe — " + a.displayName(), dump);
        } catch (Throwable t) {
            toast("Gagal membaca pohon tipe: " + Util.errorMessage(t));
        }
    }

    private void showTextViewer(String title, String body) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.panel);
        root.addView(dialogTitleBar(title, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        }), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(ui.divider(this));

        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextColor(ui.text);
        tv.setTextSize(10);
        tv.setTypeface(ui.mono);
        tv.setTextIsSelectable(true);
        tv.setHorizontallyScrolling(true);
        tv.setBackgroundColor(ui.inputBg);
        tv.setPadding(dp(10), dp(10), dp(10), dp(10));

        ScrollView vertical = new ScrollView(this);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.addView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        vertical.addView(horizontal, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout bodyWrap = new LinearLayout(this);
        bodyWrap.setOrientation(LinearLayout.VERTICAL);
        bodyWrap.setPadding(dp(10), dp(10), dp(10), dp(8));
        bodyWrap.addView(vertical, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bodyWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = new Button(this);
        close.setText("Tutup");
        ui.actionButton(close, false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        LinearLayout foot = new LinearLayout(this);
        foot.setPadding(dp(10), dp(6), dp(10), dp(8));
        foot.setBackgroundColor(ui.panelAlt);
        foot.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        root.addView(ui.divider(this));
        root.addView(foot);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(ui.panel));
        }
        d.show();
        w = d.getWindow();
        if (w != null) {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min((int) (sw * 0.94f), dp(820)),
                    Math.min((int) (sh * 0.86f), dp(720)));
        }
    }

    // -------------------------------------------------------------- dialog

    private AlertDialog.Builder themed() {
        int theme = ui.dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert;
        return new AlertDialog.Builder(this, theme);
    }

    private AlertDialog showThemed(AlertDialog d) {
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                AlertDialog a = (AlertDialog) dialog;
                int[] buttons = {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE,
                        DialogInterface.BUTTON_NEUTRAL};
                for (int b : buttons) {
                    Button btn = a.getButton(b);
                    if (btn != null) {
                        btn.setTextColor(ui.accent);
                        btn.setTypeface(ui.sansMedium);
                        btn.setAllCaps(false);
                    }
                }
            }
        });
        d.show();
        return d;
    }

    private void showAbout() {
        String msg = "UABEA Mobile 1.0\n\n"
                + "Editor AssetBundle Unity untuk Android, ditulis ulang dalam Java "
                + "dari UABEA (C#/Avalonia).\n\n"
                + "Fitur:\n"
                + "\u2022 Buka UnityFS (.bundle/.ab) dan SerializedFile mandiri (.assets)\n"
                + "\u2022 Daftar aset ala DataGrid: nama, container, tipe, ukuran, path ID\n"
                + "\u2022 Ekstrak TextAsset, Texture2D, AudioClip, dan data mentah\n"
                + "\u2022 Ganti TextAsset/Texture2D, ganti nama, ekspor/impor semua\n"
                + "\u2022 Lihat data (TypeTree) dan pohon tipe\n"
                + "\u2022 Pratinjau teks, gambar, dan audio\n"
                + "\u2022 Simpan/repack bundle, tema terang dan gelap\n\n"
                + "Batasan:\n"
                + "\u2022 Bundle terenkripsi (UnityCN) dan UnityWeb belum didukung\n"
                + "\u2022 Crunch dan format tekstur tak dikenal diekspor mentah (.tex)\n"
                + "\u2022 Audio Vorbis diekspor sebagai .fsb\n"
                + "\u2022 Hapus aset dan sistem plugin belum tersedia\n"
                + "\u2022 ASTC/BC6H/BC7/PVRTC memerlukan dukungan GPU\n\n"
                + "Terinspirasi oleh UABEA (nesrak1) dan AssetsTools.NET.";
        AlertDialog d = themed().setTitle("Tentang UABEA Mobile")
                .setMessage(msg)
                .setPositiveButton("Tutup", null)
                .create();
        showThemed(d);
    }

    private void confirmExit() {
        if (index != null && hasUnsaved()) {
            AlertDialog d = themed().setTitle("Keluar")
                    .setMessage("Ada perubahan yang belum disimpan. Keluar tanpa menyimpan?")
                    .setPositiveButton("Keluar", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .create();
            showThemed(d);
        } else {
            finish();
        }
    }

    // -------------------------------------------------------------- pekerja

    private void job(final String label, final Job body) {
        setBusy(label);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    body.run();
                } catch (Throwable t) {
                    final String msg = Util.errorMessage(t);
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(null);
                            setStatusOnly("Gagal: " + msg);
                            toast("Gagal: " + msg);
                        }
                    });
                }
            }
        }, "uabea-worker").start();
    }

    private void setBusy(String label) {
        busy = label != null;
        progress.setVisibility(label == null ? View.GONE : View.VISIBLE);
        if (label != null) {
            setStatusOnly(label);
        }
        updateButtonStates();
    }

    private void setStatusOnly(String text) {
        statusView.setText(text);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------ hasil intent

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        switch (requestCode) {
            case REQ_OPEN:
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                        // izin sementara tetap bisa dipakai
                    }
                }
                openBundle(uri);
                break;
            case REQ_EXPORT:
                if (pendingExport == null) {
                    return;
                }
                final byte[] exportData = pendingExport;
                final int exportLen = exportData.length;
                pendingExport = null;
                job("Menulis file…", new Job() {
                    @Override
                    public void run() throws Exception {
                        writeUri(uri, exportData);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setBusy(null);
                                setStatusOnly("Diekspor \u00b7 " + Util.formatSize(exportLen));
                                toast("Berhasil diekspor.");
                            }
                        });
                    }
                });
                break;
            case REQ_SAVE:
                if (pendingSave == null) {
                    return;
                }
                final byte[] saveData = pendingSave;
                final int saveLen = saveData.length;
                pendingSave = null;
                job("Menulis bundle…", new Job() {
                    @Override
                    public void run() throws Exception {
                        writeUri(uri, saveData);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setBusy(null);
                                bundleUri = uri;
                                bundleName = queryName(uri);
                                bundleSize = saveLen;
                                clearModified();
                                updateFileHeader();
                                adapter.notifyDataSetChanged();
                                setStatusOnly("Bundle disimpan \u00b7 " + Util.formatSize(saveLen));
                                toast("Bundle berhasil disimpan.");
                            }
                        });
                    }
                });
                break;
            case REQ_REPLACE:
                if (pendingReplace == null) {
                    return;
                }
                final Asset target = pendingReplace;
                pendingReplace = null;
                job("Membaca file pengganti…", new Job() {
                    @Override
                    public void run() throws Exception {
                        final byte[] bytes = readAll(uri);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                applyReplace(target, bytes);
                            }
                        });
                    }
                });
                break;
            case REQ_EXPORT_ALL:
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                    // opsional
                }
                exportAll(uri);
                break;
            case REQ_IMPORT_ALL:
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // opsional
                }
                importAll(uri);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (busy) {
            toast("Tunggu proses selesai terlebih dahulu.");
            return;
        }
        if (intent != null && intent.getData() != null) {
            final Uri uri = intent.getData();
            if (index != null && hasUnsaved()) {
                AlertDialog d = themed().setTitle("Buka file")
                        .setMessage("Ada perubahan yang belum disimpan. Buka file lain tanpa menyimpan?")
                        .setPositiveButton("Buka", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openBundle(uri);
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .create();
                showThemed(d);
            } else {
                openBundle(uri);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (openPopup != null) {
            dismissPopup();
            return;
        }
        if (busy) {
            toast("Tunggu proses selesai terlebih dahulu.");
            return;
        }
        if (index != null && hasUnsaved()) {
            confirmExit();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopAudio();
        dismissPopup();
        super.onDestroy();
    }
}
