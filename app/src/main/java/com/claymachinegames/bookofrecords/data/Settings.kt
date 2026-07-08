package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Persists the user-chosen SAF storage target. Null = use the local MediaStore default. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_TARGET = "target_tree_uri"

    fun targetUri(context: Context): Uri? =
        prefs(context).getString(KEY_TARGET, null)?.let(Uri::parse)

    fun setTarget(context: Context, uri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        targetUri(context)?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        if (uri != null) {
            resolver.takePersistableUriPermission(uri, flags)
        }
        prefs(context).edit().apply {
            if (uri != null) putString(KEY_TARGET, uri.toString()) else remove(KEY_TARGET)
        }.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
