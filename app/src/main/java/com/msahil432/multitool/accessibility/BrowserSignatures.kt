package com.msahil432.multitool.accessibility

object BrowserSignatures {
  const val PKG_CHROME = "com.android.chrome"
  const val PKG_FIREFOX = "org.mozilla.firefox"
  const val PKG_BRAVE = "com.brave.browser"
  const val PKG_EDGE = "com.microsoft.emmx"
  const val PKG_DUCKDUCKGO = "com.duckduckgo.mobile.android"
  const val PKG_OPERA = "com.opera.browser"
  const val PKG_SAMSUNG = "com.sec.android.app.sbrowser"

  /**
   * Known address bar view IDs keyed by browser package name.
   */
  val URL_BAR: Map<String, String> = mapOf(
    PKG_CHROME to "com.android.chrome:id/url_bar",
    PKG_FIREFOX to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
    PKG_BRAVE to "com.brave.browser:id/url_bar",
    PKG_EDGE to "com.microsoft.emmx:id/url_bar",
    PKG_DUCKDUCKGO to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    PKG_OPERA to "com.opera.browser:id/url_field",
    PKG_SAMSUNG to "com.sec.android.app.sbrowser:id/location_bar_edit_text"
  )

  /**
   * Returns true if the package name corresponds to a supported browser.
   */
  fun isSupportedBrowser(packageName: String?): Boolean {
    if (packageName == null) return false
    return URL_BAR.containsKey(packageName)
  }

  /**
   * Returns the address bar view ID for the given package name, if known.
   */
  fun getUrlBarViewId(packageName: String): String? {
    return URL_BAR[packageName]
  }
}
