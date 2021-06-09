package com.app.module.camera.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import java.util.*

class FaceView: View {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val callbacks: MutableList<DrawCallback> = LinkedList()

    fun addCallback(callback: DrawCallback) {
        callbacks.add(callback)
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
    }


    interface DrawCallback {
        fun drawCallback(canvas: Canvas?)
    }
}
