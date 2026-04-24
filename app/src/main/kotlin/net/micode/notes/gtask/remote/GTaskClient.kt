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
package net.micode.notes.gtask.remote

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.util.Log
import net.micode.notes.gtask.data.Node
import net.micode.notes.gtask.data.Task
import net.micode.notes.gtask.data.TaskList
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.gtask.exception.NetworkFailureException
import net.micode.notes.tool.GTaskStringUtils
import net.micode.notes.ui.NotesPreferenceActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class GTaskClient private constructor() {
    private var mCookieManager: CookieManager? = null
    private var mGetUrl: String = GTASK_GET_URL
    private var mPostUrl: String = GTASK_POST_URL
    private var mClientVersion: Long = -1
    private var mLoggedin = false
    private var mLastLoginTime: Long = 0
    private var mActionId = 1

    var syncAccount: Account? = null
        private set

    private var mUpdateArray: JSONArray? = null

    fun login(activity: Activity): Boolean {
        val syncAccountName = syncAccount?.name
        val interval = 1000L * 60 * 5
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false
        }

        if (mLoggedin && syncAccountName != NotesPreferenceActivity.getSyncAccountName(activity)) {
            mLoggedin = false
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in")
            return true
        }

        mLastLoginTime = System.currentTimeMillis()
        val authToken = loginGoogleAccount(activity, false) ?: run {
            Log.e(TAG, "login google account failed")
            return false
        }

        val accountName = syncAccount?.name ?: return false
        if (
            !accountName.lowercase(Locale.getDefault()).endsWith("gmail.com") &&
            !accountName.lowercase(Locale.getDefault()).endsWith("googlemail.com")
        ) {
            val urlPrefix = "${GTASK_URL}a/${accountName.substringAfter('@')}/"
            mGetUrl = "${urlPrefix}ig"
            mPostUrl = "${urlPrefix}r/ig"

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true
            }
        }

        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL
            mPostUrl = GTASK_POST_URL
            if (!tryToLoginGtask(activity, authToken)) {
                return false
            }
        }

        mLoggedin = true
        return true
    }

    private fun loginGoogleAccount(activity: Activity, invalidateToken: Boolean): String? {
        val accountManager = AccountManager.get(activity)
        val accounts = accountManager.getAccountsByType("com.google")
        if (accounts.isEmpty()) {
            Log.e(TAG, "there is no available google account")
            return null
        }

        val accountName = NotesPreferenceActivity.getSyncAccountName(activity)
        val account = accounts.firstOrNull { it.name == accountName } ?: run {
            Log.e(TAG, "unable to get an account with the same name in the settings")
            return null
        }
        syncAccount = account

        return try {
            val authToken = accountManager.getAuthToken(
                account,
                "goanna_mobile",
                null,
                activity,
                null,
                null
            ).result.getString(AccountManager.KEY_AUTHTOKEN)

            if (invalidateToken) {
                authToken?.let { accountManager.invalidateAuthToken("com.google", it) }
                loginGoogleAccount(activity, false)
            } else {
                authToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "get auth token failed", e)
            null
        }
    }

    private fun tryToLoginGtask(activity: Activity, authToken: String?): Boolean {
        var currentAuthToken = authToken
        if (!loginGtask(currentAuthToken)) {
            currentAuthToken = loginGoogleAccount(activity, true)
            if (currentAuthToken == null) {
                Log.e(TAG, "login google account failed")
                return false
            }

            if (!loginGtask(currentAuthToken)) {
                Log.e(TAG, "login gtask failed")
                return false
            }
        }
        return true
    }

    private fun loginGtask(authToken: String?): Boolean {
        mCookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

        return try {
            val response = executeRequest("GET", "$mGetUrl?auth=$authToken")
            val hasAuthCookie = requireCookieManager().cookieStore.cookies.any { cookie ->
                cookie.name.contains("GTL")
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie")
            }

            val parsedString = extractSetupJson(response) ?: return false
            mClientVersion = JSONObject(parsedString).getLong("v")
            true
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "login gtask failed", e)
            false
        }
    }

    private val actionId: Int
        get() = mActionId++

    @Throws(IOException::class)
    private fun executeRequest(
        method: String,
        urlString: String,
        requestBody: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val connection = openConnection(method, urlString, requestBody != null)
        try {
            attachCookies(connection, urlString)
            extraHeaders.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            if (requestBody != null) {
                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(Charsets.UTF_8))
                }
            }

            val responseCode = connection.responseCode
            storeCookies(connection, urlString)
            val responseText = readResponseContent(connection)
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode: $responseText")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        method: String,
        urlString: String,
        hasRequestBody: Boolean
    ): HttpURLConnection {
        return (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
            doInput = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "gzip, deflate")
            if (hasRequestBody) {
                doOutput = true
            }
        }
    }

    private fun attachCookies(connection: HttpURLConnection, urlString: String) {
        val cookieHeaders = mCookieManager?.get(URI(urlString), emptyMap()) ?: return
        cookieHeaders.forEach { (headerName, headerValues) ->
            headerValues.forEach { value ->
                connection.addRequestProperty(headerName, value)
            }
        }
    }

    private fun storeCookies(connection: HttpURLConnection, urlString: String) {
        mCookieManager?.put(
            URI(urlString),
            connection.headerFields.filterKeys { it != null }
        )
    }

    @Throws(IOException::class)
    private fun readResponseContent(connection: HttpURLConnection): String {
        val source = try {
            connection.inputStream
        } catch (_: IOException) {
            connection.errorStream
        } ?: return ""

        val contentStream = wrapResponseStream(source, connection.contentEncoding)
        contentStream.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                return reader.readText()
            }
        }
    }

    private fun wrapResponseStream(input: InputStream, contentEncoding: String?): InputStream {
        return when {
            contentEncoding.equals("gzip", ignoreCase = true) -> GZIPInputStream(input)
            contentEncoding.equals("deflate", ignoreCase = true) -> {
                InflaterInputStream(input, Inflater(true))
            }

            else -> input
        }
    }

    private fun extractSetupJson(response: String): String? {
        val jsBegin = "_setup("
        val jsEnd = ")}</script>"
        val begin = response.indexOf(jsBegin)
        val end = response.lastIndexOf(jsEnd)
        return if (begin != -1 && end != -1 && begin < end) {
            response.substring(begin + jsBegin.length, end)
        } else {
            null
        }
    }

    @Throws(NetworkFailureException::class)
    private fun postRequest(js: JSONObject): JSONObject {
        if (!mLoggedin) {
            Log.e(TAG, "please login first")
            throw ActionFailureException("not logged in")
        }

        return try {
            val response = executeRequest(
                "POST",
                mPostUrl,
                requestBody = "r=" + URLEncoder.encode(js.toString(), Charsets.UTF_8.name()),
                extraHeaders = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded;charset=utf-8",
                    "AT" to "1"
                )
            )
            JSONObject(response)
        } catch (e: IOException) {
            Log.e(TAG, e.toString(), e)
            throw NetworkFailureException("postRequest failed")
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("unable to convert response content to jsonobject")
        } catch (e: Exception) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("error occurs when posting request")
        }
    }

    @Throws(NetworkFailureException::class)
    fun createTask(task: Task) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            actionList.put(task.getCreateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            val jsResponse = postRequest(jsPost)
            val jsResult = jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS
            ).get(0) as JSONObject
            task.gid = jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("create task: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun createTaskList(tasklist: TaskList) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            actionList.put(tasklist.getCreateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            val jsResponse = postRequest(jsPost)
            val jsResult = jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS
            ).get(0) as JSONObject
            tasklist.gid = jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("create tasklist: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun commitUpdate() {
        if (mUpdateArray != null) {
            try {
                val jsPost = JSONObject()
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray)
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

                postRequest(jsPost)
                mUpdateArray = null
            } catch (e: JSONException) {
                Log.e(TAG, e.toString(), e)
                throw ActionFailureException("commit update: handing jsonobject failed")
            }
        }
    }

    @Throws(NetworkFailureException::class)
    fun addUpdateNode(node: Node?) {
        if (node != null) {
            if (mUpdateArray?.length() ?: 0 > 10) {
                commitUpdate()
            }

            val updateArray = mUpdateArray ?: JSONArray().also { mUpdateArray = it }
            updateArray.put(node.getUpdateAction(this.actionId))
        }
    }

    @Throws(NetworkFailureException::class)
    fun moveTask(task: Task, preParent: TaskList, curParent: TaskList) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()
            val action = JSONObject()

            action.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE
            )
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, this.actionId)
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.gid)
            if (preParent === curParent && task.priorSibling != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.priorSibling?.gid)
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.gid)
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.gid)
            if (preParent !== curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.gid)
            }
            actionList.put(action)
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            postRequest(jsPost)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("move task: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun deleteNode(node: Node) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            node.deleted = true
            actionList.put(node.getUpdateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            postRequest(jsPost)
            mUpdateArray = null
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("delete node: handing jsonobject failed")
        }
    }

    @get:Throws(NetworkFailureException::class)
    val taskLists: JSONArray
        get() {
            if (!mLoggedin) {
                Log.e(TAG, "please login first")
                throw ActionFailureException("not logged in")
            }

            try {
                val response = executeRequest("GET", mGetUrl)
                val parsedString = extractSetupJson(response)
                    ?: throw ActionFailureException("get task lists: invalid response")
                val js = JSONObject(parsedString)
                return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS)
            } catch (e: IOException) {
                Log.e(TAG, e.toString(), e)
                throw NetworkFailureException("gettasklists: httpget failed")
            } catch (e: JSONException) {
                Log.e(TAG, e.toString(), e)
                throw ActionFailureException("get task lists: handing jasonobject failed")
            }
        }

    @Throws(NetworkFailureException::class)
    fun getTaskList(listGid: String?): JSONArray {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()
            val action = JSONObject()

            action.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL
            )
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, this.actionId)
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid)
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false)
            actionList.put(action)
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            val jsResponse = postRequest(jsPost)
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString(), e)
            throw ActionFailureException("get task list: handing jsonobject failed")
        }
    }

    fun resetUpdateArray() {
        mUpdateArray = null
    }

    private fun requireCookieManager(): CookieManager {
        return mCookieManager ?: throw ActionFailureException("cookie manager is not initialized")
    }

    companion object {
        private val TAG: String = GTaskClient::class.java.simpleName
        private const val GTASK_URL = "https://mail.google.com/tasks/"
        private const val GTASK_GET_URL = "https://mail.google.com/tasks/ig"
        private const val GTASK_POST_URL = "https://mail.google.com/tasks/r/ig"

        val instance: GTaskClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GTaskClient() }
    }
}
