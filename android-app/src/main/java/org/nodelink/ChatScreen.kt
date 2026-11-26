package org.nodelink

import android.app.Activity
import android.view.Gravity
import android.widget.*
import java.util.UUID

// Conversation view for the selected peer: contact header, direct and mesh messages as bubbles with delivery ticks,
// and one composer for both routes.
class ChatScreen(private val a:Activity,box:LinearLayout,private val peer:()->String,private val store:Mailbox,private val relay:MeshRelay,private val note:(String)->Unit,private val queued:()->Unit={}){
 private val face=Ui.avatar(a,"?",44);private val title=Ui.title(a,"",17f);private val sub=Ui.caption(a,"")
 private val thread=LinearLayout(a).apply{orientation=1;setPadding(0,Ui.dp(a,8),0,Ui.dp(a,8))};private val draft=EditText(a).apply{hint="মেসেজ লিখুন"}
 private fun pin()=peer().trim().lowercase().also{require(it.matches(Regex("[0-9a-f]{64}")) && it!=Identity.fingerprint()){"Pick a contact in Contacts first"};require(!Blocklist.has(it)){"This contact is blocked"}}
 private fun text()=draft.text.toString().also{require(it.isNotBlank() && it.toByteArray().size<=4096){"Enter a message up to 4096 bytes"}}
 init{
  box.addView(Ui.card(a).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;addView(face)
   addView(LinearLayout(a).apply{orientation=1;setPadding(Ui.dp(a,12),0,0,0);addView(title);addView(sub)},Ui.lp(0,-2,1f))})
  box.addView(thread);box.addView(draft)
  box.addView(Ui.row(a,
   Ui.button(Button(a).apply{text="কাছে পাঠান";setOnClickListener{send{p,t->store.add(p,Note(UUID.randomUUID(),System.currentTimeMillis()+MessageWire.LIFE,t),"Queued");queued();"Queued. Sends automatically while linked with this phone."}}},Ui.BRAND),
   Ui.button(Button(a).apply{text="দূরে পাঠান (মেশ)";setOnClickListener{send{p,t->relay.compose(p,t);"Encrypted and queued for the mesh."}}})))
  box.addView(Ui.caption(a,"কাছে পাঠান: যুক্ত থাকা ফোনে সরাসরি যায়। দূরে পাঠান: মাঝের অন্য NODELINK ফোনগুলো তালাবদ্ধ অবস্থায় বয়ে নিয়ে যায়, কেউ পড়তে পারে না।"))
  refresh()
 }
 private fun send(f:(String,String)->String){runCatching{val msg=f(pin(),text());draft.setText("");refresh();note(msg)}.onFailure{note(it.message?:"Send failed")}}
 private fun tick(s:String)=when(s){"Queued"->"◷ অপেক্ষায়";"Delivered"->"✓✓ পৌঁছেছে";"Sent via mesh"->"✓ মেশে পাঠানো হয়েছে";"Delivered via mesh"->"✓✓ মেশ দিয়ে পৌঁছেছে";"Received","Received via mesh"->"পেয়েছেন";else->s}
 private fun bubble(t:String,s:String,mine:Boolean)=LinearLayout(a).apply{orientation=1;background=Ui.round(a,if(mine)Ui.BRAND else Ui.RAISED,18);setPadding(Ui.dp(a,14),Ui.dp(a,10),Ui.dp(a,14),Ui.dp(a,8))
  addView(TextView(a).apply{tag=Ui.DONE;text=t;textSize=15f;setTextColor(if(mine)Ui.INK else Ui.TEXT);maxWidth=Ui.dp(a,270)})
  addView(TextView(a).apply{tag=Ui.DONE;text=tick(s);textSize=11f;setTextColor(if(mine)0xB30B1220.toInt() else Ui.MUTED);setPadding(0,Ui.dp(a,4),0,0)})
  layoutParams=Ui.lp(-2,-2).apply{gravity=if(mine)Gravity.END else Gravity.START;setMargins(if(mine)Ui.dp(a,48) else 0,Ui.dp(a,4),if(mine)0 else Ui.dp(a,48),Ui.dp(a,4))}}
 fun refresh(){thread.removeAllViews();val p=peer().trim().lowercase()
  if(!p.matches(Regex("[0-9a-f]{64}"))){Ui.paint(face,"?");title.text="কেউ বাছাই করা হয়নি";sub.text="কন্টাক্ট ট্যাব থেকে একজনকে চাপুন";return}
  val n=ContactControls.name(a,p);Ui.paint(face,n?:"#");title.text=n?:Ui.nl(p);sub.text=(if(n!=null)Ui.nl(p)+" · " else "")+"🔒 শুধু আপনারা দুজন পড়তে পারবেন"
  val rows=store.thread(p)
  if(rows.isEmpty())thread.addView(Ui.caption(a,"এখনো কোনো মেসেজ নেই। হ্যালো বলুন!").apply{gravity=Gravity.CENTER;setPadding(0,Ui.dp(a,24),0,Ui.dp(a,24))},Ui.lp())
  rows.forEach{(t,s)->thread.addView(bubble(t,s,!s.startsWith("Received")))}
 }
}