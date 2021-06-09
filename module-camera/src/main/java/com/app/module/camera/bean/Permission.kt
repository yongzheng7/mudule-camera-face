package com.simplemobiletools.camera.extensions

import android.app.Activity
import androidx.core.app.ActivityCompat
import com.app.module.camera.extensions.*

class Permission(val GENERIC_PERM_HANDLER: Int = 100) {
    var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false

    fun handlePermission(ctx : Activity, permissionId: Int, callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (ctx.hasPermission(permissionId)) {
            callback(true)
        } else {
            isAskingPermissions = true
            actionOnPermission = callback
            ActivityCompat.requestPermissions(ctx, arrayOf(ctx.getPermissionString(permissionId)), GENERIC_PERM_HANDLER)
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        isAskingPermissions = false
        if (requestCode == GENERIC_PERM_HANDLER && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }
}
