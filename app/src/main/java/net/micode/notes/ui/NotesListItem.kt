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
package net.micode.notes.ui

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.ResourceParser.NoteItemBgResources
import java.util.Locale

class NotesListItem(context: Context?) : LinearLayout(context) {
    private val mAlert: ImageView
    private val mTitle: TextView
    private val mTime: TextView
    private val mCallName: TextView
    var itemData: NoteItemData? = null
        private set
    private val mCheckBox: CheckBox

    init {
        inflate(context, R.layout.note_item, this)
        mAlert = findViewById<View?>(R.id.iv_alert_icon) as ImageView
        mTitle = findViewById<View?>(R.id.tv_title) as TextView
        mTime = findViewById<View?>(R.id.tv_time) as TextView
        mCallName = findViewById<View?>(R.id.tv_name) as TextView
        mCheckBox = findViewById<View?>(android.R.id.checkbox) as CheckBox
    }

    fun bind(
        context: Context, data: NoteItemData, choiceMode: Boolean, checked: Boolean,
        searchText: String?
    ) {
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(VISIBLE)
            mCheckBox.setChecked(checked)
        } else {
            mCheckBox.setVisibility(GONE)
        }

        this.itemData = data
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            mCallName.setVisibility(GONE)
            mAlert.setVisibility(VISIBLE)
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem)
            mTitle.setText(
                getHighlightedText(
                    context,
                    context.getString(R.string.call_record_folder_name)
                            + context.getString(
                        R.string.format_folder_files_count,
                        data.getNotesCount()
                    ),
                    searchText
                )
            )
            mAlert.setImageResource(R.drawable.call_record)
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            mCallName.setVisibility(VISIBLE)
            mCallName.setText(data.getCallName())
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem)
            mTitle.setText(
                getHighlightedText(
                    context,
                    DataUtils.getFormattedSnippet(data.getSnippet()), searchText
                )
            )
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock)
                mAlert.setVisibility(VISIBLE)
            } else {
                mAlert.setVisibility(GONE)
            }
        } else {
            mCallName.setVisibility(GONE)
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem)

            if (data.getType() == Notes.TYPE_FOLDER) {
                mTitle.setText(
                    getHighlightedText(
                        context,
                        data.getSnippet() + context.getString(
                            R.string.format_folder_files_count,
                            data.getNotesCount()
                        ),
                        searchText
                    )
                )
                mAlert.setVisibility(GONE)
            } else {
                mTitle.setText(
                    getHighlightedText(
                        context,
                        DataUtils.getFormattedSnippet(data.getSnippet()), searchText
                    )
                )
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock)
                    mAlert.setVisibility(VISIBLE)
                } else {
                    mAlert.setVisibility(GONE)
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()))

        setBackground(data)
    }

    private fun setBackground(data: NoteItemData) {
        val id = data.getBgColorId()
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id))
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id))
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id))
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id))
            }
        } else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes())
        }
    }

    private fun getHighlightedText(
        context: Context,
        fullText: String?,
        query: String?
    ): CharSequence {
        val spannable = SpannableString(if (fullText == null) "" else fullText)
        if (TextUtils.isEmpty(fullText) || TextUtils.isEmpty(query)) {
            return spannable
        }

        val lowerText = fullText!!.lowercase(Locale.getDefault())
        val lowerQuery = query!!.lowercase(Locale.getDefault())
        var start = lowerText.indexOf(lowerQuery)
        while (start >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(
                    context.getResources().getColor(
                        R.color.user_query_highlight
                    )
                ), start, start + query.length,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
            start = lowerText.indexOf(lowerQuery, start + query.length)
        }
        return spannable
    }
}
