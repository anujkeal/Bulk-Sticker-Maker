package com.keal.bulkstickermaker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class StickerContentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.keal.bulkstickermaker.stickercontentprovider"
        private const val METADATA = 1
        private const val SINGLE_METADATA = 2
        private const val STICKERS = 3
        private const val ASSET = 4

        // Column names are part of WhatsApp's public sticker-provider contract.
        private val metadataColumns = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack"
        )
        private val stickerColumns = arrayOf(
            "sticker_file_name",
            "sticker_emoji",
            "sticker_accessibility_text"
        )
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        matcher.addURI(AUTHORITY, "metadata", METADATA)
        matcher.addURI(AUTHORITY, "metadata/*", SINGLE_METADATA)
        matcher.addURI(AUTHORITY, "stickers/*", STICKERS)
        matcher.addURI(AUTHORITY, "stickers_asset/*/*", ASSET)
        return true
    }

    private fun prefs() = requireNotNull(context).getSharedPreferences("pack", 0)
    private fun stickerNames(): List<String> =
        prefs().getString("stickers", "").orEmpty().split("|").filter { it.isNotBlank() }
    private fun packName() = prefs().getString("name", "My Stickers") ?: "My Stickers"
    private fun packDir() = File(requireNotNull(context).filesDir, "stickers/pack1")

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return when (matcher.match(uri)) {
            METADATA, SINGLE_METADATA -> {
                val cursor = MatrixCursor(metadataColumns)
                val names = stickerNames()
                if (names.size in 3..30 && File(packDir(), "tray.png").isFile) {
                    cursor.addRow(
                        arrayOf(
                            "pack1",
                            packName(),
                            "Bulk Sticker Maker",
                            "tray.png",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            prefs().getString("version", "1") ?: "1",
                            0,
                            0
                        )
                    )
                }
                cursor
            }
            STICKERS -> {
                val cursor = MatrixCursor(stickerColumns)
                stickerNames().forEach { file ->
                    // WhatsApp expects a comma-separated list of emoji, not a JSON array.
                    cursor.addRow(arrayOf(file, "😀", file.substringBeforeLast('.')))
                }
                cursor
            }
            else -> MatrixCursor(projection?.map { it }?.toTypedArray() ?: emptyArray())
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Sticker assets are read-only" }
        val segments = uri.pathSegments
        require(
            segments.size == 3 &&
                segments[0] == "stickers_asset" &&
                segments[1] == "pack1"
        ) { "Invalid sticker asset URI" }

        val fileName = segments[2]
        require(fileName == "tray.png" || fileName in stickerNames()) { "Unknown sticker asset" }
        val directory = packDir().canonicalFile
        val file = File(directory, fileName).canonicalFile
        require(file.parentFile == directory && file.isFile) { "Sticker asset not found" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
        SINGLE_METADATA -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
        ASSET -> if (uri.lastPathSegment == "tray.png") "image/png" else "image/webp"
        else -> "application/octet-stream"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Not supported")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("Not supported")
}
