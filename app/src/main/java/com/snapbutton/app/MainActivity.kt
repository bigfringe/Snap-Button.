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
 override fun onCreate(state:Bundle?){ super.onCreate(state)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(48,48,48,48)}
  layout.addView(TextView(this).apply{text="Snap Button";textSize=30f;gravity=Gravity.CENTER})
  layout.addView(TextView(this).apply{text="Tap Start, allow the Android permissions, then use the floating camera button at the bottom-right.";textSize=18f;gravity=Gravity.CENTER;setPadding(0,30,0,30)})
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
  setContentView(layout)
 }
 private fun begin(){
  if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));Toast.makeText(this,"Allow display over other apps, return here, then tap Start again.",Toast.LENGTH_LONG).show();return}
  val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(m.createScreenCaptureIntent(),requestCapture)
 }
 @Deprecated("Deprecated in Java") override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req==requestCapture&&res==RESULT_OK&&data!=null){startForegroundService(Intent(this,CaptureService::class.java).putExtra("resultCode",res).putExtra("captureData",data));moveTaskToBack(true)}
 }
}