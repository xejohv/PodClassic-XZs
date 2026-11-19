package io.github.xejohv.podclassicxzs.util

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import io.github.xejohv.podclassicxzs.base.BaseApplication
import io.github.xejohv.podclassicxzs.bean.Music

object MediaUtil {
    fun getMusicPath(uri: Uri): String {
        val cursor = BaseApplication.context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )
        val path = if (cursor?.moveToFirst() == true) {
            cursor.getString(0)
        } else {
            ""
        }
        cursor?.close()
        return path
    }

    fun getMusicUri(songId: Long): Uri {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
    }

    fun getAlbumImage(albumId: Long): Bitmap? {
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(artworkUri, albumId)
        val inputStream = try {
            BaseApplication.context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            return null
        }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return bitmap
    }

    private fun getMusicImage(path: String): Bitmap? {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        val byteArray = mediaMetadataRetriever.embeddedPicture
        mediaMetadataRetriever.release()
        return if (byteArray == null || byteArray.isEmpty()) {
            null
        } else {
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }


    fun getMusicImage(music: Music): Bitmap? {
        var bitmap = if (music.albumId != null && music.albumId != 0L) {
            getAlbumImage(music.albumId)
        } else {
            null
        }
        // 系统 albumart 库里没有该专辑时（album_info 与 audio 的 id 对不上），退回文件内嵌封面
        if (bitmap == null && music.data != null) {
            bitmap = getMusicImage(music.data)
        }
        if (bitmap != null) {
            // 保持原始宽高比，只限制最大边长（原来强制 400x400 会把非方形封面压扁）
            val maxSide = 400
            val w = bitmap.width
            val h = bitmap.height
            if (w <= maxSide && h <= maxSide) {
                return bitmap
            }
            val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
            val bw = (w * scale).toInt().coerceAtLeast(1)
            val bh = (h * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, bw, bh, true)
        }
        return null
    }

    fun getLyric() {

    }

}