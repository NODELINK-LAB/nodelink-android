package org.nodelink

import android.app.Activity
import android.view.Gravity
import android.widget.*
import org.json.JSONObject

// Encrypted address book: name -> full fingerprint. Tap a contact to open its chat, the call button to open the call
// screen (both also fill the direct-peer and mesh-recipient fields); long-press deletes.
class ContactControls(private val a:Activity,box:LinearLayout,form:LinearLayout,private val search:EditText,private val peer:EditText,private val dest:EditText,private val note:(String)->Unit,private val open:(String)->Unit={}){
 private val vault=Vault(a,"contacts");private val list=LinearLayout(a).apply{orientation=1};private val count=Ui.section(a,"My Contacts")
 private fun all()=JSONObject(vault.get("book")?:"{}")
 init{
  val name=EditText(a).apply{hint="Friend's name";isSingleLine=true};form.addView(name)
  form.addView(Ui.button(Button(a).apply{text="✓  Save contact";setOnClickListener{
   val n=name.text.toString().trim();val fp=peer.text.toString().trim().lowercase()
   when{n.isEmpty()||n.length>40->note("Enter a name up to 40 characters")
    !fp.matches(Regex("[a-f0-9]{64}"))->note("Scan or enter the full 64-digit fingerprint first")
    fp==Identity.fingerprint()->note("That is this phone's own fingerprint")
    else->{vault.put("book",all().put(n,fp).toString());name.setText("");refresh();note("Saved $n. Verify the fingerprint in person.")}}}},Ui.BRAND))
  box.addView(count);box.addView(list)
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()};override fun afterTextChanged(s:android.text.Editable?){}})
  refresh()
 }
 private fun select(n:String,fp:String,where:String){peer.setText(fp);dest.setText(fp);note("Selected $n for chat, call and mesh");open(where)}
 fun refresh(){list.removeAllViews();val b=all();count.text="My Contacts (${b.length()})";val query=search.text.toString().trim();val names=b.keys().asSequence().filter{it.contains(query,ignoreCase=true)}.sorted().toList();names.forEach{n->val fp=b.getString(n)
  val info=LinearLayout(a).apply{orientation=1;setPadding(Ui.dp(a,12),0,Ui.dp(a,8),0)
   addView(Ui.title(a,n,16f));addView(Ui.caption(a,Ui.nl(fp)+if(Blocklist.has(fp))" · BLOCKED" else ""))}
  fun action(icon:String,label:String,where:String)=TextView(a).apply{tag=Ui.DONE;text=icon;contentDescription="$label $n";textSize=18f;setTextColor(Ui.BRAND);gravity=Gravity.CENTER;background=Ui.oval(Ui.RAISED);setOnClickListener{select(n,fp,where)}}
  list.addView(LinearLayout(a).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(Ui.dp(a,4),Ui.dp(a,6),Ui.dp(a,4),Ui.dp(a,6))
   addView(Ui.avatar(a,n,44));addView(info,Ui.lp(0,-2,1f));addView(action("☎","Call","call"),Ui.lp(Ui.dp(a,44),Ui.dp(a,44)));addView(action("◌","Chat with","chat"),Ui.lp(Ui.dp(a,44),Ui.dp(a,44)).apply{leftMargin=Ui.dp(a,8)})
   setOnClickListener{select(n,fp,"chat")}
   setOnLongClickListener{vault.put("book",all().apply{remove(n)}.toString());refresh();note("Deleted $n");true}});list.addView(android.view.View(a).apply{setBackgroundColor(Ui.LINE)},Ui.lp(-1,Ui.dp(a,1)))}
  if(names.isEmpty())list.addView(Ui.caption(a,if(b.length()==0)"No contacts yet. Open New contact to add one." else "No matching contacts."))}
 companion object{fun name(a:Activity,fp:String):String?{val b=JSONObject(Vault(a,"contacts").get("book")?:"{}");return b.keys().asSequence().firstOrNull{b.getString(it)==fp}}}
}