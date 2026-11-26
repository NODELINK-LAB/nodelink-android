package org.nodelink

import android.app.*
import android.content.Intent
import android.widget.*

// Home screen: block or unblock identities, create and share a revocation code, apply someone's code,
// and revoke this phone's own identity (a new one is created; contacts must verify the new fingerprint).
class SecurityControls(private val a:Activity,box:LinearLayout,private val pin:EditText,private val note:(String)->Unit,private val changed:()->Unit){
 private val list=LinearLayout(a).apply{orientation=1}
 private fun confirm(title:String,msg:String,ok:()->Unit){AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("Confirm"){_,_->ok()}.setNegativeButton("Cancel",null).show()}
 private fun nl(fp:String)="NL-"+fp.take(12).uppercase().chunked(4).joinToString("-")
 init{
  box.addView(TextView(a).apply{text="SECURITY · blocked identities and revocation"})
  box.addView(Button(a).apply{text="Block the selected fingerprint";setOnClickListener{val fp=pin.text.toString().trim().lowercase()
   if(!fp.matches(Regex("[0-9a-f]{64}"))||fp==Identity.fingerprint())note("Select a contact or enter a full fingerprint first")
   else confirm("Block this identity?","${nl(fp)} will not be able to link, call, message or relay through this phone."){Blocklist.block(fp,"blocked by you");refresh();note("Blocked ${nl(fp)}")}}})
  box.addView(list)
  box.addView(Button(a).apply{text="Create my revocation code";setOnClickListener{val code=Blocklist.create()
   AlertDialog.Builder(a).setTitle("Revocation code").setMessage("Keep this OFF this phone (a trusted friend, another device, paper). If this phone is lost or stolen, apply it on any Nodelix phone: every phone that syncs with it will block this identity. Anyone holding it can revoke you, but cannot impersonate you or read anything.\n\n$code")
    .setPositiveButton("Share"){_,_->a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,code),"Revocation code"))}.setNegativeButton("Close",null).show()}})
  val code=EditText(a).apply{hint="Paste a revocation code";isSingleLine=true};box.addView(code)
  box.addView(Button(a).apply{text="Apply revocation code";setOnClickListener{runCatching{Blocklist.accept(code.text.toString())}
   .onSuccess{code.setText("");refresh();note("${nl(it)} revoked and blocked; the notice spreads with mesh syncs")}.onFailure{note(it.message?:"Invalid code")}}})
  box.addView(Button(a).apply{text="Revoke this phone's identity now";setOnClickListener{confirm("Revoke your identity?","Use this if your keys may be compromised. Your current identity is revoked (the notice spreads with mesh syncs) and a new one is created. Every contact must scan and verify your new fingerprint. Messages still on the way to the old identity are lost. The app closes; open it again.")
   {Blocklist.accept(Blocklist.create());Identity.reset();a.stopService(Intent(a,RelayService::class.java));a.finishAffinity()}}})
  refresh()
 }
 fun refresh(){list.removeAllViews();changed()
  Blocklist.list().forEach{(fp,why)->list.addView(Button(a).apply{isAllCaps=false;text="${nl(fp)} · $why (hold to unblock)"
   setOnLongClickListener{confirm("Unblock?",if(why.startsWith("revoked"))"Its owner revoked this identity; unblocking is unsafe." else "It can contact you again."){Blocklist.unblock(fp);refresh()};true}})}
  if(Blocklist.list().isEmpty())list.addView(TextView(a).apply{text="Nobody blocked"})}
}