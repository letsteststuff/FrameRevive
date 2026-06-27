package com.framerevive.app

import android.webkit.JavascriptInterface

class FrameReviveBridge {

    @JavascriptInterface
    fun getVersion(): String {
        return "0.1.0"
    }

    @JavascriptInterface
    fun getPlatform(): String {
        return "Android"
    }

    @JavascriptInterface
    fun getAndroidVersion(): String {
        return android.os.Build.VERSION.RELEASE
    }

    @JavascriptInterface
    fun getDevice(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }
}