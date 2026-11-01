package com.snapbutton.app

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.*

class CaptureService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var eye: ImageView
    private lateinit var buttonContainer: FrameLayout
    private var designSymbol: TextView? = null
    private var chooser: View? = null
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cameraSound = MediaActionSound()
    private var tts: TextToSpeech? = null
    private var activePlayer: MediaPlayer? = null
    private var longPressTriggered = false
    private val prefs by lazy { getSharedPreferences("snap_button", MODE_PRIVATE) }
    private val selectedDesign get() = prefs.getInt("design", -1).coerceIn(-1, 7)
    private val choiceBitmaps = mutableMapOf<Int, Bitmap>()

    private data class ButtonDesign(val title: String, val preview: String)
    private val designs = listOf(
        ButtonDesign("Water Pistol", "Pew pew, splash!"),
        ButtonDesign("Rocket", "Three, two, one, blast off!"),
        ButtonDesign("Robot", "Beep boop."),
        ButtonDesign("Cheeky Dog", "Woof!"),
        ButtonDesign("Duck", "Quack!"),
        ButtonDesign("Football", "Goal!"),
        ButtonDesign("Eye", "Ouch!"),
        ButtonDesign("UFO", "Alien beeps.")
    )

    private val changeHold = Runnable {
        longPressTriggered = true
        showDesignBoard()
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("snap", "Snap Button", NotificationManager.IMPORTANCE_LOW))
        startForeground(11, Notification.Builder(this, "snap")
            .setContentTitle("Snap Button is active")
            .setContentText("Tap to capture. Hold for 2.5 seconds to change the button.")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        cameraSound.load(MediaActionSound.SHUTTER_CLICK)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                engine?.language = Locale.UK
                engine?.setPitch(1.08f)
                engine?.setSpeechRate(1.75f)
            }
        }
        showEye()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection == null) {
            val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
            val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("captureData", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("captureData")
            if (data != null) {
                projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
                projection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, handler)
                createCaptureDisplay()
            }
        }
        return START_NOT_STICKY
    }

    private fun createCaptureDisplay() {
        val m = resources.displayMetrics
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3)
        virtualDisplay = projection?.createVirtualDisplay(
            "SnapButton", m.widthPixels, m.heightPixels, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler
        )
    }

    private fun showEye() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buttonContainer = FrameLayout(this).apply {
            background = circleBackground(0xFF0B1422.toInt())
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        eye = ImageView(this).apply {
            setImageResource(R.drawable.file_0000000005608210b369af43ca73cd02)
            contentDescription = "Floating screenshot button"
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0B1422.toInt())
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val inset = (2.5f * resources.displayMetrics.density).toInt()
        buttonContainer.addView(eye, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(inset, inset, inset, inset)
        })
        designSymbol = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            visibility = View.GONE
            elevation = 4f
            contentDescription = "Selected snap button design"
        }
        buttonContainer.addView(designSymbol, FrameLayout.LayoutParams(-1, -1))
        applyDesign(selectedDesign)
        buttonContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    playButtonSound(selectedDesign, false)
                    handler.postDelayed(changeHold, 2500)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(changeHold)
                    if (!longPressTriggered) takeSnap()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(changeHold)
                    true
                }
                else -> true
            }
        }
        val size = (32 * resources.displayMetrics.density).toInt()
        val p = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = 0; y = 0 }
        wm.addView(buttonContainer, p)
    }

    private fun applyDesign(index: Int) {
        if (index < 0) eye.setImageResource(R.drawable.file_0000000005608210b369af43ca73cd02)
        else if (index == 6) eye.setImageResource(R.drawable.snap_eye)
        else eye.setImageBitmap(choiceBitmap(index))
        eye.visibility = View.VISIBLE
        designSymbol?.visibility = View.GONE
        buttonContainer.background = circleBackground(0xFF0B1422.toInt())
        buttonContainer.clipToOutline = true
        buttonContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun circularPreview(index: Int, sizeDp: Int): FrameLayout {
        val frame = FrameLayout(this).apply {
            background = circleBackground(0xFF0B1422.toInt())
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val inset = (2f * resources.displayMetrics.density).toInt()
        val image = ImageView(this).apply {
            if (index == 6) setImageResource(R.drawable.snap_eye) else setImageBitmap(choiceBitmap(index))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0B1422.toInt())
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            contentDescription = designs[index].title
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(inset, inset, inset, inset)
        })
        val size = (sizeDp * resources.displayMetrics.density).toInt()
        frame.layoutParams = LinearLayout.LayoutParams(size, size)
        return frame
    }

    private fun choiceBitmap(index: Int): Bitmap = choiceBitmaps.getOrPut(index) {
        if (index == 7) {
            BitmapFactory.decodeResource(resources, R.drawable.ufo_icon)
        } else {
            val sheet = BitmapFactory.decodeResource(resources, R.drawable.button_choices)
            val column = index % 4
            val row = index / 4
            // Use the measured centres of the eight circles in the supplied 4x2 image sheet.
            val centerX = floatArrayOf(0.136f, 0.372f, 0.617f, 0.841f)[column] * sheet.width
            val centerY = if (row == 0) 0.296f * sheet.height else 0.700f * sheet.height
            val size = (sheet.width * 0.232f).toInt().coerceAtMost(sheet.height / 2)
            val left = (centerX - size / 2).toInt().coerceIn(0, sheet.width - size)
            val top = (centerY - size / 2).toInt().coerceIn(0, sheet.height - size)
            val crop = Bitmap.createBitmap(sheet, left, top, size, size)
            sheet.recycle()
            crop
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFFFFC94A.toInt())
    }

    private fun showDesignBoard() {
        if (chooser != null) return
        val gold = 0xFFFFC94A.toInt()
        val navy = 0xFF0B1422.toInt()
        fun premiumButton(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 10f
            minWidth = 0
            setTextColor(navy)
            backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            setPadding(8, 2, 8, 2)
            setOnClickListener { action() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 12)
            background = GradientDrawable().apply {
                setColor(navy)
                cornerRadius = 18f * resources.displayMetrics.density
                setStroke((2 * resources.displayMetrics.density).toInt(), gold)
            }
        }
        panel.addView(TextView(this).apply {
            text = "CHOOSE YOUR SNAP BUTTON"
            textSize = 19f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "Preview a sound, then choose the button you want."
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        designs.forEachIndexed { index, design ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6, 5, 6, 5)
                background = GradientDrawable().apply {
                    setColor(0xFF111F31.toInt())
                    cornerRadius = 12f * resources.displayMetrics.density
                }
            }
            row.addView(circularPreview(index, 44), LinearLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ))
            row.addView(TextView(this).apply {
                text = design.title
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8, 0, 4, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val testButton = premiumButton("TEST") { }
            testButton.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { playButtonSound(index, true); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopButtonSound(); true }
                    else -> true
                }
            }
            row.addView(testButton)
            val useButton = premiumButton(if (index == selectedDesign) "USING" else "USE") {
                prefs.edit().putInt("design", index).apply()
                applyDesign(index)
                closeDesignBoard()
                Toast.makeText(this@CaptureService, "${design.title} selected", Toast.LENGTH_SHORT).show()
            }
            useButton.isEnabled = index != selectedDesign
            row.addView(useButton)
            val rowParams = LinearLayout.LayoutParams(-1, -2)
            rowParams.bottomMargin = (5 * resources.displayMetrics.density).toInt()
            list.addView(row, rowParams)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(list) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        footer.addView(premiumButton("REMOVE BUTTON") {
            closeDesignBoard()
            showRemoveConfirmation()
        })
        footer.addView(premiumButton("CLOSE") { closeDesignBoard() })
        panel.addView(footer)
        val width = (390 * resources.displayMetrics.density).toInt()
            .coerceAtMost(resources.displayMetrics.widthPixels - 24)
        val height = (resources.displayMetrics.heightPixels - (64 * resources.displayMetrics.density).toInt())
            .coerceAtMost((640 * resources.displayMetrics.density).toInt())
        val params = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        chooser = panel
        wm.addView(panel, params)
    }

    private fun closeDesignBoard() {
        chooser?.let { runCatching { wm.removeView(it) } }
        chooser = null
        longPressTriggered = false
    }

    private fun stopButtonSound() {
        tts?.stop()
        activePlayer?.let { player ->
            runCatching { player.setOnCompletionListener(null); player.stop() }
            runCatching { player.release() }
        }
        activePlayer = null
    }

    private fun playButtonSound(index: Int, preview: Boolean) {
        stopButtonSound()
        val resourceName = when (index) {
            0 -> "water_gun_voice"
            1 -> "rocket_whoosh"
            2 -> "robot_voice"
            3 -> "dog_bark"
            4 -> "duck_quack"
            5 -> "football_crowd"
            7 -> "ufo_passage"
            else -> null
        }
        if (resourceName != null) {
            val soundId = resources.getIdentifier(resourceName, "raw", packageName)
            if (soundId != 0) {
                MediaPlayer.create(this, soundId)?.let { player ->
                    player.setPlaybackParams(player.playbackParams.setSpeed(1.75f))
                    activePlayer = player
                    player.setOnCompletionListener {
                        if (activePlayer === it) activePlayer = null
                        it.release()
                    }
                    player.start()
                    return
                }
            }
        }
        val phrase = if (index < 0) "Ouch!" else designs[index].preview
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, if (preview) "sound_preview" else "button_sound")
    }

    private fun playUploadedCatSound() {
        val id = resources.getIdentifier("uploaded_cat_sound", "raw", packageName)
        if (id == 0) {
            tts?.speak("Meow!", TextToSpeech.QUEUE_FLUSH, null, "cat_sound_preview")
            return
        }
        MediaPlayer.create(this, id)?.let { player ->
            player.setOnCompletionListener { it.release() }
            player.start()
        }
    }

    private fun showRemoveConfirmation() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 28, 40, 24)
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 16f * resources.displayMetrics.density }
        }
        box.addView(TextView(this).apply {
            text = "Remove Snap Button?"
            textSize = 20f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 10)
        })
        box.addView(Button(this).apply { text = "REMOVE"; setOnClickListener { stopSelf() } })
        box.addView(Button(this).apply { text = "KEEP"; setOnClickListener { runCatching { wm.removeView(box) } } })
        val params = WindowManager.LayoutParams(
            (280 * resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(box, params)
    }

    private fun takeSnap() {
        val r = reader ?: return
        while (true) { val old = r.acquireLatestImage() ?: break; old.close() }
        handler.postDelayed({
            buttonContainer.visibility = View.INVISIBLE
            handler.postDelayed({ if (::buttonContainer.isInitialized && buttonContainer.isAttachedToWindow) buttonContainer.visibility = View.VISIBLE }, 1200)
            handler.postDelayed({ captureNewest(0) }, 120)
        }, 120)
    }

    private fun captureNewest(attempt: Int) {
        val image = reader?.acquireLatestImage()
        if (image == null) {
            if (attempt < 10) handler.postDelayed({ captureNewest(attempt + 1) }, 50)
            else { buttonContainer.visibility = View.VISIBLE; Toast.makeText(this, "Try again", Toast.LENGTH_SHORT).show() }
            return
        }
        val m = resources.displayMetrics
        val plane = image.planes[0]
        val pixel = plane.pixelStride
        val row = plane.rowStride
        val raw = Bitmap.createBitmap(m.widthPixels + (row - pixel * m.widthPixels) / pixel, m.heightPixels, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(plane.buffer)
        image.close()
        val bmp = Bitmap.createBitmap(raw, 0, 0, m.widthPixels, m.heightPixels)
        raw.recycle()
        save(bmp)
        buttonContainer.visibility = View.VISIBLE
    }

    private fun save(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Snap_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(Date()) + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cameraSound.play(MediaActionSound.SHUTTER_CLICK)
            Toast.makeText(this, "Screenshot saved", Toast.LENGTH_SHORT).show()
        }
        bitmap.recycle()
    }

    override fun onDestroy() {
        closeDesignBoard()
        if (::buttonContainer.isInitialized) runCatching { wm.removeView(buttonContainer) }
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        cameraSound.release()
        stopButtonSound()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
