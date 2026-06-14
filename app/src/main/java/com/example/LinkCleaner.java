package com.example;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LinkCleaner {

    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
        "si",
        "igshid", "igsh",
        "fbclid",
        "ref_src", "ref_url",
        "_r",
        "mc_cid", "mc_eid",
        "gclid", "dclid",
        "feature",
        "pp",
        "context",
        "ref",
        "s",
        "share_id",
        "mibextid",
        "fref"
    ));

    public static String cleanUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return rawUrl;
        }

        String url = rawUrl.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        try {
            Uri uri = Uri.parse(url);
            String query = uri.getQuery();

            if (query == null || query.isEmpty()) {
                return url;
            }

            Uri.Builder builder = uri.buildUpon().clearQuery();

            Set<String> paramNames = uri.getQueryParameterNames();
            for (String paramName : paramNames) {
                if (!TRACKING_PARAMS.contains(paramName.toLowerCase())) {
                    String value = uri.getQueryParameter(paramName);
                    builder.appendQueryParameter(paramName, value != null ? value : "");
                }
            }

            String cleaned = builder.build().toString();
            if (cleaned.endsWith("?")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return cleaned;
        } catch (Exception e) {
            return url;
        }
    }
}
