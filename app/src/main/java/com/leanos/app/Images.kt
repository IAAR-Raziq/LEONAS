package com.leanos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/** Bounded decode with EXIF orientation for gallery and camera images. */
fun decodePhoto(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Select a valid image." }
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = 1
            while (
                bounds.outWidth / inSampleSize > maxDimension ||
                    bounds.outHeight / inSampleSize > maxDimension
            ) inSampleSize *= 2
        }
    val bitmap =
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to read photo.")
    val exif =
        context.contentResolver.openInputStream(uri).use { input ->
            input?.let { ExifInterface(it) }
        }
    val matrix =
        Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
