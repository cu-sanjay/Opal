package com.example;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import java.util.ArrayList;
import java.util.List;

public class SavedLinksAdapter extends RecyclerView.Adapter<SavedLinksAdapter.LinkViewHolder> {

    public interface OnLinkActionListener {
        void onDelete(SavedLink link);
    }

    private final Context context;
    private List<SavedLink> linksList = new ArrayList<>();
    private final OnLinkActionListener actionListener;

    public SavedLinksAdapter(Context context, OnLinkActionListener actionListener) {
        this.context        = context;
        this.actionListener = actionListener;
    }

    public void setLinks(List<SavedLink> links) {
        this.linksList = links;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_saved_link, parent, false);
        return new LinkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LinkViewHolder holder, int position) {
        final SavedLink link = linksList.get(position);

        holder.titleText.setText(link.getTitle());
        holder.domainText.setText(link.getDomain());

        int iconRes   = platformIcon(link.getPlatform());
        holder.platformLogo.setImageResource(iconRes);

        String imageUrl   = nvl(link.getImageUrl());
        String faviconUrl = nvl(link.getFaviconUrl());

        if (!imageUrl.isEmpty()) {
            // Show OG / preview image
            holder.linkImage.setVisibility(View.VISIBLE);
            holder.placeholderLogo.setVisibility(View.GONE);
            Glide.with(context)
                    .load(imageUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .error(android.R.color.transparent)
                    .into(holder.linkImage);
        } else {
            // No preview image: show favicon (with platform icon fallback)
            holder.linkImage.setVisibility(View.GONE);
            holder.placeholderLogo.setVisibility(View.VISIBLE);

            if (!faviconUrl.isEmpty()) {
                Glide.with(context)
                        .load(faviconUrl)
                        .placeholder(iconRes)
                        .error(iconRes)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(holder.placeholderLogo);
            } else {
                holder.placeholderLogo.setImageResource(iconRes);
            }
        }

        holder.itemView.setOnClickListener(v -> openUrl(link.getUrl()));
        holder.openButton.setOnClickListener(v -> openUrl(link.getUrl()));

        holder.copyButton.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cb =
                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Opal", link.getUrl()));
                    toast("Link copied!");
                }
            } catch (Exception ignored) { }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(link);
        });
    }

    private static int platformIcon(String platform) {
        if (platform == null) return R.drawable.ic_platform_chrome;
        switch (platform) {
            case "YouTube":   return R.drawable.ic_platform_youtube;
            case "Instagram": return R.drawable.ic_platform_instagram;
            case "X":         return R.drawable.ic_platform_x;
            case "Reddit":    return R.drawable.ic_platform_reddit;
            case "Spotify":   return R.drawable.ic_platform_spotify;
            case "TikTok":    return R.drawable.ic_platform_tiktok;
            case "LinkedIn":  return R.drawable.ic_platform_linkedin;
            case "Pinterest": return R.drawable.ic_platform_pinterest;
            case "Amazon":    return R.drawable.ic_platform_amazon;
            case "GitHub":    return R.drawable.ic_platform_github;
            case "Medium":    return R.drawable.ic_platform_medium;
            case "Substack":  return R.drawable.ic_platform_medium;   // reuse until custom icon
            case "Twitch":    return R.drawable.ic_platform_twitch;
            case "Threads":   return R.drawable.ic_platform_threads;
            default:          return R.drawable.ic_platform_chrome;
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) { toast("Could not open URL"); }
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String nvl(String s) {
        return s != null ? s.trim() : "";
    }

    @Override
    public int getItemCount() { return linksList.size(); }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        ImageView linkImage, placeholderLogo, platformLogo, copyButton, openButton, deleteButton;
        TextView  titleText, domainText;

        LinkViewHolder(@NonNull View v) {
            super(v);
            linkImage       = v.findViewById(R.id.link_image);
            placeholderLogo = v.findViewById(R.id.platform_placeholder_logo);
            platformLogo    = v.findViewById(R.id.platform_logo);
            titleText       = v.findViewById(R.id.link_title);
            domainText      = v.findViewById(R.id.link_domain);
            copyButton      = v.findViewById(R.id.copy_button);
            openButton      = v.findViewById(R.id.open_button);
            deleteButton    = v.findViewById(R.id.delete_button);
        }
    }
}
