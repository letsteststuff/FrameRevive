package com.framerevive.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val images = Executors.newSingleThreadExecutor()
    private val network = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    private val checkingCount = AtomicBoolean(false)
    private val priorityIds by lazy { LinkedHashSet(prefs.getStringSet("priorityIds", emptySet()).orEmpty()) }
    private lateinit var client: PrivatePhotosClient
    private lateinit var image: ImageView
    private lateinit var controls: LinearLayout
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var night: TextView
    private val prefs by lazy { getSharedPreferences("frame-settings", MODE_PRIVATE) }
    private var photos = emptyList<FramePhoto>()
    private var playlist = emptyList<FramePhoto>()
    private var day = LocalDate.now()
    private var index = 0
    private var albumId = ""
    private var sharedLink = ""
    private var lastPhotoRecovery = 0L
    private var title = "FrameRevive"
    private var generation = 0
    private var active = false
    private var closed = false
    private var displayed = false
    private var sleeping = false
    private var failures = 0
    private var remote: FrameRemote? = null
    private val updater by lazy { FrameUpdater(this) }
    private var localName: FrameLocalName? = null
    private var stableUrl = ""
    private var controlsUntil = 0L
    private val buttonLights by lazy { FrameButtonLights(this) }
    private var manageUrl = ""
    private var tlsFingerprint = ""
    @Volatile private var availableAlbums = emptyList<PrivatePhotosClient.Album>()
    @Volatile private var setupMessage = "Import your Google Photos session, then choose an album."
    private val setupBusy = AtomicBoolean(false)
    private val setupGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    private var pendingAlbum: PrivatePhotosClient.Album? = null
    @Volatile private var albumSwitching = false
    private val advance = Runnable { showNext() }
    private val hide = Runnable { if (displayed) controls.visibility = View.GONE }
    private val refresh = object : Runnable {
        override fun run() { sync(); handler.postDelayed(this, 60 * 60 * 1000L) }
    }
    private val countCheck = object : Runnable {
        override fun run() { checkAlbumCount(); handler.postDelayed(this, 120_000) }
    }
    private val clock = object : Runnable {
        override fun run() { checkSchedule(); handler.postDelayed(this, 30_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.attributes = window.attributes.apply { screenBrightness = FrameSchedule.awakeBrightness(prefs.getInt("brightness",55)) }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        client = PrivatePhotosClient(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = ImageView(this).apply {
            scaleType = if (prefs.getBoolean("crop", false)) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            contentDescription = "Photo slideshow. Tap for controls."
            setOnClickListener { showControls() }
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        caption = TextView(this).apply {
            textSize = 16f; setTextColor(Color.WHITE); setPadding(24, 12, 24, 12)
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        controls = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20)
            setBackgroundColor(0xDD222222.toInt())
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; text = "FrameRevive — starting…" }
        controls.addView(status)
        val buttons = LinearLayout(this)
        fun button(label: String, action: () -> Unit) {
            buttons.addView(Button(this).apply { text = label; setOnClickListener { action(); showControls() } })
        }
        button("Next") { handler.removeCallbacks(advance); showNext() }
        button("Refresh") { sync() }
        button("Settings") { settings() }
        controls.addView(buttons)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        night = TextView(this).apply {
            setBackgroundColor(Color.BLACK); setTextColor(0xFF444444.toInt()); gravity = Gravity.CENTER
            text = "Sleeping · tap for settings"; visibility = View.GONE
            setOnClickListener { settings() }
        }
        root.addView(night, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        try {
            client.importSessionIfPresent()
            val seed = File(filesDir, "album-seed.json")
            val catalog = File(filesDir, "album.json")
            val source = if (seed.exists()) seed else catalog
            if (source.exists()) {
                val json = JSONObject(source.readText())
                readCatalog(json)
                if (source == seed) { saveCatalog(json); seed.delete() }
            }
            status.text = "$title · ${photos.size} photos\nNewest photos + this time in past years"
        } catch (e: Exception) { status.text = "Setup needs attention: ${safeMessage(e)}" }
        try {
            val token = prefs.getString("managementKey", null)?.takeIf { prefs.getBoolean("httpsManagement", false) } ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
                check(prefs.edit().putString("managementKey", it).putBoolean("httpsManagement", true).commit())
            }
            val ip = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).flatMap {
                java.util.Collections.list(it.inetAddresses)
            }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: "frame-ip"
            manageUrl = "https://$ip:8765/?key=$token"
            val hostname = prefs.getString("localHostname", null) ?: "framerevive-${java.util.UUID.randomUUID().toString().take(8)}.local".also {
                prefs.edit().putString("localHostname", it).apply()
            }
            stableUrl = "https://$hostname:8765/?key=$token"
            val tls = FrameTls(this, hostname, runCatching { java.net.InetAddress.getByName(ip).address }.getOrNull())
            tlsFingerprint = tls.fingerprint
            localName = FrameLocalName(this, hostname)
            controls.addView(TextView(this).apply { text = "Manage from your phone or laptop:\n$stableUrl\nCertificate SHA-256:\n$tlsFingerprint"; textSize = 13f; setTextColor(Color.LTGRAY) })
            remote = FrameRemote(token, { remotePage() }, { fields -> applyRemote(fields) }, tls.listen(8765), upload = { input, length -> updater.receive(input, length) })
        } catch (_: Exception) { status.append("\nPhone management could not start.") }
        FramePower.schedule(this)
        if (sharedLink.isBlank() && File(filesDir, "google-session.enc").exists()) loadAlbums()
        handler.post(refresh)
        handler.postDelayed(countCheck, 120_000)
        handler.post(clock)
    }

    private fun readCatalog(json: JSONObject) {
        val rows = json.getJSONArray("photos")
        val list = (0 until rows.length()).map { i ->
            val p = rows.getJSONObject(i)
            FramePhoto(p.getString("id"), p.getString("url"), p.getLong("takenMs"), p.optLong("offsetMs"), p.optLong("discoveredMs"))
        }
        check(list.isNotEmpty()) { "Photo index is empty." }
        albumId = json.getString("albumId"); title = json.optString("title", "Your photo frame")
        sharedLink = json.optString("sharedLink")
        if (json.optLong("syncedAt") > prefs.getLong("lastSuccessfulSync", 0))
            prefs.edit().putLong("lastSuccessfulSync", json.optLong("syncedAt")).apply()
        if (sharedLink.isNotBlank()) client = PrivatePhotosClient(this, anonymous = true)
        photos = list; rebuild()
    }

    private fun saveCatalog(json: JSONObject) = PrivatePhotosClient.writeAtomic(File(filesDir, "album.json"), json.toString().toByteArray(Charsets.UTF_8))
    private fun rebuild() {
        day = LocalDate.now()
        playlist = PhotoSelection.prioritize(PhotoSelection.playlist(photos, day), photos, priorityIds)
        index = 0
    }
    private fun checkAlbumCount() {
        if (closed || !active || sleeping || sharedLink.isBlank() || syncing.get() || setupBusy.get() || !checkingCount.compareAndSet(false, true)) return
        val source = sharedLink
        network.execute {
            try {
                val count = PrivatePhotosClient(this, anonymous = true).sharedCount(source)
                prefs.edit().putLong("lastCountCheck", System.currentTimeMillis()).putInt("observedCount", count ?: -1)
                    .remove("lastCountError").apply()
                handler.post {
                    if (!closed && active && !sleeping && sharedLink == source && count != null && count != prefs.getInt("fullAlbumCount", photos.size)) sync()
                }
            } catch (e: Exception) {
                prefs.edit().putLong("lastCountCheck", System.currentTimeMillis()).putString("lastCountError", safeMessage(e)).apply()
            } finally { checkingCount.set(false) }
        }
    }
    private fun showControls() { controlsUntil = android.os.SystemClock.uptimeMillis() + 15_000; controls.visibility = View.VISIBLE; handler.removeCallbacks(hide); handler.postDelayed(hide, 15_000) }

    private fun settings() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        fun field(label: String, value: String): EditText {
            layout.addView(TextView(this).apply { text = label })
            return EditText(this).apply { setText(value); isSingleLine = true; layout.addView(this) }
        }
        val start = field("Sleep from (24-hour HH:mm)", prefs.getString("sleepStart", "22:00")!!)
        val end = field("Wake at (24-hour HH:mm)", prefs.getString("sleepEnd", "07:00")!!)
        val seconds = field("Seconds per photo (10–300)", prefs.getInt("seconds", 30).toString())
        val brightness = field("Daytime brightness (5–100 percent)", prefs.getInt("brightness",55).toString())
        val enabled = CheckBox(this).apply { text = "Enable nightly sleep schedule"; isChecked = prefs.getBoolean("schedule", false) }
        val crop = CheckBox(this).apply { text = "Fill screen (crop photo edges)"; isChecked = prefs.getBoolean("crop", false) }
        val date = CheckBox(this).apply { text = "Show photo date"; isChecked = prefs.getBoolean("date", true) }
        layout.addView(enabled); layout.addView(crop); layout.addView(date)
        layout.addView(TextView(this).apply { text = "Checks your album hourly. " + if (FramePower.hasScreenControl(this@MainActivity)) "Sleep turns the display off and schedules automatic wake." else "Sleep dims to black until screen control is enabled." })
        val dialog = AlertDialog.Builder(this).setTitle("FrameRevive settings").setView(layout).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val a = LocalTime.parse(start.text.toString().trim()); val b = LocalTime.parse(end.text.toString().trim())
                    require(a != b) { "Sleep and wake times must differ." }
                    val sec = seconds.text.toString().toInt(); require(sec in 10..300) { "Choose 10–300 seconds." }
                    val light = brightness.text.toString().toInt(); require(light in 5..100) { "Choose 5–100 percent brightness." }
                    val wasSleeping = FramePower.shouldSleep(this) || sleeping
                    prefs.edit().putString("sleepStart", a.toString()).putString("sleepEnd", b.toString())
                        .putBoolean("schedule", enabled.isChecked).putBoolean("crop", crop.isChecked)
                        .putBoolean("date", date.isChecked).putInt("seconds", sec).putInt("brightness",light).apply()
                    image.scaleType = if (crop.isChecked) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    caption.visibility = if (date.isChecked) View.VISIBLE else View.GONE
                    FramePower.schedule(this); checkSchedule(wakeFromSchedule = wasSleeping); dialog.dismiss()
                } catch (e: Exception) { Toast.makeText(this, e.message ?: "Use HH:mm times.", Toast.LENGTH_LONG).show() }
            }
        }
        dialog.show()
    }

    private fun checkSchedule(force: Boolean = false, wakeFromSchedule: Boolean = false) {
        val now = LocalTime.now()
        val start = LocalTime.parse(prefs.getString("sleepStart", "22:00"))
        val end = LocalTime.parse(prefs.getString("sleepEnd", "07:00"))
        val shouldSleep = prefs.getBoolean("schedule", false) && FrameSchedule.isSleeping(now, start, end)
        // Always restore our own daytime level; the abandoned ambient-light service
        // can leave Android's global automatic brightness at its minimum overnight.
        window.attributes = window.attributes.apply { screenBrightness = if (shouldSleep) 0f else FrameSchedule.awakeBrightness(prefs.getInt("brightness",55)) }
        // Updating brightness does not wake a display locked by DevicePolicyManager.
        // Capture the old schedule when saving, including while the activity is paused.
        if (!shouldSleep && (sleeping || wakeFromSchedule) && !getSystemService(android.os.PowerManager::class.java).isInteractive) {
            FramePower.record(this, "Display wake requested after leaving scheduled sleep")
            FramePower.wake(this)
        }
        if (shouldSleep == sleeping) {
            if (force && shouldSleep) FramePower.sleep(this)
            return
        }
        sleeping = shouldSleep
        night.visibility = if (sleeping) View.VISIBLE else View.GONE
        handler.removeCallbacks(advance)
        if (sleeping) { generation++; FramePower.sleep(this) } else if (active) showNext()
    }

    private fun cached(p: FramePhoto): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(p.id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(filesDir, "photo-cache").apply { mkdirs() }, "$hash.jpg")
    }
    private fun bitmap(p: FramePhoto): android.graphics.Bitmap {
        val file = cached(p)
        if (!file.exists()) {
            val d = resources.displayMetrics
            val bytes = client.request(p.url + "=w${d.widthPixels}-h${d.heightPixels}", image = true)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image could not be decoded." }
            PrivatePhotosClient.writeAtomic(file, bytes)
        }
        file.setLastModified(System.currentTimeMillis())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > 2400 || bounds.outHeight / options.inSampleSize > 2400) options.inSampleSize *= 2
        return BitmapFactory.decodeFile(file.path, options) ?: run { file.delete(); error("Cached image could not be decoded.") }
    }
    private fun trimCache() {
        val files = File(filesDir, "photo-cache").listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= 512L * 1024 * 1024) break
            val length = file.length(); if (file.delete()) total -= length
        }
    }
    private fun showNext() {
        if (!active || closed || sleeping) return
        handler.removeCallbacks(advance)
        if (day != LocalDate.now() || index >= playlist.size) rebuild()
        if (playlist.isEmpty()) { showControls(); return }
        val p = playlist[index++]; val ticket = ++generation
        images.execute {
            try {
                val b = bitmap(p); trimCache()
                handler.post {
                    if (closed || !active || sleeping || ticket != generation) { b.recycle(); return@post }
                    image.setImageBitmap(b)
                    caption.text = p.date().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                    caption.visibility = if (prefs.getBoolean("date", true)) View.VISIBLE else View.GONE
                    displayed = true; failures = 0
                    if (priorityIds.remove(p.id)) prefs.edit().putStringSet("priorityIds", priorityIds.toSet()).apply()
                    if (android.os.SystemClock.uptimeMillis() >= controlsUntil) controls.visibility = View.GONE
                    handler.postDelayed(advance, prefs.getInt("seconds", 30) * 1000L)
                }
            } catch (e: Exception) {
                handler.post {
                    if (closed || !active || ticket != generation) return@post
                    failures++; status.text = "$title\n${safeMessage(e)}\nTrying another photo."
                    prefs.edit().putString("lastPhotoError", safeMessage(e)).putLong("lastPhotoErrorAt", System.currentTimeMillis()).apply()
                    if (!displayed) showControls()
                    if (e.message?.contains("HTTP 403") == true && System.currentTimeMillis() - lastPhotoRecovery > 300_000) {
                        lastPhotoRecovery = System.currentTimeMillis()
                        sync()
                    }
                    val offline = (playlist.indices.filter { it >= index } + playlist.indices.filter { it < index }).firstOrNull { cached(playlist[it]).exists() }
                    if (offline != null) index = offline
                    handler.postDelayed(advance, if (failures > 2 && offline == null) 30_000 else 1000)
                }
            }
        }
    }
    private fun sync(targetId: String = albumId, targetTitle: String = title, targetShared: String = sharedLink) {
        if (targetId.isBlank() || !syncing.compareAndSet(false, true)) return
        val statusGeneration = setupGeneration.get()
        prefs.edit().putLong("lastSyncAttempt", System.currentTimeMillis()).apply()
        albumSwitching = targetId != albumId || targetShared != sharedLink
        if (!setupBusy.get()) setupMessage = "Loading $targetTitle. The existing slideshow continues until it is ready."
        status.text = "$targetTitle · checking Google Photos…"
        network.execute {
            try {
                val changingAlbum = targetId != albumId || targetShared != sharedLink
                val existing = photos.associateBy { it.id }
                val discovered = System.currentTimeMillis()
                val reader = if (targetShared.isNotBlank()) PrivatePhotosClient(this, anonymous = true)
                    else if (sharedLink.isNotBlank()) PrivatePhotosClient(this).apply { importSessionIfPresent() } else client
                val progress: (Int) -> Unit = { count ->
                    if (albumSwitching && setupGeneration.get() == statusGeneration) setupMessage = "Reading $targetTitle: $count photos…"
                    handler.post { if (!closed) status.text = "$targetTitle · reading $count photos…" }
                }
                val shared = if (targetShared.isNotBlank()) reader.syncShared(targetShared, progress) else null
                val actualTitle = shared?.title ?: targetTitle
                val fresh = (shared?.photos ?: reader.sync(targetId, progress))
                    .map { p -> p.copy(discoveredMs = if (changingAlbum) 0 else existing[p.id]?.discoveredMs ?: discovered) }
                val rows = JSONArray()
                fresh.forEach { p -> rows.put(JSONObject().put("id", p.id).put("url", p.url).put("takenMs", p.takenMs).put("offsetMs", p.offsetMs).put("discoveredMs",p.discoveredMs)) }
                saveCatalog(JSONObject().put("albumId", targetId).put("title", actualTitle).put("sharedLink", targetShared).put("photos", rows).put("syncedAt", System.currentTimeMillis()))
                prefs.edit().putLong("lastSuccessfulSync", System.currentTimeMillis()).putInt("fullAlbumCount", shared?.itemCount ?: fresh.size)
                    .remove("lastSyncError").remove("lastCountError").apply()
                val valid = fresh.map { cached(it).name }.toHashSet()
                File(filesDir, "photo-cache").listFiles()?.filter { it.extension == "jpg" && it.name !in valid }?.forEach { it.delete() }
                handler.post {
                    if (!closed) {
                        if (changingAlbum) priorityIds.clear()
                        else priorityIds.addAll(fresh.filter { it.id !in existing }.map { it.id })
                        priorityIds.retainAll(fresh.map { it.id }.toSet())
                        prefs.edit().putStringSet("priorityIds", priorityIds.toSet()).apply()
                        val hasAdditions = priorityIds.isNotEmpty()
                        albumId = targetId; title = actualTitle; sharedLink = targetShared; client = reader; photos = fresh; rebuild()
                        if (setupGeneration.get() == statusGeneration)
                            setupMessage = "Connected${if (targetShared.isNotBlank()) " through shared link (no account cookies)" else ""}. Displaying $title (${fresh.size} photos)."
                        status.text = "$title · ${fresh.size} photos\nUpdated ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())} · checks hourly"
                        if ((!displayed || changingAlbum || hasAdditions) && active) showNext()
                    }
                }
            } catch (e: Exception) {
                prefs.edit().putString("lastSyncError", safeMessage(e)).putLong("lastSyncErrorAt", System.currentTimeMillis()).apply()
                if (setupGeneration.get() == statusGeneration)
                    setupMessage = "Could not update album: ${safeMessage(e)}. Your previous album remains selected."
                handler.post { if (!closed) status.text = "$title\n${safeMessage(e)}\nKeeping the last complete photo index." }
            } finally {
                syncing.set(false)
                handler.post {
                    albumSwitching = false
                    val next = pendingAlbum; pendingAlbum = null
                    if (!closed && next != null) sync(next.id, next.title, "")
                }
            }
        }
    }
    private fun safeMessage(e: Exception): String = when (e) {
        is IllegalStateException, is IllegalArgumentException -> e.message?.take(180) ?: "Photo access needs attention."
        is java.net.SocketTimeoutException -> "Google did not respond in time."
        is java.io.IOException -> "Network unavailable or Google could not be reached."
        else -> "Photo access needs attention (${e.javaClass.simpleName})."
    }
    override fun onNewIntent(newIntent: android.content.Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (newIntent.action == FramePower.WAKE || newIntent.action == FramePower.SLEEP) {
            FramePower.record(this, "Scheduled activity: ${newIntent.action}")
            FramePower.schedule(this)
            checkSchedule(force = true)
        }
    }
    override fun onResume() {
        super.onResume(); active = true
        buttonLights.start()
        setShowWhenLocked(true); setTurnScreenOn(true)
        if (intent.action == FramePower.WAKE || intent.action == FramePower.SLEEP)
            FramePower.record(this, "Scheduled display resumed")
        FramePower.schedule(this)
        checkSchedule(force = true); showNext(); checkAlbumCount()
    }
    override fun onPause() { buttonLights.stop(); super.onPause(); active = false; generation++; handler.removeCallbacks(advance) }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) buttonLights.touch()
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) buttonLights.touch()
        return super.dispatchKeyEvent(event)
    }
    private fun loadAlbums() {
        if (!setupBusy.compareAndSet(false, true)) return
        setupGeneration.incrementAndGet()
        setupMessage = "Reading your Google Photos albums…"
        network.execute {
            try {
                availableAlbums = PrivatePhotosClient(this).apply { importSessionIfPresent() }.albums()
                setupMessage = if (availableAlbums.isEmpty()) "Connected, but no albums were found. Create an album in Google Photos, then load albums again."
                    else "Connected. ${availableAlbums.size} albums available. Choose one below."
            } catch (e: Exception) { setupMessage = "Could not read albums: ${safeMessage(e)}. Import a fresh session if needed." }
            finally { setupBusy.set(false) }
        }
    }

    private fun importSession(text: String): String {
        if (!setupBusy.compareAndSet(false, true)) return "Google setup is already running. Wait for it to finish."
        setupGeneration.incrementAndGet()
        setupMessage = "Checking the uploaded Google Photos session…"
        network.execute {
            try {
                val (connected, albums) = client.validateSession(text)
                if (sharedLink.isBlank()) client = connected
                availableAlbums = albums
                setupMessage = if (albums.isEmpty()) "Session verified and encrypted. No albums found; create one in Google Photos and load albums again."
                    else "Session verified and encrypted on this frame. Choose an album below."
            } catch (e: Exception) { setupMessage = "Import failed: ${safeMessage(e)}. The previously saved session was kept." }
            finally { setupBusy.set(false) }
        }
        return "Import started. Progress and your album list will update here."
    }

    private fun remotePage(): String {
        fun esc(s: String) = FrameRemote.escape(s)
        fun checked(name: String, default: Boolean = false) = if (prefs.getBoolean(name, default)) "checked" else ""
        fun time(key: String): String = prefs.getLong(key, 0).let { if (it == 0L) "Not yet" else esc(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))) }
        val options = availableAlbums.joinToString("") { a -> "<option value=\"${esc(a.id)}\" ${if (a.id == albumId) "selected" else ""}>${esc(a.title)}</option>" }
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>FrameRevive</title>
        
        <style>body{font:17px system-ui;background:#111923;color:#edf2f7;max-width:560px;margin:32px auto;padding:20px}h1{margin-bottom:8px}p{color:#bcc9d6;line-height:1.5}label{display:block;margin:20px 0}input,button{font:inherit;padding:10px;border-radius:8px}button{background:#b6e0bb;color:#102316;border:0;margin:8px 8px 8px 0}small{color:#aebdcd}button:disabled{opacity:.7;cursor:wait}button[aria-busy=true]:before{content:"";display:inline-block;width:12px;height:12px;border:2px solid currentColor;border-right-color:transparent;border-radius:50%;margin-right:8px;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}</style></head><body>
        <h1>FrameRevive <small>0.5.8-alpha</small></h1><p id="albumSummary">${esc(title)} · ${photos.size} photos<br>New additions first, then newest photos + memories from around today's date.</p>
        <section id="connectionStatus"><h2>Connection status</h2>
        <p>Source: <strong>${if (sharedLink.isNotBlank()) "Shared Google Photos album · no account cookies" else "Signed-in Google Photos album"}</strong><br>
        Album: ${esc(title)}<br>Last successful refresh: ${time("lastSuccessfulSync")}<br>Last refresh attempt: ${time("lastSyncAttempt")}<br>
        Refresh: ${if (syncing.get()) "In progress…" else "Idle · checks hourly while running"}</p>
        <p>${if (sharedLink.isNotBlank()) "Automatic count check: every two minutes while awake.<br>Last count check: ${time("lastCountCheck")} · observed count: ${prefs.getInt("observedCount", -1).takeIf { it >= 0 } ?: "not available"}" else "Signed-in source: hourly full checks."}<br>New photos waiting for their first display: ${priorityIds.size}</p>
        ${prefs.getString("lastCountError", null)?.let { "<p>Count check error: ${esc(it)}. Hourly full refresh remains enabled.</p>" } ?: ""}
        <p role="status">${prefs.getString("lastSyncError", null)?.let { "Refresh error (${time("lastSyncErrorAt")}): ${esc(it)}. The last complete album is retained." } ?: "No current album refresh error."}</p>
        ${prefs.getString("lastPhotoError", null)?.let { "<p>Last photo download error (${time("lastPhotoErrorAt")}): ${esc(it)}. Cached photos can continue playing.</p>" } ?: ""}
        <h2>Bookmark your controls</h2><p><a href="${esc(stableUrl)}">${esc(stableUrl)}</a><br><small>${esc(localName?.status ?: "Local hostname unavailable")}</small></p>
        <p><small>Encrypted HTTPS management. First-time certificate pairing must be verified against the SHA-256 fingerprint shown on the frame, or through authorized USB. Never accept a changed certificate without verifying it again.</small></p>
        <p><small>The hostname stays the same if the frame's IP changes. Keep the full link, including its key, private. If your phone/router does not support local names, use the IP link or reserve the frame's IP in your router.</small></p></section>
        <h2>Google Photos</h2><p id="setupStatus" data-busy="${setupBusy.get() || albumSwitching || syncing.get()}">${esc(setupMessage)}</p>
        <form method="post"><label>Shared Google Photos album link <input type="url" required name="sharedLink" placeholder="https://photos.app.goo.gl/…"></label><button name="action" value="sharedAlbum">Use shared album</button></form>
        <p><small>For an album that opens without signing in. No account cookies are used in shared-link mode. Anyone with the album link can view its photos; keep it private. Changes to the same album are checked hourly.</small></p>
        <details ${if (availableAlbums.isEmpty() && sharedLink.isBlank()) "open" else ""}><summary>Connect or renew Google Photos (signed-in alternative)</summary>
        <p>Sign in only on Google's own website. This setup imports a local session export; it is not Google's official Sign in button.</p>
        <ol><li>On your computer, install <a href="https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc" target="_blank" rel="noreferrer">Get cookies.txt LOCALLY</a> and allow it in Incognito.</li>
        <li>In a new Incognito window, sign in at <a href="https://photos.google.com/" target="_blank" rel="noreferrer">Google Photos</a> and open your library.</li>
        <li>Open a blank tab, close the Google Photos tab, and use the extension's <strong>Export All Cookies</strong> button. Choose Netscape format.</li>
        <li>Close Incognito. Select the exported file below, then import it.</li></ol>
        <p><small>Use this page on trusted home Wi-Fi. The file contains sign-in credentials. It is sent only to your frame, filtered to Google Photos cookies, validated, and stored encrypted there. Never share it with your friend or upload it to GitHub. You can delete your export after successful setup.</small></p>
        <input id="sessionFile" type="file" accept=".txt,text/plain"><button type="button" id="importButton">Import session</button><p id="importResult" role="status"></p></details>
        <form method="post"><button name="action" value="loadAlbums">Load albums</button></form>
        <div id="albumPicker">${if (availableAlbums.isNotEmpty()) "<form method='post'><label>Display album <select name='albumId'>$options</select></label><button name='action' value='selectAlbum'>Use this album</button></form>" else ""}</div>
        <hr><h2>Display settings</h2>
        <form method="post"><label>Seconds per photo <input type="number" min="10" max="300" name="seconds" value="${prefs.getInt("seconds",30)}"></label>
        <label>Daytime brightness (percent) <input type="number" min="5" max="100" name="brightness" value="${prefs.getInt("brightness",55)}"></label>
        <label><input type="checkbox" name="schedule" ${checked("schedule")}> Nightly sleep schedule</label>
        <label>Sleep at <input type="time" required name="sleepStart" value="${esc(prefs.getString("sleepStart","22:00")!!)}"></label>
        <label>Wake at <input type="time" required name="sleepEnd" value="${esc(prefs.getString("sleepEnd","07:00")!!)}"></label>
        <label><input type="checkbox" name="crop" ${checked("crop")}> Fill screen (crop photo edges)</label>
        <label><input type="checkbox" name="date" ${checked("date",true)}> Show capture date</label>
        <button name="action" value="save">Save settings</button><button name="action" value="next">Next photo</button><button name="action" value="refresh">Refresh album</button></form>
        <hr><h2>Update FrameRevive</h2><div id="updateStatus" role="status">${updater.panel()}</div>
        <p>Choose a FrameRevive APK (maximum 40 MiB). Uploading does not install it. Android asks you to confirm on the frame; use your Bluetooth mouse. Keep the frame powered and on trusted home Wi-Fi.</p>
        <input type="file" id="apkFile" accept=".apk,application/vnd.android.package-archive"><button type="button" id="apkUpload">Upload and verify APK</button>
        <form method="post"><button name="action" value="allowInstalls">Allow installs on frame</button><button name="action" value="installApk">Install on frame</button><button name="action" value="discardApk">Discard uploaded APK</button></form>
        ${RecoveryGuide.html()}
        <p id="actionResult" role="status" aria-live="polite"></p><p>Shared album counts are checked automatically every two minutes while awake. Changes trigger a full refresh and new photos get a first turn. Full album checks also run hourly. All photos stay managed in Google Photos.</p>
        <small>Screen control: ${if (FramePower.hasScreenControl(this)) "hardware sleep enabled" else "dim-to-black only"}. Times use the frame's local timezone. Keep this private management link bookmarked.</small>
        <p id="powerStatus"><small>Last power event: ${esc(prefs.getString("lastPowerEvent","No scheduled event recorded yet")!!)}.<br>Next wake: ${if(prefs.contains("nextWAKE")) esc(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(prefs.getLong("nextWAKE",0)))) else "schedule disabled"}</small></p>
        <script>
        const result=document.getElementById('actionResult');let sending=false;let timer;
        function busy(button,on){button.disabled=on;button.setAttribute('aria-busy',String(on));}
        async function updateStatus(){
          const response=await fetch(location.href,{cache:'no-store'});
          if(!response.ok)throw new Error('Could not read frame status.');
          const page=new DOMParser().parseFromString(await response.text(),'text/html');
          for(const id of ['setupStatus','albumSummary','albumPicker','powerStatus','connectionStatus','updateStatus']){
            const fresh=page.getElementById(id),current=document.getElementById(id);
            if(fresh&&current){
              if(id==='albumPicker'&&document.activeElement&&current.contains(document.activeElement))continue;
              if(current.outerHTML!==fresh.outerHTML)current.replaceWith(fresh);
            }
          }
          clearTimeout(timer);
          timer=setTimeout(poll,document.getElementById('setupStatus').dataset.busy==='true'?4000:15000);
        }
        async function poll(){try{await updateStatus();}catch(error){result.textContent='Status update interrupted. Retrying…';timer=setTimeout(poll,5000);}}
        async function send(body,button){
          if(sending)return;sending=true;busy(button,true);result.textContent='Sending…';
          try{
            const response=await fetch(location.href,{method:'POST',body,cache:'no-store'});
            if(!response.ok)throw new Error('Frame rejected the request.');
            const reply=new DOMParser().parseFromString(await response.text(),'text/html');
            result.textContent=reply.querySelector('p')?.textContent||'Request received.';
            await updateStatus();timer=setTimeout(poll,1500);
          }catch(error){result.textContent='Could not confirm the request. Check your connection before retrying.';}
          finally{sending=false;busy(button,false);}
        }
        document.addEventListener('submit',function(event){
          event.preventDefault();const button=event.submitter;if(!button)return;
          const body=new URLSearchParams(new FormData(event.target));body.set(button.name,button.value);send(body,button);
        });
        document.getElementById('apkUpload').addEventListener('click',async function(){
          if(sending)return;const file=document.getElementById('apkFile').files[0];
          if(!file||!file.name.toLowerCase().endsWith('.apk')||file.size<1||file.size>41943040){result.textContent='Choose a FrameRevive APK no larger than 40 MiB.';return;}
          sending=true;busy(this,true);clearTimeout(timer);result.textContent='Uploading APK… Keep this page open.';
          try{
            const endpoint=new URL(location.href);endpoint.searchParams.set('action','uploadApk');
            const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/vnd.android.package-archive'},body:file,cache:'no-store'});
            const reply=new DOMParser().parseFromString(await response.text(),'text/html');
            result.textContent=reply.querySelector('p')?.textContent||'Upload finished. Check update status.';
            await updateStatus();
          }catch(error){result.textContent='Upload connection interrupted. Check update status before trying again.';timer=setTimeout(poll,5000);}
          finally{sending=false;busy(this,false);}
        });
        document.getElementById('importButton').addEventListener('click',async function(){
          if(sending)return;
          const file=document.getElementById('sessionFile').files[0];
          if(!file){result.textContent='Choose your exported cookies.txt file first.';return;}
          if(file.size>200000){result.textContent='That file is too large. Export a dedicated Google Photos session.';return;}
          try{await send(new URLSearchParams({action:'importSession',session:await file.text()}),this);}
          catch(error){result.textContent='Could not read the selected file.';}
        });
        timer=setTimeout(poll,4000);
        </script></body></html>""".trimIndent()
    }
    private fun applyRemote(fields: Map<String, String>): String {
        return try {
            when (fields["action"]) {
                "allowInstalls" -> { handler.post { try { updater.allowInstalls() } catch (_: Exception) { updater.reportFailure() } }; "Opening Android's install permission setting on the frame. Approve there, then return to FrameRevive." }
                "installApk" -> { handler.post { try { updater.install() } catch (e: Exception) { updater.reportFailure(e) } }; "Request sent. Check Update status and confirm Android's prompt on the frame." }
                "discardApk" -> updater.discard()
                "sharedAlbum" -> {
                    val link = fields["sharedLink"]?.trim().orEmpty()
                    require(link.length in 1..2048)
                    if (syncing.get() || setupBusy.get()) return "An album check is already running. Try again when it finishes."
                    handler.post { sync("shared", "Shared album", link) }
                    "Reading the shared album without account cookies. The previous album stays available until the complete scan succeeds."
                }
                "importSession" -> importSession(fields["session"] ?: "")
                "loadAlbums" -> { loadAlbums(); "Reading your albums. The album list will update here." }
                "selectAlbum" -> {
                    if (setupBusy.get()) return "Google setup is still running. Wait for the album list to finish loading."
                    val selected = availableAlbums.firstOrNull { it.id == fields["albumId"] }
                        ?: return "That album is not in the current list. Load albums and try again."
                    handler.post {
                        if (syncing.get()) { pendingAlbum = selected; setupMessage = "Your album switch is queued after the current refresh." }
                        else sync(selected.id, selected.title, "")
                    }
                    "Loading the selected album. Your current slideshow continues until the new album is ready."
                }
                "save" -> {
                    val start = LocalTime.parse(fields["sleepStart"]); val end = LocalTime.parse(fields["sleepEnd"])
                    require(start != end) { "Choose different sleep and wake times." }
                    val seconds = fields["seconds"]?.toIntOrNull() ?: 30
                    require(seconds in 10..300) { "Choose 10 to 300 seconds per photo." }
                    val brightness = fields["brightness"]?.toIntOrNull() ?: prefs.getInt("brightness",55)
                    require(brightness in 5..100) { "Choose 5 to 100 percent brightness." }
                    val wasSleeping = FramePower.shouldSleep(this) || sleeping
                    prefs.edit().putString("sleepStart",start.toString()).putString("sleepEnd",end.toString())
                        .putBoolean("schedule",fields.containsKey("schedule")).putBoolean("crop",fields.containsKey("crop"))
                        .putBoolean("date",fields.containsKey("date")).putInt("seconds",seconds).putInt("brightness",brightness).commit()
                    FramePower.schedule(this)
                    handler.postDelayed({
                        if (!closed) {
                            image.scaleType = if (prefs.getBoolean("crop",false)) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                            caption.visibility = if (prefs.getBoolean("date",true)) View.VISIBLE else View.GONE
                            checkSchedule(wakeFromSchedule = wasSleeping)
                        }
                    }, 1000)
                    "Settings saved."
                }
                "next" -> { handler.post { showNext() }; "Advancing to the next photo." }
                "refresh" -> { handler.post { sync() }; "Album refresh requested; the slideshow will keep playing." }
                else -> "Unknown action."
            }
        } catch (_: Exception) { "Could not save. Use valid times and 10 to 300 seconds per photo." }
    }
    override fun onDestroy() { closed = true; localName?.close(); remote?.close(); handler.removeCallbacksAndMessages(null); images.shutdownNow(); network.shutdownNow(); super.onDestroy() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FrameUpdater.INSTALL_REQUEST) updater.finished(resultCode)
    }
}
