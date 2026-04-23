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
import android.text.TextUtils
import android.util.Log
import net.micode.notes.gtask.data.Node
import net.micode.notes.gtask.data.Task
import net.micode.notes.gtask.data.TaskList
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.gtask.exception.NetworkFailureException
import net.micode.notes.tool.GTaskStringUtils
import net.micode.notes.ui.NotesPreferenceActivity
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.params.HttpProtocolParams
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class GTaskClient private constructor() {
    private var mHttpClient: DefaultHttpClient? = null

    private var mGetUrl: String?

    private var mPostUrl: String?

    private var mClientVersion: Long

    private var mLoggedin = false

    private var mLastLoginTime: Long = 0

    private var mActionId = 1

    var syncAccount: Account? = null
        private set

    private var mUpdateArray: JSONArray? = null

    init {
        mGetUrl = GTASK_GET_URL
        mPostUrl = GTASK_POST_URL
        mClientVersion = -1
    }

    fun login(activity: Activity): Boolean {
        // we suppose that the cookie would expire after 5 minutes
        // then we need to re-login
        val interval = (1000 * 60 * 5).toLong()
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false
        }

        // need to re-login after account switch
        if (mLoggedin
            && !TextUtils.equals(
                this.syncAccount!!.name,
                NotesPreferenceActivity.Companion.getSyncAccountName(activity)
            )
        ) {
            mLoggedin = false
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in")
            return true
        }

        mLastLoginTime = System.currentTimeMillis()
        val authToken = loginGoogleAccount(activity, false)
        if (authToken == null) {
            Log.e(TAG, "login google account failed")
            return false
        }

        // login with custom domain if necessary
        if (!(syncAccount!!.name.lowercase(Locale.getDefault())
                .endsWith("gmail.com") || syncAccount!!.name.lowercase(
                Locale.getDefault()
            )
                .endsWith("googlemail.com"))
        ) {
            val url = StringBuilder(GTASK_URL).append("a/")
            val index = syncAccount!!.name.indexOf('@') + 1
            val suffix = syncAccount!!.name.substring(index)
            url.append(suffix + "/")
            mGetUrl = url.toString() + "ig"
            mPostUrl = url.toString() + "r/ig"

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true
            }
        }

        // try to login with google official url
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
        var authToken: String?
        val accountManager = AccountManager.get(activity)
        val accounts = accountManager.getAccountsByType("com.google")

        if (accounts.size == 0) {
            Log.e(TAG, "there is no available google account")
            return null
        }

        val accountName: String? = NotesPreferenceActivity.Companion.getSyncAccountName(activity)
        var account: Account? = null
        for (a in accounts) {
            if (a.name == accountName) {
                account = a
                break
            }
        }
        if (account != null) {
            this.syncAccount = account
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings")
            return null
        }

        // get the token now
        val accountManagerFuture = accountManager.getAuthToken(
            account,
            "goanna_mobile", null, activity, null, null
        )
        try {
            val authTokenBundle = accountManagerFuture.getResult()
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN)
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken)
                loginGoogleAccount(activity, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "get auth token failed")
            authToken = null
        }

        return authToken
    }

    private fun tryToLoginGtask(activity: Activity, authToken: String?): Boolean {
        var authToken = authToken
        if (!loginGtask(authToken)) {
            // maybe the auth token is out of date, now let's invalidate the
            // token and try again
            authToken = loginGoogleAccount(activity, true)
            if (authToken == null) {
                Log.e(TAG, "login google account failed")
                return false
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed")
                return false
            }
        }
        return true
    }

    private fun loginGtask(authToken: String?): Boolean {
        val timeoutConnection = 10000
        val timeoutSocket = 15000
        val httpParameters: HttpParams = BasicHttpParams()
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection)
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket)
        mHttpClient = DefaultHttpClient(httpParameters)
        val localBasicCookieStore = BasicCookieStore()
        mHttpClient!!.setCookieStore(localBasicCookieStore)
        HttpProtocolParams.setUseExpectContinue(mHttpClient!!.getParams(), false)

        // login gtask
        try {
            val loginUrl = mGetUrl + "?auth=" + authToken
            val httpGet = HttpGet(loginUrl)
            var response: HttpResponse? = null
            response = mHttpClient!!.execute(httpGet)

            // get the cookie now
            val cookies = mHttpClient!!.getCookieStore().getCookies()
            var hasAuthCookie = false
            for (cookie in cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie")
            }

            // get the client version
            val resString = getResponseContent(response.getEntity())
            val jsBegin = "_setup("
            val jsEnd = ")}</script>"
            val begin = resString.indexOf(jsBegin)
            val end = resString.lastIndexOf(jsEnd)
            var jsString: String? = null
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length, end)
            }
            val js = JSONObject(jsString)
            mClientVersion = js.getLong("v")
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return false
        } catch (e: Exception) {
            // simply catch all exceptions
            Log.e(TAG, "httpget gtask_url failed")
            return false
        }

        return true
    }

    private val actionId: Int
        get() = mActionId++

    private fun createHttpPost(): HttpPost {
        val httpPost = HttpPost(mPostUrl)
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
        httpPost.setHeader("AT", "1")
        return httpPost
    }

    @Throws(IOException::class)
    private fun getResponseContent(entity: HttpEntity): String {
        var contentEncoding: String? = null
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue()
            Log.d(TAG, "encoding: " + contentEncoding)
        }

        var input = entity.getContent()
        if (contentEncoding != null && contentEncoding.equals("gzip", ignoreCase = true)) {
            input = GZIPInputStream(entity.getContent())
        } else if (contentEncoding != null && contentEncoding.equals(
                "deflate",
                ignoreCase = true
            )
        ) {
            val inflater = Inflater(true)
            input = InflaterInputStream(entity.getContent(), inflater)
        }

        try {
            val isr = InputStreamReader(input)
            val br = BufferedReader(isr)
            var sb = StringBuilder()

            while (true) {
                val buff = br.readLine()
                if (buff == null) {
                    return sb.toString()
                }
                sb = sb.append(buff)
            }
        } finally {
            input.close()
        }
    }

    @Throws(NetworkFailureException::class)
    private fun postRequest(js: JSONObject): JSONObject {
        if (!mLoggedin) {
            Log.e(TAG, "please login first")
            throw ActionFailureException("not logged in")
        }

        val httpPost = createHttpPost()
        try {
            val list = LinkedList<BasicNameValuePair?>()
            list.add(BasicNameValuePair("r", js.toString()))
            val entity = UrlEncodedFormEntity(list, "UTF-8")
            httpPost.setEntity(entity)

            // execute the post
            val response = mHttpClient!!.execute(httpPost)
            val jsString = getResponseContent(response.getEntity())
            return JSONObject(jsString)
        } catch (e: ClientProtocolException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw NetworkFailureException("postRequest failed")
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw NetworkFailureException("postRequest failed")
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("unable to convert response content to jsonobject")
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("error occurs when posting request")
        }
    }

    @Throws(NetworkFailureException::class)
    fun createTask(task: Task) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            // action_list
            actionList.put(task.getCreateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            // post
            val jsResponse = postRequest(jsPost)
            val jsResult = jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS
            ).get(0) as JSONObject
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID))
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("create task: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun createTaskList(tasklist: TaskList) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            // action_list
            actionList.put(tasklist.getCreateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)

            // client version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            // post
            val jsResponse = postRequest(jsPost)
            val jsResult = jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS
            ).get(0) as JSONObject
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID))
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("create tasklist: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun commitUpdate() {
        if (mUpdateArray != null) {
            try {
                val jsPost = JSONObject()

                // action_list
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray)

                // client_version
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

                postRequest(jsPost)
                mUpdateArray = null
            } catch (e: JSONException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
                throw ActionFailureException("commit update: handing jsonobject failed")
            }
        }
    }

    @Throws(NetworkFailureException::class)
    fun addUpdateNode(node: Node?) {
        if (node != null) {
            // too many update items may result in an error
            // set max to 10 items
            if (mUpdateArray != null && mUpdateArray!!.length() > 10) {
                commitUpdate()
            }

            if (mUpdateArray == null) mUpdateArray = JSONArray()
            mUpdateArray!!.put(node.getUpdateAction(this.actionId))
        }
    }

    @Throws(NetworkFailureException::class)
    fun moveTask(task: Task, preParent: TaskList, curParent: TaskList) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()
            val action = JSONObject()

            // action_list
            action.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE
            )
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, this.actionId)
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid())
            if (preParent === curParent && task.getPriorSibling() != null) {
                // put prioring_sibing_id only if moving within the tasklist and
                // it is not the first one
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling())
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid())
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid())
            if (preParent !== curParent) {
                // put the dest_list only if moving between tasklists
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid())
            }
            actionList.put(action)
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            postRequest(jsPost)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("move task: handing jsonobject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    fun deleteNode(node: Node) {
        commitUpdate()
        try {
            val jsPost = JSONObject()
            val actionList = JSONArray()

            // action_list
            node.setDeleted(true)
            actionList.put(node.getUpdateAction(this.actionId))
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            postRequest(jsPost)
            mUpdateArray = null
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
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
                val httpGet = HttpGet(mGetUrl)
                var response: HttpResponse? = null
                response = mHttpClient!!.execute(httpGet)

                // get the task list
                val resString = getResponseContent(response.getEntity())
                val jsBegin = "_setup("
                val jsEnd = ")}</script>"
                val begin = resString.indexOf(jsBegin)
                val end = resString.lastIndexOf(jsEnd)
                var jsString: String? = null
                if (begin != -1 && end != -1 && begin < end) {
                    jsString = resString.substring(begin + jsBegin.length, end)
                }
                val js = JSONObject(jsString)
                return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS)
            } catch (e: ClientProtocolException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
                throw NetworkFailureException("gettasklists: httpget failed")
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
                throw NetworkFailureException("gettasklists: httpget failed")
            } catch (e: JSONException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
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

            // action_list
            action.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL
            )
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, this.actionId)
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid)
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false)
            actionList.put(action)
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList)

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion)

            val jsResponse = postRequest(jsPost)
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("get task list: handing jsonobject failed")
        }
    }

    fun resetUpdateArray() {
        mUpdateArray = null
    }

    companion object {
        private val TAG: String = GTaskClient::class.java.getSimpleName()

        private const val GTASK_URL = "https://mail.google.com/tasks/"

        private const val GTASK_GET_URL = "https://mail.google.com/tasks/ig"

        private const val GTASK_POST_URL = "https://mail.google.com/tasks/r/ig"

        private var mInstance: GTaskClient? = null

        @get:Synchronized
        val instance: GTaskClient
            get() {
                if (mInstance == null) {
                    mInstance = GTaskClient()
                }
                return mInstance!!
            }
    }
}
