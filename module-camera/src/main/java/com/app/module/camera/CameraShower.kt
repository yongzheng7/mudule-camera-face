package com.app.module.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.*
import android.widget.Toast
import com.app.module.camera.bean.FocusArea
import com.app.module.camera.bean.SimpleSize
import com.app.module.camera.callback.PhotoProcessor
import com.app.module.camera.dialog.ChangeResolutionDialog
import com.app.module.camera.encoder.*
import com.app.module.camera.extensions.*
import com.app.module.camera.ui.CameraActivity
import com.app.module.camera.utils.CameraUtil
import java.io.File
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraShower : View.OnTouchListener {

    private val FOCUS_TAG = "focus_tag"
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080

    private lateinit var mActivity: CameraActivity
    private var mSensorOrientation = 0
    private var mRotationAtCapture = 0
    private var mZoomLevel = 1f
    private var mZoomFingerSpacing = 0
    private var mMaxZoomLevel = 1f
    private var mLastFocusX = 0f
    private var mLastFocusY = 0f
    private var mIsFlashSupported = true
    private var mIsZoomSupported = true
    private var mIsFocusSupported = true
    private var mUseFrontCamera = false
    private var mCameraId = ""
    private var mCameraState = STATE_INIT
    private var mFlashlightState = FLASH_OFF

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private var mImageReader: ImageReader? = null
    private var mPreviewSize: Size = Size(1280, 720)
    private var mTargetUri: Uri? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraCharacteristics: CameraCharacteristics? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private val mCameraToPreviewMatrix = Matrix()
    private val mPreviewToCameraMatrix = Matrix()
    private val mCameraOpenCloseLock = Semaphore(1)
    private val mMediaActionSound = MediaActionSound()
    private var mZoomRect: Rect? = null

    private val mImagePreviewCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            mCaptureSession = cameraCaptureSession
            try {
                mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getFrameRange())
                mPreviewRequestBuilder?.apply {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    setFlashAndExposure(this)
                    mPreviewRequest = build()
                }
                cameraCaptureSession.setRepeatingRequest(mPreviewRequest!!, mImageCaptureCallback, mBackgroundHandler)
                mCameraState = STATE_PREVIEW
            } catch (e: Exception) {
            }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            try {
                val surfaceList = mutableListOf<Surface>()
                closeCaptureSession()
                val texture = surface
                texture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
                surfaceList.add(Surface(texture))
                if (mIsFace) {
                    mFaceReader?.also {
                        surfaceList.add(it.surface)
                    }
                }
                mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW)
                    surfaceList.forEach {
                        addTarget(it)
                    }
                }
                mImageReader?.also {
                    surfaceList.add(it.surface)
                }
                cameraDevice.createCaptureSession(surfaceList, mImagePreviewCallback, null)
            } catch (e: Exception) {
            }
            mActivity.setIsCameraAvailable(true)
        }
        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            mActivity.setIsCameraAvailable(false)
        }
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            mActivity.setIsCameraAvailable(false)
        }
    }
    private val mImageCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mCameraState) {
                STATE_WAITING_LOCK -> {
                    val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (autoFocusState == null) {
                        captureStillPicture()
                    } else if (autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || autoFocusState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mCameraState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            process(result)
        }
    }
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        try {
            val image = reader.acquireNextImage()
            val bytes = CameraUtil.JPEG_data(image)
            image.close()
            PhotoProcessor(mActivity, mTargetUri, mRotationAtCapture, mSensorOrientation, mUseFrontCamera).execute(bytes)
        } catch (e: Exception) {
        }
    }

    private val gestureDetector: GestureDetector

    private var mIsFace = false
    private var mFaceReader: ImageReader? = null
    private val faceAvailableListener = object : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            try {
                reader ?: return
                val image = reader.acquireLatestImage()
                val bytes = CameraUtil.YUV_420_888_data(image);
                val w = image.width
                val h = image.height
                image.close()
                mActivity.onPreviewFrame(bytes, w, h, mSensorOrientation, mUseFrontCamera)
            } catch (e: Exception) {

            }
        }
    }

    constructor(activity: CameraActivity, isFace: Boolean = false) {
        this.mActivity = activity
        this.mIsFace = isFace
        if (isFace) {
            EncoderBus.GetInstance().Registe(BitmapEncoder(activity))
            EncoderBus.GetInstance().Registe(CircleEncoder(activity))
            EncoderBus.GetInstance().Registe(RectEncoder(activity))
            EncoderBus.GetInstance().Registe(EmptyEncoder(activity))
        }
        val cameraCharacteristics = try {
            getCameraCharacteristics(activity.appConfig.lastUsedCamera)
        } catch (e: Exception) {
            null
        }
        val isFrontCamera = cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING).toString() == activity.frontCameraId.toString()
        mUseFrontCamera = !activity.appConfig.alwaysOpenBackCamera && isFrontCamera
        mMediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING)
        mMediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING)
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        this.gestureDetector = GestureDetector(mActivity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (e != null && mIsFocusSupported && mCaptureSession != null) {
                    focusArea(e.rawX, e.rawY, true)
                }
                return true
            }
        })
    }

    private lateinit var surface: SurfaceTexture

    fun setSurfaceAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = surface
        openCamera(width, height)
    }

    fun onResumed(surfaceView: TextureView) {
        HandlerThread("SimpleCameraBackground").also {
            it.start()
            mBackgroundHandler = Handler(it.looper)
            mBackgroundThread = it
        }
        if (surfaceView.isAvailable) {
            openCamera(mPreviewSize.height, mPreviewSize.width)
        }
    }

    fun onPaused() {
        closeCamera()
        try {
            mBackgroundThread?.quitSafely()
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }

    // 根据大小打开指定的camera
    @SuppressLint("MissingPermission")
    fun openCamera(width: Int, height: Int) {
        try {
            mActivity.runOnUiThread {
                setupCameraOutputs(width, height)
                if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw RuntimeException("Time out waiting to lock camera opening.")
                }
                getCameraManager().openCamera(mCameraId, cameraStateCallback, mBackgroundHandler)
            }
        } catch (e: Exception) {
        }
    }

    fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            mCaptureSession?.close()
            mCaptureSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            mImageReader?.close()
            mImageReader = null
        } catch (e: Exception) {
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private fun closeCaptureSession() {
        mCaptureSession?.close()
        mCaptureSession = null
    }

    private fun handleZoom(event: MotionEvent) {
        val sensorRect = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return
        val currentFingerSpacing = getFingerSpacing(event)

        var delta = 0.05f
        if (mZoomFingerSpacing != 0) {
            if (currentFingerSpacing > mZoomFingerSpacing) {
                if (mMaxZoomLevel - mZoomLevel <= delta) {
                    delta = mMaxZoomLevel - mZoomLevel
                }
                mZoomLevel += delta
            } else if (currentFingerSpacing < mZoomFingerSpacing) {
                if (mZoomLevel - delta < 1f) {
                    delta = mZoomLevel - 1f
                }
                mZoomLevel -= delta
            }

            val ratio = 1 / mZoomLevel
            val croppedWidth = sensorRect.width() - Math.round(sensorRect.width() * ratio)
            val croppedHeight = sensorRect.height() - Math.round(sensorRect.height() * ratio)
            mZoomRect = Rect(croppedWidth / 2, croppedHeight / 2, sensorRect.width() - croppedWidth / 2, sensorRect.height() - croppedHeight / 2)
            mPreviewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mZoomRect)
            mPreviewRequest = mPreviewRequestBuilder!!.build()
            mCaptureSession!!.setRepeatingRequest(mPreviewRequest!!, mImageCaptureCallback, mBackgroundHandler)
        }
        mZoomFingerSpacing = currentFingerSpacing.toInt()
    }

    private fun getFingerSpacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun getCurrentResolution(): SimpleSize {
        val configMap = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return SimpleSize(0, 0)
        val resIndex = if (mUseFrontCamera) {
            mActivity.appConfig.frontPhotoResIndex
        } else {
            mActivity.appConfig.backPhotoResIndex
        }

        val outputSizes = getAvailableImageSizes(configMap)
        val size = outputSizes.sortedByDescending { it.width * it.height }[resIndex]
        return SimpleSize(size.width, size.height)
    }

    private fun setupCameraOutputs(width: Int, height: Int) {
        val manager = getCameraManager()
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue
                if ((mUseFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK)
                        || (!mUseFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT)) {
                    continue
                }
                mCameraId = cameraId
                mCameraCharacteristics = characteristics
                mMaxZoomLevel = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                        ?: return
                mZoomLevel = 1f
                mActivity.appConfig.lastUsedCamera = mCameraId
                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                val currentResolution: SimpleSize = getCurrentResolution()
                mImageReader = ImageReader.newInstance(currentResolution.width, currentResolution.height, ImageFormat.JPEG, 2)
                mImageReader?.setOnImageAvailableListener(imageAvailableListener, null)
                val displaySize = getRealDisplaySize()
                var maxPreviewWidth = displaySize.width
                var maxPreviewHeight = displaySize.height
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width

                    val tmpWidth = maxPreviewWidth
                    maxPreviewWidth = maxPreviewHeight
                    maxPreviewHeight = tmpWidth
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                val outputSizes = configMap.getOutputSizes(SurfaceTexture::class.java)

                mPreviewSize = chooseOptimalPreviewSize(outputSizes, rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, currentResolution)

                mActivity.setAspectRatio(mPreviewSize.height, mPreviewSize.width)
                if (mIsFace) {
                    mFaceReader = ImageReader.newInstance(mPreviewSize.height, mPreviewSize.width, ImageFormat.YUV_420_888, 2)
                    mFaceReader?.setOnImageAvailableListener(faceAvailableListener, null)
                }
                characteristics.apply {
                    mIsFlashSupported = get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    mIsZoomSupported = get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 0f > 0f
                    mIsFocusSupported = get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)!!.size > 1
                }
                mActivity.setFlashAvailable(mIsFlashSupported)
                mActivity.updateCameraIcon(mUseFrontCamera)
                return
            }
        } catch (e: Exception) {
        }
    }

    private fun chooseOptimalPreviewSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int, maxWidth: Int, maxHeight: Int, selectedResolution: SimpleSize): Size {
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        val bigEnoughIncorrectAR = ArrayList<Size>()
        val notBigEnoughIncorrectAR = ArrayList<Size>()
        val width = selectedResolution.width
        val height = selectedResolution.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight) {
                if (option.height == option.width * height / width) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                } else {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnoughIncorrectAR.add(option)
                    } else {
                        notBigEnoughIncorrectAR.add(option)
                    }
                }
            }
        }

        return when {
            bigEnough.isNotEmpty() -> selectMin(bigEnough) { it.width * it.height }!!
            notBigEnough.isNotEmpty() -> selectMax(notBigEnough) { it.width * it.height }!!
            bigEnoughIncorrectAR.isNotEmpty() -> selectMin(bigEnoughIncorrectAR) { it.width * it.height }!!
            notBigEnoughIncorrectAR.isNotEmpty() -> selectMax(notBigEnoughIncorrectAR) { it.width * it.height }!!
            else -> selectedResolution.toSize()
        }
    }

    private fun selectMin(list: ArrayList<Size>, selector: (Size) -> Int): Size? {
        if (list.isEmpty()) return null
        var minElem = list[0]
        val lastIndex = list.lastIndex
        if (lastIndex == 0) return minElem
        var minValue = selector(minElem)
        for (i in 1..lastIndex) {
            val e = list[i]
            val v = selector(e)
            if (minValue > v) {
                minElem = e
                minValue = v
            }
        }
        return minElem
    }

    private fun selectMax(list: ArrayList<Size>, selector: (Size) -> Int): Size? {
        val iterator = list.iterator()
        if (!iterator.hasNext()) return null
        var maxElem = iterator.next()
        if (!iterator.hasNext()) return maxElem
        var maxValue = selector(maxElem)
        do {
            val e = iterator.next()
            val v = selector(e)
            if (maxValue < v) {
                maxElem = e
                maxValue = v
            }
        } while (iterator.hasNext())
        return maxElem
    }

    private fun getRealDisplaySize(): SimpleSize {
        val metrics = DisplayMetrics()
        mActivity.windowManager.defaultDisplay.getRealMetrics(metrics)
        return SimpleSize(metrics.widthPixels, metrics.heightPixels)
    }

    private fun getFrameRange(): Range<Int> {
        val ranges = mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: return Range<Int>(0, 1)
        var currRangeSize = -1
        var currMinRange = 0
        var result: Range<Int>? = null
        for (range in ranges) {
            val diff = range.upper - range.lower
            if (diff > currRangeSize || (diff == currRangeSize && range.lower > currMinRange)) {
                currRangeSize = diff
                currMinRange = range.lower
                result = range
            }
        }

        return result!!
    }

    private fun runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            mCameraState = STATE_WAITING_PRECAPTURE
            mCaptureSession?.capture(mPreviewRequestBuilder!!.build(), mImageCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

    private fun captureStillPicture() {
        try {
            mCameraDevice?.also { camera ->
                if (mActivity.appConfig.isSoundEnabled) {
                    mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                }
                mCameraState = STATE_PICTURE_TAKEN
                mRotationAtCapture = mActivity.mLastHandledOrientation
                val jpegOrientation = (mSensorOrientation + compensateDeviceRotation(mRotationAtCapture)) % 360
                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    setFlashAndExposure(this)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                    set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getFrameRange())
                    if (mZoomRect != null) {
                        set(CaptureRequest.SCALER_CROP_REGION, mZoomRect)
                    }
                    mImageReader?.surface?.also {
                        addTarget(it)
                    }
                }
                val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        unlockFocus()
                        mActivity.toggleBottomButtons(false)
                    }

                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        super.onCaptureFailed(session, request, failure)
                        mActivity.toggleBottomButtons(false)
                    }
                }
                mCaptureSession?.apply {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                        stopRepeating()
                        abortCaptures()
                    }
                    capture(captureBuilder.build(), captureCallback, null)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun focusArea(x: Float, y: Float, drawCircle: Boolean) {
        mLastFocusX = x
        mLastFocusY = y
        if (drawCircle) {
            mActivity.drawFocusCircle(x, y)
        }
        val captureCallbackHandler = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                if (request.tag == FOCUS_TAG) {
                    mCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder!!.build(), mImageCaptureCallback, mBackgroundHandler)
                }
            }
        }

        try {
            mCaptureSession!!.stopRepeating()

            mPreviewRequestBuilder!!.apply {
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                mCaptureSession!!.capture(build(), mImageCaptureCallback, mBackgroundHandler)
                if (mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)!! >= 1) {
                    val focusArea = getFocusArea(x, y)
                    val sensorRect = mCameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
                    val meteringRect = convertAreaToMeteringRectangle(sensorRect, focusArea)
                    set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
                }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                setTag(FOCUS_TAG)
                mCaptureSession!!.capture(build(), captureCallbackHandler, mBackgroundHandler)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun convertAreaToMeteringRectangle(sensorRect: Rect, focusArea: FocusArea): MeteringRectangle {
        val camera2Rect = convertRectToCamera2(sensorRect, focusArea.rect)
        return MeteringRectangle(camera2Rect, focusArea.weight)
    }

    private fun convertRectToCamera2(cropRect: Rect, rect: Rect): Rect {
        val leftF = (rect.left + 1000) / 2000f
        val topF = (rect.top + 1000) / 2000f
        val rightF = (rect.right + 1000) / 2000f
        val bottomF = (rect.bottom + 1000) / 2000f
        var left = (cropRect.left + leftF * (cropRect.width() - 1)).toInt()
        var right = (cropRect.left + rightF * (cropRect.width() - 1)).toInt()
        var top = (cropRect.top + topF * (cropRect.height() - 1)).toInt()
        var bottom = (cropRect.top + bottomF * (cropRect.height() - 1)).toInt()
        left = Math.max(left, cropRect.left)
        right = Math.max(right, cropRect.left)
        top = Math.max(top, cropRect.top)
        bottom = Math.max(bottom, cropRect.top)
        left = Math.min(left, cropRect.right)
        right = Math.min(right, cropRect.right)
        top = Math.min(top, cropRect.bottom)
        bottom = Math.min(bottom, cropRect.bottom)

        return Rect(left, top, right, bottom)
    }

    private fun getFocusArea(x: Float, y: Float): FocusArea {
        val coords = floatArrayOf(x, y)
        calculateCameraToPreviewMatrix()
        mPreviewToCameraMatrix.mapPoints(coords)
        val focusX = coords[0].toInt()
        val focusY = coords[1].toInt()

        val focusSize = 50
        val rect = Rect()
        rect.left = focusX - focusSize
        rect.right = focusX + focusSize
        rect.top = focusY - focusSize
        rect.bottom = focusY + focusSize

        if (rect.left < -1000) {
            rect.left = -1000
            rect.right = rect.left + 2 * focusSize
        } else if (rect.right > 1000) {
            rect.right = 1000
            rect.left = rect.right - 2 * focusSize
        }

        if (rect.top < -1000) {
            rect.top = -1000
            rect.bottom = rect.top + 2 * focusSize
        } else if (rect.bottom > 1000) {
            rect.bottom = 1000
            rect.top = rect.bottom - 2 * focusSize
        }

        return FocusArea(rect, MeteringRectangle.METERING_WEIGHT_MAX)
    }

    private fun calculateCameraToPreviewMatrix() {
        val yScale = if (mUseFrontCamera) -1 else 1
        mCameraToPreviewMatrix.apply {
            reset()
            setScale(1f, yScale.toFloat())
            postRotate(mSensorOrientation.toFloat())
            val width = mPreviewSize.height
            val height = mPreviewSize.width
            postScale(width / 2000f, height / 2000f)
            postTranslate(width / 2f, height / 2f)
            invert(mPreviewToCameraMatrix)
        }
    }

    private fun lockFocus() {
        try {
            mPreviewRequestBuilder!!.apply {
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                mCameraState = STATE_WAITING_LOCK
                mCaptureSession?.capture(build(), mImageCaptureCallback, mBackgroundHandler)
            }
        } catch (e: CameraAccessException) {
            mCameraState = STATE_PREVIEW
        }
    }

    private fun unlockFocus() {
        try {
            mPreviewRequestBuilder!!.apply {
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                mCaptureSession?.capture(build(), mImageCaptureCallback, mBackgroundHandler)
            }
            mCameraState = STATE_PREVIEW
            mCaptureSession?.setRepeatingRequest(mPreviewRequest!!, mImageCaptureCallback, mBackgroundHandler)

            if (mLastFocusX != 0f && mLastFocusY != 0f) {
                focusArea(mLastFocusX, mLastFocusY, false)
            }
        } catch (e: Exception) {
        } finally {
            mCameraState = STATE_PREVIEW
        }
    }

    private fun setFlashAndExposure(builder: CaptureRequest.Builder) {
        val aeMode = if (mFlashlightState == FLASH_AUTO) CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH else CameraMetadata.CONTROL_AE_MODE_ON
        builder.apply {
            set(CaptureRequest.FLASH_MODE, getFlashlightMode())
            set(CaptureRequest.CONTROL_AE_MODE, aeMode)
        }
    }

    private fun getCameraManager() = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun getCameraCharacteristics(cameraId: String = mCameraId) = getCameraManager().getCameraCharacteristics(cameraId)

    private fun getFlashlightMode() = when (mFlashlightState) {
        FLASH_ON -> CameraMetadata.FLASH_MODE_TORCH
        else -> CameraMetadata.FLASH_MODE_OFF
    }

    private fun getAvailableImageSizes(configMap: StreamConfigurationMap, int: Int = ImageFormat.JPEG) = configMap.getOutputSizes(int)

    private fun shouldLockFocus() = mIsFocusSupported && mActivity.appConfig.focusBeforeCapture

    fun setTargetUri(uri: Uri) {
        mTargetUri = uri
    }

    fun setFlashlightState(state: Int) {
        mFlashlightState = state
        checkFlashlight()
    }

    fun getCameraState() = mCameraState

    fun showChangeResolutionDialog() {
        openResolutionsDialog(false)
    }

    private fun openResolutionsDialog(openVideoResolutions: Boolean) {
        val oldResolution = getCurrentResolution()
        val configMap = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return

        val photoResolutions = getAvailableImageSizes(configMap).map { SimpleSize(it.width, it.height) } as ArrayList

        ChangeResolutionDialog(mActivity, mUseFrontCamera, photoResolutions, openVideoResolutions) {
            if (oldResolution != getCurrentResolution()) {
                closeCamera()
                openCamera(mPreviewSize.height, mPreviewSize.width)
            }
        }
    }

    fun toggleFrontBackCamera() {
        mUseFrontCamera = !mUseFrontCamera
        closeCamera()
        openCamera(mPreviewSize.height, mPreviewSize.width)
    }

    fun toggleFlashlight() {
        val newState = ++mFlashlightState % 3
        setFlashlightState(newState)
    }

    fun tryTakePicture() {
        if (mCameraState != STATE_PREVIEW) {
            return
        }
        if (shouldLockFocus()) {
            lockFocus()
        } else {
            captureStillPicture()
        }
    }

    fun checkFlashlight() {
        if ((mCameraState == STATE_PREVIEW || mCameraState == STATE_RECORDING) && mIsFlashSupported) {
            try {
                setFlashAndExposure(mPreviewRequestBuilder!!)
                mPreviewRequest = mPreviewRequestBuilder!!.build()
                mCaptureSession?.setRepeatingRequest(mPreviewRequest!!, mImageCaptureCallback, mBackgroundHandler)
                mActivity.updateFlashlightState(mFlashlightState)
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        event ?: return false
        if (mIsZoomSupported && event.pointerCount > 1 && mCaptureSession != null) {
            try {
                handleZoom(event)
            } catch (e: Exception) {
            }
        }
        return true
    }
}
