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


//该类属于 data 模块，负责数据层面的辅助功能
package net.micode.notes.data;
//导入必要的Android SDK类
import android.content.Context;//获取内容解析器
import android.database.Cursor; //查询结果游标
import android.provider.ContactsContract.CommonDataKinds.Phone;//联系人电话数据标准结构
import android.provider.ContactsContract.Data;//联系人详细数据统一
import android.telephony.PhoneNumberUtils; //电话号码处理
import android.util.Log;//日志

import java.util.HashMap;//内存缓存

public class Contact {
      //存储电话号码->联系人姓名的映射
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";
 /**
     * 用于查询联系人姓名的SQL选择条件（selection）
     * - PHONE_NUMBERS_EQUAL(Phone.NUMBER, ?) : 数据库自定义函数，用于匹配电话号码相等
     * - Data.MIMETYPE = Phone.CONTENT_ITEM_TYPE : 限定数据类型为电话号码
     * - Data.RAW_CONTACT_ID IN (子查询) : 通过 phone_lookup 表查找最小匹配为 '+' 的 raw_contact_id，
     *   目的是只匹配主要联系人，避免模糊匹配到不相关号码
     * - replace("+", minMatch) 在运行时根据实际号码动态替换 '+'，形成最终匹配规则
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";
 /**
     * 根据电话号码获取联系人姓名
     * @param context   用于获取 ContentResolver
     * @param phoneNumber 待查询的电话号码
     * @return 联系人姓名，未找到则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
         // 懒加载初始化缓存，仅当第一次调用时创建
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }
// 先检查缓存，命中则直接返回，避免数据库查询
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }
// 根据当前号码计算最小匹配值，替换 SQL 中的 '+' 占位符
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        // 通过 ContentResolver 查询联系人数据库
        // URI: Data.CONTENT_URI -> 包含所有联系人详细数据的合并视图
        // 查询列：只需显示名称 Phone.DISPLAY_NAME
        // 选择条件及参数：selection 和 phoneNumber 对应的占位符
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                 // 获取第一列的字符串值，即 DISPLAY_NAME，联系人姓名
                String name = cursor.getString(0);
                // 将结果存入缓存，以备后续相同号码直接使用
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                 // 确保游标被关闭，释放数据库连接资源
                cursor.close();
            }
        } else {
            // 没有匹配到任何联系人，打印调试日志
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
