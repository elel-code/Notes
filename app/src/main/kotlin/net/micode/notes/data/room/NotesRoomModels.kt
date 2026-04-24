package net.micode.notes.data.room

import androidx.room.ColumnInfo
import net.micode.notes.data.Notes

data class NoteListRow(
    @ColumnInfo(name = Notes.NoteColumns.ID)
    val id: Long,
    @ColumnInfo(name = Notes.NoteColumns.ALERTED_DATE)
    val alertedDate: Long,
    @ColumnInfo(name = Notes.NoteColumns.BG_COLOR_ID)
    val bgColorId: Int,
    @ColumnInfo(name = Notes.NoteColumns.HAS_ATTACHMENT)
    val hasAttachment: Int,
    @ColumnInfo(name = Notes.NoteColumns.MODIFIED_DATE)
    val modifiedDate: Long,
    @ColumnInfo(name = Notes.NoteColumns.NOTES_COUNT)
    val notesCount: Int,
    @ColumnInfo(name = Notes.NoteColumns.PARENT_ID)
    val parentId: Long,
    @ColumnInfo(name = Notes.NoteColumns.SNIPPET)
    val snippet: String,
    @ColumnInfo(name = Notes.NoteColumns.TYPE)
    val type: Int,
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_ID)
    val widgetId: Int,
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_TYPE)
    val widgetType: Int
)

data class WidgetRow(
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_ID)
    val widgetId: Int,
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_TYPE)
    val widgetType: Int
)

data class AlarmRow(
    @ColumnInfo(name = "noteId")
    val noteId: Long,
    @ColumnInfo(name = Notes.NoteColumns.ALERTED_DATE)
    val alertedDate: Long
)

data class WidgetNoteRow(
    @ColumnInfo(name = Notes.NoteColumns.ID)
    val id: Long,
    @ColumnInfo(name = Notes.NoteColumns.BG_COLOR_ID)
    val bgColorId: Int,
    @ColumnInfo(name = Notes.NoteColumns.SNIPPET)
    val snippet: String
)

data class CallNoteLookupRow(
    val noteId: Long,
    val phoneNumber: String
)

data class StoredNotePayload(
    val note: RoomNoteEntity,
    val textData: RoomDataEntity?,
    val callData: RoomDataEntity?
)
