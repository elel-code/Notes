package net.micode.notes.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.micode.notes.data.Notes

@Entity(tableName = "note")
data class RoomNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = Notes.NoteColumns.ID)
    val id: Long? = null,
    @ColumnInfo(name = Notes.NoteColumns.PARENT_ID, defaultValue = "0")
    val parentId: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.ALERTED_DATE, defaultValue = "0")
    val alertedDate: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.BG_COLOR_ID, defaultValue = "0")
    val bgColorId: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.CREATED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val createdDate: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.HAS_ATTACHMENT, defaultValue = "0")
    val hasAttachment: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.MODIFIED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val modifiedDate: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.NOTES_COUNT, defaultValue = "0")
    val notesCount: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.SNIPPET, defaultValue = "''")
    val snippet: String = "",
    @ColumnInfo(name = Notes.NoteColumns.TYPE, defaultValue = "0")
    val type: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_ID, defaultValue = "0")
    val widgetId: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_TYPE, defaultValue = "-1")
    val widgetType: Int = -1,
    @ColumnInfo(name = Notes.NoteColumns.SYNC_ID, defaultValue = "0")
    val syncId: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.LOCAL_MODIFIED, defaultValue = "0")
    val localModified: Int = 0,
    @ColumnInfo(name = Notes.NoteColumns.ORIGIN_PARENT_ID, defaultValue = "0")
    val originParentId: Long = 0,
    @ColumnInfo(name = Notes.NoteColumns.GTASK_ID, defaultValue = "''")
    val gtaskId: String = "",
    @ColumnInfo(name = Notes.NoteColumns.VERSION, defaultValue = "0")
    val version: Long = 0
)

@Entity(
    tableName = "data",
    indices = [Index(value = [Notes.DataColumns.NOTE_ID], name = "note_id_index")]
)
data class RoomDataEntity(
    @PrimaryKey
    @ColumnInfo(name = Notes.DataColumns.ID)
    val id: Long? = null,
    @ColumnInfo(name = Notes.DataColumns.MIME_TYPE)
    val mimeType: String,
    @ColumnInfo(name = Notes.DataColumns.NOTE_ID, defaultValue = "0")
    val noteId: Long = 0,
    @ColumnInfo(name = Notes.DataColumns.CREATED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val createdDate: Long = 0,
    @ColumnInfo(name = Notes.DataColumns.MODIFIED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val modifiedDate: Long = 0,
    @ColumnInfo(name = Notes.DataColumns.CONTENT, defaultValue = "''")
    val content: String = "",
    @ColumnInfo(name = Notes.DataColumns.DATA1)
    val data1: Long? = null,
    @ColumnInfo(name = Notes.DataColumns.DATA2)
    val data2: Long? = null,
    @ColumnInfo(name = Notes.DataColumns.DATA3, defaultValue = "''")
    val data3: String = "",
    @ColumnInfo(name = Notes.DataColumns.DATA4, defaultValue = "''")
    val data4: String = "",
    @ColumnInfo(name = Notes.DataColumns.DATA5, defaultValue = "''")
    val data5: String = ""
)
