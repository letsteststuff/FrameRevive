package com.framerevive.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Stages an owner-supplied APK. Android always performs and confirms installation. */
class FrameUpdater(private val activity: Activity) {
    companion object { const val MAX_BYTES = 40 * 1024 * 1024; const val INSTALL_REQUEST = 550 }
    private val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
    private val staged = File(directory, "FrameRevive.apk")
    @Volatile var status = "Choose a FrameRevive APK to upload. Android confirmation is required on the frame."
        private set
    @Volatile private var installing = false
    init {
        File(directory, "upload.part").delete()
        if (staged.exists()) runCatching { checkArchive(staged) }.onFailure { staged.delete() }
    }
    fun reportFailure(error: Exception? = null) {
        status = if (error is IllegalArgumentException || error is IllegalStateException)
            error.message ?: "Update request could not complete."
        else "Could not open Android's installer/settings. Return to FrameRevive on the frame and try again."
    }

    private fun checkArchive(file: File): String {
        val pm = activity.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(file.path, flags)
            ?: error("That file is not a readable APK.")
        val current = pm.getPackageInfo(activity.packageName, flags)
        require(candidate.packageName == activity.packageName) { "This APK is not FrameRevive." }
        require(candidate.longVersionCode >= current.longVersionCode) { "Older APKs cannot be installed through this page." }
        require((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "This APK requires a newer Android version." }
        fun signatures(info: android.content.pm.PackageInfo) = (info.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
            ?: info.signatures)?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }?.toSet().orEmpty()
        val expected = signatures(current); val received = signatures(candidate)
        require(expected.isNotEmpty() && expected == received) { "Signing key does not match this installation. The APK was not staged." }
        return candidate.versionName ?: candidate.longVersionCode.toString()
    }

    @Synchronized fun receive(input: InputStream, length: Int): String {
        require(length in 1..MAX_BYTES) { "Choose an APK no larger than 40 MiB." }
        check(!installing) { "Finish or cancel the installation on the frame before uploading another APK." }
        val temp = File(directory, "upload.part")
        try {
            status = "Receiving APK…"
            temp.outputStream().use { out ->
                val buffer = ByteArray(32768); var remaining = length
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    check(count > 0) { "Upload was interrupted. Upload the APK again." }
                    out.write(buffer, 0, count); remaining -= count
                }
            }
            val version = checkArchive(temp)
            check(temp.renameTo(staged)) { "Could not stage the update. Try again." }
            status = "FrameRevive $version verified. Click Install on frame, then confirm using your Bluetooth mouse."
            return status
        } catch (e: Exception) {
            status = if (e is IllegalArgumentException || e is IllegalStateException) e.message ?: "APK validation failed."
                else "Upload failed. Check your connection and try again."
            throw IllegalArgumentException(status)
        } finally { temp.delete() }
    }

    fun allowInstalls() {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
        status = "On the frame, allow FrameRevive to request app installs, then return here. Android will still ask before each installation."
    }

    @Synchronized fun install() {
        check(!installing) { "An installation is already awaiting confirmation on the frame." }
        check(staged.exists()) { "Upload and verify an APK first." }
        check(activity.packageManager.canRequestPackageInstalls()) { "First use Allow installs on frame and approve that setting on the frame." }
        val version = checkArchive(staged)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", staged)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        activity.startActivityForResult(intent, INSTALL_REQUEST)
        installing = true
        status = "Android is asking to install FrameRevive $version on the frame. Confirm or cancel there. The webpage may briefly disconnect during installation."
    }

    @Synchronized fun finished(result: Int) {
        installing = false
        status = if (result == Activity.RESULT_OK) "Installation completed. Reopen your saved controls link."
            else "Installation was canceled or did not complete. The current app and settings are retained."
    }
    @Synchronized fun discard(): String {
        check(!installing) { "Cancel the installer on the frame first." }
        staged.delete(); status = "Uploaded APK removed. Current app and settings are unchanged."; return status
    }
    fun panel(): String = "<p>${FrameRemote.escape(status)}</p><p>Android install permission: ${if (activity.packageManager.canRequestPackageInstalls()) "allowed" else "not yet allowed"}. ${if (staged.exists()) "A verified APK is staged." else "No APK staged."}</p>"
}
