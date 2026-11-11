package com.magpie.pocket.core.util

/**
 * Warning seam for a failure the page cannot show, such as a rejected preference write. The app
 * points [logger] at `android.util.Log` in debuggable builds only, so a release APK stays silent
 * and JVM unit tests never need the Android runtime.
 */
object AppLog {

    @Volatile
    var logger: ((String, Throwable) -> Unit)? = null

    /** Records one failure without changing the caller's flow. */
    fun warn(message: String, error: Throwable) {
        logger?.invoke(message, error)
    }
}
