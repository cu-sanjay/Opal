package com.example;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LinkCleaner {

    /** Query parameters that are purely for tracking and carry no content value. */
    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
        // Universal UTM
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term", "utm_id",
        // YouTube / Spotify share token
        "si", "feature", "pp",
        // Instagram
        "igshid", "igsh",
        // Facebook
        "fbclid", "mibextid", "fref",
        // Twitter / X
        "ref_src", "ref_url",
        // Reddit share
        "_r", "share_id",
        // Generic share/ref
        "ref", "source", "s",
        // Email marketing
        "mc_cid", "mc_eid",
        // Google Ads
        "gclid", "dclid",
        // Amazon affiliate / tracking
        "tag", "linkCode", "linkId", "ref_", "pd_rd_r", "pd_rd_w", "pd_rd_wg",
        "pd_rd_i", "pf_rd_p", "pf_rd_r", "content-id", "ascsubtag",
        // Misc
        "zanpid", "origin", "context"
    ));

    /**
     * Strips known tracking query parameters from a URL.
     * Always ensures the URL has an http/https scheme.
     */
    public static String cleanUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return rawUrl;

        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        try {
            Uri uri   = Uri.parse(url);
            String query = uri.getQuery();

            // Nothing to clean
            if (query == null || query.isEmpty()) return url;

            Uri.Builder builder = uri.buildUpon().clearQuery();
            for (String param : uri.getQueryParameterNames()) {
                if (!TRACKING_PARAMS.contains(param.toLowerCase())) {
                    String value = uri.getQueryParameter(param);
                    builder.appendQueryParameter(param, value != null ? value : "");
                }
            }

            String cleaned = builder.build().toString();
            if (cleaned.endsWith("?")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            return cleaned;

        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Returns true if the URL looks like a known short-link domain.
     * These always need redirect resolution before platform detection.
     */
    public static boolean isShortUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("a.co/")    || lower.contains("amzn.to/")  ||
               lower.contains("amzn.eu/") || lower.contains("amzn.in/")  ||
               lower.contains("pin.it/")  || lower.contains("bit.ly/")   ||
               lower.contains("t.co/")    || lower.contains("tinyurl.com/") ||
               lower.contains("ow.ly/")   || lower.contains("buff.ly/")  ||
               lower.contains("rb.gy/")   || lower.contains("lnk.to/")   ||
               lower.contains("ln.to/")   || lower.contains("redd.it/");
    }
}
