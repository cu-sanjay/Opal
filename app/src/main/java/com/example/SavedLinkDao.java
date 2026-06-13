package com.example;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;
import java.util.List;

@Dao
public interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY timestamp DESC")
    LiveData<List<SavedLink>> getAllLinksLiveData();

    @Query("SELECT * FROM saved_links ORDER BY timestamp DESC")
    List<SavedLink> getAllLinks();

    @Query("SELECT * FROM saved_links WHERE id = :id LIMIT 1")
    SavedLink getLinkById(int id);

    @Query("SELECT * FROM saved_links WHERE url = :url LIMIT 1")
    SavedLink getLinkByUrl(String url);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SavedLink link);

    @Query("DELETE FROM saved_links WHERE id = :id")
    void deleteById(int id);
}
