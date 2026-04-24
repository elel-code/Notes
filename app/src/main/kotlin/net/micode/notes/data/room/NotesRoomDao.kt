package net.micode.notes.data.room

import androidx.room.Dao
import androidx.room.Query

@Dao
interface NotesRoomDao {
    @Query("UPDATE note SET gtask_id = '', sync_id = 0")
    suspend fun clearSyncMetadata()
}
