package com.example;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MetadataFetchWorker extends Worker {
    public MetadataFetchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int linkId = getInputData().getInt("link_id", -1);
        if (linkId == -1) return Result.failure();

        AppDatabase db   = AppDatabase.getDatabase(getApplicationContext());
        SavedLink   link = db.savedLinkDao().getLinkById(linkId);
        if (link == null) return Result.failure();

        try {
            MetadataHelper.Metadata meta = MetadataHelper.fetchMetadata(link.getUrl());
            if (meta != null) {
                // Update URL if a short link was resolved to its destination
                if (meta.resolvedUrl != null && !meta.resolvedUrl.isEmpty()
                        && !meta.resolvedUrl.equals(link.getUrl())) {
                    link.setUrl(meta.resolvedUrl);
                }

                link.setTitle(meta.title != null && !meta.title.isEmpty()
                        ? meta.title : link.getUrl());
                link.setImageUrl(meta.imageUrl);
                link.setFaviconUrl(meta.faviconUrl);
                link.setDomain(meta.domain);
                link.setPlatform(meta.platform);

                db.savedLinkDao().insert(link); // REPLACE by id
                return Result.success();
            }
        } catch (Exception e) {
            return Result.retry();
        }

        return Result.failure();
    }
}
