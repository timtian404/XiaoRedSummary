package com.xiaoredsummary.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.xiaoredsummary.service.OverlayService

class MediaProjectionRequestActivity : Activity() {

    companion object {
        private const val REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                OverlayService.mediaProjectionResultCode = resultCode
                OverlayService.mediaProjectionData = data

                val serviceIntent = Intent(this, OverlayService::class.java)
                startForegroundService(serviceIntent)
            }
        }
        finish()
    }
}
