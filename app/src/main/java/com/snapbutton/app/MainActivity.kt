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
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(32,40,32,40)}
  layout.addView(TextView(this).apply{text="Snap Button";textSize=30f;gravity=Gravity.CENTER})
  layout.addView(TextView(this).apply{
   text="Tap the floating button to take a screenshot. Hold it for 4 seconds to change its design. Use TEST SOUND on the button board to preview a sound."
   textSize=18f;gravity=Gravity.CENTER;setPadding(0,24,0,24)
  })
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