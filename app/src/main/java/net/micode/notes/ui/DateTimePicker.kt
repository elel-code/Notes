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
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.NumberPicker.OnValueChangeListener
import net.micode.notes.R
import java.text.DateFormatSymbols
import java.util.Calendar

class DateTimePicker @JvmOverloads constructor(
    context: Context,
    date: Long = System.currentTimeMillis(),
    is24HourView: Boolean = DateFormat.is24HourFormat(
        context
    )
) : FrameLayout(context) {
    private val mDateSpinner: NumberPicker
    private val mHourSpinner: NumberPicker
    private val mMinuteSpinner: NumberPicker
    private val mAmPmSpinner: NumberPicker
    private val mDate: Calendar

    private val mDateDisplayValues = arrayOfNulls<String>(DAYS_IN_ALL_WEEK)

    private var mIsAm: Boolean

    private var mIs24HourView = false

    private var mIsEnabled: Boolean = DEFAULT_ENABLE_STATE

    private var mInitialising = true

    private var mOnDateTimeChangedListener: OnDateTimeChangedListener? = null

    private val mOnDateChangedListener: OnValueChangeListener = object : OnValueChangeListener {
        override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal)
            updateDateControl()
            onDateTimeChanged()
        }
    }

    private val mOnHourChangedListener: OnValueChangeListener = object : OnValueChangeListener {
        override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
            var isDateChanged = false
            val cal = Calendar.getInstance()
            if (!mIs24HourView) {
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis())
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    isDateChanged = true
                } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis())
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    isDateChanged = true
                }
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                    oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1
                ) {
                    mIsAm = !mIsAm
                    updateAmPmControl()
                }
            } else {
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis())
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    isDateChanged = true
                } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis())
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    isDateChanged = true
                }
            }
            val newHour: Int =
                mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (if (mIsAm) 0 else HOURS_IN_HALF_DAY)
            mDate.set(Calendar.HOUR_OF_DAY, newHour)
            onDateTimeChanged()
            if (isDateChanged) {
                this.currentYear = cal.get(Calendar.YEAR)
                this.currentMonth = cal.get(Calendar.MONTH)
                this.currentDay = cal.get(Calendar.DAY_OF_MONTH)
            }
        }
    }

    private val mOnMinuteChangedListener: OnValueChangeListener = object : OnValueChangeListener {
        override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
            val minValue = mMinuteSpinner.getMinValue()
            val maxValue = mMinuteSpinner.getMaxValue()
            var offset = 0
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1
            }
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset)
                mHourSpinner.setValue(this.currentHour)
                updateDateControl()
                val newHour: Int = this.currentHourOfDay
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false
                    updateAmPmControl()
                } else {
                    mIsAm = true
                    updateAmPmControl()
                }
            }
            mDate.set(Calendar.MINUTE, newVal)
            onDateTimeChanged()
        }
    }

    private val mOnAmPmChangedListener: OnValueChangeListener = object : OnValueChangeListener {
        override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
            mIsAm = !mIsAm
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY)
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY)
            }
            updateAmPmControl()
            onDateTimeChanged()
        }
    }

    interface OnDateTimeChangedListener {
        fun onDateTimeChanged(
            view: DateTimePicker?, year: Int, month: Int,
            dayOfMonth: Int, hourOfDay: Int, minute: Int
        )
    }

    init {
        mDate = Calendar.getInstance()
        mIsAm = this.currentHourOfDay >= HOURS_IN_HALF_DAY
        inflate(context, R.layout.datetime_picker, this)

        mDateSpinner = findViewById<View?>(R.id.date) as NumberPicker
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL)
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL)
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener)

        mHourSpinner = findViewById<View?>(R.id.hour) as NumberPicker
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener)
        mMinuteSpinner = findViewById<View?>(R.id.minute) as NumberPicker
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL)
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL)
        mMinuteSpinner.setOnLongPressUpdateInterval(100)
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener)

        val stringsForAmPm = DateFormatSymbols().getAmPmStrings()
        mAmPmSpinner = findViewById<View?>(R.id.amPm) as NumberPicker
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL)
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL)
        mAmPmSpinner.setDisplayedValues(stringsForAmPm)
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener)

        // update controls to initial state
        updateDateControl()
        updateHourControl()
        updateAmPmControl()

        set24HourView(is24HourView)

        // set to current time
        setCurrentDate(date)

        setEnabled(isEnabled())

        // set the content descriptions
        mInitialising = false
    }

    override fun setEnabled(enabled: Boolean) {
        if (mIsEnabled == enabled) {
            return
        }
        super.setEnabled(enabled)
        mDateSpinner.setEnabled(enabled)
        mMinuteSpinner.setEnabled(enabled)
        mHourSpinner.setEnabled(enabled)
        mAmPmSpinner.setEnabled(enabled)
        mIsEnabled = enabled
    }

    override fun isEnabled(): Boolean {
        return mIsEnabled
    }

    val currentDateInTimeMillis: Long
        /**
         * Get the current date in millis
         * 
         * @return the current date in millis
         */
        get() = mDate.getTimeInMillis()

    /**
     * Set the current date
     * 
     * @param date The current date in millis
     */
    fun setCurrentDate(date: Long) {
        val cal = Calendar.getInstance()
        cal.setTimeInMillis(date)
        setCurrentDate(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
    }

    /**
     * Set the current date
     * 
     * @param year The current year
     * @param month The current month
     * @param dayOfMonth The current dayOfMonth
     * @param hourOfDay The current hourOfDay
     * @param minute The current minute
     */
    fun setCurrentDate(
        year: Int, month: Int,
        dayOfMonth: Int, hourOfDay: Int, minute: Int
    ) {
        this.currentYear = year
        this.currentMonth = month
        this.currentDay = dayOfMonth
        this.currentHour = hourOfDay
        this.currentMinute = minute
    }

    var currentYear: Int
        /**
         * Get current year
         * 
         * @return The current year
         */
        get() = mDate.get(Calendar.YEAR)
        /**
         * Set current year
         * 
         * @param year The current year
         */
        set(year) {
            if (!mInitialising && year == field) {
                return
            }
            mDate.set(Calendar.YEAR, year)
            updateDateControl()
            onDateTimeChanged()
        }

    var currentMonth: Int
        /**
         * Get current month in the year
         * 
         * @return The current month in the year
         */
        get() = mDate.get(Calendar.MONTH)
        /**
         * Set current month in the year
         * 
         * @param month The month in the year
         */
        set(month) {
            if (!mInitialising && month == field) {
                return
            }
            mDate.set(Calendar.MONTH, month)
            updateDateControl()
            onDateTimeChanged()
        }

    var currentDay: Int
        /**
         * Get current day of the month
         * 
         * @return The day of the month
         */
        get() = mDate.get(Calendar.DAY_OF_MONTH)
        /**
         * Set current day of the month
         * 
         * @param dayOfMonth The day of the month
         */
        set(dayOfMonth) {
            if (!mInitialising && dayOfMonth == field) {
                return
            }
            mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateControl()
            onDateTimeChanged()
        }

    val currentHourOfDay: Int
        /**
         * Get current hour in 24 hour mode, in the range (0~23)
         * @return The current hour in 24 hour mode
         */
        get() = mDate.get(Calendar.HOUR_OF_DAY)

    private var currentHour: Int
        get() {
            if (mIs24HourView) {
                return this.currentHourOfDay
            } else {
                val hour = this.currentHourOfDay
                if (hour > HOURS_IN_HALF_DAY) {
                    return hour - HOURS_IN_HALF_DAY
                } else {
                    return if (hour == 0) HOURS_IN_HALF_DAY else hour
                }
            }
        }
        /**
         * Set current hour in 24 hour mode, in the range (0~23)
         * 
         * @param hourOfDay
         */
        set(hourOfDay) {
            var hourOfDay = hourOfDay
            if (!mInitialising && hourOfDay == this.currentHourOfDay) {
                return
            }
            mDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
            if (!mIs24HourView) {
                if (hourOfDay >= HOURS_IN_HALF_DAY) {
                    mIsAm = false
                    if (hourOfDay > HOURS_IN_HALF_DAY) {
                        hourOfDay -= HOURS_IN_HALF_DAY
                    }
                } else {
                    mIsAm = true
                    if (hourOfDay == 0) {
                        hourOfDay = HOURS_IN_HALF_DAY
                    }
                }
                updateAmPmControl()
            }
            mHourSpinner.setValue(hourOfDay)
            onDateTimeChanged()
        }

    var currentMinute: Int
        /**
         * Get currentMinute
         * 
         * @return The Current Minute
         */
        get() = mDate.get(Calendar.MINUTE)
        /**
         * Set current minute
         */
        set(minute) {
            if (!mInitialising && minute == field) {
                return
            }
            mMinuteSpinner.setValue(minute)
            mDate.set(Calendar.MINUTE, minute)
            onDateTimeChanged()
        }

    /**
     * @return true if this is in 24 hour view else false.
     */
    fun is24HourView(): Boolean {
        return mIs24HourView
    }

    /**
     * Set whether in 24 hour or AM/PM mode.
     * 
     * @param is24HourView True for 24 hour mode. False for AM/PM mode.
     */
    fun set24HourView(is24HourView: Boolean) {
        if (mIs24HourView == is24HourView) {
            return
        }
        mIs24HourView = is24HourView
        mAmPmSpinner.setVisibility(if (is24HourView) GONE else VISIBLE)
        val hour = this.currentHourOfDay
        updateHourControl()
        this.currentHour = hour
        updateAmPmControl()
    }

    private fun updateDateControl() {
        val cal = Calendar.getInstance()
        cal.setTimeInMillis(mDate.getTimeInMillis())
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1)
        mDateSpinner.setDisplayedValues(null)
        for (i in 0..<DAYS_IN_ALL_WEEK) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            mDateDisplayValues[i] = DateFormat.format("MM.dd EEEE", cal) as String?
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues)
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2)
        mDateSpinner.invalidate()
    }

    private fun updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(GONE)
        } else {
            val index = if (mIsAm) Calendar.AM else Calendar.PM
            mAmPmSpinner.setValue(index)
            mAmPmSpinner.setVisibility(VISIBLE)
        }
    }

    private fun updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW)
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW)
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW)
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW)
        }
    }

    /**
     * Set the callback that indicates the 'Set' button has been pressed.
     * @param callback the callback, if null will do nothing
     */
    fun setOnDateTimeChangedListener(callback: OnDateTimeChangedListener?) {
        mOnDateTimeChangedListener = callback
    }

    private fun onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener!!.onDateTimeChanged(
                this, this.currentYear,
                this.currentMonth, this.currentDay, this.currentHourOfDay, this.currentMinute
            )
        }
    }

    companion object {
        private const val DEFAULT_ENABLE_STATE = true

        private const val HOURS_IN_HALF_DAY = 12
        private const val HOURS_IN_ALL_DAY = 24
        private const val DAYS_IN_ALL_WEEK = 7
        private const val DATE_SPINNER_MIN_VAL = 0
        private val DATE_SPINNER_MAX_VAL: Int = DAYS_IN_ALL_WEEK - 1
        private const val HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0
        private const val HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23
        private const val HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1
        private const val HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12
        private const val MINUT_SPINNER_MIN_VAL = 0
        private const val MINUT_SPINNER_MAX_VAL = 59
        private const val AMPM_SPINNER_MIN_VAL = 0
        private const val AMPM_SPINNER_MAX_VAL = 1
    }
}
