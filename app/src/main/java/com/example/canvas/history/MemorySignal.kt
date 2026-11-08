package com.example.canvas.history

/**
 * How much memory is really free for history data.
 *
 * Two different pools matter on Android 8+:
 *  - the **Java heap** (limited per app): compressed byte arrays, stroke point arrays, bookkeeping;
 *  - **native memory** (limited by the whole device): Bitmap pixels since API 26. `Runtime.maxMemory()`
 *    cannot see it, so a budget based only on the Java heap is blind to the biggest consumer here.
 *
 * The app installs [systemFree] (from `ActivityManager.MemoryInfo`: available memory minus the low-memory
 * threshold). Until then only the Java heap is considered (unit tests, tools).
 */
object MemorySignal {

    /** Device-wide bytes available before the low-memory killer starts; null until the app installs it. */
    @Volatile
    var systemFree: (() -> Long)? = null

    /** Installs the device-wide signal from the system (call once, e.g. in Activity.onCreate). */
    fun install(context: android.content.Context) {
        val am = context.applicationContext.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager ?: return
        systemFree = {
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            (info.availMem - info.threshold).coerceAtLeast(0L)
        }
    }

    fun javaFree(): Long {
        val rt = Runtime.getRuntime()
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    }

    fun nativeFree(): Long = systemFree?.invoke() ?: Long.MAX_VALUE

    /** Conservative: what is free in the scarcer of the two pools. */
    fun free(): Long = minOf(javaFree(), nativeFree())
}
