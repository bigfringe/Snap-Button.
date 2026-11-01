package com.snapbutton.app

import android.app.*
import android.content.*
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
 private val requestCapture=7001
 private var waitingForOverlay=false
 private var previewTts:TextToSpeech?=null
 private var previewTtsReady=false
 private val previewShutter=MediaActionSound()

 override fun onCreate(state:Bundle?){ super.onCreate(state)
  previewTts=TextToSpeech(this){ status -> previewTtsReady=status==TextToSpeech.SUCCESS }
  showHome()
 }

 private fun makePage():LinearLayout {
  val page=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.CENTER_HORIZONTAL
   setPadding(32,28,32,28)
  }
  setContentView(ScrollView(this).apply{addView(page)})
  return page
 }

 private fun showHome(){
  val page=makePage()
  page.addView(TextView(this).apply{text="Snap Button";textSize=30f;gravity=Gravity.CENTER})
  page.addView(TextView(this).apply{
   text="FIRST TIME SETUP\n1. Tap START SNAP BUTTON.\n2. Find and tap Screenshot in Settings.\n3. Switch on Allow display over other apps.\n4. Come back here. When asked, tap Start now.\n\nYour Snap Button will then appear."
   textSize=17f
   gravity=Gravity.START
   setLineSpacing(6f,1f)
   setPadding(0,22,0,22)
  })
  page.addView(TextView(this).apply{text="Choose your Snap Button";textSize=20f;gravity=Gravity.CENTER})
  val choices=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
  listOf("1 — Original Eye", "2 — Smiling Fat Cat", "3 — Coming soon", "4 — Coming soon", "5 — Coming soon", "6 — Coming soon", "7 — Coming soon", "8 — Coming soon").forEachIndexed { i, title ->
   choices.addView(RadioButton(this).apply { text=title; id=i+1; isEnabled=i<2 })
  }
  val prefs=getSharedPreferences("snap",MODE_PRIVATE)
  choices.check(prefs.getInt("button",1))
  choices.setOnCheckedChangeListener { _, id -> prefs.edit().putInt("button",id).apply() }
  page.addView(choices)
  page.addView(Button(this).apply{text="TEST PICTURES & SOUNDS";setOnClickListener{showTestScreen()}})
  page.addView(Button(this).apply{text="START SNAP BUTTON";setOnClickListener{begin()}})
 }

 private fun showTestScreen(){
  val page=makePage()
  page.addView(TextView(this).apply{text="Test pictures and sounds";textSize=26f;gravity=Gravity.CENTER})
  page.addView(TextView(this).apply{
   text="Tap a sound button to hear it. Testing here will not take a screenshot."
   textSize=17f
   gravity=Gravity.CENTER
   setPadding(0,12,0,20)
  })
  addPreview(page,"1 — Original Eye",R.drawable.file_0000000005608210b369af43ca73cd02,"TEST Ouch! + camera sound"){
   if(previewTtsReady) previewTts?.speak("Ouch!",TextToSpeech.QUEUE_FLUSH,null,"preview_ouch")
   previewShutter.play(MediaActionSound.SHUTTER_CLICK)
  }
  addPreview(page,"2 — Smiling Fat Cat",R.drawable.smiling_cat,"TEST angry cat sound"){
   MediaPlayer.create(this,R.raw.cat_meow)?.apply{
    setOnCompletionListener{it.release()}
    start()
   }
  }
  page.addView(Button(this).apply{text="BACK TO SETUP";setOnClickListener{showHome()}})
 }

 private fun addPreview(page:LinearLayout,title:String,imageRes:Int,soundLabel:String,onTest:()->Unit){
  val card=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(16,12,16,16)
   setBackgroundColor(0xFFF0F0F0.toInt())
  }
  card.addView(TextView(this).apply{text=title;textSize=20f;gravity=Gravity.CENTER})
  card.addView(ImageView(this).apply{
   setImageResource(imageRes)
   scaleType=ImageView.ScaleType.FIT_CENTER
   contentDescription=title+" picture"
   layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(190))
  })
  card.addView(Button(this).apply{text=soundLabel;setOnClickListener{onTest()}})
  page.addView(card,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(0,12,0,12)})
 }

 private fun dp(value:Int)= (value*resources.displayMetrics.density).toInt()

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

 override fun onDestroy(){
  previewTts?.shutdown()
  previewShutter.release()
  super.onDestroy()
 }

 @Deprecated("Deprecated in Java") override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data)
  if(req==requestCapture&&res==RESULT_OK&&data!=null){startForegroundService(Intent(this,CaptureService::class.java).putExtra("resultCode",res).putExtra("captureData",data));moveTaskToBack(true)}
 }
}
