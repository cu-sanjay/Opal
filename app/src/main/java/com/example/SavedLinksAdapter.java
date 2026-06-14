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
        this.context = context;
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

        // Resolve platform drawable for the small domain-row icon
        int platformDrawableId = platformIcon(link.getPlatform());
        holder.platformLogo.setImageResource(platformDrawableId);

        String imageUrl   = link.getImageUrl()   != null ? link.getImageUrl().trim()   : "";
        String faviconUrl = link.getFaviconUrl()  != null ? link.getFaviconUrl().trim() : "";

        if (!imageUrl.isEmpty()) {
            // ── Show OG/preview image ────────────────────────────────────────────
            holder.linkImage.setVisibility(View.VISIBLE);
            holder.placeholderLogo.setVisibility(View.GONE);
            Glide.with(context)
                    .load(imageUrl)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .error(android.R.color.transparent)
                    .into(holder.linkImage);
        } else {
            // ── No OG image — try favicon, fall back to platform icon ────────────
            holder.linkImage.setVisibility(View.GONE);
            holder.placeholderLogo.setVisibility(View.VISIBLE);

            if (!faviconUrl.isEmpty()) {
                Glide.with(context)
                        .load(faviconUrl)
                        .placeholder(platformDrawableId)
                        .error(platformDrawableId)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(holder.placeholderLogo);
            } else {
                holder.placeholderLogo.setImageResource(platformDrawableId);
            }
        }

        holder.itemView.setOnClickListener(v -> openUrlInBrowser(link.getUrl()));
        holder.openButton.setOnClickListener(v -> openUrlInBrowser(link.getUrl()));

        holder.copyButton.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip =
                        android.content.ClipData.newPlainText("Opal Link", link.getUrl());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(context, "Link copied!", android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) { /* ignore */ }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(link);
        });
    }

    private int platformIcon(String platform) {
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
            default:          return R.drawable.ic_platform_chrome;
        }
    }

    private void openUrlInBrowser(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(browserIntent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "Could not open URL", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return linksList.size();
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        ImageView linkImage;
        ImageView placeholderLogo;
        ImageView platformLogo;
        TextView titleText;
        TextView domainText;
        ImageView copyButton;
        ImageView openButton;
        ImageView deleteButton;

        LinkViewHolder(@NonNull View itemView) {
            super(itemView);
            linkImage       = itemView.findViewById(R.id.link_image);
            placeholderLogo = itemView.findViewById(R.id.platform_placeholder_logo);
            platformLogo    = itemView.findViewById(R.id.platform_logo);
            titleText       = itemView.findViewById(R.id.link_title);
            domainText      = itemView.findViewById(R.id.link_domain);
            copyButton      = itemView.findViewById(R.id.copy_button);
            openButton      = itemView.findViewById(R.id.open_button);
            deleteButton    = itemView.findViewById(R.id.delete_button);
        }
    }
}
