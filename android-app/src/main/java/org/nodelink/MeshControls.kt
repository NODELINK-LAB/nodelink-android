package org.nodelink

import android.app.Activity
import android.widget.*

class MeshControls(a:Activity,box:LinearLayout,val relay:MeshRelay,note:(String)->Unit,qr:QrControls){
 private val info=TextView(a)
 val dest=EditText(a).apply{hint="Final recipient full fingerprint";isSingleLine=true}
 init{
  box.addView(TextView(a).apply{text="MESH RELAY · end-to-end encrypted"})
  box.addView(dest);qr.scanButton(box,"Scan recipient QR",0x4e2,dest)
  val draft=EditText(a).apply{hint="Mesh message (up to 4096 bytes)"};box.addView(draft)
  box.addView(Button(a).apply{text="Send via mesh";setOnClickListener{runCatching{relay.compose(dest.text.toString(),draft.text.toString());draft.setText("");refresh();note("Encrypted and queued. Sync with any nearby NODELINK phone.")}.onFailure{note(it.message?:"Mesh send failed")}}})
  box.addView(Button(a).apply{text="Retry undelivered now";setOnClickListener{runCatching{val n=relay.retry(true);refresh();note(if(n==0)"Nothing waiting for a receipt" else "$n message(s) re-queued. Sync with a nearby phone.")}.onFailure{note(it.message?:"Retry failed")}}})
  box.addView(info);refresh()
 }
 fun refresh(){info.text=relay.status()}
}