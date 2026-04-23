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

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.util.Log

object Contact {
    private var sContactCache: HashMap<String?, String?>? = null
    private const val TAG = "Contact"

    private val CALLER_ID_SELECTION = ("PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + ContactsContract.Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + ContactsContract.Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')")

    fun getContact(context: Context, phoneNumber: String?): String? {
        if (sContactCache == null) {
            sContactCache = HashMap<String?, String?>()
        }

        if (sContactCache!!.containsKey(phoneNumber)) {
            return sContactCache!!.get(phoneNumber)
        }

        val selection = CALLER_ID_SELECTION.replace(
            "+",
            PhoneNumberUtils.toCallerIDMinMatch(phoneNumber)
        )
        val cursor = context.getContentResolver().query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf<String>(Phone.DISPLAY_NAME),
            selection,
            arrayOf<String?>(phoneNumber),
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            try {
                val name = cursor.getString(0)
                sContactCache!!.put(phoneNumber, name)
                return name
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, " Cursor get string error " + e.toString())
                return null
            } finally {
                cursor.close()
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber)
            return null
        }
    }
}
