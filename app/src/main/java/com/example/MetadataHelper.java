package com.example;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.net.URLEncoder;
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

    public static Metadata fetchMetadata(String urlString) {
        Metadata meta = new Metadata();
        if (urlString == null || urlString.trim().isEmpty()) {
            return meta;
        }

        meta.domain = getDomainName(urlString);
        meta.platform = detectPlatform(meta.domain);

        // 1. YouTube Specialized Fetcher
        String ytVideoId = extractYoutubeVideoId(urlString);
        if (ytVideoId != null) {
            meta.platform = "YouTube";
            meta.imageUrl = "https://img.youtube.com/vi/" + ytVideoId + "/hqdefault.jpg";
            meta.domain = "youtube.com";

            try {
                String oembedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(urlString, "UTF-8") + "&format=json";
                OkHttpClient client = new OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build();

                Request request = new Request.Builder()
                        .url(oembedUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();

                        Pattern pTitle = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mTitle = pTitle.matcher(json);
                        String ytTitle = "";
                        if (mTitle.find()) {
                            ytTitle = decodeUnicodeEscapes(mTitle.group(1));
                        }

                        Pattern pAuthor = Pattern.compile("\"author_name\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mAuthor = pAuthor.matcher(json);
                        String ytAuthor = "";
                        if (mAuthor.find()) {
                            ytAuthor = decodeUnicodeEscapes(mAuthor.group(1));
                        }

                        Pattern pThumb = Pattern.compile("\"thumbnail_url\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mThumb = pThumb.matcher(json);
                        if (mThumb.find()) {
                            String thumbUrl = mThumb.group(1).replace("\\/", "/");
                            if (!thumbUrl.isEmpty()) {
                                meta.imageUrl = thumbUrl;
                            }
                        }

                        if (!ytTitle.isEmpty()) {
                            meta.title = decodeHtmlEntities(ytTitle);
                            if (!ytAuthor.isEmpty()) {
                                meta.domain = "YouTube • " + decodeHtmlEntities(ytAuthor);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching YouTube oembed: " + e.getMessage());
            }

            if (meta.title == null || meta.title.trim().isEmpty()) {
                meta.title = "YouTube Video (" + ytVideoId + ")";
            }
            return meta;
        }

        // 2. Reddit Specialized Fetcher
        if (urlString.contains("reddit.com")) {
            meta.platform = "Reddit";
            meta.domain = "reddit.com";

            try {
                String cleanUrl = urlString;
                if (cleanUrl.contains("?")) {
                    cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf("?"));
                }
                if (!cleanUrl.endsWith("/")) {
                    cleanUrl += "/";
                }
                String jsonUrl = cleanUrl + ".json";

                OkHttpClient client = new OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build();

                Request request = new Request.Builder()
                        .url(jsonUrl)
                        .header("User-Agent", "Mozilla/5.0 OpalLinkSaver/1.0")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();

                        Pattern pSub = Pattern.compile("\"subreddit\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mSub = pSub.matcher(json);
                        String subName = "";
                        if (mSub.find()) {
                            subName = decodeUnicodeEscapes(mSub.group(1));
                        }

                        Pattern pTitle = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mTitle = pTitle.matcher(json);
                        String postTitle = "";
                        if (mTitle.find()) {
                            postTitle = decodeUnicodeEscapes(mTitle.group(1));
                        }

                        Pattern pMedia = Pattern.compile("\"url_overridden_by_dest\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mMedia = pMedia.matcher(json);
                        String mediaUrl = "";
                        if (mMedia.find()) {
                            mediaUrl = mMedia.group(1).replace("\\/", "/");
                        }

                        Pattern pThumb = Pattern.compile("\"thumbnail\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mThumb = pThumb.matcher(json);
                        String thumbUrl = "";
                        if (mThumb.find()) {
                            thumbUrl = mThumb.group(1).replace("\\/", "/");
                        }

                        if (!postTitle.isEmpty()) {
                            meta.title = decodeHtmlEntities(postTitle);
                        }
                        if (!subName.isEmpty()) {
                            meta.domain = "r/" + decodeHtmlEntities(subName);
                        }

                        if (mediaUrl.endsWith(".jpg") || mediaUrl.endsWith(".jpeg") || mediaUrl.endsWith(".png") || mediaUrl.endsWith(".webp") || mediaUrl.endsWith(".gif")) {
                            meta.imageUrl = mediaUrl;
                        } else if (!thumbUrl.isEmpty() && !thumbUrl.equals("default") && !thumbUrl.equals("self") && !thumbUrl.equals("nsfw") && !thumbUrl.equals("image")) {
                            meta.imageUrl = thumbUrl;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching Reddit details: " + e.getMessage());
            }

            if (meta.title == null || meta.title.trim().isEmpty()) {
                meta.title = "Reddit Post";
            }
            return meta;
        }

        // 3. TikTok Specialized Fetcher
        if (urlString.contains("tiktok.com")) {
            meta.platform = "Web";
            meta.domain = "tiktok.com";

            try {
                String oembedUrl = "https://www.tiktok.com/oembed?url=" + URLEncoder.encode(urlString, "UTF-8");
                OkHttpClient client = new OkHttpClient.Builder().build();
                Request request = new Request.Builder().url(oembedUrl).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();

                        Pattern pTitle = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mTitle = pTitle.matcher(json);
                        if (mTitle.find()) {
                            meta.title = decodeHtmlEntities(decodeUnicodeEscapes(mTitle.group(1)));
                        }

                        Pattern pAuthor = Pattern.compile("\"author_name\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mAuthor = pAuthor.matcher(json);
                        if (mAuthor.find()) {
                            meta.domain = "TikTok • " + decodeHtmlEntities(decodeUnicodeEscapes(mAuthor.group(1)));
                        }

                        Pattern pThumb = Pattern.compile("\"thumbnail_url\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher mThumb = pThumb.matcher(json);
                        if (mThumb.find()) {
                            meta.imageUrl = mThumb.group(1).replace("\\/", "/");
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            if (meta.title == null || meta.title.trim().isEmpty()) {
                meta.title = "TikTok Video";
            }
            return meta;
        }

        // 4. Instagram Pre-parsing
        if (urlString.contains("instagram.com")) {
            meta.platform = "Instagram";
            meta.domain = "instagram.com";

            String shortcode = "";
            Pattern pShort = Pattern.compile("/(?:p|reel|tv)/([\\w-]+)");
            Matcher mShort = pShort.matcher(urlString);
            if (mShort.find()) {
                shortcode = mShort.group(1);
            }

            String username = "";
            Pattern pUser = Pattern.compile("instagram\\.com/([\\w\\._-]+)");
            Matcher mUser = pUser.matcher(urlString);
            if (mUser.find()) {
                String potential = mUser.group(1);
                if (!potential.equals("p") && !potential.equals("reel") && !potential.equals("tv") && !potential.equals("stories") && !potential.equals("explore") && !potential.equals("static")) {
                    username = potential;
                }
            }

            if (!username.isEmpty()) {
                meta.domain = "Instagram • @" + username;
                if (!shortcode.isEmpty()) {
                    meta.title = "Instagram Reel (" + shortcode + ")";
                } else {
                    meta.title = "Instagram Profile - @" + username;
                }
            } else if (!shortcode.isEmpty()) {
                meta.title = "Instagram Post (" + shortcode + ")";
            } else {
                meta.title = "Instagram Link";
            }
        }

        // General Web scraper
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Request request = new Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String html = response.body().string();

                String ogTitle = extractMetaTag(html, "property=\"og:title\"");
                if (ogTitle.isEmpty()) {
                    ogTitle = extractMetaTag(html, "name=\"twitter:title\"");
                }
                if (ogTitle.isEmpty()) {
                    ogTitle = extractTitleTag(html);
                }

                if (!ogTitle.isEmpty()) {
                    meta.title = decodeHtmlEntities(ogTitle);
                }

                String ogImage = extractMetaTag(html, "property=\"og:image\"");
                if (ogImage.isEmpty()) {
                    ogImage = extractMetaTag(html, "name=\"twitter:image\"");
                }
                if (!ogImage.isEmpty()) {
                    meta.imageUrl = ogImage;
                }

                meta.faviconUrl = extractFaviconUrl(html, urlString);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching general metadata: " + e.getMessage());
        }

        if (meta.title == null || meta.title.trim().isEmpty()) {
            meta.title = meta.domain;
        }

        return meta;
    }

    private static String getDomainName(String url) {
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

    public static String detectPlatform(String domain) {
        if (domain == null) return "Web";
        String lower = domain.toLowerCase();
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) return "YouTube";
        if (lower.contains("instagram.com")) return "Instagram";
        if (lower.contains("twitter.com") || lower.contains("x.com")) return "X";
        if (lower.contains("reddit.com")) return "Reddit";
        return "Web";
    }

    private static String extractMetaTag(String html, String attributeQuery) {
        try {
            Pattern metaPattern = Pattern.compile("<meta\\s+([^>]+)>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = metaPattern.matcher(html);
            while (matcher.find()) {
                String metaTagContent = matcher.group(1);
                String cleanQuery = attributeQuery.replace("\"", "").replace("'", "");
                String cleanTag = metaTagContent.replace("\"", "").replace("'", "");
                if (cleanTag.toLowerCase().contains(cleanQuery.toLowerCase())) {
                    Pattern contentPattern = Pattern.compile("content\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                    Matcher contentMatcher = contentPattern.matcher(metaTagContent);
                    if (contentMatcher.find()) {
                        return contentMatcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private static String extractTitleTag(String html) {
        try {
            Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private static String extractFaviconUrl(String html, String originalUrl) {
        try {
            Pattern pattern = Pattern.compile("<link\\s+[^>]*?rel=\"(?:shortcut )?icon\"[^>]*?href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String href = matcher.group(1);
                if (href.startsWith("http")) {
                    return href;
                } else if (href.startsWith("//")) {
                    return "https:" + href;
                } else {
                    java.net.URI uri = new java.net.URI(originalUrl);
                    String base = uri.getScheme() + "://" + uri.getHost();
                    if (href.startsWith("/")) {
                        return base + href;
                    } else {
                        return base + "/" + href;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        try {
            java.net.URI uri = new java.net.URI(originalUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
        } catch (Exception e) {
            return "";
        }
    }

    public static String extractYoutubeVideoId(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String pattern = "(?i)(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtu\\.be\\/|youtube\\.com\\/(?:embed\\/|v\\/|watch\\?v=|watch\\?.+&v=|shorts\\/|live\\/))([\\w-]{11})";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)&amp;", "&")
                   .replaceAll("(?i)&quot;", "\"")
                   .replaceAll("(?i)&apos;", "'")
                   .replaceAll("(?i)&lt;", "<")
                   .replaceAll("(?i)&gt;", ">")
                   .replaceAll("(?i)&#39;", "'")
                   .replaceAll("(?i)&#34;", "\"")
                   .replaceAll("(?i)&#039;", "'")
                   .replaceAll("(?i)&#034;", "\"")
                   .replaceAll("(?i)&middot;", "•")
                   .replaceAll("(?i)&ndash;", "–")
                   .replaceAll("(?i)&mdash;", "—")
                   .trim();
    }

    public static String decodeUnicodeEscapes(String text) {
        if (text == null) return "";
        try {
            Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            Matcher matcher = pattern.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int charVal = Integer.parseInt(matcher.group(1), 16);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) charVal)));
            }
            matcher.appendTail(sb);
            return sb.toString()
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        } catch (Exception e) {
            return text;
        }
    }
}
