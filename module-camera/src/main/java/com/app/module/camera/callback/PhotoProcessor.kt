package com.app.module.camera.callback

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import com.app.module.camera.R
import com.app.module.camera.extensions.appConfig
import com.app.module.camera.extensions.compensateDeviceRotation
import com.app.module.camera.extensions.getOutputMediaFile
import com.app.module.camera.extensions.toast
import com.app.module.camera.ui.CameraActivity
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream

class PhotoProcessor(val context: CameraActivity, val saveUri: Uri?, val deviceOrientation: Int, val previewRotation: Int, val isUsingFrontCamera: Boolean) :
    AsyncTask<ByteArray, Void, Uri?>() {

    @TargetApi(Build.VERSION_CODES.N)
    override fun doInBackground(vararg params: ByteArray): Uri? {
        var fos: OutputStream? = null
        val path: String
        try {
            path = if (saveUri != null) {
                saveUri.path!!
            } else {
                context.getOutputMediaFile(true)
            }

            if (path.isEmpty()) {
                return null
            }

            val data = params[0]
            val photoFile = File(path)

            fos = if (saveUri == null) {
                FileOutputStream(photoFile)
            } else {
                context.contentResolver.openOutputStream(saveUri)
            }
            var image = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (isUsingFrontCamera) {
                if (context.appConfig.flipPhotos) {
                    val matrix = Matrix()
                    val isPortrait = image.width < image.height
                    matrix.preScale(if (isPortrait) -1f else 1f, if (isPortrait) 1f else -1f)

                    try {
                        image = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, false)
                    } catch (e: OutOfMemoryError) {
                        context.toast(R.string.out_of_memory_error)
                    }
                }
            }
            try {
                image.compress(Bitmap.CompressFormat.JPEG, context.appConfig.photoQuality, fos)
            } catch (e: Exception) {
                context.toast(e.toString())
                return null
            }
            return Uri.fromFile(photoFile)
        } catch (e: FileNotFoundException) {
            context.toast(e.toString())
        } finally {
            fos?.close()
        }
        return null
    }

    private fun rotate(bitmap: Bitmap, degree: Int): Bitmap? {
        if (degree == 0) {
            return bitmap
        }

        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.setRotate(degree.toFloat())
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        } catch (e: OutOfMemoryError) {
            context.toast(e.toString())
        }
        return null
    }

    override fun onPostExecute(path: Uri?) {
        super.onPostExecute(path)
        path?.also {
            context.mediaSaved(it )
        }
    }
}
