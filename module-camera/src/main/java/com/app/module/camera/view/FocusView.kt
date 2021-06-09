package com.app.module.camera.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.util.AttributeSet
import android.view.ViewGroup
import com.app.module.camera.extensions.appConfig

class FocusView : ViewGroup {

    private val CIRCLE_RADIUS = 50f
    private val CIRCLE_DURATION = 500L

    private var mDrawCircle = false
    private var mHandler: Handler
    private var mPaint: Paint
    private var mLastCenterX = 0f
    private var mLastCenterY = 0f

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setWillNotDraw(false)
        mHandler = Handler()
        mPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = context.appConfig.primaryColor
            strokeWidth = 2f
        }
    }

    fun setStrokeColor(color: Int) {
        mPaint.color = color
    }

    fun drawFocusCircle(x: Float, y: Float) {
        mLastCenterX = x
        mLastCenterY = y
        toggleCircle(true)

        mHandler.removeCallbacksAndMessages(null)
        mHandler.postDelayed({
            toggleCircle(false)
        }, CIRCLE_DURATION)
    }

    private fun toggleCircle(show: Boolean) {
        mDrawCircle = show
        invalidate()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawCircle) {
            canvas.drawCircle(mLastCenterX, mLastCenterY, CIRCLE_RADIUS, mPaint)
        }
    }
}
