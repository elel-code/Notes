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

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.format.DateFormat
import android.text.format.DateUtils
import net.micode.notes.R
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener
import java.util.Calendar

class DateTimePickerDialog(context: Context, date: Long) : AlertDialog(context),
    DialogInterface.OnClickListener {
    private val mDate: Calendar = Calendar.getInstance()
    private var mIs24HourView = false
    private var mOnDateTimeSetListener: OnDateTimeSetListener? = null
    private val mDateTimePicker: DateTimePicker

    interface OnDateTimeSetListener {
        fun OnDateTimeSet(dialog: AlertDialog?, date: Long)
    }

    init {
        mDateTimePicker = DateTimePicker(context)
        setView(mDateTimePicker)
        mDateTimePicker.setOnDateTimeChangedListener(object : OnDateTimeChangedListener {
            override fun onDateTimeChanged(
                view: DateTimePicker?, year: Int, month: Int,
                dayOfMonth: Int, hourOfDay: Int, minute: Int
            ) {
                mDate.set(Calendar.YEAR, year)
                mDate.set(Calendar.MONTH, month)
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                mDate.set(Calendar.MINUTE, minute)
                updateTitle(mDate.getTimeInMillis())
            }
        })
        mDate.setTimeInMillis(date)
        mDate.set(Calendar.SECOND, 0)
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis())
        setButton(context.getString(R.string.datetime_dialog_ok), this)
        setButton2(
            context.getString(R.string.datetime_dialog_cancel),
            null as DialogInterface.OnClickListener?
        )
        set24HourView(DateFormat.is24HourFormat(this.getContext()))
        updateTitle(mDate.getTimeInMillis())
    }

    fun set24HourView(is24HourView: Boolean) {
        mIs24HourView = is24HourView
    }

    fun setOnDateTimeSetListener(callBack: OnDateTimeSetListener?) {
        mOnDateTimeSetListener = callBack
    }

    private fun updateTitle(date: Long) {
        var flag =
            DateUtils.FORMAT_SHOW_YEAR or
                    DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_TIME
        flag = flag or if (mIs24HourView) DateUtils.FORMAT_24HOUR else DateUtils.FORMAT_24HOUR
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag))
    }

    override fun onClick(arg0: DialogInterface?, arg1: Int) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener!!.OnDateTimeSet(this, mDate.getTimeInMillis())
        }
    }
}