/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.micode.notes.data

import android.net.Uri

object Notes {
    const val AUTHORITY: String = "micode_notes"
    const val TAG: String = "Notes"
    const val TYPE_NOTE: Int = 0
    const val TYPE_FOLDER: Int = 1
    const val TYPE_SYSTEM: Int = 2

    /**
     * Following IDs are system folders' identifiers
     * [Notes.ID_ROOT_FOLDER] is default folder
     * [Notes.ID_TEMPARAY_FOLDER] is for notes belonging no folder
     * [Notes.ID_CALL_RECORD_FOLDER] is to store call records
     */
    const val ID_ROOT_FOLDER: Int = 0
    val ID_TEMPARAY_FOLDER: Int = -1
    val ID_CALL_RECORD_FOLDER: Int = -2
    val ID_TRASH_FOLER: Int = -3

    const val INTENT_EXTRA_ALERT_DATE: String = "net.micode.notes.alert_date"
    const val INTENT_EXTRA_BACKGROUND_ID: String = "net.micode.notes.background_color_id"
    const val INTENT_EXTRA_WIDGET_ID: String = "net.micode.notes.widget_id"
    const val INTENT_EXTRA_WIDGET_TYPE: String = "net.micode.notes.widget_type"
    const val INTENT_EXTRA_FOLDER_ID: String = "net.micode.notes.folder_id"
    const val INTENT_EXTRA_CALL_DATE: String = "net.micode.notes.call_date"

    val TYPE_WIDGET_INVALIDE: Int = -1
    const val TYPE_WIDGET_2X: Int = 0
    const val TYPE_WIDGET_4X: Int = 1

    /**
     * Uri to query all notes and folders
     */
    val CONTENT_NOTE_URI: Uri? = Uri.parse("content://" + AUTHORITY + "/note")

    /**
     * Uri to query data
     */
    val CONTENT_DATA_URI: Uri? = Uri.parse("content://" + AUTHORITY + "/data")

    object DataConstants {
        val NOTE: String = TextNote.CONTENT_ITEM_TYPE
        val CALL_NOTE: String = CallNote.CONTENT_ITEM_TYPE
    }

    interface NoteColumns {
        companion object {
            /**
             * The unique ID for a row
             * <P> Type: INTEGER (long) </P>
             */
            const val ID: String = "_id"

            /**
             * The parent's id for note or folder
             * <P> Type: INTEGER (long) </P>
             */
            const val PARENT_ID: String = "parent_id"

            /**
             * Created data for note or folder
             * <P> Type: INTEGER (long) </P>
             */
            const val CREATED_DATE: String = "created_date"

            /**
             * Latest modified date
             * <P> Type: INTEGER (long) </P>
             */
            const val MODIFIED_DATE: String = "modified_date"


            /**
             * Alert date
             * <P> Type: INTEGER (long) </P>
             */
            const val ALERTED_DATE: String = "alert_date"

            /**
             * Folder's name or text content of note
             * <P> Type: TEXT </P>
             */
            const val SNIPPET: String = "snippet"

            /**
             * Note's widget id
             * <P> Type: INTEGER (long) </P>
             */
            const val WIDGET_ID: String = "widget_id"

            /**
             * Note's widget type
             * <P> Type: INTEGER (long) </P>
             */
            const val WIDGET_TYPE: String = "widget_type"

            /**
             * Note's background color's id
             * <P> Type: INTEGER (long) </P>
             */
            const val BG_COLOR_ID: String = "bg_color_id"

            /**
             * For text note, it doesn't has attachment, for multi-media
             * note, it has at least one attachment
             * <P> Type: INTEGER </P>
             */
            const val HAS_ATTACHMENT: String = "has_attachment"

            /**
             * Folder's count of notes
             * <P> Type: INTEGER (long) </P>
             */
            const val NOTES_COUNT: String = "notes_count"

            /**
             * The file type: folder or note
             * <P> Type: INTEGER </P>
             */
            const val TYPE: String = "type"

            /**
             * The last sync id
             * <P> Type: INTEGER (long) </P>
             */
            const val SYNC_ID: String = "sync_id"

            /**
             * Sign to indicate local modified or not
             * <P> Type: INTEGER </P>
             */
            const val LOCAL_MODIFIED: String = "local_modified"

            /**
             * Original parent id before moving into temporary folder
             * <P> Type : INTEGER </P>
             */
            const val ORIGIN_PARENT_ID: String = "origin_parent_id"

            /**
             * The gtask id
             * <P> Type : TEXT </P>
             */
            const val GTASK_ID: String = "gtask_id"

            /**
             * The version code
             * <P> Type : INTEGER (long) </P>
             */
            const val VERSION: String = "version"
        }
    }

    interface DataColumns {
        companion object {
            /**
             * The unique ID for a row
             * <P> Type: INTEGER (long) </P>
             */
            const val ID: String = "_id"

            /**
             * The MIME type of the item represented by this row.
             * <P> Type: Text </P>
             */
            const val MIME_TYPE: String = "mime_type"

            /**
             * The reference id to note that this data belongs to
             * <P> Type: INTEGER (long) </P>
             */
            const val NOTE_ID: String = "note_id"

            /**
             * Created data for note or folder
             * <P> Type: INTEGER (long) </P>
             */
            const val CREATED_DATE: String = "created_date"

            /**
             * Latest modified date
             * <P> Type: INTEGER (long) </P>
             */
            const val MODIFIED_DATE: String = "modified_date"

            /**
             * Data's content
             * <P> Type: TEXT </P>
             */
            const val CONTENT: String = "content"


            /**
             * Generic data column, the meaning is [.MIMETYPE] specific, used for
             * integer data type
             * <P> Type: INTEGER </P>
             */
            const val DATA1: String = "data1"

            /**
             * Generic data column, the meaning is [.MIMETYPE] specific, used for
             * integer data type
             * <P> Type: INTEGER </P>
             */
            const val DATA2: String = "data2"

            /**
             * Generic data column, the meaning is [.MIMETYPE] specific, used for
             * TEXT data type
             * <P> Type: TEXT </P>
             */
            const val DATA3: String = "data3"

            /**
             * Generic data column, the meaning is [.MIMETYPE] specific, used for
             * TEXT data type
             * <P> Type: TEXT </P>
             */
            const val DATA4: String = "data4"

            /**
             * Generic data column, the meaning is [.MIMETYPE] specific, used for
             * TEXT data type
             * <P> Type: TEXT </P>
             */
            const val DATA5: String = "data5"
        }
    }

    object TextNote : DataColumns {
        /**
         * Mode to indicate the text in check list mode or not
         * <P> Type: Integer 1:check list mode 0: normal mode </P>
         */
        val MODE: String = DataColumns.Companion.DATA1

        const val MODE_CHECK_LIST: Int = 1

        const val CONTENT_TYPE: String = "vnd.android.cursor.dir/text_note"

        const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/text_note"

        val CONTENT_URI: Uri? = Uri.parse("content://" + AUTHORITY + "/text_note")
    }

    object CallNote : DataColumns {
        /**
         * Call date for this record
         * <P> Type: INTEGER (long) </P>
         */
        val CALL_DATE: String = DataColumns.Companion.DATA1

        /**
         * Phone number for this record
         * <P> Type: TEXT </P>
         */
        val PHONE_NUMBER: String = DataColumns.Companion.DATA3

        const val CONTENT_TYPE: String = "vnd.android.cursor.dir/call_note"

        const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/call_note"

        val CONTENT_URI: Uri? = Uri.parse("content://" + AUTHORITY + "/call_note")
    }
}
