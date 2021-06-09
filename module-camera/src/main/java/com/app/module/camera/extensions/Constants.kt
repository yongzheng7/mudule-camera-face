package com.app.module.camera.extensions

const val ORIENT_PORTRAIT = 0
const val ORIENT_LANDSCAPE_LEFT = 1
const val ORIENT_LANDSCAPE_RIGHT = 2

// shared preferences
const val PREFS_KEY = "Camera_Prefs"
const val SAVE_PHOTOS = "save_photos"
const val SOUND = "sound"
const val FOCUS_BEFORE_CAPTURE = "focus_before_capture_2"
const val VOLUME_BUTTONS_AS_SHUTTER = "volume_buttons_as_shutter"
const val TURN_FLASH_OFF_AT_STARTUP = "turn_flash_off_at_startup"
const val FLIP_PHOTOS = "flip_photos"
const val LAST_USED_CAMERA = "last_used_camera_2"
const val FLASHLIGHT_STATE = "flashlight_state"
const val INIT_PHOTO_MODE = "init_photo_mode"
const val BACK_PHOTO_RESOLUTION_INDEX = "back_photo_resolution_index_2"
const val BACK_VIDEO_RESOLUTION_INDEX = "back_video_resolution_index_2"
const val FRONT_PHOTO_RESOLUTION_INDEX = "front_photo_resolution_index_2"
const val FRONT_VIDEO_RESOLUTION_INDEX = "front_video_resolution_index_2"
const val KEEP_SETTINGS_VISIBLE = "keep_settings_visible"
const val ALWAYS_OPEN_BACK_CAMERA = "always_open_back_camera"
const val SAVE_PHOTO_METADATA = "save_photo_metadata"
const val PHOTO_QUALITY = "photo_quality"

const val FLASH_OFF = 0
const val FLASH_ON = 1
const val FLASH_AUTO = 2

// camera states
const val STATE_INIT = 0
const val STATE_PREVIEW = 1
const val STATE_PICTURE_TAKEN = 2
const val STATE_WAITING_LOCK = 3
const val STATE_WAITING_PRECAPTURE = 4
const val STATE_WAITING_NON_PRECAPTURE = 5
const val STATE_STARTING_RECORDING = 6
const val STATE_STOPING_RECORDING = 7
const val STATE_RECORDING = 8

// permissions
const val PERMISSION_READ_STORAGE = 1
const val PERMISSION_WRITE_STORAGE = 2
const val PERMISSION_CAMERA = 3
const val PERMISSION_RECORD_AUDIO = 4
const val PERMISSION_READ_CONTACTS = 5
const val PERMISSION_WRITE_CONTACTS = 6
const val PERMISSION_READ_CALENDAR = 7
const val PERMISSION_WRITE_CALENDAR = 8
const val PERMISSION_CALL_PHONE = 9
const val PERMISSION_READ_CALL_LOG = 10
const val PERMISSION_WRITE_CALL_LOG = 11
const val PERMISSION_GET_ACCOUNTS = 12
const val PERMISSION_READ_SMS = 13
const val PERMISSION_SEND_SMS = 14
const val PERMISSION_READ_PHONE_STATE = 15


const val PRIMARY_COLOR = "primary_color_2"

fun compensateDeviceRotation(orientation: Int) = when (orientation) {
    ORIENT_LANDSCAPE_LEFT -> 270
    ORIENT_LANDSCAPE_RIGHT -> 90
    else -> 0
}
