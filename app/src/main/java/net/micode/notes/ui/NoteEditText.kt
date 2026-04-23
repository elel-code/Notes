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
import android.graphics.Rect
import android.text.Selection
import android.text.Spanned
import android.text.TextUtils
import android.text.style.URLSpan
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import net.micode.notes.R
import kotlin.math.max
import kotlin.math.min

class NoteEditText : EditText {
    private var mIndex = 0
    private var mSelectionStartBeforeDelete = 0

    /**
     * Call by the [NoteEditActivity] to delete or add edit text
     */
    interface OnTextViewChangeListener {
        /**
         * Delete current edit text when [KeyEvent.KEYCODE_DEL] happens
         * and the text is null
         */
        fun onEditTextDelete(index: Int, text: String?)

        /**
         * Add edit text after current edit text when [KeyEvent.KEYCODE_ENTER]
         * happen
         */
        fun onEditTextEnter(index: Int, text: String?)

        /**
         * Hide or show item option when text change
         */
        fun onTextChange(index: Int, hasText: Boolean)
    }

    private var mOnTextViewChangeListener: OnTextViewChangeListener? = null

    constructor(context: Context?) : super(context, null) {
        mIndex = 0
    }

    fun setIndex(index: Int) {
        mIndex = index
    }

    fun setOnTextViewChangeListener(listener: OnTextViewChangeListener?) {
        mOnTextViewChangeListener = listener
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context,
        attrs,
        android.R.attr.editTextStyle
    )

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.getAction()) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.getX().toInt()
                val y = event.getY().toInt()
                x -= getTotalPaddingLeft()
                y -= getTotalPaddingTop()
                x += getScrollX()
                y += getScrollY()

                val layout = getLayout()
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())
                Selection.setSelection(getText(), off)
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> if (mOnTextViewChangeListener != null) {
                return false
            }

            KeyEvent.KEYCODE_DEL -> mSelectionStartBeforeDelete = getSelectionStart()
            else -> {}
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> if (mOnTextViewChangeListener != null) {
                if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                    mOnTextViewChangeListener!!.onEditTextDelete(mIndex, getText().toString())
                    return true
                }
            } else {
                Log.d(TAG, "OnTextViewChangeListener was not seted")
            }

            KeyEvent.KEYCODE_ENTER -> if (mOnTextViewChangeListener != null) {
                val selectionStart = getSelectionStart()
                val text = getText().subSequence(selectionStart, length()).toString()
                setText(getText().subSequence(0, selectionStart))
                mOnTextViewChangeListener!!.onEditTextEnter(mIndex + 1, text)
            } else {
                Log.d(TAG, "OnTextViewChangeListener was not seted")
            }

            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener!!.onTextChange(mIndex, false)
            } else {
                mOnTextViewChangeListener!!.onTextChange(mIndex, true)
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun onCreateContextMenu(menu: ContextMenu) {
        if (getText() is Spanned) {
            val selStart = getSelectionStart()
            val selEnd = getSelectionEnd()

            val min = min(selStart, selEnd)
            val max = max(selStart, selEnd)

            val urls = (getText() as Spanned).getSpans<URLSpan?>(min, max, URLSpan::class.java)
            if (urls.size == 1) {
                var defaultResId = 0
                for (schema in sSchemaActionResMap.keys) {
                    if (urls[0]!!.getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema)!!
                        break
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other
                }

                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                    object : MenuItem.OnMenuItemClickListener {
                        override fun onMenuItemClick(item: MenuItem?): Boolean {
                            // goto a new intent
                            urls[0]!!.onClick(this@NoteEditText)
                            return true
                        }
                    })
            }
        }
        super.onCreateContextMenu(menu)
    }

    companion object {
        private const val TAG = "NoteEditText"
        private const val SCHEME_TEL = "tel:"
        private const val SCHEME_HTTP = "http:"
        private const val SCHEME_EMAIL = "mailto:"

        private val sSchemaActionResMap: MutableMap<String, Int?> = HashMap<String, Int?>()

        init {
            sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel)
            sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web)
            sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email)
        }
    }
}
