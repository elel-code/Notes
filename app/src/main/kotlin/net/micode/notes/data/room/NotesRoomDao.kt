package net.micode.notes.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NotesRoomDao {
    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE ((noteItem.type <> :systemType AND noteItem.parent_id = :folderId)
            OR (noteItem._id = :callRecordFolderId
                AND EXISTS(SELECT 1 FROM note AS child WHERE child.parent_id = noteItem._id)))
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun getRootListItems(
        folderId: Long,
        systemType: Int,
        noteType: Int,
        callRecordFolderId: Long
    ): List<NoteListRow>

    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE (((noteItem.type <> :systemType AND noteItem.parent_id = :folderId)
            OR (noteItem._id = :callRecordFolderId
                AND EXISTS(SELECT 1 FROM note AS child WHERE child.parent_id = noteItem._id)))
            AND noteItem.snippet LIKE :searchText)
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun searchRootListItems(
        folderId: Long,
        systemType: Int,
        noteType: Int,
        callRecordFolderId: Long,
        searchText: String
    ): List<NoteListRow>

    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE noteItem.parent_id = :folderId
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun getFolderListItems(folderId: Long, noteType: Int): List<NoteListRow>

    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE noteItem.parent_id = :folderId AND noteItem.snippet LIKE :searchText
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun searchFolderListItems(folderId: Long, noteType: Int, searchText: String): List<NoteListRow>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM note
            WHERE type = :folderType
              AND parent_id <> :trashFolderId
              AND snippet = :name
        )
        """
    )
    suspend fun hasVisibleFolderNamed(
        name: String,
        folderType: Int,
        trashFolderId: Long
    ): Boolean

    @Insert
    suspend fun insertNote(note: RoomNoteEntity): Long

    @Update
    suspend fun updateNote(note: RoomNoteEntity): Int

    @Query("SELECT * FROM note WHERE _id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): RoomNoteEntity?

    @Query(
        """
        UPDATE note
        SET snippet = :name,
            type = :folderType,
            local_modified = 1
        WHERE _id = :folderId
        """
    )
    suspend fun renameFolder(folderId: Long, name: String, folderType: Int): Int

    @Query(
        """
        WITH RECURSIVE descendants(_id) AS (
            SELECT _id
            FROM note
            WHERE _id IN (:rootIds)
            UNION
            SELECT child._id
            FROM note AS child
            INNER JOIN descendants ON child.parent_id = descendants._id
        )
        SELECT _id FROM descendants
        """
    )
    suspend fun getDescendantNoteIds(rootIds: List<Long>): List<Long>

    @Query("DELETE FROM data WHERE note_id IN (:noteIds)")
    suspend fun deleteDataByNoteIds(noteIds: List<Long>): Int

    @Query("DELETE FROM note WHERE _id IN (:noteIds)")
    suspend fun deleteNotesByIds(noteIds: List<Long>): Int

    @Query(
        """
        SELECT widget_id, widget_type
        FROM note
        WHERE parent_id = :folderId
        """
    )
    suspend fun getFolderWidgets(folderId: Long): List<WidgetRow>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM note
            WHERE _id = :noteId
              AND type = :type
              AND parent_id <> :trashFolderId
        )
        """
    )
    suspend fun isVisibleNote(noteId: Long, type: Int, trashFolderId: Long): Boolean

    @Query("SELECT snippet FROM note WHERE _id = :noteId LIMIT 1")
    suspend fun getSnippetById(noteId: Long): String?

    @Query(
        """
        SELECT _id AS noteId, alert_date
        FROM note
        WHERE alert_date > :currentDate
          AND type = :noteType
        """
    )
    suspend fun getFutureAlertNotes(currentDate: Long, noteType: Int): List<AlarmRow>

    @Query(
        """
        SELECT _id, bg_color_id, snippet
        FROM note
        WHERE widget_id = :appWidgetId
          AND parent_id <> :trashFolderId
        """
    )
    suspend fun getNotesByWidgetId(appWidgetId: Int, trashFolderId: Long): List<WidgetNoteRow>

    @Query(
        """
        UPDATE note
        SET widget_id = :invalidWidgetId
        WHERE widget_id = :appWidgetId
        """
    )
    suspend fun clearWidgetBinding(appWidgetId: Int, invalidWidgetId: Int): Int

    @Query("SELECT * FROM data WHERE note_id = :noteId")
    suspend fun getDataByNoteId(noteId: Long): List<RoomDataEntity>

    @Query("SELECT * FROM data WHERE note_id = :noteId AND mime_type = :mimeType LIMIT 1")
    suspend fun getDataByNoteIdAndMimeType(noteId: Long, mimeType: String): RoomDataEntity?

    @Insert
    suspend fun insertData(data: RoomDataEntity): Long

    @Update
    suspend fun updateData(data: RoomDataEntity): Int

    @Query(
        """
        SELECT note_id AS noteId, data3 AS phoneNumber
        FROM data
        WHERE data1 = :callDate
          AND mime_type = :mimeType
        """
    )
    suspend fun findCallNotesByDate(callDate: Long, mimeType: String): List<CallNoteLookupRow>

}
