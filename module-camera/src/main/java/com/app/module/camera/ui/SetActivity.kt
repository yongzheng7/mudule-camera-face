package com.app.module.camera.ui

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.app.module.camera.R
import com.app.module.camera.extensions.appConfig
import kotlinx.android.synthetic.main.activity_set.*

class SetActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set)
    }

    override fun onResume() {
        super.onResume()
        setupSound()
        setupFocusBeforeCapture()
        setupVolumeButtonsAsShutter()
        setupTurnFlashOffAtStartup()
        setupFlipPhotos()
        setupKeepSettingsVisible()
        setupAlwaysOpenBackCamera()
        setupSavePhotosFolder()
        setupPhotoQuality()
        invalidateOptionsMenu()
    }

    private fun setupSound() {
        settings_sound.isChecked = appConfig.isSoundEnabled
        settings_sound_holder.setOnClickListener {
            settings_sound.toggle()
            appConfig.isSoundEnabled = settings_sound.isChecked
        }
    }

    private fun setupFocusBeforeCapture() {
        settings_focus_before_capture.isChecked = appConfig.focusBeforeCapture
        settings_focus_before_capture_holder.setOnClickListener {
            settings_focus_before_capture.toggle()
            appConfig.focusBeforeCapture = settings_focus_before_capture.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() {
        settings_volume_buttons_as_shutter.isChecked = appConfig.volumeButtonsAsShutter
        settings_volume_buttons_as_shutter_holder.setOnClickListener {
            settings_volume_buttons_as_shutter.toggle()
            appConfig.volumeButtonsAsShutter = settings_volume_buttons_as_shutter.isChecked
        }
    }

    private fun setupTurnFlashOffAtStartup() {
        settings_turn_flash_off_at_startup.isChecked = appConfig.turnFlashOffAtStartup
        settings_turn_flash_off_at_startup_holder.setOnClickListener {
            settings_turn_flash_off_at_startup.toggle()
            appConfig.turnFlashOffAtStartup = settings_turn_flash_off_at_startup.isChecked
        }
    }

    private fun setupFlipPhotos() {
        settings_flip_photos.isChecked = appConfig.flipPhotos
        settings_flip_photos_holder.setOnClickListener {
            settings_flip_photos.toggle()
            appConfig.flipPhotos = settings_flip_photos.isChecked
        }
    }

    private fun setupKeepSettingsVisible() {
        settings_keep_settings_visible.isChecked = appConfig.keepSettingsVisible
        settings_keep_settings_visible_holder.setOnClickListener {
            settings_keep_settings_visible.toggle()
            appConfig.keepSettingsVisible = settings_keep_settings_visible.isChecked
        }
    }

    private fun setupAlwaysOpenBackCamera() {
        settings_always_open_back_camera.isChecked = appConfig.alwaysOpenBackCamera
        settings_always_open_back_camera_holder.setOnClickListener {
            settings_always_open_back_camera.toggle()
            appConfig.alwaysOpenBackCamera = settings_always_open_back_camera.isChecked
        }
    }

    private fun setupSavePhotosFolder() {
        settings_save_photos.text = appConfig.savePhotosFolder
        settings_save_photos_holder.setOnClickListener {
            // TODO 
        }
    }

    private fun setupPhotoQuality() {
        settings_photo_quality.text = "${appConfig.photoQuality}%"
        settings_photo_quality_holder.setOnClickListener {
            val itemsString = arrayListOf<String>()
            for (idx in 0..100) {
                itemsString.add("$idx%")
            }
            val builder = AlertDialog.Builder(this@SetActivity)
            builder.setTitle("分辨率:")
            builder.setSingleChoiceItems(itemsString.toTypedArray(), appConfig.photoQuality, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    if(which==0) return
                    appConfig.photoQuality = which
                    settings_photo_quality.text = "${appConfig.photoQuality}%"
                    dialog?.dismiss()
                }
            })
            builder.create().show()
        }
    }
}
