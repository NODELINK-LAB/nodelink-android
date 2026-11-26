package org.nodelink

import android.app.Activity
import android.content.Intent
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder

// Shows this phone's fingerprint as a QR code and scans another phone's code into a field. Works fully offline.
class QrControls(private val a:Activity,box:LinearLayout,fp:String,private val note:(String)->Unit){
 private val targets=mutableMapOf<Int,EditText>()
 init{val frame=FrameLayout(a).apply{background=Ui.gradient(a,Ui.BRAND,Ui.GREEN,12,Ui.BRAND);setPadding(Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4),Ui.dp(a,4));elevation=Ui.dp(a,7).toFloat();outlineSpotShadowColor=Ui.GREEN}
  frame.addView(ImageView(a).apply{setImageBitmap(BarcodeEncoder().encodeBitmap("nodelink:$fp",BarcodeFormat.QR_CODE,640,640));scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="My NODELINK QR code"},FrameLayout.LayoutParams(-1,-1))
  box.addView(frame,Ui.lp(Ui.dp(a,182),Ui.dp(a,182)).apply{gravity=android.view.Gravity.CENTER_HORIZONTAL;topMargin=Ui.dp(a,12);bottomMargin=Ui.dp(a,12)})}
 fun scanButton(box:LinearLayout,label:String,code:Int,into:EditText){targets[code]=into
  box.addView(Button(a).apply{text=label;setOnClickListener{IntentIntegrator(a).setRequestCode(code).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("Scan the other phone's NODELINK QR").setBeepEnabled(false).initiateScan()}})}
 fun result(code:Int,resultCode:Int,data:Intent?):Boolean{
  val into=targets[code]?:return false
  val text=IntentIntegrator.parseActivityResult(resultCode,data)?.contents?:run{note("Scan cancelled");return true}
  val fp=text.removePrefix("nodelink:").lowercase()
  if(text.startsWith("nodelink:") && fp.matches(Regex("[0-9a-f]{64}"))){into.setText(fp);note("Fingerprint scanned. Make sure it was the right person's phone.")} else note("Not a NODELINK QR code")
  return true
 }
}