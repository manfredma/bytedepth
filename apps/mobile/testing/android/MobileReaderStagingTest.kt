package cn.bytedepth.reader.preview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the installed, signed release bytes. No app hooks or production test endpoints. */
@RunWith(AndroidJUnit4::class)
class MobileReaderStagingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val target = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val origin = "https://staging-bytedepth.bytedepth.cn"
    private lateinit var fixture: JSONObject
    private lateinit var scenario: ActivityScenario<Activity>
    private val appId = "cn.bytedepth.reader.preview"

    @Test
    fun managedStagingCase() {
        val mobileCase = args.getString("mobileCase") ?: error("mobileCase missing")
        assertEquals("1", args.getString("apiSchemaVersion"))
        val backendSha = args.getString("backendCommitSha") ?: error("backend SHA missing")
        val backendDigest = args.getString("backendArtifactSha256") ?: error("backend digest missing")
        assertTrue("backend SHA", Regex("[a-f0-9]{40}").matches(backendSha))
        assertTrue("backend digest", Regex("[a-f0-9]{64}").matches(backendDigest))
        assertEquals("/data/local/tmp/bytedepth-mobile-fixtures.json", args.getString("mobileFixturePath"))
        // Shell identity reads the shell-owned 0600 input; credentials never enter a launch argument.
        val raw = device.executeShellCommand("cat /data/local/tmp/bytedepth-mobile-fixtures.json")
        assertTrue("bounded managed fixture", raw.toByteArray().size in 1..65536)
        fixture = JSONObject(raw)
        assertEquals(1, fixture.getInt("schemaVersion"))
        assertTrue("owned fixture preparation incomplete", fixture.getBoolean("complete"))
        assertEquals(origin, fixture.getString("origin"))
        assertEquals(backendSha, fixture.getString("backendCommitSha"))
        assertEquals(backendDigest, fixture.getString("backendArtifactSha256"))
        assertEquals(appId, target.packageName)
        launch()
        try {
            when (mobileCase) {
                "guest-reading" -> guestReading()
                "public-content" -> publicContent()
                "offline-recovery" -> offlineRecovery()
                "resume-seed" -> resumeSeed()
                "resume-rotation" -> resumeRotation()
                "session-isolation" -> sessionIsolation()
                "anonymous-rating" -> anonymousRating()
                "bridge-navigation" -> bridgeNavigation()
                "no-history-upload" -> noHistoryUpload()
                "upgrade-seed" -> upgradeSeed()
                "upgrade-preservation" -> upgradePreservation()
                else -> error("Unknown mobile case")
            }
            // One completion marker, reached only after all case assertions. B2 also requires JUnit OK/code -1.
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nMOBILE_CASE $mobileCase PASS\n") })
        } finally {
            scenario.close()
            device.unfreezeRotation()
        }
    }

    private fun launch(expectHome: Boolean = true) {
        val intent = target.packageManager.getLaunchIntentForPackage(appId) ?: error("release launcher missing")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        scenario = ActivityScenario.launch(intent)
        if (expectHome) node("搜索文章")
    }

    private fun relaunch() { scenario.close(); launch() }
    private fun article(key: String = "primary") = fixture.getJSONObject("articles").getJSONObject(key)
    private fun node(text: String): UiObject2 {
        return device.wait(Until.findObject(By.desc(text)), 15000)
            ?: device.wait(Until.findObject(By.text(text)), 15000)
            ?: error("Required UI control unavailable")
    }
    private fun click(text: String) { node(text).click(); device.waitForIdle() }
    private fun visible(text: String) = device.hasObject(By.textContains(text)) || device.hasObject(By.descContains(text))
    private fun await(message: String, timeout: Long = 20000, check: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) {
            if (check()) return
            SystemClock.sleep(150)
        }
        fail(message)
    }

    private fun open(key: String = "primary") {
        click("搜索文章")
        val search = node("搜索关键词"); search.click(); search.text = article(key).getString("title")
        device.pressBack() // dismiss the IME, retain search route
        click("搜索")
        node(article(key).getString("title")).click()
        await("actual reader mounted") { runCatching { js("!!window.MobileReader && !!document.querySelector('#post-article .content [data-annotation-segment]')") == true }.getOrDefault(false) }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val found = findWebView(view.getChildAt(i))
            if (found != null) return found
        }
        return null
    }
    private fun js(script: String): Any? {
        val latch = CountDownLatch(1)
        var value: Any? = null
        var failure: Throwable? = null
        scenario.onActivity { activity ->
            val web = findWebView(activity.window.decorView)
            if (web == null) { failure = AssertionError("Actual WebView absent"); latch.countDown() }
            else web.evaluateJavascript("(function(){return ($script);})()") { result ->
                try { value = JSONTokener(result).nextValue() } catch (error: Throwable) { failure = error }
                latch.countDown()
            }
        }
        assertTrue("WebView evaluation timed out", latch.await(8, TimeUnit.SECONDS))
        failure?.let { throw it }
        return value
    }
    
    private fun db(): SQLiteDatabase {
        val file = File(target.filesDir, "mobile-local/reader.sqlite")
        assertTrue("real SQLite file absent", file.isFile)
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }
    private fun history(key: String = "primary"): JSONObject? = db().use { database ->
        database.rawQuery("SELECT value FROM local_history WHERE post_id = ?", arrayOf(article(key).getString("id"))).use { cursor ->
            if (cursor.moveToFirst()) JSONObject(cursor.getString(0)) else null
        }
    }
    private fun copy(key: String = "primary"): JSONObject? = db().use { database ->
        database.rawQuery("SELECT value FROM cache_index WHERE id = 1", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            JSONObject(cursor.getString(0)).getJSONObject("copies").optJSONObject(article(key).getString("id"))
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun verifiedCopy(key: String = "primary"): JSONObject {
        await("all required resources must be atomically saved") { copy(key) != null }
        val saved = copy(key)!!
        val files = mutableListOf(saved.getString("body"))
        val assets = saved.getJSONObject("assets")
        for (name in assets.keys()) files.add(assets.getJSONObject(name).getString("file"))
        for (name in files) {
            assertTrue(Regex("[ab]-[a-f0-9]{64}").matches(name))
            val file = File(target.cacheDir, "mobile-reader/$name")
            assertTrue("cache byte missing", file.isFile)
            assertEquals("cache digest mismatch", name.substring(2), hash(file.readBytes()))
        }
        val content = JSONObject(File(target.cacheDir, "mobile-reader/${saved.getString("body")}").readText())
        assertEquals(content.getJSONArray("resources").length(), assets.length())
        assertEquals(article(key).getString("id"), content.getString("postId"))
        return saved
    }
    private fun guestReading() {
        click("我")
        assertTrue(visible("游客阅读"))
        click("发现")
        open()
        assertTrue(js("document.querySelector('.content').textContent.includes(${JSONObject.quote(fixture.getString("exactQuote"))})") == true)
        verifiedCopy()
        assertNotNull(history())
        device.pressBack(); device.pressBack(); click("最近")
        assertTrue(visible(article().getString("title")))
        assertTrue(visible("离线可读"))
    }
    private fun publicContent() {
        open()
        verifiedCopy()
        await("native WebView must render code, table, Mermaid SVG and both verified images") {
            js("!!document.querySelector('pre code') && !!document.querySelector('table') && !!document.querySelector('.mermaid svg, .language-mermaid svg, .bd-mermaid svg') && document.querySelectorAll('img[data-resource-key]').length === 2 && Array.from(document.querySelectorAll('img[data-resource-key]')).every(i => i.complete && i.naturalWidth > 0 && i.src.startsWith('data:image/'))") == true
        }
        assertTrue(js("Array.from(document.querySelectorAll('img')).some(i => i.src.startsWith('data:image/svg+xml;base64,'))") == true)
        click("目录")
        UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Chapter 12"))
        click("Chapter 12")
        await("table of contents real jump") { (js("window.scrollY") as Number).toDouble() > 100 }
    }
    private fun online(enabled: Boolean) {
        device.executeShellCommand("svc wifi ${if (enabled) "enable" else "disable"}")
        device.executeShellCommand("svc data ${if (enabled) "enable" else "disable"}")
        val connectivity = target.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        await("emulator network toggle failed", 30000) {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            connected == enabled
        }
    }
    private fun offlineRecovery() {
        open(); val previous = verifiedCopy().getString("body")
        try {
            online(false)
            relaunch(); click("最近"); click(article().getString("title"))
            await("offline body and images readable") { js("document.querySelectorAll('img[data-resource-key]').length === 2 && Array.from(document.querySelectorAll('img[data-resource-key]')).every(i => i.complete && i.naturalWidth > 0)") == true }
            assertEquals(previous, verifiedCopy().getString("body"))
            await("offline visibility check must remain unconfirmed") { visible("暂未确认更新") }
            online(true); relaunch(); open("broken")
            await("failed required image must show placeholder") { js("!!document.querySelector('.reader-image-missing')") == true }
            await("failed resource cannot publish complete copy", 8000) { copy("broken") == null }
            assertTrue(visible("暂未确认更新") || visible("正在自动保存") || visible("未完整"))
            online(false); relaunch(); click("最近"); click(article("broken").getString("title"))
            node("此文尚未保存，需要联网后打开。")
            online(true); relaunch(); open("uncached"); verifiedCopy("uncached")
            assertEquals(previous, copy()!!.getString("body"))
        } finally { online(true) }
    }
    private fun scrollPosition(): JSONObject {
        js("(window.scrollTo(0,document.documentElement.scrollHeight*0.55), true)")
        await("nonzero precise position persisted") { (history()?.optJSONObject("position")?.optDouble("progress") ?: 0.0) > 0.25 }
        return history()!!.getJSONObject("position")
    }
    private fun assertPosition(expected: JSONObject) {
        await("canonical segment and offset must survive reflow") {
            val position = history()?.optJSONObject("position")
            position != null && position.getString("segmentId") == expected.getString("segmentId") && kotlin.math.abs(position.getInt("codePointOffset") - expected.getInt("codePointOffset")) <= 2
        }
        assertTrue("real viewport resumed", (js("window.scrollY") as Number).toDouble() > 100)
        val saved = verifiedCopy()
        val content = JSONObject(File(target.cacheDir, "mobile-reader/${saved.getString("body")}").readText())
        val model = content.getJSONObject("document").put("revision", content.getString("contentVersion"))
        val segment = expected.getString("segmentId")
        val offset = expected.getInt("codePointOffset")
        val ratio = expected.getDouble("viewportRatio")
        await("actual canonical character must return to its saved viewport fraction") {
            js("(function(){const m=window.AnnotationDocument.create(document.querySelector('.content'),$model);try{const r=m.ranges([{segmentId:${JSONObject.quote(segment)},start:$offset,end:${offset + 1}}]);return Array.isArray(r)&&r.length>0&&Math.abs(r[0].getBoundingClientRect().top/window.innerHeight-$ratio)<0.18;}finally{m.dispose();}})()") == true
        }
    }
    private fun resumeSeed() {
        open(); verifiedCopy(); val position = scrollPosition()
        File(target.filesDir, "resume-sentinel.json").writeText(JSONObject().put("runId", fixture.getString("runId")).put("position", position).toString())
    }
    private fun resumeRotation() {
        val sentinel = File(target.filesDir, "resume-sentinel.json")
        assertTrue("Host must seed then force-stop the process", sentinel.isFile)
        val seed = JSONObject(sentinel.readText())
        assertEquals(fixture.getString("runId"), seed.getString("runId"))
        val position = seed.getJSONObject("position")
        click("最近"); click(article().getString("title")); assertPosition(position)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await("landscape recreation") { target.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        assertPosition(position)
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        relaunch(); click("最近"); click(article().getString("title")); assertPosition(position)
    }
    private fun login(account: JSONObject) {
        click("我")
        val username = node("用户名"); username.click(); username.text = account.getString("username")
        val password = node("密码"); password.click(); password.text = account.getString("password")
        device.pressBack(); click("登录账号")
        node("退出登录")
        assertTrue(visible(account.getString("username")))
        click("发现")
    }
    private fun logout() { relaunch(); click("我"); click("退出登录"); node("登录账号"); click("发现") }
    private fun annotationVisible(own: String, absent: String) {
        open(); click("评注")
        await("own private fixture must be visible") { visible(own) }
        assertFalse("another identity's private annotation leaked", visible(absent))
        click("关闭")
    }
    private fun assertNoPrivateOnDisk() {
        val markers = listOf(fixture.getString("privateNote"), fixture.getString("secondPrivateNote"))
        for (root in listOf(target.filesDir, target.cacheDir)) for (file in root.walkTopDown().filter { it.isFile }) {
            if (file.length() < 16 * 1024 * 1024) {
                val text = file.readBytes().toString(Charsets.UTF_8)
                for (marker in markers) assertFalse("private annotation persisted in application files", text.contains(marker))
            }
        }
    }
    private fun sessionIsolation() {
        login(fixture.getJSONObject("account"))
        annotationVisible(fixture.getString("privateNote"), fixture.getString("secondPrivateNote"))
        val copy = verifiedCopy().getString("body")
        logout(); open(); click("评注")
        node("登录后长按原文，选择文字可新建评注")
        assertFalse(visible(fixture.getString("privateNote"))); assertFalse(visible(fixture.getString("secondPrivateNote")))
        click("关闭"); relaunch()
        login(fixture.getJSONObject("secondAccount"))
        annotationVisible(fixture.getString("secondPrivateNote"), fixture.getString("privateNote"))
        logout(); relaunch(); login(fixture.getJSONObject("account"))
        annotationVisible(fixture.getString("privateNote"), fixture.getString("secondPrivateNote"))
        assertEquals("device public copy survives accounts", copy, verifiedCopy().getString("body"))
        assertNoPrivateOnDisk(); logout()
    }
    private fun rating(): JSONObject {
        val identity = File(target.filesDir, ".installation/marker").readText()
        assertTrue(Regex("app:[a-f0-9-]{36}").matches(identity))
        return JSONObject(get("/api/mobile/v1/posts/${article().getString("id")}/ratings", mapOf("X-App-Visitor" to identity)))
    }
    private fun anonymousRating() {
        open(); click("评分")
        val before = rating(); click("4 分")
        await("anonymous rating persisted") { rating().optInt("visitorScore") == 4 }
        val after = rating(); click("2 分")
        await("same installation updates its rating") { rating().optInt("visitorScore") == 2 }
        assertEquals(after.getLong("ratingCount"), rating().getLong("ratingCount"))
        val increment = if (before.isNull("visitorScore")) 1 else 0
        assertEquals(before.getLong("ratingCount") + increment, after.getLong("ratingCount"))
        click("关闭")
    }
    private fun bridgeNavigation() {
        open(); val before = history()!!.getJSONObject("position")
        assertTrue(js("(window.ReactNativeWebView.postMessage(JSON.stringify({type:'position',postId:'wrong',contentVersion:'wrong',generation:-1,payload:{progress:1}})),true)") == true)
        SystemClock.sleep(400)
        assertEquals(before.getString("segmentId"), history()!!.getJSONObject("position").getString("segmentId"))
        assertEquals(before.getInt("codePointOffset"), history()!!.getJSONObject("position").getInt("codePointOffset"))
        assertEquals("about:blank", js("location.href"))
        assertTrue(js("!document.querySelector('a[href^=\"javascript:\"]') && !document.querySelector('script[src^=\"https:\"]')") == true)
        js("(document.querySelector('a[href^=\"/posts/\"]').click(),true)")
        await("internal link must enter a native reader route") { runCatching { js("document.querySelector('.content').textContent.includes(${JSONObject.quote("Owned secondary ${fixture.getString("runId")}")})") == true }.getOrDefault(false) }
        assertNotNull(history("secondary"))
        // Untrusted WebView navigation must remain in the about:blank shell.
        js("(window.location.href='https://example.com/mobile-denied',true)")
        SystemClock.sleep(500)
        assertEquals("about:blank", js("location.href"))
        assertEquals(appId, device.currentPackageName)
    }
    private fun get(path: String, headers: Map<String, String> = emptyMap()): String {
        require(path.startsWith("/") && !path.startsWith("//"))
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            assertEquals("staging probe request failed", 200, connection.responseCode)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun serverHistory(): List<String> {
        val cookie = fixture.getJSONObject("webCookie")
        val html = get("/reading-history", mapOf("Cookie" to "${cookie.getString("name")}=${cookie.getString("value")}"))
        assertTrue("authenticated server baseline required", Regex("data-authenticated=\"true\"").containsMatchIn(html))
        // Compare real server readCount/totalActiveSeconds per row, independently of API success.
        return Regex("<a class=\"reading-history-item\"[\\s\\S]*?</a>").findAll(html).map { row ->
            val href = Regex("href=\"([^\"]+)\"").find(row.value)!!.groupValues[1]
            val counters = Regex("class=\"reading-history-meta\"[^>]*>([^<]+)").find(row.value)!!.groupValues[1]
            "$href|$counters"
        }.toList()
    }
    private fun noHistoryUpload() {
        login(fixture.getJSONObject("account"))
        val baseline = serverHistory()
        open(); verifiedCopy(); scrollPosition()
        SystemClock.sleep(16000) // exceed the website tracker flush cadence
        assertEquals("mobile reading changed server history counters", baseline, serverHistory())
        assertNotNull(history())
        assertTrue(js("!window.BytedepthReadingTracker && !document.querySelector('script[src*=\"tracker\"]')") == true)
        logout()
    }
    private fun version() = target.packageManager.getPackageInfo(appId, 0).getLongVersionCode()
    private fun sentinel() = File(target.filesDir, "upgrade-sentinel.json")
    private fun upgradeSeed() {
        assertEquals(1L, version())
        open(); val saved = verifiedCopy(); val position = scrollPosition()
        val files = JSONObject()
        files.put(saved.getString("body"), hash(File(target.cacheDir, "mobile-reader/${saved.getString("body")}").readBytes()))
        val assets = saved.getJSONObject("assets")
        for (key in assets.keys()) {
            val name = assets.getJSONObject(key).getString("file")
            files.put(name, hash(File(target.cacheDir, "mobile-reader/$name").readBytes()))
        }
        sentinel().writeText(JSONObject().put("runId", fixture.getString("runId")).put("postId", article().getString("id")).put("position", position).put("body", saved.getString("body")).put("files", files).toString())
        assertTrue(sentinel().isFile)
    }
    private fun upgradePreservation() {
        assertEquals(2L, version())
        assertTrue("upgrade seed missing; never clear/uninstall between phases", sentinel().isFile)
        val seed = JSONObject(sentinel().readText())
        assertEquals(fixture.getString("runId"), seed.getString("runId"))
        assertEquals(article().getString("id"), seed.getString("postId"))
        assertEquals(seed.getString("body"), copy()!!.getString("body"))
        val files = seed.getJSONObject("files")
        for (name in files.keys()) assertEquals(files.getString(name), hash(File(target.cacheDir, "mobile-reader/$name").readBytes()))
        try {
            online(false); click("最近"); click(article().getString("title"))
            assertPosition(seed.getJSONObject("position")); verifiedCopy()
            await("upgraded copy renders offline images") { js("Array.from(document.querySelectorAll('img[data-resource-key]')).length === 2 && Array.from(document.querySelectorAll('img[data-resource-key]')).every(i => i.complete && i.naturalWidth > 0)") == true }
        } finally { online(true) }
        rejectFutureSchemaWithoutErasing()
    }
    private fun rejectFutureSchemaWithoutErasing() {
        val before = history()!!.toString()
        val body = copy()!!.getString("body")
        scenario.close()
        val file = File(target.filesDir, "mobile-local/reader.sqlite")
        fun setVersion(version: Int) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL("PRAGMA user_version = $version")
            }
        }
        try {
            setVersion(2)
            launch(false)
            node("本机阅读数据暂时无法打开，请重试。")
            assertEquals("future schema failure must preserve history", before, history()!!.toString())
            assertEquals("future schema failure must preserve cache", body, copy()!!.getString("body"))
            db().use { database ->
                database.rawQuery("PRAGMA user_version", null).use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(2, cursor.getInt(0))
                }
            }
        } finally {
            scenario.close()
            setVersion(1)
            launch()
        }
    }
}
