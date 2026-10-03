package com.snapbutton.app
import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class CaptureService:Service(){
 private lateinit var wm:WindowManager; private lateinit var snap:Button
 private var projection:android.media.projection.MediaProjection?=null
 private var reader:ImageReader?=null; private var virtualDisplay:android.hardware.display.VirtualDisplay?=null
 override fun onCreate(){super.onCreate()
  val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  nm.createNotificationChannel(NotificationChannel("snap","Snap Button",NotificationManager.IMPORTANCE_LOW))
  startForeground(11,Notification.Builder(this,"snap").setContentTitle("Snap Button ready").setContentText("Tap the floating camera button to take a screenshot").setSmallIcon(android.R.drawable.ic_menu_camera).build())
  wm=getSystemService(WINDOW_SERVICE) as WindowManager
  snap=Button(this).apply{text="SNAP";textSize=14f;setOnClickListener{capture()}}
  val p=WindowManager.LayoutParams(180,140,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
  p.gravity=Gravity.BOTTOM or Gravity.END;p.x=20;p.y=70;wm.addView(snap,p)
 }
 override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{
  if(projection==null){val code=i?.getIntExtra("resultCode",Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
   val data=if(Build.VERSION.SDK_INT>=33)i?.getParcelableExtra("captureData",Intent::class.java) else @Suppress("DEPRECATION") i?.getParcelableExtra("captureData")
   if(data!=null) projection=(getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code,data)}
  return START_STICKY
 }
 private fun capture(){
  val m=resources.displayMetrics;val w=m.widthPixels;val h=m.heightPixels
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  virtualDisplay=projection?.createVirtualDisplay("snap",w,h,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
  reader?.setOnImageAvailableListener({r->
   val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
   val p=image.planes[0];val row=p.rowStride;val pixel=p.pixelStride
   val raw=Bitmap.createBitmap(w+(row-pixel*w)/pixel,h,Bitmap.Config.ARGB_8888);raw.copyPixelsFromBuffer(p.buffer);image.close()
   val bmp=Bitmap.createBitmap(raw,0,0,w,h);raw.recycle();save(bmp)
   virtualDisplay?.release();virtualDisplay=null;reader?.close();reader=null
  },Handler(Looper.getMainLooper()))
 }
 private fun save(b:Bitmap){
  val v=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"Snap_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.UK).format(Date())+".png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Screenshots")}
  contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v)?.let{u->contentResolver.openOutputStream(u)?.use{b.compress(Bitmap.CompressFormat.PNG,100,it)};Toast.makeText(this,"Screenshot saved",Toast.LENGTH_SHORT).show()};b.recycle()
 }
 override fun onDestroy(){if(::snap.isInitialized)wm.removeView(snap);virtualDisplay?.release();reader?.close();projection?.stop();super.onDestroy()}
 override fun onBind(i:Intent?)=null
}