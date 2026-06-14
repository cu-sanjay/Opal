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
        public String resolvedUrl = ""; // Final URL after following redirects
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────────

    private static OkHttpClient buildClient(int connectSec, int readSec) {
        return new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(connectSec, TimeUnit.SECONDS)
                .readTimeout(readSec, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Follows redirects and returns the final destination URL.
     * Handles a.co → amazon, pin.it → pinterest, bit.ly, t.co, amzn.to etc.
     */
    public static String resolveToFinalUrl(String url) {
        // Try HEAD first (faster — no body download)
        try {
            OkHttpClient client = buildClient(8, 5);
            Request req = new Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            try (Response r = client.newCall(req).execute()) {
                String finalUrl = r.request().url().toString();
                if (!finalUrl.isEmpty() && !finalUrl.equals(url)) return finalUrl;
            }
        } catch (Exception ignored) { }

        // Fallback: GET (some servers reject HEAD)
        try {
            OkHttpClient client = buildClient(8, 5);
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            try (Response r = client.newCall(req).execute()) {
                if (r.body() != null) r.body().close();
                String finalUrl = r.request().url().toString();
                if (!finalUrl.isEmpty()) return finalUrl;
            }
        } catch (Exception ignored) { }

        return url;
    }

    private static String httpGet(String url, String userAgent) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();
            try (Response r = buildClient(12, 12).newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null) return r.body().string();
            }
        } catch (Exception e) {
            Log.d(TAG, "httpGet failed: " + url + " — " + e.getMessage());
        }
        return null;
    }

    // ── Entry point ───────────────────────────────────────────────────────────────

    public static Metadata fetchMetadata(String urlString) {
        Metadata meta = new Metadata();
        if (urlString == null || urlString.trim().isEmpty()) return meta;

        // Step 1: Resolve short/redirect URLs (a.co, pin.it, bit.ly, t.co, amzn.to …)
        String resolvedUrl = resolveToFinalUrl(urlString);
        meta.resolvedUrl = resolvedUrl;
        String url = resolvedUrl; // work with final URL from here

        meta.domain   = getDomainName(url);
        meta.platform = detectPlatform(meta.domain);

        // ── 1. YouTube ────────────────────────────────────────────────────────────
        String ytVideoId = extractYoutubeVideoId(url);
        if (ytVideoId != null) {
            meta.platform   = "YouTube";
            meta.imageUrl   = "https://img.youtube.com/vi/" + ytVideoId + "/hqdefault.jpg";
            meta.domain     = "youtube.com";
            meta.faviconUrl = "https://www.youtube.com/favicon.ico";

            try {
                String oembedUrl = "https://www.youtube.com/oembed?url="
                        + URLEncoder.encode(url, "UTF-8") + "&format=json";
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
            } catch (Exception e) { Log.e(TAG, "YouTube oembed: " + e.getMessage()); }

            if (meta.title.isEmpty()) meta.title = "YouTube Video";
            return meta;
        }

        // ── 2. Reddit ─────────────────────────────────────────────────────────────
        if (url.contains("reddit.com") || url.contains("redd.it")) {
            meta.platform   = "Reddit";
            meta.domain     = "reddit.com";
            meta.faviconUrl = "https://www.reddit.com/favicon.ico";

            try {
                String cleanUrl = url.contains("?")
                        ? url.substring(0, url.indexOf("?")) : url;
                if (!cleanUrl.endsWith("/")) cleanUrl += "/";
                String jsonUrl = cleanUrl + ".json";

                String json = httpGet(jsonUrl, "Mozilla/5.0 OpalApp/1.4 (Android link saver)");
                if (json != null) {
                    String subreddit = extractJsonString(json, "subreddit");
                    String postTitle = extractJsonString(json, "title");
                    String author    = extractJsonString(json, "author");
                    String mediaUrl  = extractJsonString(json, "url_overridden_by_dest");
                    String thumbUrl  = extractJsonString(json, "thumbnail");
                    String previewImage = extractRedditPreviewImage(json);

                    // Determine page type from URL structure
                    boolean isComment = isRedditCommentPage(url);
                    boolean isUser    = url.contains("/u/") || url.contains("/user/");

                    if (!postTitle.isEmpty()) {
                        meta.title = decodeHtmlEntities(decodeUnicodeEscapes(postTitle));
                    }

                    // Domain shows: "r/subreddit • u/author" or "Reddit Profile • u/author"
                    if (isUser) {
                        String userPattern = extractRedditUsername(url);
                        meta.domain = "Reddit Profile • u/" + (userPattern.isEmpty() ? "user" : userPattern);
                        meta.title  = meta.title.isEmpty() ? "u/" + userPattern + " — Reddit" : meta.title;
                    } else if (!subreddit.isEmpty()) {
                        String authorTag = (!author.isEmpty() && !author.equals("[deleted]"))
                                ? " • u/" + decodeUnicodeEscapes(author) : "";
                        String typeTag   = isComment ? " • Comment" : "";
                        meta.domain = "r/" + decodeUnicodeEscapes(subreddit) + authorTag + typeTag;
                    }

                    // Image priority: preview > direct media image > thumbnail
                    if (!previewImage.isEmpty()) {
                        meta.imageUrl = previewImage;
                    } else {
                        String mUrl = mediaUrl.replace("\\/", "/");
                        if (mUrl.matches("(?i).*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$")) {
                            meta.imageUrl = mUrl;
                        } else if (!thumbUrl.isEmpty()
                                && !thumbUrl.equals("default") && !thumbUrl.equals("self")
                                && !thumbUrl.equals("nsfw")    && !thumbUrl.equals("image")
                                && !thumbUrl.equals("spoiler") && thumbUrl.startsWith("http")) {
                            meta.imageUrl = thumbUrl.replace("\\/", "/");
                        }
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Reddit: " + e.getMessage()); }

            if (meta.title.isEmpty()) meta.title = "Reddit Post";
            return meta;
        }

        // ── 3. Instagram ──────────────────────────────────────────────────────────
        if (url.contains("instagram.com")) {
            meta.platform   = "Instagram";
            meta.domain     = "instagram.com";
            meta.faviconUrl = "https://www.instagram.com/favicon.ico";

            String shortcode = "";
            Matcher mShort = Pattern.compile("/(?:p|reel|tv)/([\\w-]+)").matcher(url);
            if (mShort.find()) shortcode = mShort.group(1);

            String username = "";
            Matcher mUser = Pattern.compile("instagram\\.com/([\\w\\._-]+)").matcher(url);
            if (mUser.find()) {
                String p = mUser.group(1);
                if (!p.equals("p") && !p.equals("reel") && !p.equals("tv")
                        && !p.equals("stories") && !p.equals("explore")
                        && !p.equals("static") && !p.equals("accounts")) {
                    username = p;
                }
            }
            if (!username.isEmpty()) meta.domain = "Instagram • @" + username;

            // Try public oEmbed
            try {
                String oembedUrl = "https://www.instagram.com/oembed/?url="
                        + URLEncoder.encode(url, "UTF-8") + "&format=json&maxwidth=640";
                String json = httpGet(oembedUrl, "Mozilla/5.0 (compatible; OpalApp/1.4)");
                if (json != null) {
                    String thumb   = extractJsonString(json, "thumbnail_url");
                    String author  = extractJsonString(json, "author_name");
                    String igTitle = extractJsonString(json, "title");
                    if (!thumb.isEmpty())   meta.imageUrl = thumb.replace("\\/", "/");
                    if (!author.isEmpty() && username.isEmpty())
                        meta.domain = "Instagram • @" + decodeUnicodeEscapes(author);
                    if (!igTitle.isEmpty()) meta.title = decodeHtmlEntities(decodeUnicodeEscapes(igTitle));
                }
            } catch (Exception e) { Log.d(TAG, "Instagram oembed: " + e.getMessage()); }

            if (meta.title.isEmpty()) {
                if (!shortcode.isEmpty()) {
                    meta.title = url.contains("/reel/") ? "Instagram Reel" : "Instagram Post";
                } else if (!username.isEmpty()) {
                    meta.title = "@" + username + " on Instagram";
                } else {
                    meta.title = "Instagram";
                }
            }
            return meta;
        }

        // ── 4. TikTok ─────────────────────────────────────────────────────────────
        if (url.contains("tiktok.com")) {
            meta.platform   = "TikTok";
            meta.domain     = "tiktok.com";
            meta.faviconUrl = "https://www.tiktok.com/favicon.ico";

            try {
                String oembedUrl = "https://www.tiktok.com/oembed?url="
                        + URLEncoder.encode(url, "UTF-8");
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String t  = extractJsonString(json, "title");
                    String a  = extractJsonString(json, "author_name");
                    String th = extractJsonString(json, "thumbnail_url");
                    if (!t.isEmpty())  meta.title    = decodeHtmlEntities(decodeUnicodeEscapes(t));
                    if (!a.isEmpty())  meta.domain   = "TikTok • @" + decodeUnicodeEscapes(a);
                    if (!th.isEmpty()) meta.imageUrl = th.replace("\\/", "/");
                }
            } catch (Exception e) { Log.d(TAG, "TikTok oembed: " + e.getMessage()); }

            if (meta.title.isEmpty()) meta.title = "TikTok Video";
            return meta;
        }

        // ── 5. Spotify ────────────────────────────────────────────────────────────
        if (url.contains("spotify.com")) {
            meta.platform   = "Spotify";
            meta.faviconUrl = "https://open.spotify.com/favicon.ico";

            if      (url.contains("/track/"))    meta.domain = "Spotify Track";
            else if (url.contains("/album/"))    meta.domain = "Spotify Album";
            else if (url.contains("/artist/"))   meta.domain = "Spotify Artist";
            else if (url.contains("/playlist/")) meta.domain = "Spotify Playlist";
            else if (url.contains("/episode/"))  meta.domain = "Spotify Podcast";
            else if (url.contains("/show/"))     meta.domain = "Spotify Show";
            else                                 meta.domain = "Spotify";

            try {
                String oembedUrl = "https://open.spotify.com/oembed?url="
                        + URLEncoder.encode(url, "UTF-8");
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String t  = extractJsonString(json, "title");
                    String th = extractJsonString(json, "thumbnail_url");
                    if (!t.isEmpty())  meta.title    = decodeHtmlEntities(decodeUnicodeEscapes(t));
                    if (!th.isEmpty()) meta.imageUrl = th.replace("\\/", "/");
                }
            } catch (Exception e) { Log.d(TAG, "Spotify oembed: " + e.getMessage()); }

            if (meta.title.isEmpty()) meta.title = "Spotify";
            return meta;
        }

        // ── 6. X / Twitter ────────────────────────────────────────────────────────
        if (url.contains("twitter.com") || url.contains("x.com")) {
            meta.platform   = "X";
            meta.domain     = "x.com";
            meta.faviconUrl = "https://x.com/favicon.ico";

            try {
                String oembedUrl = "https://publish.twitter.com/oembed?url="
                        + URLEncoder.encode(url, "UTF-8") + "&format=json";
                String json = httpGet(oembedUrl, "Mozilla/5.0");
                if (json != null) {
                    String tAuthor = extractJsonString(json, "author_name");
                    if (!tAuthor.isEmpty()) meta.domain = "X • @" + decodeUnicodeEscapes(tAuthor);
                }
            } catch (Exception e) { Log.d(TAG, "Twitter oembed: " + e.getMessage()); }

            // Scrape og: tags but reject profile photos
            try {
                String html = httpGet(url, "Mozilla/5.0 (compatible; Twitterbot/1.0)");
                if (html != null) {
                    String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                    if (ogTitle.isEmpty()) ogTitle = extractMetaTag(html, "name=\"twitter:title\"");
                    if (!ogTitle.isEmpty() && meta.title.isEmpty())
                        meta.title = decodeHtmlEntities(ogTitle);

                    String ogImage = extractMetaTag(html, "property=\"og:image\"");
                    if (ogImage.isEmpty()) ogImage = extractMetaTag(html, "name=\"twitter:image\"");
                    // Only use media images — never profile pictures
                    if (!ogImage.isEmpty()
                            && !ogImage.contains("/profile_images/")
                            && !ogImage.contains("abs.twimg.com/sticky/")) {
                        meta.imageUrl = ogImage;
                    }
                }
            } catch (Exception e) { Log.d(TAG, "X scrape: " + e.getMessage()); }

            if (meta.title.isEmpty()) meta.title = "X Post";
            return meta;
        }

        // ── 7. Amazon ─────────────────────────────────────────────────────────────
        if (isAmazonUrl(url)) {
            meta.platform   = "Amazon";
            meta.faviconUrl = "https://www.amazon.com/favicon.ico";
            meta.domain     = "Amazon";

            // Try to extract ASIN for clean domain label
            String asin = extractAmazonAsin(url);
            String amazonDomain = getDomainName(url);
            meta.domain = "Amazon" + (amazonDomain.contains("amazon.") ? " • " + amazonDomain : "");

            // General scraper — Amazon has very good og: tags for product pages
            String html = httpGet(url,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            if (html != null) {
                String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                if (ogTitle.isEmpty()) ogTitle = extractTitleTag(html);
                if (!ogTitle.isEmpty()) meta.title = decodeHtmlEntities(ogTitle);

                String ogImage = extractMetaTag(html, "property=\"og:image\"");
                if (!ogImage.isEmpty()) meta.imageUrl = ogImage;

                meta.faviconUrl = extractFaviconUrl(html, url);
            }

            if (meta.title.isEmpty()) meta.title = asin.isEmpty() ? "Amazon Product" : "Amazon • " + asin;
            return meta;
        }

        // ── 8. GitHub ─────────────────────────────────────────────────────────────
        if (url.contains("github.com")) {
            meta.platform   = "GitHub";
            meta.faviconUrl = "https://github.com/favicon.ico";

            // Parse user/repo from URL
            Matcher ghMatcher = Pattern.compile("github\\.com/([\\w._-]+)(?:/([\\w._-]+))?").matcher(url);
            if (ghMatcher.find()) {
                String ghUser = ghMatcher.group(1);
                String ghRepo = ghMatcher.group(2);
                meta.domain = (ghRepo != null && !ghRepo.isEmpty())
                        ? ghUser + "/" + ghRepo : "GitHub • @" + ghUser;
            } else {
                meta.domain = "github.com";
            }

            // GitHub has excellent og: social cards — use general scraper
            String html = httpGet(url,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            if (html != null) {
                String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                if (!ogTitle.isEmpty()) meta.title = decodeHtmlEntities(ogTitle);

                String ogImage = extractMetaTag(html, "property=\"og:image\"");
                if (!ogImage.isEmpty()) meta.imageUrl = ogImage;
            }

            if (meta.title.isEmpty()) meta.title = meta.domain;
            return meta;
        }

        // ── 9. Medium / Substack ──────────────────────────────────────────────────
        if (url.contains("medium.com") || url.contains("substack.com")) {
            meta.platform   = url.contains("substack.com") ? "Substack" : "Medium";
            meta.domain     = url.contains("substack.com") ? "substack.com" : "medium.com";
            meta.faviconUrl = "https://" + meta.domain + "/favicon.ico";
            // Fall through to general scraper — both serve great og: tags
        }

        // ── 10. Twitch ────────────────────────────────────────────────────────────
        if (url.contains("twitch.tv")) {
            meta.platform   = "Twitch";
            meta.faviconUrl = "https://www.twitch.tv/favicon.ico";

            Matcher twMatcher = Pattern.compile("twitch\\.tv/([\\w]+)").matcher(url);
            if (twMatcher.find()) {
                meta.domain = "Twitch • " + twMatcher.group(1);
            } else {
                meta.domain = "twitch.tv";
            }
            // Fall through to general scraper
        }

        // ── 11. Threads ───────────────────────────────────────────────────────────
        if (url.contains("threads.net")) {
            meta.platform   = "Threads";
            meta.faviconUrl = "https://www.threads.net/favicon.ico";

            Matcher thrMatcher = Pattern.compile("threads\\.net/@([\\w._]+)").matcher(url);
            if (thrMatcher.find()) {
                meta.domain = "Threads • @" + thrMatcher.group(1);
            } else {
                meta.domain = "threads.net";
            }
            // Fall through to general scraper
        }

        // ── 12. LinkedIn ──────────────────────────────────────────────────────────
        if (url.contains("linkedin.com")) {
            meta.platform   = "LinkedIn";
            meta.domain     = "linkedin.com";
            meta.faviconUrl = "https://www.linkedin.com/favicon.ico";
            // Fall through
        }

        // ── 13. Pinterest ─────────────────────────────────────────────────────────
        if (url.contains("pinterest.com") || url.contains("pin.it")) {
            meta.platform   = "Pinterest";
            meta.domain     = "pinterest.com";
            meta.faviconUrl = "https://www.pinterest.com/favicon.ico";
            // Fall through
        }

        // ── General Web Scraper ───────────────────────────────────────────────────
        try {
            String html = httpGet(url,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            if (html != null) {
                if (meta.title.isEmpty()) {
                    String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                    if (ogTitle.isEmpty()) ogTitle = extractMetaTag(html, "name=\"twitter:title\"");
                    if (ogTitle.isEmpty()) ogTitle = extractTitleTag(html);
                    if (!ogTitle.isEmpty()) meta.title = decodeHtmlEntities(ogTitle);
                }

                if (meta.imageUrl.isEmpty()) {
                    String ogImage = extractMetaTag(html, "property=\"og:image\"");
                    if (ogImage.isEmpty()) ogImage = extractMetaTag(html, "name=\"twitter:image\"");
                    if (!ogImage.isEmpty() && !ogImage.contains("/profile_images/"))
                        meta.imageUrl = ogImage;
                }

                if (meta.faviconUrl.isEmpty()) {
                    meta.faviconUrl = extractFaviconUrl(html, url);
                }
            }
        } catch (Exception e) { Log.e(TAG, "General scraper: " + e.getMessage()); }

        if (meta.title.isEmpty()) meta.title = meta.domain;

        if (meta.faviconUrl == null || meta.faviconUrl.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(url);
                meta.faviconUrl = uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
            } catch (Exception ignored) { }
        }

        return meta;
    }

    // ── Platform helpers ──────────────────────────────────────────────────────────

    public static String detectPlatform(String domain) {
        if (domain == null) return "Web";
        String d = domain.toLowerCase();
        if (d.contains("youtube.com") || d.contains("youtu.be"))           return "YouTube";
        if (d.contains("instagram.com"))                                    return "Instagram";
        if (d.contains("twitter.com") || d.contains("x.com"))              return "X";
        if (d.contains("reddit.com")  || d.contains("redd.it"))            return "Reddit";
        if (d.contains("spotify.com"))                                      return "Spotify";
        if (d.contains("tiktok.com"))                                       return "TikTok";
        if (d.contains("linkedin.com"))                                     return "LinkedIn";
        if (d.contains("pinterest.com") || d.contains("pin.it"))           return "Pinterest";
        if (isAmazonDomain(d))                                              return "Amazon";
        if (d.contains("github.com"))                                       return "GitHub";
        if (d.contains("medium.com"))                                       return "Medium";
        if (d.contains("substack.com"))                                     return "Substack";
        if (d.contains("twitch.tv"))                                        return "Twitch";
        if (d.contains("threads.net"))                                      return "Threads";
        return "Web";
    }

    private static boolean isAmazonUrl(String url) {
        String d = getDomainName(url).toLowerCase();
        return isAmazonDomain(d);
    }

    private static boolean isAmazonDomain(String d) {
        return d.contains("amazon.") || d.equals("a.co")
                || d.contains("amzn.to") || d.contains("amzn.eu")
                || d.contains("amzn.in") || d.contains("amzn.com");
    }

    private static String extractAmazonAsin(String url) {
        try {
            Matcher m = Pattern.compile("/(?:dp|gp/product|ASIN)/([A-Z0-9]{10})").matcher(url);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "";
    }

    private static boolean isRedditCommentPage(String url) {
        // Comment page has 6+ segments: /r/sub/comments/id/slug/comment_id/
        try {
            String path = new java.net.URI(url).getPath();
            String[] parts = path.split("/");
            int nonEmpty = 0;
            for (String p : parts) if (!p.isEmpty()) nonEmpty++;
            return nonEmpty >= 6;
        } catch (Exception ignored) { }
        return false;
    }

    private static String extractRedditUsername(String url) {
        try {
            Matcher m = Pattern.compile("/(?:u|user)/([\\w_-]+)").matcher(url);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "";
    }

    // ── Metadata extractors ───────────────────────────────────────────────────────

    private static String extractRedditPreviewImage(String json) {
        try {
            Pattern p = Pattern.compile(
                    "\"preview\"\\s*:\\s*\\{.*?\"images\"\\s*:\\s*\\[\\s*\\{.*?" +
                    "\"source\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL);
            Matcher m = p.matcher(json);
            if (m.find()) {
                return decodeHtmlEntities(m.group(1).replace("\\/", "/"));
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String extractJsonString(String json, String key) {
        try {
            Pattern p = Pattern.compile(
                    "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "";
    }

    private static String getDomainName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String domain = uri.getHost();
            if (domain != null) return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (Exception ignored) { }
        return "web";
    }

    private static String extractMetaTag(String html, String attributeQuery) {
        try {
            Pattern metaPattern = Pattern.compile("<meta\\s+([^>]+?)\\s*/?>",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = metaPattern.matcher(html);
            while (matcher.find()) {
                String attrs = matcher.group(1);
                String cleanQ = attributeQuery.replace("\"", "").replace("'", "");
                String cleanA = attrs.replace("\"", "").replace("'", "");
                if (cleanA.toLowerCase().contains(cleanQ.toLowerCase())) {
                    Pattern cp = Pattern.compile(
                            "content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                    Matcher cm = cp.matcher(attrs);
                    if (cm.find()) return cm.group(1);
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String extractTitleTag(String html) {
        try {
            Pattern p = Pattern.compile(
                    "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) { }
        return "";
    }

    private static String extractFaviconUrl(String html, String originalUrl) {
        try {
            // Match any <link> tag whose rel contains "icon"
            Pattern p = Pattern.compile("<link\\s+([^>]+?)\\s*/?>", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            while (m.find()) {
                String attrs      = m.group(1);
                String attrsLower = attrs.toLowerCase();
                if (attrsLower.contains("rel=") && (
                        attrsLower.contains("\"icon\"") || attrsLower.contains("'icon'") ||
                        attrsLower.contains("shortcut icon") || attrsLower.contains("apple-touch-icon"))) {
                    Pattern hp = Pattern.compile(
                            "href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                    Matcher hm = hp.matcher(attrs);
                    if (hm.find()) {
                        String href = hm.group(1);
                        if (href.startsWith("http"))  return href;
                        if (href.startsWith("//"))    return "https:" + href;
                        java.net.URI uri = new java.net.URI(originalUrl);
                        String base = uri.getScheme() + "://" + uri.getHost();
                        return href.startsWith("/") ? base + href : base + "/" + href;
                    }
                }
            }
        } catch (Exception ignored) { }
        try {
            java.net.URI uri = new java.net.URI(originalUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
        } catch (Exception ignored) { return ""; }
    }

    public static String extractYoutubeVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        Pattern p = Pattern.compile(
                "(?i)(?:https?://)?(?:www\\.|m\\.)?(?:youtu\\.be/|youtube\\.com/" +
                "(?:embed/|v/|watch\\?v=|watch\\?.+&v=|shorts/|live/))([\\w-]{11})");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    // ── Text decoders ─────────────────────────────────────────────────────────────

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
                   .replaceAll("(?i)&nbsp;",   " ")
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
                    .replace("\\/", "/").replace("\\\"", "\"")
                    .replace("\\'", "'").replace("\\n", "\n")
                    .replace("\\r", "\r").replace("\\t", "\t");
        } catch (Exception ignored) { return text; }
    }
}
