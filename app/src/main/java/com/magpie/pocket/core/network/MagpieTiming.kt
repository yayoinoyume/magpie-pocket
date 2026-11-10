package com.magpie.pocket.core.network

/**
 * Timing lines for the connection path. The app points [logger] at `android.util.Log` in
 * debuggable builds only, so a release APK stays silent and JVM unit tests never need the
 * Android runtime.
 */
object MagpieTiming {

    private val originNanos = System.nanoTime()

    @Volatile
    var logger: ((String) -> Unit)? = null

    /** Milliseconds since this object was loaded, which is close to process start. */
    fun sinceStartMs(): Long = (System.nanoTime() - originNanos) / 1_000_000L

    /** Logs one milestone prefixed with its offset from process start. */
    fun mark(name: String) {
        logger?.invoke("+${sinceStartMs()}ms $name")
    }

    /** Logs a line that already carries its own measurements. */
    fun log(line: String) {
        logger?.invoke(line)
    }
}
