package com.example.muzpleer.ui.local.adapters

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri

private const val TAG = "33333"

fun getFilePathFromUri(context: Context, uri: Uri): String? {
    return try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            if (uri.authority == "com.android.providers.downloads.documents") {
                if (docId.startsWith("raw:")) {
                    docId.substringAfter("raw:")
                } else {
                    // Для download documents
                    val contentUri = ContentUris.withAppendedId(
                        "content://downloads/public_downloads".toUri(),
                        docId.toLong()
                    )
                    getDataColumn(context, contentUri, null, null)
                }
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun getDataColumn(
    context: Context,
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)

    return try {
        cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndexOrThrow(column)
            cursor.getString(columnIndex)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting data column: ${e.message}")
        null
    } finally {
        cursor?.close()
    }
}
