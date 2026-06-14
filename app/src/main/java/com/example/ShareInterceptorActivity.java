package com.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareInterceptorActivity extends Activity {
    private ViewGroup rootView;
    private View pillView;
    private TextView hudTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setDimAmount(0.0f);

        rootView = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.hud_share_save, null);
        setContentView(rootView);

        pillView    = rootView.findViewById(R.id.pill_container);
        hudTextView = rootView.findViewById(R.id.hud_text);

        pillView.setVisibility(View.INVISIBLE);
        handleIncomingIntent(getIntent());
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) { finish(); return; }

        String action = intent.getAction();
        String type   = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.trim().isEmpty()) {
                String extractedUrl = parseUrl(sharedText);
                if (extractedUrl != null) {
                    // Clean tracking params before saving
                    String cleanedUrl = LinkCleaner.cleanUrl(extractedUrl);
                    processSaveLink(cleanedUrl);
                    return;
                }
            }
        }

        showHudMessage(getString(R.string.invalid_link), false);
    }

    private String parseUrl(String input) {
        Matcher matcher = Patterns.WEB_URL.matcher(input);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private void processSaveLink(final String url) {
        new Thread(() -> {
            final AppDatabase db = AppDatabase.getDatabase(this);
            SavedLink existing = db.savedLinkDao().getLinkByUrl(url);

            if (existing != null) {
                runOnUiThread(() -> showHudMessage(getString(R.string.already_in_opal), true));
            } else {
                String domain   = getDomainName(url);
                String platform = MetadataHelper.detectPlatform(domain);

                final SavedLink newLink = new SavedLink(url, domain, domain, platform, "", "");
                final long newId = db.savedLinkDao().insert(newLink);

                runOnUiThread(() -> {
                    scheduleMetadataFetch((int) newId);
                    showHudMessage(getString(R.string.saved_to_opal), true);
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

    private void showHudMessage(String message, boolean isStatusOk) {
        hudTextView.setText(message);

        View glow = pillView.findViewById(R.id.glow_circle);
        if (!isStatusOk && glow != null) glow.setVisibility(View.GONE);

        pillView.setVisibility(View.VISIBLE);

        TranslateAnimation slideDown = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0);
        slideDown.setDuration(350);
        pillView.startAnimation(slideDown);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            TranslateAnimation slideUp = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, -1.2f);
            slideUp.setDuration(300);

            slideUp.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    pillView.setVisibility(View.INVISIBLE);
                    finish();
                }
            });
            pillView.startAnimation(slideUp);
        }, 1300);
    }
}
