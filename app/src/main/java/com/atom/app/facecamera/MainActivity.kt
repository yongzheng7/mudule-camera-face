package com.atom.app.facecamera

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.app.module.camera.ui.CameraActivity

class MainActivity : AppCompatActivity() {

    private var image_url: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.face).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(CameraActivity.STATE_SINGLE, true)
            intent.putExtra(CameraActivity.AUTO_IMAGE_CAPTURE, true)
            intent.putExtra(CameraActivity.AUTO_IMAGE_FACE, true)
            intent.putExtra(CameraActivity.AUTO_IMAGE_TIME, 3)
            startActivityForResult(intent, 1024)
        }
    }

    override fun onResume() {
        super.onResume()
        if(image_url!= null){
            Drawable.createFromPath(image_url!!.path)?.also {
                findViewById<ImageView>(R.id.face).setImageDrawable(it)
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == 1024 && resultCode == Activity.RESULT_OK) {
            if (data != null) image_url = data.data
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}