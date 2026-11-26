package org.nodelink

import android.app.Activity
import android.view.Gravity
import android.widget.*
import org.json.JSONObject

// Encrypted address book: name -> full fingerprint. Tap a contact to open its chat, the call button to open the call
// screen (both also fill the direct-peer and mesh-recipient fields); long-press deletes.
class ContactControls(private val a:Activity,box:LinearLayout,private val peer:EditText,private val dest:EditText,private val note:(String)->Unit,private val open:(String)->Unit={}){
 private val vault=Vault(a,"contacts");private val list=LinearLayout(a).apply{orientation=1}
 private fun all()=JSONObject(vault.get("book")?:"{}")
 init{
  val name=EditText(a).apply{hint="বন্ধুর নাম";isSingleLine=true};box.addView(name)
  box.addView(Ui.button(Button(a).apply{text="✓  কন্টাক্ট সেভ করুন";setOnClickListener{
   val n=name.text.toString().trim();val fp=peer.text.toString().trim().lowercase()
   when{n.isEmpty()||n.length>40->note("Enter a name up to 40 characters")
    !fp.matches(Regex("[a-f0-9]{64}"))->note("Scan or enter the full 64-digit fingerprint first")
    fp==Identity.fingerprint()->note("That is this phone's own fingerprint")
    else->{vault.put("book",all().put(n,fp).toString());name.setText("");refresh();note("Saved $n. Verify the fingerprint in person.")}}}},Ui.BRAND))
  box.addView(Ui.section(a,"আমার কন্টাক্ট · নাম চাপলে চ্যাট, 📞 চাপলে কল"));box.addView(list);refresh()
 }
 private fun select(n:String,fp:String,where:String){peer.setText(fp);dest.setText(fp);note("Selected $n for chat, call and mesh");open(where)}
 fun refresh(){list.removeAllViews();val b=all();b.keys().asSequence().sorted().forEach{n->val fp=b.getString(n)
  val info=LinearLayout(a).apply{orientation=1;setPadding(Ui.dp(a,12),0,Ui.dp(a,8),0)
   addView(Ui.title(a,n,16f));addView(Ui.caption(a,Ui.nl(fp)+if(Blocklist.has(fp))" · BLOCKED" else ""))}
  val call=Ui.button(Button(a).apply{text="📞";setOnClickListener{select(n,fp,"call")}},Ui.GREEN).apply{minWidth=0;minimumWidth=0;setPadding(0,0,0,0);layoutParams=Ui.lp(Ui.dp(a,48),Ui.dp(a,48))}
  list.addView(Ui.card(a).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL
   addView(Ui.avatar(a,n,44));addView(info,Ui.lp(0,-2,1f));addView(call)
   setOnClickListener{select(n,fp,"chat")}
   setOnLongClickListener{vault.put("book",all().apply{remove(n)}.toString());refresh();note("Deleted $n");true}})}
  if(b.length()==0)list.addView(Ui.caption(a,"এখনো কোনো কন্টাক্ট নেই। উপরে বন্ধুর QR স্ক্যান করে যোগ করুন।"))}
 companion object{fun name(a:Activity,fp:String):String?{val b=JSONObject(Vault(a,"contacts").get("book")?:"{}");return b.keys().asSequence().firstOrNull{b.getString(it)==fp}}}
}