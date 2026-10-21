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
    private var title = "FrameRevive"
    private var generation = 0
    private var active = false
    private var closed = false
    private var displayed = false
    private var sleeping = false
    private var failures = 0
    private var remote: FrameRemote? = null
    private var manageUrl = ""
    private val advance = Runnable { showNext() }
    private val hide = Runnable { if (displayed) controls.visibility = View.GONE }
    private val refresh = object : Runnable {
        override fun run() { sync(); handler.postDelayed(this, 60 * 60 * 1000L) }
    }
    private val clock = object : Runnable {
        override fun run() { checkSchedule(); handler.postDelayed(this, 30_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
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
            val token = prefs.getString("managementKey", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
                prefs.edit().putString("managementKey", it).apply()
            }
            val ip = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).flatMap {
                java.util.Collections.list(it.inetAddresses)
            }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: "frame-ip"
            manageUrl = "http://$ip:8765/?key=$token"
            controls.addView(TextView(this).apply { text = "Manage from your phone or laptop:\n$manageUrl"; textSize = 13f; setTextColor(Color.LTGRAY) })
            remote = FrameRemote(token, { remotePage() }, { fields -> applyRemote(fields) })
        } catch (_: Exception) { status.append("\nPhone management could not start.") }
        FramePower.schedule(this)
        handler.post(refresh)
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
        photos = list; rebuild()
    }

    private fun saveCatalog(json: JSONObject) = PrivatePhotosClient.writeAtomic(File(filesDir, "album.json"), json.toString().toByteArray(Charsets.UTF_8))
    private fun rebuild() { day = LocalDate.now(); playlist = PhotoSelection.playlist(photos, day); index = 0 }
    private fun showControls() { controls.visibility = View.VISIBLE; handler.removeCallbacks(hide); handler.postDelayed(hide, 15_000) }

    private fun settings() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        fun field(label: String, value: String): EditText {
            layout.addView(TextView(this).apply { text = label })
            return EditText(this).apply { setText(value); isSingleLine = true; layout.addView(this) }
        }
        val start = field("Sleep from (24-hour HH:mm)", prefs.getString("sleepStart", "22:00")!!)
        val end = field("Wake at (24-hour HH:mm)", prefs.getString("sleepEnd", "07:00")!!)
        val seconds = field("Seconds per photo (10–300)", prefs.getInt("seconds", 30).toString())
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
                    prefs.edit().putString("sleepStart", a.toString()).putString("sleepEnd", b.toString())
                        .putBoolean("schedule", enabled.isChecked).putBoolean("crop", crop.isChecked)
                        .putBoolean("date", date.isChecked).putInt("seconds", sec).apply()
                    image.scaleType = if (crop.isChecked) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    caption.visibility = if (date.isChecked) View.VISIBLE else View.GONE
                    FramePower.schedule(this); checkSchedule(); dialog.dismiss()
                } catch (e: Exception) { Toast.makeText(this, e.message ?: "Use HH:mm times.", Toast.LENGTH_LONG).show() }
            }
        }
        dialog.show()
    }

    private fun checkSchedule() {
        val now = LocalTime.now()
        val start = LocalTime.parse(prefs.getString("sleepStart", "22:00"))
        val end = LocalTime.parse(prefs.getString("sleepEnd", "07:00"))
        val shouldSleep = prefs.getBoolean("schedule", false) && FrameSchedule.isSleeping(now, start, end)
        if (shouldSleep == sleeping) return
        sleeping = shouldSleep
        night.visibility = if (sleeping) View.VISIBLE else View.GONE
        window.attributes = window.attributes.apply { screenBrightness = if (sleeping) 0f else -1f }
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
                    handler.removeCallbacks(hide); handler.postDelayed(hide, 15_000)
                    handler.postDelayed(advance, prefs.getInt("seconds", 30) * 1000L)
                }
            } catch (e: Exception) {
                handler.post {
                    if (closed || !active || ticket != generation) return@post
                    failures++; status.text = "$title\n${safeMessage(e)}\nTrying another photo."
                    showControls()
                    val offline = (playlist.indices.filter { it >= index } + playlist.indices.filter { it < index }).firstOrNull { cached(playlist[it]).exists() }
                    if (offline != null) index = offline
                    handler.postDelayed(advance, if (failures > 2 && offline == null) 30_000 else 1000)
                }
            }
        }
    }
    private fun sync() {
        if (albumId.isBlank() || !syncing.compareAndSet(false, true)) return
        status.text = "$title · checking Google Photos…"
        network.execute {
            try {
                val existing = photos.associateBy { it.id }
                val discovered = System.currentTimeMillis()
                val fresh = client.sync(albumId) { count -> handler.post { if (!closed) status.text = "$title · reading $count photos…" } }
                    .map { p -> p.copy(discoveredMs = existing[p.id]?.discoveredMs ?: discovered) }
                val rows = JSONArray()
                fresh.forEach { p -> rows.put(JSONObject().put("id", p.id).put("url", p.url).put("takenMs", p.takenMs).put("offsetMs", p.offsetMs).put("discoveredMs",p.discoveredMs)) }
                saveCatalog(JSONObject().put("albumId", albumId).put("title", title).put("photos", rows).put("syncedAt", System.currentTimeMillis()))
                val valid = fresh.map { cached(it).name }.toHashSet()
                File(filesDir, "photo-cache").listFiles()?.filter { it.extension == "jpg" && it.name !in valid }?.forEach { it.delete() }
                handler.post {
                    if (!closed) {
                        photos = fresh; rebuild()
                        status.text = "$title · ${fresh.size} photos\nUpdated ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())} · checks hourly"
                        if (!displayed && active) showNext()
                    }
                }
            } catch (e: Exception) {
                handler.post { if (!closed) status.text = "$title\n${safeMessage(e)}\nKeeping the last complete photo index." }
            } finally { syncing.set(false) }
        }
    }
    private fun safeMessage(e: Exception): String = when (e) {
        is IllegalStateException, is IllegalArgumentException -> e.message?.take(180) ?: "Photo access needs attention."
        is java.net.SocketTimeoutException -> "Google did not respond in time."
        is java.io.IOException -> "Network unavailable or Google could not be reached."
        else -> "Photo access needs attention (${e.javaClass.simpleName})."
    }
    override fun onResume() { super.onResume(); active = true; checkSchedule(); showNext() }
    override fun onPause() { super.onPause(); active = false; generation++; handler.removeCallbacks(advance) }
    private fun remotePage(): String {
        fun esc(s: String) = FrameRemote.escape(s)
        fun checked(name: String, default: Boolean = false) = if (prefs.getBoolean(name, default)) "checked" else ""
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>FrameRevive</title>
        <style>body{font:17px system-ui;background:#111923;color:#edf2f7;max-width:560px;margin:32px auto;padding:20px}h1{margin-bottom:8px}p{color:#bcc9d6;line-height:1.5}label{display:block;margin:20px 0}input,button{font:inherit;padding:10px;border-radius:8px}button{background:#b6e0bb;color:#102316;border:0;margin:8px 8px 8px 0}small{color:#aebdcd}</style></head><body>
        <h1>FrameRevive</h1><p>${esc(title)} · ${photos.size} photos<br>Newest photos + memories from around today's date.</p>
        <form method="post"><label>Seconds per photo <input type="number" min="10" max="300" name="seconds" value="${prefs.getInt("seconds",30)}"></label>
        <label><input type="checkbox" name="schedule" ${checked("schedule")}> Nightly sleep schedule</label>
        <label>Sleep at <input type="time" required name="sleepStart" value="${esc(prefs.getString("sleepStart","22:00")!!)}"></label>
        <label>Wake at <input type="time" required name="sleepEnd" value="${esc(prefs.getString("sleepEnd","07:00")!!)}"></label>
        <label><input type="checkbox" name="crop" ${checked("crop")}> Fill screen (crop photo edges)</label>
        <label><input type="checkbox" name="date" ${checked("date",true)}> Show capture date</label>
        <button name="action" value="save">Save settings</button><button name="action" value="next">Next photo</button><button name="action" value="refresh">Refresh album</button></form>
        <p>Album checks run hourly. All photos stay managed in Google Photos. These controls work on your home Wi-Fi while the frame is awake.</p>
        <small>Screen control: ${if (FramePower.hasScreenControl(this)) "hardware sleep enabled" else "dim-to-black only"}. Times use the frame's local timezone. Keep this private management link bookmarked.</small></body></html>""".trimIndent()
    }
    private fun applyRemote(fields: Map<String, String>): String {
        return try {
            when (fields["action"]) {
                "save" -> {
                    val start = LocalTime.parse(fields["sleepStart"]); val end = LocalTime.parse(fields["sleepEnd"])
                    require(start != end) { "Choose different sleep and wake times." }
                    val seconds = fields["seconds"]?.toIntOrNull() ?: 30
                    require(seconds in 10..300) { "Choose 10 to 300 seconds per photo." }
                    prefs.edit().putString("sleepStart",start.toString()).putString("sleepEnd",end.toString())
                        .putBoolean("schedule",fields.containsKey("schedule")).putBoolean("crop",fields.containsKey("crop"))
                        .putBoolean("date",fields.containsKey("date")).putInt("seconds",seconds).commit()
                    FramePower.schedule(this)
                    handler.postDelayed({
                        if (!closed) {
                            image.scaleType = if (prefs.getBoolean("crop",false)) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                            caption.visibility = if (prefs.getBoolean("date",true)) View.VISIBLE else View.GONE
                            checkSchedule()
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
    override fun onDestroy() { closed = true; remote?.close(); handler.removeCallbacksAndMessages(null); images.shutdownNow(); network.shutdownNow(); super.onDestroy() }
}
