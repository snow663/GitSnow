package com.gitsnow.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal GitHub REST client. All methods must be called off the UI thread. */
class Github(private val token: String) {
    private fun esc(s: String): String = s.split('/').joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    private fun request(method: String, endpoint: String, payload: JSONObject? = null): String {
        val conn = URL("https://api.github.com$endpoint").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (payload != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("message") }.getOrDefault(text)
                error("GitHub HTTP $code: ${detail.take(250)}")
            }
            return text
        } finally { conn.disconnect() }
    }
    private fun arr(s: String) = JSONArray(s)
    fun repositories(): JSONArray = arr(request("GET", "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member"))
    fun branches(repo: String): JSONArray = arr(request("GET", "/repos/$repo/branches?per_page=100"))
    fun contents(repo: String, path: String, branch: String): JSONArray {
        val s = request("GET", "/repos/$repo/contents/${esc(path)}?ref=${esc(branch)}")
        return if (s.trimStart().startsWith("[")) arr(s) else JSONArray().put(JSONObject(s))
    }
    fun file(repo: String, path: String, branch: String) =
        JSONObject(request("GET", "/repos/$repo/contents/${esc(path)}?ref=${esc(branch)}"))

    fun put(repo: String, path: String, branch: String, bytes: ByteArray, message: String) {
        require(bytes.size <= 15 * 1024 * 1024) { "Files over 15 MB are not supported in this version" }
        val payload = JSONObject()
            .put("message", message)
            .put("branch", branch)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        val previous = try { file(repo, path, branch).getString("sha") }
            catch (e: IllegalStateException) {
                if (!e.message.orEmpty().startsWith("GitHub HTTP 404:")) throw e
                null
            }
        if (previous != null) payload.put("sha", previous)
        request("PUT", "/repos/$repo/contents/${esc(path)}", payload)
    }
    fun delete(repo: String, path: String, branch: String) {
        val sha = file(repo, path, branch).getString("sha")
        request("DELETE", "/repos/$repo/contents/${esc(path)}",
            JSONObject().put("message", "Delete $path").put("sha", sha).put("branch", branch))
    }
    fun createRepository(name: String, privateRepo: Boolean) {
        request("POST", "/user/repos",
            JSONObject().put("name", name).put("private", privateRepo).put("auto_init", true))
    }
}
