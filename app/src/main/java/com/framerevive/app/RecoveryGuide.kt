package com.framerevive.app

object RecoveryGuide {
    val text = """
        Black screen
        Check power first. If it is within your sleep schedule, wait until wake time. If the controls page works, check the schedule and daytime brightness. Use your Bluetooth mouse to bring up controls. If it is unresponsive, disconnect and reconnect frame power once. Avoid factory reset or uninstalling; those can lose account/settings data. Reconnect USB/ADB if the launcher does not recover.

        Photos stopped or HTTP 403
        Read Connection status and try Refresh album. Shared-link mode does not need account cookies: check that the saved album link still opens without sign-in and that link sharing is enabled. If necessary paste the working shared link again. For signed-in mode, use Connect or renew Google Photos and reselect the album. Cached photos can keep playing while Google is unavailable.

        Controls page will not open
        Your phone/computer must be on the same home Wi-Fi, and the frame must be awake. Use the full saved link, including its key. Try the IP link if .local names do not work on your network. Guest networks/client isolation may prevent access. A router DHCP reservation is the fallback for a stable IP.

        Changed Wi-Fi network
        Reconnect USB and run the Windows installer script with -ConfigureWifi. Keep the Bluetooth mouse paired so you can accept USB debugging prompts on the frame. Once photos and the Wi-Fi controls link work again, disconnect USB. An offline frame cannot change its network through the webpage.

        Webpage update
        Download the FrameRevive APK, not the source-code ZIP. Upload it under Update FrameRevive. The app checks package, version, and signing key. If needed, click Allow installs on frame and use your Bluetooth mouse to approve that setting on the frame, then return to FrameRevive. Click Install on frame and approve Android's installation prompt. Never uninstall to solve a signing-key mismatch; keep your data and contact the build's author. The page may briefly disconnect while Android replaces the app; reopen your saved link afterward. You can cancel the Android prompt or discard a staged APK.

        Restore Lenovo's launcher (USB/ADB)
        First turn off FrameRevive's nightly schedule. In PowerShell run these commands using your installed adb.exe:
        adb shell pm enable --user 0 com.walnut.bluetooth
        adb shell cmd package set-home-activity com.walnut.bluetooth/com.smart_frame.SplashActivity
        adb shell am start -n com.walnut.bluetooth/com.smart_frame.SplashActivity
        Android Studio's adb.exe is normally in your LocalAppData Android/Sdk/platform-tools folder. Restoring Lenovo's launcher does not repair its discontinued Google Photos service. Disable FrameRevive's device-administrator activation in Android settings before uninstalling it.

        Keep your shared album URL, account-cookie exports, and full controls link private. Do not post them in screenshots or GitHub issues.
    """.trimIndent()
    fun html() = "<details><summary>Recovery guide</summary><pre style='white-space:pre-wrap;font:inherit;line-height:1.5'>${FrameRemote.escape(text)}</pre></details>"
}
