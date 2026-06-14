package com.example;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SavedLinksAdapter.OnLinkActionListener {

    private RecyclerView recyclerView;
    private SavedLinksAdapter adapter;
    private EditText searchEditText;
    private View emptyStateLayout;

    private ImageView settingsButton;
    private EditText quickAddEditText;
    private View quickAddBtn;

    private View clipboardBanner;
    private TextView clipboardUrlText;
    private View clipboardSaveBtn;
    private ImageView clipboardDismissBtn;

    private TextView filterAll;
    private TextView filterYouTube;
    private TextView filterInstagram;
    private TextView filterX;
    private TextView filterReddit;
    private TextView filterWeb;

    private List<SavedLink> allStoredLinks = new ArrayList<>();
    private String currentSelectedPlatform = "All";
    private String currentSearchQuery = "";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("opal_prefs", MODE_PRIVATE);

        View mainView = findViewById(android.R.id.content);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        recyclerView      = findViewById(R.id.links_recycler);
        searchEditText    = findViewById(R.id.search_edit_text);
        emptyStateLayout  = findViewById(R.id.empty_state_layout);
        settingsButton    = findViewById(R.id.settings_button);
        quickAddEditText  = findViewById(R.id.quick_add_edit_text);
        quickAddBtn       = findViewById(R.id.quick_add_btn);
        clipboardBanner   = findViewById(R.id.clipboard_banner);
        clipboardUrlText  = findViewById(R.id.clipboard_url_text);
        clipboardSaveBtn  = findViewById(R.id.clipboard_save_btn);
        clipboardDismissBtn = findViewById(R.id.clipboard_dismiss_btn);
        filterAll         = findViewById(R.id.filter_all);
        filterYouTube     = findViewById(R.id.filter_youtube);
        filterInstagram   = findViewById(R.id.filter_instagram);
        filterX           = findViewById(R.id.filter_x);
        filterReddit      = findViewById(R.id.filter_reddit);
        filterWeb         = findViewById(R.id.filter_web);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedLinksAdapter(this, this);
        recyclerView.setAdapter(adapter);

        AppDatabase.getDatabase(this).savedLinkDao().getAllLinksLiveData()
                .observe(this, new Observer<List<SavedLink>>() {
                    @Override
                    public void onChanged(List<SavedLink> savedLinks) {
                        if (savedLinks != null) {
                            allStoredLinks = savedLinks;
                            applyFilterAndSearch();
                        }
                    }
                });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                applyFilterAndSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setupFilterListeners();

        settingsButton.setOnClickListener(v -> showSettingsDialog());

        quickAddBtn.setOnClickListener(v -> {
            String inputUrl = quickAddEditText.getText().toString();
            if (inputUrl.trim().isEmpty()) {
                Toast.makeText(this, "URL cannot be empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            saveNewUrl(inputUrl);
            quickAddEditText.setText("");
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            findViewById(android.R.id.content).post(this::checkClipboardForLinks);
        }
    }

    private void checkClipboardForLinks() {
        boolean autoDetect = prefs.getBoolean("auto_detect_clipboard", true);
        if (!autoDetect) {
            clipboardBanner.setVisibility(View.GONE);
            return;
        }

        try {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb == null || !cb.hasPrimaryClip()) return;
            android.content.ClipData data = cb.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return;
            CharSequence text = data.getItemAt(0).getText();
            if (text == null) return;

            final String copiedText = text.toString().trim();
            if (Patterns.WEB_URL.matcher(copiedText).matches()
                    && (copiedText.startsWith("http") || copiedText.contains("."))) {

                String rawUrl = copiedText.startsWith("http") ? copiedText : "https://" + copiedText;
                final String cleanedUrl = LinkCleaner.cleanUrl(rawUrl);

                new Thread(() -> {
                    AppDatabase db = AppDatabase.getDatabase(this);
                    SavedLink existing = db.savedLinkDao().getLinkByUrl(cleanedUrl);
                    if (existing == null) {
                        runOnUiThread(() -> {
                            clipboardUrlText.setText(cleanedUrl);
                            clipboardBanner.setVisibility(View.VISIBLE);
                            clipboardSaveBtn.setOnClickListener(v -> {
                                saveNewUrl(cleanedUrl);
                                clipboardBanner.setVisibility(View.GONE);
                            });
                            clipboardDismissBtn.setOnClickListener(v ->
                                    clipboardBanner.setVisibility(View.GONE));
                        });
                    }
                }).start();
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void saveNewUrl(final String urlString) {
        // Clean tracking params first
        String cleaned = LinkCleaner.cleanUrl(urlString.trim());

        if (!Patterns.WEB_URL.matcher(cleaned).matches()) {
            Toast.makeText(this, "Please enter a valid link!", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalUrl = cleaned;

        new Thread(() -> {
            final AppDatabase db = AppDatabase.getDatabase(this);
            SavedLink existing = db.savedLinkDao().getLinkByUrl(finalUrl);

            if (existing != null) {
                runOnUiThread(() ->
                        Toast.makeText(this, "This link is already saved to Opal", Toast.LENGTH_SHORT).show());
            } else {
                String domain   = getDomainName(finalUrl);
                String platform = MetadataHelper.detectPlatform(domain);
                final SavedLink newLink = new SavedLink(finalUrl, domain, domain, platform, "", "");
                final long newId = db.savedLinkDao().insert(newLink);

                runOnUiThread(() -> {
                    scheduleMetadataFetch((int) newId);
                    Toast.makeText(this, "Link saved!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String getDomainName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getHost();
            if (domain != null) return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (Exception e) { /* ignore */ }
        return "web";
    }

    private void scheduleMetadataFetch(int linkId) {
        Data inputData = new Data.Builder().putInt("link_id", linkId).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MetadataFetchWorker.class)
                .setInputData(inputData).build();
        WorkManager.getInstance(getApplicationContext()).enqueue(request);
    }

    private void showSettingsDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        dialog.setContentView(dialogView);

        androidx.appcompat.widget.SwitchCompat clipboardSwitch =
                dialogView.findViewById(R.id.pref_clipboard_switch);
        View clipboardContainer = dialogView.findViewById(R.id.pref_clipboard_container);
        View githubCard         = dialogView.findViewById(R.id.github_card);
        View cleanCard          = dialogView.findViewById(R.id.clean_links_card);

        boolean autoDetect = prefs.getBoolean("auto_detect_clipboard", true);
        clipboardSwitch.setChecked(autoDetect);

        clipboardSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("auto_detect_clipboard", isChecked).apply();
            if (!isChecked) clipboardBanner.setVisibility(View.GONE);
            else checkClipboardForLinks();
        });

        clipboardContainer.setOnClickListener(v ->
                clipboardSwitch.setChecked(!clipboardSwitch.isChecked()));

        githubCard.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/cu-sanjay/Opal")));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        });

        cleanCard.setOnClickListener(v -> {
            dialog.dismiss();
            cleanAllTrackingParams();
        });

        dialog.show();
    }

    private void cleanAllTrackingParams() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<SavedLink> all = db.savedLinkDao().getAllLinks();
            int cleaned = 0;
            for (SavedLink link : all) {
                String original = link.getUrl();
                String fixed    = LinkCleaner.cleanUrl(original);
                if (!fixed.equals(original)) {
                    db.savedLinkDao().updateUrl(link.getId(), fixed);
                    cleaned++;
                }
            }
            final int count = cleaned;
            runOnUiThread(() -> {
                if (count > 0) {
                    Toast.makeText(this,
                            "Cleaned " + count + " link" + (count == 1 ? "" : "s") + "!",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "All links are already clean.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void setupFilterListeners() {
        View.OnClickListener clickListener = v -> {
            resetFilterPillsStyle();
            TextView pill = (TextView) v;
            pill.setBackgroundResource(R.drawable.bg_pill_active);
            pill.setTextColor(0xFF000000);

            int id = v.getId();
            if      (id == R.id.filter_all)       currentSelectedPlatform = "All";
            else if (id == R.id.filter_youtube)   currentSelectedPlatform = "YouTube";
            else if (id == R.id.filter_instagram) currentSelectedPlatform = "Instagram";
            else if (id == R.id.filter_x)         currentSelectedPlatform = "X";
            else if (id == R.id.filter_reddit)    currentSelectedPlatform = "Reddit";
            else if (id == R.id.filter_web)       currentSelectedPlatform = "Web";

            applyFilterAndSearch();
        };

        filterAll.setOnClickListener(clickListener);
        filterYouTube.setOnClickListener(clickListener);
        filterInstagram.setOnClickListener(clickListener);
        filterX.setOnClickListener(clickListener);
        filterReddit.setOnClickListener(clickListener);
        filterWeb.setOnClickListener(clickListener);
    }

    private void resetFilterPillsStyle() {
        int bg    = R.drawable.bg_pill_inactive;
        int color = 0x80FFFFFF;
        for (TextView pill : new TextView[]{filterAll, filterYouTube, filterInstagram,
                filterX, filterReddit, filterWeb}) {
            pill.setBackgroundResource(bg);
            pill.setTextColor(color);
        }
    }

    private void applyFilterAndSearch() {
        List<SavedLink> filtered = new ArrayList<>();
        for (SavedLink item : allStoredLinks) {
            boolean matchesPlatform = currentSelectedPlatform.equals("All")
                    || (item.getPlatform() != null
                        && item.getPlatform().equalsIgnoreCase(currentSelectedPlatform));

            boolean matchesQuery = currentSearchQuery.isEmpty()
                    || (item.getTitle() != null && item.getTitle().toLowerCase().contains(currentSearchQuery))
                    || (item.getUrl()   != null && item.getUrl().toLowerCase().contains(currentSearchQuery));

            if (matchesPlatform && matchesQuery) filtered.add(item);
        }

        adapter.setLinks(filtered);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE  : View.VISIBLE);
        emptyStateLayout.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDelete(SavedLink link) {
        new Thread(() ->
                AppDatabase.getDatabase(this).savedLinkDao().deleteById(link.getId())
        ).start();
    }
}
