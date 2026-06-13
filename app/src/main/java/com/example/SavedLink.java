package com.example;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_links")
public class SavedLink {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String url;
    private String title;
    private String domain;
    private String platform;
    private String imageUrl;
    private long timestamp;
    private String faviconUrl;

    public SavedLink() {
        this.timestamp = System.currentTimeMillis();
    }

    public SavedLink(String url, String title, String domain, String platform, String imageUrl, String faviconUrl) {
        this.url = url;
        this.title = title != null ? title : url;
        this.domain = domain;
        this.platform = platform != null ? platform : "Web";
        this.imageUrl = imageUrl;
        this.faviconUrl = faviconUrl;
        this.timestamp = System.currentTimeMillis();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }
}
