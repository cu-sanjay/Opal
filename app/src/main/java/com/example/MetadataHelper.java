package com.example;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataHelper {
    private static final String TAG = "MetadataHelper";

    public static class Metadata {
        public String title = "";
        public String imageUrl = "";
        public String faviconUrl = "";
        public String domain = "";
        public String platform = "Web";
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    public static Metadata fetchMetadata(String urlString) {
        Metadata meta = new Metadata();
        if (urlString == null || urlString.trim().isEmpty()) {
            return meta;
        }

        meta.domain = getDomainName(urlString);
        meta.platform = detectPlatform(meta.domain);

        // ── 1. YouTube ────────────────────────────────────────────────────────────
        String ytVideoId = extractYoutubeVideoId(urlString);
        if (ytVideoId != null) {
            meta.platform = "YouTube";
            meta.imageUrl = "https://img.youtube.com/vi/" + ytVideoId + "/hqdefault.jpg";
            meta.domain = "youtube.com";
            meta.faviconUrl = "https://www.youtube.com/favicon.ico";

            try {
                String oembedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(urlString, "UTF-8") + "&format=json";
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String ytTitle  = extractJsonString(json, "title");
                    String ytAuthor = extractJsonString(json, "author_name");
                    String thumb    = extractJsonString(json, "thumbnail_url");
                    if (!thumb.isEmpty())    meta.imageUrl = thumb.replace("\\/", "/");
                    if (!ytTitle.isEmpty()) {
                        meta.title = decodeHtmlEntities(decodeUnicodeEscapes(ytTitle));
                        if (!ytAuthor.isEmpty()) {
                            meta.domain = "YouTube • " + decodeHtmlEntities(decodeUnicodeEscapes(ytAuthor));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "YouTube oembed: " + e.getMessage());
            }

            if (meta.title.isEmpty()) meta.title = "YouTube Video";
            return meta;
        }

        // ── 2. Reddit ─────────────────────────────────────────────────────────────
        if (urlString.contains("reddit.com") || urlString.contains("redd.it")) {
            meta.platform = "Reddit";
            meta.domain   = "reddit.com";
            meta.faviconUrl = "https://www.reddit.com/favicon.ico";

            try {
                String cleanUrl = urlString.contains("?")
                        ? urlString.substring(0, urlString.indexOf("?")) : urlString;
                if (!cleanUrl.endsWith("/")) cleanUrl += "/";
                String jsonUrl = cleanUrl + ".json";

                String json = httpGet(jsonUrl, "Mozilla/5.0 OpalApp/1.0 (Android link saver)");
                if (json != null) {
                    String subName    = extractJsonString(json, "subreddit");
                    String postTitle  = extractJsonString(json, "title");
                    String mediaUrl   = extractJsonString(json, "url_overridden_by_dest");
                    String thumbUrl   = extractJsonString(json, "thumbnail");

                    // Best quality preview: preview.images[0].source.url (HTML-entity encoded in JSON)
                    String previewImage = extractRedditPreviewImage(json);

                    if (!postTitle.isEmpty()) meta.title  = decodeHtmlEntities(decodeUnicodeEscapes(postTitle));
                    if (!subName.isEmpty())   meta.domain = "r/" + decodeUnicodeEscapes(subName);

                    if (!previewImage.isEmpty()) {
                        meta.imageUrl = previewImage;
                    } else {
                        String mUrl = mediaUrl.replace("\\/", "/");
                        if (mUrl.matches(".*\\.(jpg|jpeg|png|webp|gif)$")) {
                            meta.imageUrl = mUrl;
                        } else if (!thumbUrl.isEmpty()
                                && !thumbUrl.equals("default")
                                && !thumbUrl.equals("self")
                                && !thumbUrl.equals("nsfw")
                                && !thumbUrl.equals("image")
                                && thumbUrl.startsWith("http")) {
                            meta.imageUrl = thumbUrl.replace("\\/", "/");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Reddit: " + e.getMessage());
            }

            if (meta.title.isEmpty()) meta.title = "Reddit Post";
            return meta;
        }

        // ── 3. Instagram ──────────────────────────────────────────────────────────
        if (urlString.contains("instagram.com")) {
            meta.platform   = "Instagram";
            meta.domain     = "instagram.com";
            meta.faviconUrl = "https://www.instagram.com/favicon.ico";

            String shortcode = "";
            Matcher mShort = Pattern.compile("/(?:p|reel|tv)/([\\w-]+)").matcher(urlString);
            if (mShort.find()) shortcode = mShort.group(1);

            String username = "";
            Matcher mUser = Pattern.compile("instagram\\.com/([\\w\\._-]+)").matcher(urlString);
            if (mUser.find()) {
                String p = mUser.group(1);
                if (!p.equals("p") && !p.equals("reel") && !p.equals("tv")
                        && !p.equals("stories") && !p.equals("explore")
                        && !p.equals("static") && !p.equals("accounts")) {
                    username = p;
                }
            }
            if (!username.isEmpty()) meta.domain = "Instagram • @" + username;

            // Try public oembed (works for public posts/reels without auth)
            try {
                String oembedUrl = "https://www.instagram.com/oembed/?url="
                        + URLEncoder.encode(urlString, "UTF-8") + "&format=json&maxwidth=640";
                String json = httpGet(oembedUrl, "Mozilla/5.0 (compatible; OpalApp/1.0)");
                if (json != null) {
                    String thumb    = extractJsonString(json, "thumbnail_url");
                    String author   = extractJsonString(json, "author_name");
                    String ogTitle  = extractJsonString(json, "title");
                    if (!thumb.isEmpty())   meta.imageUrl = thumb.replace("\\/", "/");
                    if (!author.isEmpty() && username.isEmpty()) meta.domain = "Instagram • @" + decodeUnicodeEscapes(author);
                    if (!ogTitle.isEmpty()) meta.title = decodeHtmlEntities(decodeUnicodeEscapes(ogTitle));
                }
            } catch (Exception e) {
                Log.d(TAG, "Instagram oembed: " + e.getMessage());
            }

            if (meta.title.isEmpty()) {
                if (!shortcode.isEmpty()) {
                    meta.title = urlString.contains("/reel/") ? "Instagram Reel" : "Instagram Post";
                } else if (!username.isEmpty()) {
                    meta.title = "@" + username + " on Instagram";
                } else {
                    meta.title = "Instagram";
                }
            }
            return meta;
        }

        // ── 4. TikTok ─────────────────────────────────────────────────────────────
        if (urlString.contains("tiktok.com")) {
            meta.platform   = "TikTok";
            meta.domain     = "tiktok.com";
            meta.faviconUrl = "https://www.tiktok.com/favicon.ico";

            try {
                String oembedUrl = "https://www.tiktok.com/oembed?url=" + URLEncoder.encode(urlString, "UTF-8");
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String tTitle  = extractJsonString(json, "title");
                    String tAuthor = extractJsonString(json, "author_name");
                    String tThumb  = extractJsonString(json, "thumbnail_url");
                    if (!tTitle.isEmpty())  meta.title    = decodeHtmlEntities(decodeUnicodeEscapes(tTitle));
                    if (!tAuthor.isEmpty()) meta.domain   = "TikTok • @" + decodeUnicodeEscapes(tAuthor);
                    if (!tThumb.isEmpty())  meta.imageUrl = tThumb.replace("\\/", "/");
                }
            } catch (Exception e) {
                Log.d(TAG, "TikTok oembed: " + e.getMessage());
            }

            if (meta.title.isEmpty()) meta.title = "TikTok Video";
            return meta;
        }

        // ── 5. Spotify ────────────────────────────────────────────────────────────
        if (urlString.contains("spotify.com")) {
            meta.platform   = "Spotify";
            meta.domain     = "Spotify";
            meta.faviconUrl = "https://open.spotify.com/favicon.ico";

            if      (urlString.contains("/track/"))    meta.domain = "Spotify Track";
            else if (urlString.contains("/album/"))    meta.domain = "Spotify Album";
            else if (urlString.contains("/artist/"))   meta.domain = "Spotify Artist";
            else if (urlString.contains("/playlist/")) meta.domain = "Spotify Playlist";
            else if (urlString.contains("/episode/"))  meta.domain = "Spotify Podcast";

            try {
                String oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(urlString, "UTF-8");
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String sTitle = extractJsonString(json, "title");
                    String sThumb = extractJsonString(json, "thumbnail_url");
                    if (!sTitle.isEmpty()) meta.title    = decodeHtmlEntities(decodeUnicodeEscapes(sTitle));
                    if (!sThumb.isEmpty()) meta.imageUrl = sThumb.replace("\\/", "/");
                }
            } catch (Exception e) {
                Log.d(TAG, "Spotify oembed: " + e.getMessage());
            }

            if (meta.title.isEmpty()) meta.title = "Spotify";
            return meta;
        }

        // ── 6. X / Twitter ───────────────────────────────────────────────────────
        if (urlString.contains("twitter.com") || urlString.contains("x.com")) {
            meta.platform   = "X";
            meta.domain     = "x.com";
            meta.faviconUrl = "https://x.com/favicon.ico";

            // Twitter publish oembed for author name
            try {
                String oembedUrl = "https://publish.twitter.com/oembed?url="
                        + URLEncoder.encode(urlString, "UTF-8") + "&format=json";
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String tAuthor = extractJsonString(json, "author_name");
                    if (!tAuthor.isEmpty()) meta.domain = "X • @" + decodeUnicodeEscapes(tAuthor);
                }
            } catch (Exception e) {
                Log.d(TAG, "Twitter oembed: " + e.getMessage());
            }

            // Scrape og:title and og:image — but filter profile pictures
            try {
                String html = httpGet(urlString, "Mozilla/5.0 (compatible; Twitterbot/1.0)");
                if (html != null) {
                    String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                    if (ogTitle.isEmpty()) ogTitle = extractMetaTag(html, "name=\"twitter:title\"");
                    if (!ogTitle.isEmpty() && meta.title.isEmpty()) meta.title = decodeHtmlEntities(ogTitle);

                    String ogImage = extractMetaTag(html, "property=\"og:image\"");
                    if (ogImage.isEmpty()) ogImage = extractMetaTag(html, "name=\"twitter:image\"");
                    // Skip profile images — they contain /profile_images/ in the path
                    if (!ogImage.isEmpty()
                            && !ogImage.contains("/profile_images/")
                            && !ogImage.contains("abs.twimg.com/sticky/")) {
                        meta.imageUrl = ogImage;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "X page scrape: " + e.getMessage());
            }

            if (meta.title.isEmpty()) meta.title = "X Post";
            return meta;
        }

        // ── 7. LinkedIn ───────────────────────────────────────────────────────────
        if (urlString.contains("linkedin.com")) {
            meta.platform   = "LinkedIn";
            meta.domain     = "linkedin.com";
            meta.faviconUrl = "https://www.linkedin.com/favicon.ico";
            // Fall through to general scraper — LinkedIn serves og:image for public pages
        }

        // ── 8. Pinterest ──────────────────────────────────────────────────────────
        if (urlString.contains("pinterest.com") || urlString.contains("pin.it")) {
            meta.platform   = "Pinterest";
            meta.domain     = "pinterest.com";
            meta.faviconUrl = "https://www.pinterest.com/favicon.ico";
            // Fall through to general scraper
        }

        // ── General Web Scraper ───────────────────────────────────────────────────
        try {
            String html = httpGet(urlString,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            if (html != null) {
                String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                if (ogTitle.isEmpty()) ogTitle = extractMetaTag(html, "name=\"twitter:title\"");
                if (ogTitle.isEmpty()) ogTitle = extractTitleTag(html);
                if (!ogTitle.isEmpty()) meta.title = decodeHtmlEntities(ogTitle);

                String ogImage = extractMetaTag(html, "property=\"og:image\"");
                if (ogImage.isEmpty()) ogImage = extractMetaTag(html, "name=\"twitter:image\"");
                if (!ogImage.isEmpty() && !ogImage.contains("/profile_images/")) {
                    meta.imageUrl = ogImage;
                }

                if (meta.faviconUrl == null || meta.faviconUrl.isEmpty()) {
                    meta.faviconUrl = extractFaviconUrl(html, urlString);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "General scraper: " + e.getMessage());
        }

        if (meta.title.isEmpty()) meta.title = meta.domain;

        // Favicon fallback: always ensure we at least have /favicon.ico
        if (meta.faviconUrl == null || meta.faviconUrl.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(urlString);
                meta.faviconUrl = uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
            } catch (Exception e) { /* ignore */ }
        }

        return meta;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private static String httpGet(String url, String userAgent) {
        try {
            OkHttpClient client = buildClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "httpGet failed for " + url + ": " + e.getMessage());
        }
        return null;
    }

    private static String extractRedditPreviewImage(String json) {
        try {
            // preview.images[0].source.url  —  the value is HTML-entity-encoded inside JSON
            Pattern p = Pattern.compile(
                    "\"preview\"\\s*:\\s*\\{.*?\"images\"\\s*:\\s*\\[\\s*\\{.*?" +
                    "\"source\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL);
            Matcher m = p.matcher(json);
            if (m.find()) {
                // URL has HTML-encoded ampersands (&amp;) and escaped slashes
                return decodeHtmlEntities(m.group(1).replace("\\/", "/"));
            }
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private static String extractJsonString(String json, String key) {
        try {
            Pattern p = Pattern.compile(
                    "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private static String getDomainName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getHost();
            if (domain != null) {
                return domain.startsWith("www.") ? domain.substring(4) : domain;
            }
        } catch (Exception e) { /* ignore */ }
        return "web";
    }

    public static String detectPlatform(String domain) {
        if (domain == null) return "Web";
        String lower = domain.toLowerCase();
        if (lower.contains("youtube.com") || lower.contains("youtu.be"))      return "YouTube";
        if (lower.contains("instagram.com"))                                   return "Instagram";
        if (lower.contains("twitter.com") || lower.contains("x.com"))         return "X";
        if (lower.contains("reddit.com")  || lower.contains("redd.it"))       return "Reddit";
        if (lower.contains("spotify.com"))                                     return "Spotify";
        if (lower.contains("tiktok.com"))                                      return "TikTok";
        if (lower.contains("linkedin.com"))                                    return "LinkedIn";
        if (lower.contains("pinterest.com") || lower.contains("pin.it"))      return "Pinterest";
        return "Web";
    }

    private static String extractMetaTag(String html, String attributeQuery) {
        try {
            Pattern metaPattern = Pattern.compile("<meta\\s+([^>]+?)\\s*/?>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = metaPattern.matcher(html);
            while (matcher.find()) {
                String attrs = matcher.group(1);
                String cleanQuery = attributeQuery.replace("\"", "").replace("'", "");
                String cleanAttrs = attrs.replace("\"", "").replace("'", "");
                if (cleanAttrs.toLowerCase().contains(cleanQuery.toLowerCase())) {
                    Pattern cp = Pattern.compile("content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                    Matcher cm = cp.matcher(attrs);
                    if (cm.find()) return cm.group(1);
                }
            }
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private static String extractTitleTag(String html) {
        try {
            Pattern p = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) return m.group(1).trim();
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private static String extractFaviconUrl(String html, String originalUrl) {
        try {
            // Match <link> tags with rel containing "icon" — handle any attribute order
            Pattern p = Pattern.compile("<link\\s+([^>]+?)\\s*/?>", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            while (m.find()) {
                String attrs = m.group(1);
                String attrsLower = attrs.toLowerCase();
                if (attrsLower.contains("rel=") && (attrsLower.contains("\"icon\"")
                        || attrsLower.contains("'icon'")
                        || attrsLower.contains("shortcut icon"))) {
                    Pattern hp = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                    Matcher hm = hp.matcher(attrs);
                    if (hm.find()) {
                        String href = hm.group(1);
                        if (href.startsWith("http")) return href;
                        if (href.startsWith("//"))   return "https:" + href;
                        java.net.URI uri = new java.net.URI(originalUrl);
                        String base = uri.getScheme() + "://" + uri.getHost();
                        return href.startsWith("/") ? base + href : base + "/" + href;
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        try {
            java.net.URI uri = new java.net.URI(originalUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
        } catch (Exception e) { return ""; }
    }

    public static String extractYoutubeVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        Pattern p = Pattern.compile(
                "(?i)(?:https?://)?(?:www\\.|m\\.)?(?:youtu\\.be/|youtube\\.com/" +
                "(?:embed/|v/|watch\\?v=|watch\\?.+&v=|shorts/|live/))([\\w-]{11})");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)&amp;",    "&")
                   .replaceAll("(?i)&quot;",   "\"")
                   .replaceAll("(?i)&apos;",   "'")
                   .replaceAll("(?i)&lt;",     "<")
                   .replaceAll("(?i)&gt;",     ">")
                   .replaceAll("(?i)&#39;",    "'")
                   .replaceAll("(?i)&#34;",    "\"")
                   .replaceAll("(?i)&#039;",   "'")
                   .replaceAll("(?i)&#034;",   "\"")
                   .replaceAll("(?i)&middot;", "•")
                   .replaceAll("(?i)&ndash;",  "–")
                   .replaceAll("(?i)&mdash;",  "—")
                   .trim();
    }

    public static String decodeUnicodeEscapes(String text) {
        if (text == null) return "";
        try {
            Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            Matcher m = p.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int c = Integer.parseInt(m.group(1), 16);
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) c)));
            }
            m.appendTail(sb);
            return sb.toString()
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        } catch (Exception e) { return text; }
    }
}
