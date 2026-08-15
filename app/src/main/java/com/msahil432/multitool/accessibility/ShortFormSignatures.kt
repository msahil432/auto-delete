package com.msahil432.multitool.accessibility

/**
 * Detection signatures for identifying short-form video feeds (Shorts / Reels)
 * across target applications.
 *
 * Kept separate from handler logic so signatures can be easily updated when
 * third-party applications modify their view hierarchies.
 */
object ShortFormSignatures {
    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_FACEBOOK = "com.facebook.katana"

    /**
     * package -> list of node resource-id signatures indicating a short-form feed.
     */
    val SHORT_FORM: Map<String, List<String>> = mapOf(
        PKG_YOUTUBE to listOf(
            "reel_recycler",              // Shorts feed recycler view id
            "reel_player_page_container", // Shorts player container
            "reel_watch_fragment_root"    // Shorts watch container
        ),
        PKG_INSTAGRAM to listOf(
            "clips_viewer_view_pager",    // Reels viewer pager
            "clips_tab",                  // Clips tab view
            "reel_viewer_root",           // Reels root
            "clips_video_container"       // Clips video container
        ),
        PKG_FACEBOOK to listOf(
            "reels_viewer",               // Reels viewer container
            "video_home_reels",           // Video home reels
            "fb_shorts_container"         // FB Shorts container
        )
    )

    /**
     * package -> list of known short-form Activity/Fragment class names.
     */
    val SHORT_FORM_CLASSES: Map<String, List<String>> = mapOf(
        PKG_YOUTUBE to listOf(
            "com.google.android.apps.youtube.app.extensions.reel.edit.activity.ReelCameraActivity",
            "com.google.android.apps.youtube.app.extensions.reel.watch.activity.ReelWatchActivity"
        ),
        PKG_INSTAGRAM to listOf(
            "com.instagram.clips.viewer.ClipsViewerActivity",
            "com.instagram.modal.ModalActivity"
        ),
        PKG_FACEBOOK to listOf(
            "com.facebook.video.videohome.reels"
        )
    )

    /**
     * Returns true if the given [packageName] is one of the supported short-form video apps.
     */
    fun isSupportedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return SHORT_FORM.containsKey(packageName)
    }
}
