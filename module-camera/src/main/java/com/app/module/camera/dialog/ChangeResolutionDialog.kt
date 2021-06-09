package com.app.module.camera.dialog

import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.app.module.camera.R
import com.app.module.camera.bean.SimpleSize
import com.app.module.camera.extensions.appConfig

class ChangeResolutionDialog(val activity: Activity, val isFrontCamera: Boolean, val photoResolutions: ArrayList<SimpleSize>,
                             val openVideoResolutions: Boolean, val callback: () -> Unit) {
    private var dialog: AlertDialog
    private val config = activity.appConfig

    init {

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_change, null).apply {
            setupPhotoResolutionPicker(this)
        }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { callback() }
            .create().apply {
                if (activity.isDestroyed || activity.isFinishing) {
                    return@apply
                }
                setView(view)
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                if (isFrontCamera) {
                    setTitle(R.string.front_camera)
                } else {
                    setTitle(R.string.back_camera)
                }
                setCanceledOnTouchOutside(true)
                show()
            }
    }

    private fun setupPhotoResolutionPicker(view: View) {
        val items = getFormattedResolutions(photoResolutions)
        var selectionIndex = if (isFrontCamera) config.frontPhotoResIndex else config.backPhotoResIndex
        selectionIndex = Math.max(selectionIndex, 0)
        val key = view.findViewById<View>(R.id.change_resolution_photo_holder)
        val value = view.findViewById<TextView>(R.id.change_resolution_photo)
        key.setOnClickListener {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("图片分辨率:")
            builder.setSingleChoiceItems(items.toTypedArray(), selectionIndex, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    selectionIndex = which
                    value.text = items[selectionIndex]
                    if (isFrontCamera) {
                        config.frontPhotoResIndex = which
                    } else {
                        config.backPhotoResIndex = which
                    }
                    dialog?.dismiss()
                }
            })
            builder.create().show()
        }
        value.text = items.getOrNull(selectionIndex)
    }

    private fun getFormattedResolutions(resolutions: List<SimpleSize>): ArrayList<String> {
        val items = ArrayList<String>(resolutions.size)
        val sorted = resolutions.sortedByDescending { it.width * it.height }
        sorted.forEachIndexed { index, size ->
            val megapixels = String.format("%.1f", (size.width * size.height.toFloat()) / 1000000)
            val aspectRatio = size.getAspectRatio(activity)
            items.add("${size.width} x ${size.height}  ($megapixels MP,  $aspectRatio)")
        }
        return items
    }
}
