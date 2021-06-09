package com.app.module.camera.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import com.app.module.camera.R
import com.app.module.camera.encoder.EncoderBus
import com.app.module.camera.extensions.*
import com.app.module.camera.utils.SensorEventUtil
import com.app.module.camera.CameraShower
import com.app.module.camera.view.FaceView
import com.simplemobiletools.camera.extensions.Permission
import com.tenginekit.AndroidConfig
import com.tenginekit.KitCore
import com.tenginekit.face.Face
import kotlinx.android.synthetic.main.activity_camera.*

class CameraActivity : AppCompatActivity() {
    companion object {
        const val AUTO_IMAGE_CAPTURE = "AUTO_IMAGE_CAPTURE"
        const val AUTO_IMAGE_FACE = "AUTO_IMAGE_FACE"
        const val AUTO_IMAGE_TIME = "AUTO_IMAGE_TIME"

        const val EXTRA_OUTPUT = "Extra_OUTPUT"

        const val STATE_SINGLE = "STATE_SINGLE"
    }

    private val FADE_DELAY = 5000L
    private val CAPTURE_ANIMATION_DURATION = 100L

    private val permissio = Permission()

    private lateinit var mOrientationEventListener: OrientationEventListener

    private var mPreview: CameraShower? = null
    private var mPreviewUri: Uri? = null

    private var mIsCameraAvailable = false
    private var mIsHardwareShutterHandled = false
    var mLastHandledOrientation = 0

    private var workHandler: Handler? = null
    private var workHandlerThread: HandlerThread? = null
    private var mTimerHandler: Handler = Handler(Looper.getMainLooper())
    private var mFadeHandler: Handler = Handler(Looper.getMainLooper())
    private var autoCaptureHandler: Handler = Handler(Looper.getMainLooper())

    private var sensorEventUtil: SensorEventUtil? = null
    private var isProcessingFrame = false
    @Volatile
    private var isFaceShow = false

    private val autoCaptureImage = Runnable {
        shutterPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_camera)
        initVariables()
        tryInitCamera()
        supportActionBar?.hide()
        setupOrientationEventListener()
    }

    override fun onResume() {
        super.onResume()
        workHandlerThread = HandlerThread("inference").also {
            it.start()
            workHandler = Handler(it.looper)
        }
        if (hasStorageAndCameraPermissions()) {
            mPreview?.onResumed(auto_texture_view)
            resumeCameraItems()
            scheduleFadeOut()
            focus_view.setStrokeColor(appConfig.primaryColor)
            toggleBottomButtons(false)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (hasStorageAndCameraPermissions()) {
            mOrientationEventListener.enable()
        }
    }

    override fun onPause() {
        try {
            workHandlerThread?.quitSafely()
            workHandlerThread?.join()
            workHandlerThread = null
            workHandler = null
        } catch (e: InterruptedException) {

        }
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasStorageAndCameraPermissions()) {
            return
        }
        mFadeHandler.removeCallbacksAndMessages(null)
        hideTimer()
        mOrientationEventListener.disable()
        if (mPreview?.getCameraState() == STATE_PICTURE_TAKEN) {
            toast(R.string.photo_not_saved)
        }
        runOnWorkTHread {
            mPreview?.onPaused()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mPreview = null
        KitCore.release()
    }

    private fun initVariables() {
        mIsCameraAvailable = false
        mIsHardwareShutterHandled = false
        mLastHandledOrientation = 0
        if (appConfig.alwaysOpenBackCamera) {
            appConfig.lastUsedCamera = backCameraId.toString()
        }
        mPreview = CameraShower(this,  isAutoCaptureImageByFace())
    }

    private fun tryInitCamera() {
        permissio.handlePermission(this, PERMISSION_CAMERA) { camera ->
            if (camera) {
                permissio.handlePermission(this, PERMISSION_WRITE_STORAGE) { storage ->
                    if (storage) {
                        initializeCamera()
                    } else {
                        toast(R.string.no_storage_permissions)
                        finish()
                    }
                }
            } else {
                toast(R.string.no_camera_permissions)
                finish()
            }
        }
    }

    private fun checkImageCaptureIntent() {
        val output = intent.extras?.get(EXTRA_OUTPUT)
        if (output != null && output is Uri) {
            mPreview?.setTargetUri(output)
        }
    }

    private fun initializeCamera() {
        initButtons()
        auto_texture_view.surfaceTextureListener =  object : TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                mPreview?.closeCamera()
                mPreview?.setSurfaceAvailable(surface ,width, height)
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                mPreview?.setSurfaceAvailable(surface ,width, height)
            }
        }
        checkImageCaptureIntent()
        val imageDrawable = if (appConfig.lastUsedCamera == backCameraId.toString()) R.drawable.ic_camera_front_vector else R.drawable.ic_camera_rear_vector
        toggle_camera.setImageResource(imageDrawable)
        val initialFlashlightState = if (appConfig.turnFlashOffAtStartup) FLASH_OFF else appConfig.flashlightState
        mPreview?.setFlashlightState(initialFlashlightState)
        updateFlashlightState(initialFlashlightState)
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener {
            if (checkCameraAvailable()) {
                mPreview?.toggleFrontBackCamera()
            }
        }
        toggle_flash.setOnClickListener {
            if (checkCameraAvailable()) {
                mPreview?.toggleFlashlight()
            }
        }
        shutter.setOnClickListener { shutterPressed() }
        settings.setOnClickListener {
            if (settings.alpha == 1f) {
                val intent = Intent(applicationContext, SetActivity::class.java)
                startActivity(intent)
            } else {
                fadeInButtons()
            }
        }
        change_resolution.setOnClickListener { mPreview?.showChangeResolutionDialog() }
        shutter.setImageResource(R.drawable.ic_shutter_vector)
        focus_view.setOnTouchListener(mPreview)
    }

    fun updateFlashlightState(state: Int) {
        appConfig.flashlightState = state
        val flashDrawable = when (state) {
            FLASH_OFF -> R.drawable.ic_flash_off_vector
            FLASH_ON -> R.drawable.ic_flash_on_vector
            else -> R.drawable.ic_flash_auto_vector
        }
        toggle_flash.setImageResource(flashDrawable)
    }

    fun updateCameraIcon(isUsingFrontCamera: Boolean) {
        toggle_camera.setImageResource(if (isUsingFrontCamera) R.drawable.ic_camera_rear_vector else R.drawable.ic_camera_front_vector)
    }

    private fun shutterPressed() {
        if (checkCameraAvailable()) {
            handleShutter()
        }
    }

    private fun handleShutter() {
        toggleBottomButtons(true)
        mPreview?.tryTakePicture()
        capture_black_screen.animate().alpha(0.8f).setDuration(CAPTURE_ANIMATION_DURATION).withEndAction {
            capture_black_screen.animate().alpha(0f).setDuration(CAPTURE_ANIMATION_DURATION).start()
        }.start()
    }

    fun toggleBottomButtons(hide: Boolean) {
        runOnUiThread {
            val alpha = if (hide) 0f else 1f
            shutter.animate().alpha(alpha).start()
            toggle_camera.animate().alpha(alpha).start()
            toggle_flash.animate().alpha(alpha).start()

            shutter.isClickable = !hide
            toggle_camera.isClickable = !hide
            toggle_flash.isClickable = !hide
        }
    }

    private fun scheduleFadeOut() {
        if (!appConfig.keepSettingsVisible) {
            mFadeHandler.postDelayed({
                fadeOutButtons()
            }, FADE_DELAY)
        }
    }

    private fun fadeOutButtons() {
        fadeAnim(settings, .5f)
        fadeAnim(change_resolution, .0f)
    }

    private fun fadeInButtons() {
        fadeAnim(settings, 1f)
        fadeAnim(change_resolution, 1f)
        scheduleFadeOut()
    }

    private fun fadeAnim(view: View, value: Float) {
        view.animate().alpha(value).start()
        view.isClickable = value != .0f
    }

    private fun hideNavigationBarIcons() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
    }

    private fun showTimer() {
        video_rec_curr_timer.beVisible()
        setupTimer()
    }

    private fun hideTimer() {
        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()
        mTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun setupTimer() {
        runOnUiThread(object : Runnable {
            private var mCurrVideoRecTimer = 0
            override fun run() {
                video_rec_curr_timer.text = mCurrVideoRecTimer++.getFormattedDuration()
                mTimerHandler.postDelayed(this, 1000L)
            }
        })
    }

    private fun resumeCameraItems() {
        showToggleCameraIfNeeded()
        hideNavigationBarIcons()
    }

    private fun showToggleCameraIfNeeded() {
        toggle_camera?.beInvisibleIf(countOfCameras() ?: 1 <= 1)
    }

    private fun hasStorageAndCameraPermissions() = hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA)

    private fun setupOrientationEventListener() {
        mOrientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (isDestroyed) {
                    mOrientationEventListener.disable()
                    return
                }

                val currOrient = when (orientation) {
                    in 75..134 -> ORIENT_LANDSCAPE_RIGHT
                    in 225..289 -> ORIENT_LANDSCAPE_LEFT
                    else -> ORIENT_PORTRAIT
                }

                if (currOrient != mLastHandledOrientation) {
                    val degrees = when (currOrient) {
                        ORIENT_LANDSCAPE_LEFT -> 90
                        ORIENT_LANDSCAPE_RIGHT -> -90
                        else -> 0
                    }

                    animateViews(degrees)
                    mLastHandledOrientation = currOrient
                }
            }
        }
    }

    private fun animateViews(degrees: Int) {
        val views = arrayOf<View>(toggle_camera, toggle_flash,  change_resolution, shutter, settings)
        for (view in views) {
            rotate(view, degrees)
        }
    }

    private fun rotate(view: View, degrees: Int) = view.animate().rotation(degrees.toFloat()).start()

    private fun checkCameraAvailable(): Boolean {
        if (!mIsCameraAvailable) {
            toast(R.string.camera_unavailable)
        }
        return mIsCameraAvailable
    }

    fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.beVisible()
        } else {
            toggle_flash.beInvisible()
            toggle_flash.setImageResource(R.drawable.ic_flash_off_vector)
            mPreview?.setFlashlightState(FLASH_OFF)
        }
    }

    fun setIsCameraAvailable(available: Boolean) {
        mIsCameraAvailable = available
    }

    fun drawFocusCircle(x: Float, y: Float) = focus_view.drawFocusCircle(x, y)

    fun mediaSaved(uri: Uri) {
        mPreviewUri = uri
        if (isCreateSingle()) {
            finish()
        }
    }

    override fun finish() {
        if (mPreviewUri != null) {
            Intent().apply {
                data = mPreviewUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
        }
        super.finish()
    }

    fun setAspectRatio(width: Int, height: Int) {
        runOnUiThread {
            auto_texture_view.setAspectRatio(width, height)
            KitCore.release()
        }
    }

    fun onPreviewFrame(data: ByteArray?, width: Int, height: Int, sensorOrientation: Int, isFrontCamera: Boolean) {
        if (isProcessingFrame || isDestroyed) {
            return
        }
        isProcessingFrame = true
        if (!KitCore.getInit()) {
            if (sensorEventUtil == null) {
                sensorEventUtil = SensorEventUtil(this)
            }
            KitCore.init(
                    this@CameraActivity.applicationContext,
                    AndroidConfig
                            .create()
                            .setCameraMode()
                            .openFunc(AndroidConfig.Func.Detect)
                            .setDefaultInputImageFormat()
                            .setInputImageSize(width, height)
                            .setOutputImageSize(auto_texture_view.width, auto_texture_view.height)
            )
            EncoderBus.GetInstance().onSetFrameConfiguration(auto_texture_view.height, auto_texture_view.width)
            facing_overlay.addCallback(object : FaceView.DrawCallback {
                override fun drawCallback(canvas: Canvas?) {
                    EncoderBus.GetInstance().onDraw(canvas)
                }
            })
        }
        workHandler?.post {
            data?.also {
                if (sensorEventUtil != null) {
                    var degrees = 0
                    when (sensorEventUtil!!.orientation) {
                        0 -> degrees = 0
                        1 -> degrees = 90
                        2 -> degrees = 270
                        3 -> degrees = 180
                    }

                    var result: Int

                    if (isFrontCamera) {
                        result = (sensorOrientation + degrees) % 360
                        result = (360 - result) % 360
                    } else {
                        result = (sensorOrientation - degrees + 360) % 360
                    }
                    KitCore.Camera.setRotation(result - 90, false, auto_texture_view.width, auto_texture_view.height)
                    val faceDetect = Face.detect(it)
                    if (faceDetect.faceCount > 0) {
                        val detectInfos = faceDetect.detectInfos
                        if (detectInfos != null && detectInfos.size > 0) {
                            val face_rect = arrayOfNulls<Rect>(detectInfos.size)
                            for (i in detectInfos.indices) {
                                face_rect[i] = detectInfos[i].asRect()
                            }
                            EncoderBus.GetInstance().onProcessResults(face_rect)
                        }
                        if(isAutoCaptureImage() && !isFaceShow){
                            isFaceShow = true
                            autoCaptureHandler.postDelayed(autoCaptureImage, isAutoCaptureImageWithTime()*1000L)
                        }
                    } else {
                        EncoderBus.GetInstance().onProcessResults(null)
                        if(isAutoCaptureImage() && isFaceShow){
                            isFaceShow = false
                            autoCaptureHandler.removeCallbacks(autoCaptureImage)
                        }
                    }
                }
            }
            if (facing_overlay != null) {
                facing_overlay.postInvalidate()
            }
            isProcessingFrame = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else if (!mIsHardwareShutterHandled && appConfig.volumeButtonsAsShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mIsHardwareShutterHandled = false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isCreateSingle() = intent?.getBooleanExtra(STATE_SINGLE, false) ?: false

    private fun isAutoCaptureImage() = intent?.getBooleanExtra(AUTO_IMAGE_CAPTURE, false) ?: false

    private fun isAutoCaptureImageByFace() = intent?.getBooleanExtra(AUTO_IMAGE_FACE, false)
            ?: false

    private fun isAutoCaptureImageWithTime() = intent?.getIntExtra(AUTO_IMAGE_TIME, -1) ?: -1

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissio.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
