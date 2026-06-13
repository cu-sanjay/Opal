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
import android.widget.LinearLayout;
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

    // Custom controls
    private ImageView settingsButton;
    private EditText quickAddEditText;
    private View quickAddBtn;

    // Clipboard banner views
    private View clipboardBanner;
    private TextView clipboardUrlText;
    private View clipboardSaveBtn;
    private ImageView clipboardDismissBtn;

    // Filter Pills
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

        // Apply Edge-to-Edge System Bars Padding
        View mainView = findViewById(android.R.id.content);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Initialize UI Views
        recyclerView = findViewById(R.id.links_recycler);
        searchEditText = findViewById(R.id.search_edit_text);
        emptyStateLayout = findViewById(R.id.empty_state_layout);

        settingsButton = findViewById(R.id.settings_button);
        quickAddEditText = findViewById(R.id.quick_add_edit_text);
        quickAddBtn = findViewById(R.id.quick_add_btn);

        clipboardBanner = findViewById(R.id.clipboard_banner);
        clipboardUrlText = findViewById(R.id.clipboard_url_text);
        clipboardSaveBtn = findViewById(R.id.clipboard_save_btn);
        clipboardDismissBtn = findViewById(R.id.clipboard_dismiss_btn);

        filterAll = findViewById(R.id.filter_all);
        filterYouTube = findViewById(R.id.filter_youtube);
        filterInstagram = findViewById(R.id.filter_instagram);
        filterX = findViewById(R.id.filter_x);
        filterReddit = findViewById(R.id.filter_reddit);
        filterWeb = findViewById(R.id.filter_web);

        // Set up recycler
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedLinksAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Fetch LiveData from Room
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

        // Search edit text changes listener
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                applyFilterAndSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set up filter pill click listeners
        setupFilterListeners();

        // Settings Button trigger
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        // Quick add manual paste handler
        quickAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputUrl = quickAddEditText.getText().toString();
                if (inputUrl.trim().isEmpty()) {
                    Toast.makeText(MainActivity.this, "Url cannot be empty!", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveNewUrl(inputUrl);
                quickAddEditText.setText("");
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            findViewById(android.R.id.content).post(new Runnable() {
                @Override
                public void run() {
                    checkClipboardForLinks();
                }
            });
        }
    }

    private void checkClipboardForLinks() {
        boolean autoDetect = prefs.getBoolean("auto_detect_clipboard", true);
        if (!autoDetect) {
            clipboardBanner.setVisibility(View.GONE);
            return;
        }

        try {
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip()) {
                android.content.ClipData data = cb.getPrimaryClip();
                if (data != null && data.getItemCount() > 0) {
                    CharSequence text = data.getItemAt(0).getText();
                    if (text != null) {
                        final String copiedText = text.toString().trim();
                        // Pre-check filter
                        if (Patterns.WEB_URL.matcher(copiedText).matches() && (copiedText.startsWith("http://") || copiedText.startsWith("https://") || copiedText.contains("."))) {
                            String matchedUrl = copiedText;
                            if (!matchedUrl.startsWith("http://") && !matchedUrl.startsWith("https://")) {
                                matchedUrl = "https://" + matchedUrl;
                            }
                            final String finalMatchedUrl = matchedUrl;

                            // Run matching check in database in background thread
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    AppDatabase db = AppDatabase.getDatabase(MainActivity.this);
                                    SavedLink existing = db.savedLinkDao().getLinkByUrl(finalMatchedUrl);
                                    if (existing == null) {
                                        // Not in database, show banner
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                clipboardUrlText.setText(copiedText);
                                                clipboardBanner.setVisibility(View.VISIBLE);

                                                clipboardSaveBtn.setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        saveNewUrl(finalMatchedUrl);
                                                        clipboardBanner.setVisibility(View.GONE);
                                                    }
                                                });

                                                clipboardDismissBtn.setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        clipboardBanner.setVisibility(View.GONE);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void saveNewUrl(final String urlString) {
        String url = urlString.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://" )) {
            url = "https://" + url;
        }

        if (!Patterns.WEB_URL.matcher(url).matches()) {
            Toast.makeText(this, "Please enter a valid link!", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalUrl = url;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final AppDatabase db = AppDatabase.getDatabase(MainActivity.this);
                SavedLink existing = db.savedLinkDao().getLinkByUrl(finalUrl);

                if (existing != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "This link is already saved to Opal", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    String domain = getDomainName(finalUrl);
                    String platform = MetadataHelper.detectPlatform(domain);
                    final SavedLink newLink = new SavedLink(finalUrl, domain, domain, platform, "", "");
                    final long newId = db.savedLinkDao().insert(newLink);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scheduleMetadataFetch((int) newId);
                            Toast.makeText(MainActivity.this, "Link saved successfully!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String getDomainName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getHost();
            if (domain != null) {
                return domain.startsWith("www.") ? domain.substring(4) : domain;
            }
        } catch (Exception e) {
            // ignore
        }
        return "web";
    }

    private void scheduleMetadataFetch(int linkId) {
        Data inputData = new Data.Builder()
                .putInt("link_id", linkId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MetadataFetchWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(getApplicationContext()).enqueue(request);
    }

    private void showSettingsDialog() {
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        bottomSheetDialog.setContentView(dialogView);

        androidx.appcompat.widget.SwitchCompat clipboardSwitch = dialogView.findViewById(R.id.pref_clipboard_switch);
        View clipboardContainer = dialogView.findViewById(R.id.pref_clipboard_container);
        View githubCard = dialogView.findViewById(R.id.github_card);

        // Load existing value
        boolean autoDetect = prefs.getBoolean("auto_detect_clipboard", true);
        clipboardSwitch.setChecked(autoDetect);

        clipboardSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_detect_clipboard", isChecked).apply();
            if (!isChecked) {
                clipboardBanner.setVisibility(View.GONE);
            } else {
                checkClipboardForLinks();
            }
        });

        clipboardContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !clipboardSwitch.isChecked();
                clipboardSwitch.setChecked(nextState);
            }
        });

        githubCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cu-sanjay/Opal"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open link", Toast.LENGTH_SHORT).show();
                }
            }
        });

        bottomSheetDialog.show();
    }

    private void setupFilterListeners() {
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear active styling on all pills
                resetFilterPillsStyle();

                // Style the active pill and update state
                TextView clickedPill = (TextView) v;
                clickedPill.setBackgroundResource(R.drawable.bg_pill_active);
                clickedPill.setTextColor(0xFF000000); // solid black

                int id = v.getId();
                if (id == R.id.filter_all) {
                    currentSelectedPlatform = "All";
                } else if (id == R.id.filter_youtube) {
                    currentSelectedPlatform = "YouTube";
                } else if (id == R.id.filter_instagram) {
                    currentSelectedPlatform = "Instagram";
                } else if (id == R.id.filter_x) {
                    currentSelectedPlatform = "X";
                } else if (id == R.id.filter_reddit) {
                    currentSelectedPlatform = "Reddit";
                } else if (id == R.id.filter_web) {
                    currentSelectedPlatform = "Web";
                }

                applyFilterAndSearch();
            }
        };

        filterAll.setOnClickListener(clickListener);
        filterYouTube.setOnClickListener(clickListener);
        filterInstagram.setOnClickListener(clickListener);
        filterX.setOnClickListener(clickListener);
        filterReddit.setOnClickListener(clickListener);
        filterWeb.setOnClickListener(clickListener);
    }

    private void resetFilterPillsStyle() {
        int inactiveBg = R.drawable.bg_pill_inactive;
        int inactiveTextColor = 0x80FFFFFF; // semi-transparent white

        filterAll.setBackgroundResource(inactiveBg);
        filterAll.setTextColor(inactiveTextColor);

        filterYouTube.setBackgroundResource(inactiveBg);
        filterYouTube.setTextColor(inactiveTextColor);

        filterInstagram.setBackgroundResource(inactiveBg);
        filterInstagram.setTextColor(inactiveTextColor);

        filterX.setBackgroundResource(inactiveBg);
        filterX.setTextColor(inactiveTextColor);

        filterReddit.setBackgroundResource(inactiveBg);
        filterReddit.setTextColor(inactiveTextColor);

        filterWeb.setBackgroundResource(inactiveBg);
        filterWeb.setTextColor(inactiveTextColor);
    }

    private void applyFilterAndSearch() {
        List<SavedLink> filtered = new ArrayList<>();

        for (SavedLink item : allStoredLinks) {
            // 1. Filter by platform
            boolean matchesPlatform = currentSelectedPlatform.equals("All") ||
                    (item.getPlatform() != null && item.getPlatform().equalsIgnoreCase(currentSelectedPlatform));

            // 2. Filter by search query
            boolean matchesQuery = currentSearchQuery.isEmpty() ||
                    (item.getTitle() != null && item.getTitle().toLowerCase().contains(currentSearchQuery)) ||
                    (item.getUrl() != null && item.getUrl().toLowerCase().contains(currentSearchQuery));

            if (matchesPlatform && matchesQuery) {
                filtered.add(item);
            }
        }

        // Set list to adapter
        adapter.setLinks(filtered);

        // Toggle empty state
        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDelete(SavedLink link) {
        // Run database tasks on a simple background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppDatabase.getDatabase(MainActivity.this).savedLinkDao().deleteById(link.getId());
            }
        }).start();
    }
}
