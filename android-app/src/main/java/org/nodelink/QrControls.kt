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
 init{box.addView(ImageView(a).apply{setImageBitmap(BarcodeEncoder().encodeBitmap("nodelink:$fp",BarcodeFormat.QR_CODE,640,640));adjustViewBounds=true;contentDescription="My NODELINK QR code"})}
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