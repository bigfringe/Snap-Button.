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
 private val requestCapture=7001
 private var waitingForOverlay=false

 override fun onCreate(state:Bundle?){ super.onCreate(state)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(48,32,48,32)}
  layout.addView(TextView(this).apply{text="Snap Button";textSize=30f;gravity=Gravity.CENTER})
  layout.addView(TextView(this).apply{
   text="FIRST TIME SETUP\n1. Tap START SNAP BUTTON.\n2. Find and tap Screenshot in Settings.\n3. Switch on Allow display over other apps.\n4. Come back here. When asked, tap Start now.\n\nYour Snap Button will then appear."
   textSize=17f
   gravity=Gravity.START
   setLineSpacing(6f,1f)
   setPadding(0,22,0,22)
  })
  layout.addView(TextView(this).apply{text="Choose your Snap Button";textSize=20f;gravity=Gravity.CENTER})
  val choices=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
  listOf("1 — Original Eye", "2 — Smiling Fat Cat", "3 — Coming soon", "4 — Coming soon", "5 — Coming soon", "6 — Coming soon", "7 — Coming soon", "8 — Coming soon").forEachIndexed { i, title ->
   choices.addView(RadioButton(this).apply { text=title; id=i+1; isEnabled=i<2 })
  }
  val prefs=getSharedPreferences("snap",MODE_PRIVATE)
  choices.check(prefs.getInt("button",1))
  choices.setOnCheckedChangeListener { _, id -> prefs.edit().putInt("button",id).apply() }
  layout.addView(choices)
  layout.addView(Button(this).apply{text="START SNAP BUTTON";setOnClickListener{begin()}})
  setContentView(ScrollView(this).apply { addView(layout) })
 }

 override fun onResume(){
  super.onResume()
  if(waitingForOverlay){
   waitingForOverlay=false
   if(Settings.canDrawOverlays(this)) requestScreenCapture()
   else Toast.makeText(this,"Turn on Allow display over other apps for Screenshot, then come back here.",Toast.LENGTH_LONG).show()
  }
 }

 private fun begin(){
  if(!Settings.canDrawOverlays(this)){
   waitingForOverlay=true
   try {
    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
   } catch (_: ActivityNotFoundException) {
    waitingForOverlay=false
    Toast.makeText(this,"Open Android Settings and switch on Allow display over other apps for Screenshot.",Toast.LENGTH_LONG).show()
   }
   return
  }
  requestScreenCapture()
 }

 private fun requestScreenCapture(){
  val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(m.createScreenCaptureIntent(),requestCapture)
 }

 @Deprecated("Deprecated in Java") override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req==requestCapture&&res==RESULT_OK&&data!=null){startForegroundService(Intent(this,CaptureService::class.java).putExtra("resultCode",res).putExtra("captureData",data));moveTaskToBack(true)}
 }
}
