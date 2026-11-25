package com.snapbutton.app

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val requestCapture = 7001
    private val requestCustomPhoto = 7002

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
        }
        layout.addView(TextView(this).apply {
            text = "Snap Button"
            textSize = 30f
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "Tap the floating button to take a screenshot. Hold it for 2.5 seconds to change the button. You can upload your own photo for a silent button with a camera click."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        })
        layout.addView(Button(this).apply { text = "UPLOAD PHOTO FOR BUTTON"; setOnClickListener { chooseCustomPhoto() } })
        layout.addView(Button(this).apply { text = "START SNAP BUTTON"; setOnClickListener { begin() } })
        val brand = ImageView(this).apply {
            contentDescription = "Think Atlas"
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(resources, R.raw.think_atlas)
                val logo = android.graphics.ImageDecoder.decodeDrawable(source)
                setImageDrawable(logo)
                (logo as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            } else {
                setImageBitmap(android.graphics.BitmapFactory.decodeResource(resources, R.raw.think_atlas))
            }
        }
        layout.addView(brand, LinearLayout.LayoutParams(-1, (42 * resources.displayMetrics.density).toInt()))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(layout) })
        if (state == null && !intent.getBooleanExtra("chooseCustomPhoto", false)) showLaunchEye()
        if (intent.getBooleanExtra("chooseCustomPhoto", false)) chooseCustomPhoto()
    }

    @Suppress("DEPRECATION")
    private fun showLaunchEye() {
        val movie = resources.openRawResource(R.raw.think_atlas).use {
            android.graphics.Movie.decodeStream(it)
        } ?: return
        val frame = findViewById<android.widget.FrameLayout>(android.R.id.content)
        val eye = object : android.view.View(this) {
            val started = android.os.SystemClock.uptimeMillis()
            override fun onDraw(canvas: android.graphics.Canvas) {
                canvas.drawColor(android.graphics.Color.BLACK)
                val scale = minOf(width.toFloat() / movie.width(), height.toFloat() / movie.height())
                canvas.save()
                canvas.translate((width - scale * movie.width()) / 2, (height - scale * movie.height()) / 2)
                canvas.scale(scale, scale)
                movie.setTime(((android.os.SystemClock.uptimeMillis() - started) % movie.duration().coerceAtLeast(1)).toInt())
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
                postInvalidateOnAnimation()
            }
        }
        eye.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        eye.contentDescription = "Think Atlas"
        eye.isClickable = true
        frame.addView(eye, android.widget.FrameLayout.LayoutParams(-1, -1))
        eye.postDelayed({ frame.removeView(eye) }, 4000)
    }

    private fun chooseCustomPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCustomPhoto)
    }

    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Allow display over other apps, return here, then tap Start again.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCustomPhoto && resultCode == RESULT_OK && data?.data != null) {
            val photo = data.data!!
            runCatching {
                contentResolver.takePersistableUriPermission(photo, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            getSharedPreferences("snap_button", MODE_PRIVATE).edit()
                .putString("custom_photo_uri", photo.toString())
                .putInt("design", 8)
                .apply()
            sendBroadcast(Intent(CaptureService.ACTION_CUSTOM_PHOTO_UPDATED).setPackage(packageName))
            Toast.makeText(this, "Your photo is now the Snap Button. Hold the button for 2.5 seconds to choose another.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("captureData", data))
            moveTaskToBack(true)
        }
    }
}