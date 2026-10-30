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
    private val images = LatestPhotoExecutor()
    private val cacheLock = Any()
    private val preloader by lazy { PhotoPreloader<FramePhoto>({ it.id + it.url }, { cached(it).exists() }, { p, current -> ensureCached(p,current) }, {}) }
    private val collageCadence = CollageCadence()
    private var previewCollageCount=2
    private var lastSlide: FrameSlide? = null
    private val network = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    private val checkingCount = AtomicBoolean(false)
    private val priorityIds by lazy { LinkedHashSet(prefs.getStringSet("priorityIds", emptySet()).orEmpty()) }
    private lateinit var client: PrivatePhotosClient
    private lateinit var image: FramePhotoView
    @Volatile private var photoLoading = false
    private lateinit var pausedBadge: TextView
    private lateinit var pauseButton: Button
    @Volatile private var playbackPaused = false
    private val frontButtons = FrameButtons()
    private val holdStar = Runnable { performFrontAction(frontButtons.hold(android.os.SystemClock.elapsedRealtime())) }
    private lateinit var controls: LinearLayout
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var night: TextView
    private val prefs by lazy { getSharedPreferences("frame-settings", MODE_PRIVATE) }
    private var photos = emptyList<FramePhoto>()
    private var playlist = emptyList<FramePhoto>()
    private var day = LocalDate.now()
    private var index = 0
    private val history = PhotoHistory<FrameSlide> { it.id }
    private val gestures = FrameGestures()
    private var albumId = ""
    private var sharedLink = ""
    private var lastPhotoRecovery = 0L
    private var title = "FrameRevive"
    @Volatile private var generation = 0
    @Volatile private var active = false
    @Volatile private var closed = false
    private var displayed = false
    @Volatile private var sleeping = false
    private var failures = 0
    private var remote: FrameRemote? = null
    private val updater by lazy { FrameUpdater(this) }
    private var localName: FrameLocalName? = null
    private var stableUrl = ""
    private var controlsUntil = 0L
    private val buttonLights by lazy { FrameButtonLights(this) }
    private var manageUrl = ""
    private var tlsFingerprint = ""
    @Volatile private var setupMessage = "Connect a Google Photos album link that opens without signing in."
    @Volatile private var albumSwitching = false
    private val advance = Runnable { if (!playbackPaused) showNext() }
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
        playbackPaused = prefs.getBoolean("playbackPaused", false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.attributes = window.attributes.apply { screenBrightness = FrameSchedule.awakeBrightness(prefs.getInt("brightness",55)) }
        enterFullscreen()
        client = PrivatePhotosClient(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = FramePhotoView(this).apply {
            gentleTransitions = prefs.getBoolean("gentleTransitions", true)
            blurredBackground = prefs.getBoolean("blurBackground", true)
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
        pausedBadge = TextView(this).apply {
            text = "Paused"; textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(0xBB15171B.toInt()); setPadding(18,10,18,10)
            visibility = if (playbackPaused) View.VISIBLE else View.GONE
        }
        root.addView(pausedBadge, FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply { setMargins(18,18,18,18) })
        controls = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20)
            setBackgroundColor(0xDD222222.toInt())
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; text = "FrameRevive — starting…" }
        controls.addView(status)
        val buttons = LinearLayout(this)
        fun button(label: String, action: () -> Unit): Button {
            return Button(this).apply { text = label; setOnClickListener { action(); showControls() } }.also { buttons.addView(it) }
        }
        button("Previous") { showPrevious() }
        button("Next") { handler.removeCallbacks(advance); showNext() }
        pauseButton = button(if (playbackPaused) "Resume" else "Pause") { setPlaybackPaused(!playbackPaused) }
        button("Refresh") { sync() }
        button("Settings") { settings() }
        button("Close") { handler.post { controlsUntil = 0; controls.visibility = View.GONE; handler.removeCallbacks(hide) } }
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
            val addressPanel = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,16,0,0) }
            val qr = ImageView(this).apply {
                setImageBitmap(FrameQr.bitmap(stableUrl)); contentDescription = "Scan for private web controls"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addressPanel.addView(qr, LinearLayout.LayoutParams(240,240))
            addressPanel.addView(TextView(this).apply {
                text = "Web controls — scan with your phone\n$stableUrl\n\nKeep this link private. Verify HTTPS pairing before trusting a new certificate.\nCertificate SHA-256:\n$tlsFingerprint"
                textSize = 13f; setPadding(20,0,0,0); setTextColor(Color.LTGRAY)
            }, LinearLayout.LayoutParams(0,-2,1f))
            controls.addView(addressPanel)
            remote = FrameRemote(token, { remotePage() }, { fields -> applyRemote(fields) }, tls.listen(8765), upload = { input, length -> updater.receive(input, length) })
        } catch (_: Exception) { status.append("\nPhone management could not start.") }
        FramePower.schedule(this)
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
        else runCatching { client.loadSavedSession() }
        photos = list; rebuild()
    }

    private fun saveCatalog(json: JSONObject) = PrivatePhotosClient.writeAtomic(File(filesDir, "album.json"), json.toString().toByteArray(Charsets.UTF_8))
    private fun rebuild() {
        day = LocalDate.now()
        val normal = PhotoSelection.playlist(photos,day,recentCount=prefs.getInt("recentCount",300),
            memoryDays=prefs.getInt("memoryDays",14),recentPercent=prefs.getInt("recentPercent",50))
        playlist = PhotoSelection.prioritize(normal,photos,priorityIds)
        preloader.clear()
        index = 0
    }
    private fun checkAlbumCount() {
        if (closed || !active || sleeping || sharedLink.isBlank() || syncing.get() || !checkingCount.compareAndSet(false, true)) return
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
    private fun showControls() { controlsUntil = android.os.SystemClock.uptimeMillis() + 30_000; controls.visibility = View.VISIBLE; handler.removeCallbacks(hide); handler.postDelayed(hide, 30_000) }

    private fun scheduleAdvance(delay: Long) {
        handler.removeCallbacks(advance)
        if (!playbackPaused && active && !sleeping && !closed) handler.postDelayed(advance, delay)
    }
    private fun setPlaybackPaused(paused: Boolean) {
        playbackPaused = paused
        if (paused && displayed) { image.finishTransition(); generation++; setPhotoLoading(false) }
        prefs.edit().putBoolean("playbackPaused", paused).apply()
        pausedBadge.visibility = if (paused) View.VISIBLE else View.GONE
        pauseButton.text = if (paused) "Resume" else "Pause"
        handler.removeCallbacks(advance)
        if (!paused) { scheduleAdvance(prefs.getInt("seconds",30) * 1000L); planPreloads() }
    }
    private fun performFrontAction(action: FrameButtons.Action) {
        when (action) {
            FrameButtons.Action.NEXT -> showNext()
            FrameButtons.Action.PREVIOUS -> showPrevious()
            FrameButtons.Action.TOGGLE_PAUSE -> setPlaybackPaused(!playbackPaused)
            FrameButtons.Action.CONTROLS -> showControls()
            FrameButtons.Action.NONE -> Unit
        }
    }

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
        val blur = CheckBox(this).apply { text = "Blurred background (when not filling screen)"; isChecked = prefs.getBoolean("blurBackground", true) }
        val date = CheckBox(this).apply { text = "Show photo date"; isChecked = prefs.getBoolean("date", true) }
        val hand = CheckBox(this).apply { text = "Hand gestures: left for next, right for previous"; isChecked = prefs.getBoolean("gestures", true) }
        layout.addView(enabled); layout.addView(crop); layout.addView(blur); layout.addView(date); layout.addView(hand)
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
                        .putBoolean("schedule", enabled.isChecked).putBoolean("crop", crop.isChecked).putBoolean("blurBackground",blur.isChecked)
                        .putBoolean("date", date.isChecked).putBoolean("gestures",hand.isChecked).putInt("seconds", sec).putInt("brightness",light).apply()
                    image.blurredBackground = blur.isChecked
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
        if (sleeping) { image.finishTransition(); preloader.clear(); setPhotoLoading(false); handler.removeCallbacks(holdStar); frontButtons.cancel(); generation++; FramePower.sleep(this) } else if (active && (!playbackPaused || !displayed)) showNext()
    }

    private fun cached(p: FramePhoto): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(p.id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(filesDir, "photo-cache").apply { mkdirs() }, "$hash.jpg")
    }
    private fun ensureCached(p:FramePhoto,current:()->Boolean = { true }) {
        val file=cached(p)
        if(synchronized(cacheLock) { file.exists() } || !current()) return
        val d=resources.displayMetrics
        val reader=client
        val bytes=reader.request(p.url + "=w${d.widthPixels}-h${d.heightPixels}",image=true)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image could not be decoded." }
        if(!current()) return
        synchronized(cacheLock) { if(current() && !file.exists()) PrivatePhotosClient.writeAtomic(file,bytes) }
    }
    private fun photoSize(p:FramePhoto):CollageLayout.Size = synchronized(cacheLock) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(cached(p).path,bounds)
        CollageLayout.Size(bounds.outWidth,bounds.outHeight)
    }
    private fun bitmap(p:FramePhoto,maxEdge:Int=2400):android.graphics.Bitmap {
        ensureCached(p)
        return synchronized(cacheLock) {
            val file=cached(p); file.setLastModified(System.currentTimeMillis())
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,bounds)
            val options=BitmapFactory.Options().apply { inSampleSize=1 }
            while(bounds.outWidth/options.inSampleSize > maxEdge || bounds.outHeight/options.inSampleSize > maxEdge) options.inSampleSize *= 2
            BitmapFactory.decodeFile(file.path,options) ?: run { file.delete(); error("Cached image could not be decoded.") }
        }
    }
    private fun planPreloads() {
        if(closed || !active || sleeping || !prefs.getBoolean("preload",true)) { preloader.clear(); return }
        if(playlist.isNotEmpty() && index >= playlist.size) rebuild()
        val count=if(prefs.getBoolean("collages",true) && collageCadence.singles >= prefs.getInt("collageEvery",20)-1) maxOf(3,collageCadence.nextCount) else 3
        preloader.plan(playlist.drop(index).distinctBy { it.id }.take(count))
    }
    private fun markDisplayed(slide:FrameSlide,fromHistory:Boolean=false) {
        lastSlide=slide; history.record(slide,fromHistory)
        if(!fromHistory) { collageCadence.displayed(slide.collage);if(slide.collage) collageCadence.collageShown() }
        caption.text=if(slide.collage) "" else slide.photos.first().date().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        caption.visibility=if(!slide.collage && prefs.getBoolean("date",true)) View.VISIBLE else View.GONE
        if(priorityIds.removeAll(slide.photos.map { it.id }.toSet())) prefs.edit().putStringSet("priorityIds",priorityIds.toSet()).apply()
    }
    private fun trimCache() = synchronized(cacheLock) {
        val files = File(filesDir, "photo-cache").listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.lastModified() } ?: return@synchronized
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= 512L * 1024 * 1024) break
            val length = file.length(); if (file.delete()) total -= length
        }
    }
    private fun showNext() {
        if (!active || closed || sleeping) return
        handler.removeCallbacks(advance)
        history.next()?.let { showSlide(it, fromHistory = true); return }
        if (day != LocalDate.now() || index >= playlist.size) rebuild()
        if (playlist.isEmpty()) { showControls(); return }
        val selected=if(prefs.getBoolean("collages",true) && collageCadence.due(prefs.getInt("collageEvery",20)))
            CollageCadence.candidates(playlist.drop(index),lastSlide?.photos.orEmpty(),collageCadence.nextCount) { cached(it).exists() } else emptyList()
        if(selected.size in 2..4) {
            val ids=selected.map { it.id }.toSet()
            playlist=playlist.take(index)+playlist.drop(index).filterNot { it.id in ids }
            showSlide(FrameSlide(selected))
        } else showSlide(FrameSlide(listOf(playlist[index++])))
    }
    private fun showPrevious() {
        if (!active || closed || sleeping) return
        val previous = history.previous() ?: return
        handler.removeCallbacks(advance)
        showSlide(previous, fromHistory = true)
    }
    private fun setPhotoLoading(loading: Boolean) {
        photoLoading = loading
    }
    private fun showSlide(slide: FrameSlide, fromHistory: Boolean = false) {
        val ticket = ++generation
        setPhotoLoading(true)
        images.execute {
            if (ticket != generation || closed || !active || sleeping) return@execute
            try {
                val b = if(slide.collage) {
                    val d=resources.displayMetrics
                    FrameCollage.render(slide.photos,d.widthPixels,d.heightPixels,prefs.getBoolean("blurBackground",true),prefs.getBoolean("date",true),::photoSize) { bitmap(it,1200) }
                } else bitmap(slide.photos.first())
                if (ticket != generation || closed || !active || sleeping) { b.recycle(); return@execute }
                trimCache()
                val background = FramePhotoView.background(b)
                handler.post {
                    if (closed || !active || sleeping || ticket != generation) {
                        b.recycle(); background.recycle(); return@post
                    }
                    setPhotoLoading(false)
                    image.setPhoto(b, background)
                    markDisplayed(slide,fromHistory)
                    displayed = true; failures = 0
                    planPreloads()
                    if (android.os.SystemClock.uptimeMillis() >= controlsUntil) controls.visibility = View.GONE
                    scheduleAdvance(prefs.getInt("seconds", 30) * 1000L)
                }
            } catch (e: Exception) {
                handler.post {
                    if (closed || !active || ticket != generation) return@post
                    setPhotoLoading(false)
                    history.forget(slide)
                    failures++; status.text = "$title\n${safeMessage(e)}\nTrying another photo."
                    prefs.edit().putString("lastPhotoError", safeMessage(e)).putLong("lastPhotoErrorAt", System.currentTimeMillis()).apply()
                    if (!displayed) showControls()
                    if (e.message?.contains("HTTP 403") == true && System.currentTimeMillis() - lastPhotoRecovery > 300_000) {
                        lastPhotoRecovery = System.currentTimeMillis()
                        sync()
                    }
                    val offline = (playlist.indices.filter { it >= index } + playlist.indices.filter { it < index }).firstOrNull { cached(playlist[it]).exists() }
                    if (offline != null) index = offline
                    scheduleAdvance(if (failures > 2 && offline == null) 30_000 else 1000)
                }
            }
        }
    }
    private fun sync(targetId: String = albumId, targetTitle: String = title, targetShared: String = sharedLink) {
        if (targetId.isBlank() || !syncing.compareAndSet(false, true)) return
        prefs.edit().putLong("lastSyncAttempt", System.currentTimeMillis()).apply()
        albumSwitching = targetId != albumId || targetShared != sharedLink
        setupMessage = "Loading $targetTitle. The existing slideshow continues until it is ready."
        status.text = "$targetTitle · checking Google Photos…"
        network.execute {
            try {
                val changingAlbum = targetId != albumId || targetShared != sharedLink
                val existing = photos.associateBy { it.id }
                val discovered = System.currentTimeMillis()
                val reader = if (targetShared.isNotBlank()) PrivatePhotosClient(this, anonymous = true)
                    else if (sharedLink.isNotBlank()) PrivatePhotosClient(this).apply { loadSavedSession() } else client
                val progress: (Int) -> Unit = { count ->
                    if (albumSwitching) setupMessage = "Reading $targetTitle: $count photos…"
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
                        // Refresh history URLs and remove deleted items while retaining navigation within this album.
                        if (changingAlbum) { history.clear(); collageCadence.reset() } else {
                            val updated=fresh.associateBy { it.id }
                            history.transform { old -> old.photos.mapNotNull { updated[it.id] }.takeIf { it.size == old.photos.size }?.let(::FrameSlide) }
                        }
                        if (changingAlbum) priorityIds.clear()
                        else priorityIds.addAll(fresh.filter { it.id !in existing }.map { it.id })
                        priorityIds.retainAll(fresh.map { it.id }.toSet())
                        prefs.edit().putStringSet("priorityIds", priorityIds.toSet()).apply()
                        val hasAdditions = priorityIds.isNotEmpty()
                        albumId = targetId; title = actualTitle; sharedLink = targetShared; client = reader; photos = fresh; rebuild(); planPreloads()
                        setupMessage = "Connected${if (targetShared.isNotBlank()) " through shared link (no account cookies)" else ""}. Displaying $title (${fresh.size} photos)."
                        status.text = "$title · ${fresh.size} photos\nUpdated ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())} · checks hourly"
                        if ((!displayed || changingAlbum || hasAdditions) && active && (!playbackPaused || !displayed || changingAlbum)) showNext()
                    }
                }
            } catch (e: Exception) {
                prefs.edit().putString("lastSyncError", safeMessage(e)).putLong("lastSyncErrorAt", System.currentTimeMillis()).apply()
                setupMessage = "Could not update album: ${safeMessage(e)}. Your previous album remains selected."
                handler.post { if (!closed) status.text = "$title\n${safeMessage(e)}\nKeeping the last complete photo index." }
            } finally {
                syncing.set(false)
                handler.post {
                    albumSwitching = false
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
    private fun enterFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor=Color.BLACK; window.navigationBarColor=Color.BLACK
        window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && !closed) enterFullscreen()
    }
    override fun onResume() {
        super.onResume(); active = true; enterFullscreen()
        buttonLights.start()
        setShowWhenLocked(true); setTurnScreenOn(true)
        if (intent.action == FramePower.WAKE || intent.action == FramePower.SLEEP)
            FramePower.record(this, "Scheduled display resumed")
        FramePower.schedule(this)
        checkSchedule(force = true); if (!playbackPaused || !displayed) showNext(); planPreloads(); checkAlbumCount()
    }
    override fun onPause() { image.finishTransition(); preloader.clear(); setPhotoLoading(false); handler.removeCallbacks(holdStar); frontButtons.cancel(); buttonLights.stop(); super.onPause(); active = false; generation++; handler.removeCallbacks(advance) }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) { buttonLights.touch(); if (controls.visibility == View.VISIBLE) showControls() }
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) buttonLights.touch()
        if (android.os.Build.MODEL == "Lenovo CD-3L501F" && event.device?.name == "as9061d_key" && FrameButtons.supports(event.keyCode) && active && !closed && !sleeping) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (event.action == android.view.KeyEvent.ACTION_DOWN && controls.visibility == View.VISIBLE) showControls()
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                performFrontAction(frontButtons.down(event.keyCode, now))
                if (event.keyCode == FrameButtons.STAR) { handler.removeCallbacks(holdStar); handler.postDelayed(holdStar, FrameButtons.HOLD_MS) }
            } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                if (event.keyCode == FrameButtons.STAR) handler.removeCallbacks(holdStar)
                performFrontAction(frontButtons.up(event.keyCode, now, event.isCanceled))
            }
            return true
        }
        if (android.os.Build.MODEL == "Lenovo CD-3L501F" && event.device?.name == "gesture" && event.keyCode in 289..294 &&
            prefs.getBoolean("gestures", true) && active && !closed && !sleeping && controls.visibility != View.VISIBLE) {
            if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) {
                val action = gestures.accept(event.keyCode, windowManager.defaultDisplay.rotation, android.os.SystemClock.elapsedRealtime())
                if (action != 0) {
                    prefs.edit().putString("lastGesture",if(action > 0) "Next photo" else "Previous photo")
                        .putLong("lastGestureAt",System.currentTimeMillis()).apply()
                    if (action > 0) showNext() else showPrevious()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun remotePage(): String {
        val preloadEntries=preloader.snapshot()
        val preloadSummary=if(!prefs.getBoolean("preload",true)) "Disabled" else if(sleeping || !active) "Waiting while frame is inactive" else "Ready: ${preloadEntries.count { it.status == PhotoPreloader.Status.READY }} · Loading: ${preloadEntries.count { it.status == PhotoPreloader.Status.LOADING }} · Queued: ${preloadEntries.count { it.status == PhotoPreloader.Status.QUEUED }} · Failed: ${preloadEntries.count { it.status == PhotoPreloader.Status.FAILED }}"
        val preloadDetails=preloadEntries.mapIndexed { i,e -> "${i+1}. ${java.time.Instant.ofEpochMilli(e.item.takenMs+e.item.offsetMs).atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))} — ${e.status.name.lowercase().replaceFirstChar { it.uppercase() }}" }.joinToString("\n")

        fun time(key: String) = prefs.getLong(key, 0).let { if (it == 0L) "Not yet" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
        val flags = mutableSetOf<String>()
        if (photos.isEmpty()) flags += "needsSetup"
        if (photos.isNotEmpty() && sharedLink.isBlank()) flags += "legacySession"
        if (playbackPaused) flags += "paused"
        if (photoLoading) flags += "photoLoading"
        if(prefs.getBoolean("collages",true)) flags += "collages"
        if(prefs.getBoolean("preload",true)) flags += "preload"
        if (sharedLink.isNotBlank()) flags += "shared"
        if (syncing.get()) flags += "syncing"
        if (albumSwitching || syncing.get() || preloadEntries.any { it.status == PhotoPreloader.Status.QUEUED || it.status == PhotoPreloader.Status.LOADING }) flags += "busy"
        for (name in listOf("schedule", "crop", "date", "gestures", "blurBackground", "gentleTransitions")) {
            if (prefs.getBoolean(name, name == "date" || name == "gestures" || name == "blurBackground" || name == "gentleTransitions")) flags += name
        }
        if (FramePower.hasScreenControl(this)) flags += "hardwareSleep"
        val text = mapOf(
            "recentCount" to prefs.getInt("recentCount",300).toString(), "memoryDays" to prefs.getInt("memoryDays",14).toString(),
            "recentPercent" to prefs.getInt("recentPercent",50).toString(), "memoryPercent" to (100-prefs.getInt("recentPercent",50)).toString(),
            "collageEvery" to prefs.getInt("collageEvery",20).toString(),
            "collageSummary" to (if(!prefs.getBoolean("collages",true)) "Disabled" else "Every ${prefs.getInt("collageEvery",20)} individual photos · Next: ${collageCadence.nextCount} photos · ${collageCadence.singles} shown · ${if(lastSlide?.collage == true) "${lastSlide?.photos?.size}-photo collage on screen" else "Single photo"}"),
            "preloadSummary" to preloadSummary, "preloadDetails" to preloadDetails,
            "frameTime" to java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM).format(java.util.Date()),
            "frameTimeZone" to (java.util.TimeZone.getDefault().id + " (" + java.time.ZonedDateTime.now().offset.toString() + ")"),
            "albumLink" to sharedLink,
            "title" to title, "photoCount" to photos.size.toString(), "pending" to priorityIds.size.toString(),
            "lastSync" to time("lastSuccessfulSync"), "lastAttempt" to time("lastSyncAttempt"),
            "seconds" to prefs.getInt("seconds",30).toString(), "brightness" to prefs.getInt("brightness",55).toString(),
            "sleepStart" to prefs.getString("sleepStart","22:00").orEmpty(), "sleepEnd" to prefs.getString("sleepEnd","07:00").orEmpty(),
            "setupMessage" to setupMessage, "albumId" to albumId,
            "bookmark" to stableUrl, "hostnameStatus" to (localName?.status ?: "Local hostname unavailable"),
            "fingerprint" to tlsFingerprint, "countCheck" to time("lastCountCheck"),
            "observedCount" to (prefs.getInt("observedCount",-1).takeIf { it >= 0 }?.toString() ?: "Not available"),
            "syncError" to prefs.getString("lastSyncError",null).orEmpty(), "countError" to prefs.getString("lastCountError",null).orEmpty(),
            "photoError" to prefs.getString("lastPhotoError",null).orEmpty(), "photoErrorAt" to time("lastPhotoErrorAt"),
            "lastGesture" to prefs.getString("lastGesture","None yet").orEmpty(), "lastGestureAt" to time("lastGestureAt"),
            "powerEvent" to prefs.getString("lastPowerEvent","No scheduled event recorded yet").orEmpty(),
            "nextWake" to if (prefs.contains("nextWAKE")) time("nextWAKE") else "Schedule disabled",
            "diagnostics" to listOf(
                "FrameRevive ${FrameWeb.VERSION}", "Android API ${android.os.Build.VERSION.SDK_INT}",
                "Playback paused: $playbackPaused",
                "Photo mix: ${prefs.getInt("recentPercent",50)}% recent; count ${prefs.getInt("recentCount",300)}; memories +/-${prefs.getInt("memoryDays",14)} days",
                "Preloading: $preloadSummary",
                "Gentle transitions: ${prefs.getBoolean("gentleTransitions",true)}",
                "Collages: ${prefs.getBoolean("collages",true)}; every ${prefs.getInt("collageEvery",20)} photos",
                "Source: ${if (sharedLink.isNotBlank()) "Shared album" else "Signed-in album"}",
                "Photos: ${photos.size}; waiting: ${priorityIds.size}",
                "Last refresh: ${time("lastSuccessfulSync")}", "Last attempt: ${time("lastSyncAttempt")}",
                "Last count check: ${time("lastCountCheck")}", "Refreshing: ${syncing.get()}",
                "Refresh error: ${FrameDiagnostics.errorSummary(prefs.getString("lastSyncError",null))}",
                "Count error: ${FrameDiagnostics.errorSummary(prefs.getString("lastCountError",null))}",
                "Last photo error: ${FrameDiagnostics.errorSummary(prefs.getString("lastPhotoError",null))}",
                "Schedule enabled: ${prefs.getBoolean("schedule",false)}",
                "Hardware sleep: ${FramePower.hasScreenControl(this)}",
                "Gestures enabled: ${prefs.getBoolean("gestures",true)}",
                "Last gesture: ${time("lastGestureAt")}",
                "Private links and credentials omitted."
            ).joinToString("\n")
        )
        return FrameWeb.render(FrameWeb.State(text, flags, updateHtml=updater.panel(), recoveryHtml=RecoveryGuide.html()))
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
                    if (syncing.get()) return "An album check is already running. Try again when it finishes."
                    handler.post { sync("shared", "Shared album", link) }
                    "Reading the shared album without account cookies. The previous album stays available until the complete scan succeeds."
                }
                "importSession", "loadAlbums", "selectAlbum" -> "Signed-in session setup has been retired. Connect a shared album link that opens without signing in. Existing saved-session playback is unchanged."
                "save" -> {
                    val start = LocalTime.parse(fields["sleepStart"]); val end = LocalTime.parse(fields["sleepEnd"])
                    require(start != end) { "Choose different sleep and wake times." }
                    val seconds = fields["seconds"]?.toIntOrNull() ?: 30
                    require(seconds in 10..300) { "Choose 10 to 300 seconds per photo." }
                    val brightness = fields["brightness"]?.toIntOrNull() ?: prefs.getInt("brightness",55)
                    require(brightness in 5..100) { "Choose 5 to 100 percent brightness." }
                    val changingMix=fields["mixSettings"] == "1"
                    val recentCount=if(changingMix) fields["recentCount"]?.toIntOrNull() ?: error("Recent count required") else prefs.getInt("recentCount",300)
                    val memoryDays=if(changingMix) fields["memoryDays"]?.toIntOrNull() ?: error("Memory window required") else prefs.getInt("memoryDays",14)
                    val recentPercent=if(changingMix) fields["recentPercent"]?.toIntOrNull() ?: error("Mix required") else prefs.getInt("recentPercent",50)
                    require(recentCount in 1..2000 && memoryDays in 0..60 && recentPercent in 0..100)
                    val collageEvery=if(changingMix) fields["collageEvery"]?.toIntOrNull() ?: prefs.getInt("collageEvery",20) else prefs.getInt("collageEvery",20)
                    require(collageEvery in 5..100) { "Choose 5 to 100 photos between collages." }
                    val wasSleeping = FramePower.shouldSleep(this) || sleeping
                    val edit = prefs.edit().putString("sleepStart",start.toString()).putString("sleepEnd",end.toString())
                        .putBoolean("schedule",fields.containsKey("schedule")).putBoolean("crop",fields.containsKey("crop"))
                        .putBoolean("date",fields.containsKey("date")).putInt("seconds",seconds).putInt("brightness",brightness)
                    if (fields["transitionSettings"] == "1") edit.putBoolean("gentleTransitions",fields.containsKey("gentleTransitions"))
                    if (fields["backgroundSettings"] == "1") edit.putBoolean("blurBackground",fields.containsKey("blurBackground"))
                    if (fields["gestureSettings"] == "1") edit.putBoolean("gestures",fields.containsKey("gestures"))
                    if(changingMix) edit.putInt("recentCount",recentCount).putInt("memoryDays",memoryDays).putInt("recentPercent",recentPercent)
                        .putBoolean("collages",fields.containsKey("collages")).putInt("collageEvery",collageEvery).remove("bursts").putBoolean("preload",fields.containsKey("preload"))
                    edit.commit()
                    FramePower.schedule(this)
                    handler.postDelayed({
                        if (!closed) {
                            image.gentleTransitions = prefs.getBoolean("gentleTransitions",true)
                            image.blurredBackground = prefs.getBoolean("blurBackground",true)
                            image.scaleType = if (prefs.getBoolean("crop",false)) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                            caption.visibility = if (lastSlide?.collage != true && prefs.getBoolean("date",true)) View.VISIBLE else View.GONE
                            if(changingMix) { generation++; setPhotoLoading(false); rebuild(); planPreloads(); scheduleAdvance(prefs.getInt("seconds",30)*1000L) }
                            checkSchedule(wakeFromSchedule = wasSleeping)
                        }
                    }, 1000)
                    "Settings saved."
                }
                "previewCollage" -> {
                    if(!active || sleeping) return "Wake the frame before previewing a collage."
                    handler.post {
                        if(active && !closed && !sleeping) {
                            val selected=photos.shuffled().asSequence().filter { cached(it).exists() }.take(previewCollageCount).toList()
                            if(selected.size == previewCollageCount) { handler.removeCallbacks(advance); showSlide(FrameSlide(selected),fromHistory=true);previewCollageCount=2+(previewCollageCount-1)%3 }
                        }
                    }
                    "Showing a collage if enough cached photos are available. Pause stays unchanged."
                }
                "pause" -> { handler.post { setPlaybackPaused(true) }; "Slideshow paused." }
                "resume" -> { handler.post { setPlaybackPaused(false) }; "Slideshow resumed." }
                "next" -> { handler.post { showNext() }; "Advancing to the next photo." }
                "previous" -> { handler.post { showPrevious() }; "Returning to the previous displayed photo, if available." }
                "refresh" -> { handler.post { sync() }; "Album refresh requested; the slideshow will keep playing." }
                else -> "Unknown action."
            }
        } catch (_: Exception) { "Could not save. Use valid times, 10–300 seconds, 1–2000 recent photos, a 0–60 day memory window, and a 0–100 mix." }
    }
    override fun onDestroy() { image.release(); closed = true; localName?.close(); remote?.close(); handler.removeCallbacksAndMessages(null); preloader.close(); images.shutdownNow(); network.shutdownNow(); super.onDestroy() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FrameUpdater.INSTALL_REQUEST) updater.finished(resultCode)
    }
}
