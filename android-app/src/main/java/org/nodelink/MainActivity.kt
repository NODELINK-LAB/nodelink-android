package org.nodelink

import android.Manifest
import android.app.Activity
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.widget.*
import java.io.*

class MainActivity:Activity(){
 private lateinit var m:WifiP2pManager;private lateinit var ch:WifiP2pManager.Channel
 private lateinit var peers:LinearLayout; private lateinit var status:TextView;private lateinit var pin:EditText
 private lateinit var qr:QrControls;private lateinit var auto:AutoSync
 private lateinit var web:WebScreen;private var session:Session?=null;private var registered=false;private lateinit var link:Link
 private val perm get()=if(Build.VERSION.SDK_INT>=33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
 private fun note(s:String){status.text="●  $s"}
 private fun action(ok:String)=object:WifiP2pManager.ActionListener{override fun onSuccess(){note(ok)};override fun onFailure(n:Int){note("Wi-Fi Direct request failed: $n")}}
 private val rx=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)return
  if(i.action==WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)m.requestPeers(ch){list->peers.removeAllViews();if(::auto.isInitialized)auto.peers(list.deviceList);list.deviceList.forEach{d->peers.addView(Button(this@MainActivity).apply{text="📱  যুক্ত হন: ${d.deviceName}";setOnClickListener{m.connect(ch,WifiP2pConfig().apply{deviceAddress=d.deviceAddress},action("Connection requested; accept on peer"))}})}}
  if(i.action==WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)m.requestConnectionInfo(ch){if(it==null||!it.groupFormed){if(!(::link.isInitialized && link.lan)){if(::link.isInitialized)link.stop();session?.close()};note("No group formed")}else{note(if(it.isGroupOwner)"Group owner: start probe here first" else "Group client: start after owner is listening");if(::auto.isInitialized)auto.connected(it)
   val p=pin.text.toString().trim().lowercase();if(::link.isInitialized && ::auto.isInitialized && !auto.on && p.matches(Regex("[0-9a-f]{64}")))link.start(it,p)}}
 }}
 override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=0xFF102A22.toInt();window.navigationBarColor=Ui.BG
  val root=LinearLayout(this).apply{orientation=1;setPadding(Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,24))}
  val scroll=ScrollView(this).apply{setBackgroundColor(Ui.BG);addView(root)}
  fun page(show:Boolean)=LinearLayout(this).apply{orientation=1;visibility=if(show)android.view.View.VISIBLE else android.view.View.GONE}
  val home=page(true);val chatPage=page(false);val callPage=page(false);val contactsPage=page(false);val walletPage=page(false);var box=root;var onChat:()->Unit={};var onCall:()->Unit={}
  fun label(s:String)=TextView(this).apply{text=s;box.addView(this)}
  status=TextView(this).apply{text="কোনো ফোনের সাথে যুক্ত নেই"}
  val pages=listOf(home,chatPage,callPage,contactsPage,walletPage);pages.forEach{root.addView(it)};box=home
  lateinit var nav:NavBar;web=WebScreen(this)
  fun showWeb(){scroll.visibility=android.view.View.GONE;web.view.visibility=android.view.View.VISIBLE;nav.select(5);web.open()}
  fun show(p:LinearLayout){scroll.visibility=android.view.View.VISIBLE;web.view.visibility=android.view.View.GONE;pages.forEach{it.visibility=if(it===p)android.view.View.VISIBLE else android.view.View.GONE};nav.select(pages.indexOf(p));scroll.scrollTo(0,0);if(p===chatPage)onChat();if(p===callPage)onCall()}
  nav=NavBar(this,listOf("🏠" to "হোম","💬" to "চ্যাট","📞" to "কল","👥" to "কন্টাক্ট","👛" to "ওয়ালেট","🔎" to "এক্সপ্লোরার")){if(it==5)showWeb() else show(pages[it])}
  setContentView(LinearLayout(this).apply{orientation=1;setBackgroundColor(Ui.BG);addView(Ui.topBar(this@MainActivity,status));addView(scroll,Ui.lp(-1,0,1f));addView(web.view,Ui.lp(-1,0,1f));addView(nav.view)
   setOnApplyWindowInsetsListener{v,ins->if(Build.VERSION.SDK_INT>=30){val b=ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)};ins}})
  root.viewTreeObserver.addOnGlobalLayoutListener{Ui.style(root)}   // themes controls as screens add them
  val openCall={show(callPage)}
  if(!packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)){note("Wi-Fi Direct unsupported");return}
  m=getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager;ch=m.initialize(this,mainLooper){session?.close();note("Radio channel lost. Restart the app.")}
  Blocklist.load(this);LanBeacon.init(this);Identity.prepare();if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3)
  val adv=LinearLayout(this).apply{orientation=1;visibility=android.view.View.GONE}
  box.addView(Ui.steps(this,"কিভাবে শুরু করবেন","বন্ধুর ফোনেও NODELINK ইনস্টল করুন।","কন্টাক্ট ট্যাবে গিয়ে একে অপরের QR স্ক্যান করে নাম দিয়ে সেভ করুন।","দুই ফোন কাছাকাছি আনুন, নিচে \"কাছের ফোন খুঁজুন\" চেপে বন্ধুর ফোনে যুক্ত হন।","কন্টাক্টে বন্ধুর নাম চাপলে চ্যাট, 📞 চাপলে কল। ইন্টারনেট লাগবে না।"))
  val fp=Identity.fingerprint();box.addView(Ui.section(this,"আমার পরিচয় · এই QR শুধু বিশ্বস্ত মানুষকে দেখান"));label("আমার আইডি: NL-"+fp.take(12).uppercase().chunked(4).joinToString("-"));label("পূর্ণ ফিঙ্গারপ্রিন্ট: "+fp).apply{setTextIsSelectable(true);textSize=11f;tag=Ui.DONE;setTextColor(Ui.MUTED)};qr=QrControls(this,box,fp,::note)
  box=contactsPage;box.addView(Ui.section(this,"নতুন কন্টাক্ট · সামনাসামনি যাচাই করুন"));label("বন্ধুর ফোনের QR স্ক্যান করুন, তারপর একটা নাম দিয়ে সেভ করুন।")
  pin=EditText(this).apply{hint="বন্ধুর ফিঙ্গারপ্রিন্ট (QR স্ক্যান করলে নিজে বসবে)";isSingleLine=true};box.addView(pin);qr.scanButton(box,"📷  বন্ধুর QR স্ক্যান করুন",0x4e1,pin)
  box=home;box.addView(Ui.section(this,"কাছের ফোন · ইন্টারনেট ছাড়া"))
  fun btn(s:String,f:()->Unit)=Button(this).apply{text=s;setOnClickListener{if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(perm),1) else f()}}.also{box.addView(it)}
  Ui.button(btn("🔍  কাছের ফোন খুঁজুন"){m.discoverPeers(ch,action("খুঁজছি… নিচে ফোনের নাম এলে চাপুন"))},Ui.BRAND);peers=LinearLayout(this).apply{orientation=1};box.addView(peers)
  box=adv;btn("Run authenticated probe"){val expected=pin.text.toString().trim();m.requestConnectionInfo(ch){info->if(info==null||!info.groupFormed)note("Create a group first") else if(session==null){val run=Session();session=run;note(if(info.isGroupOwner)"Waiting for client (60s)" else "Authenticating…");Thread{val result=runCatching{run.probe(info,expected)}.getOrElse{"Failed: ${it.message}"};runOnUiThread{if(session===run){session=null;note(result)}}}.start()}}}
  val chat=ChatControls(this){onChat()};val relay=MeshRelay(this,chat.store)
  box=chatPage;val screen=ChatScreen(this,box,{pin.text.toString()},chat.store,relay,::note){if(::link.isInitialized)link.job(Link.MESSAGES)};onChat={screen.refresh()}
  btn("Exchange messages"){if(link.connected){link.job(Link.MESSAGES);note("Exchanging messages over the link");return@btn};val dest=pin.text.toString().trim();m.requestConnectionInfo(ch){info->if(info==null||!info.groupFormed)note("Messages remain queued. Connect both phones first.") else if(session==null){val run=Session();session=run;note(if(info.isGroupOwner)"Waiting for peer to tap Exchange messages" else "Opening authenticated message session");Thread{val result=runCatching{run.probe(info,dest){i,o->chat.exchange(dest,info.isGroupOwner,i,o)}}.getOrElse{"Exchange interrupted: ${it.message}. Unacknowledged messages remain queued."};runOnUiThread{if(session===run){session=null;note(result)}}}.start()}}}
  box=adv;label("MESH & RELAY · carry messages for others");val mesh=MeshControls(this,box,relay,::note,qr);onChat={screen.refresh();mesh.refresh()}
  box=contactsPage;val contacts=ContactControls(this,box,pin,mesh.dest,::note){if(it=="call")show(callPage) else show(chatPage)}
  SecurityControls(this,box,pin,::note){contacts.refresh()};box=adv
  RelayControls(this,box,m,ch,{checkSelfPermission(perm)==PackageManager.PERMISSION_GRANTED && !auto.on},::note)
  auto=AutoSync(m,ch,mesh.relay,{if(session==null)Session().also{session=it} else null},{r->if(session===r)session=null},{session!=null},::note,{onChat()})
  val prefs=getSharedPreferences("settings",MODE_PRIVATE)
  fun relay(v:Boolean){auto.on=v;if(v && ::link.isInitialized)link.stop();prefs.edit().putBoolean("auto",v).apply();val svc=Intent(this,RelayService::class.java);if(v){auto.start();startForegroundService(svc)}else{auto.stop();stopService(svc)}}
  box.addView(Switch(this).apply{text="Auto relay, also in background (turn off for direct chat or calls)";isChecked=prefs.getBoolean("auto",false)&&checkSelfPermission(perm)==PackageManager.PERMISSION_GRANTED;if(isChecked)relay(true)
   setOnCheckedChangeListener{_,v->if(v && checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(perm),1);isChecked=false}else{if(v && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3);relay(v);note(if(v)"Auto relay on, keeps running in the background" else "Auto relay off")}}})
  btn("Mesh relay sync"){if(link.connected){link.job(Link.MESH);note("Mesh sync over the link");return@btn};val dest=pin.text.toString().trim();m.requestConnectionInfo(ch){info->if(info==null||!info.groupFormed)note("Connect to a nearby phone first; mesh messages stay queued") else if(session==null){val run=Session();session=run;note(if(info.isGroupOwner)"Waiting for peer to tap Mesh relay sync" else "Syncing mesh relay");Thread{val result=runCatching{var out="";run.probe(info,dest){i,o->out=mesh.relay.sync(dest,info.isGroupOwner,i,o)};out}.getOrElse{"Mesh sync interrupted: ${it.message}. Unacknowledged envelopes remain queued."};runOnUiThread{onChat();if(session===run){session=null;note(result)}}}.start()}}}
  box=callPage;val call=CallControls(this);call.header(box);onCall={call.peer(pin.text.toString().trim().lowercase())}
  link=Link({if(session==null)Session().also{session=it} else null},{r->if(session===r)session=null},object:Link.Events{
   override fun state(s:String)=runOnUiThread{note(s)}
   override fun ringing(peer:String)=runOnUiThread{openCall();call.ringing(peer);note("Incoming call")}
   override fun ringStop(why:String)=runOnUiThread{call.ringStop();note(why)}
   override fun callLive()=runOnUiThread{call.live();note("Voice call live · tap Hang up to end")}
   override fun callEnded()=runOnUiThread{call.ended();note("Call ended")}
   override fun exchange(kind:Int,peer:String,owner:Boolean,i:DataInputStream,o:DataOutputStream):String=
    if(kind==Link.MESSAGES){chat.exchange(peer,owner,i,o);"Messages exchanged automatically"} else mesh.relay.sync(peer,owner,i,o).also{runOnUiThread{onChat()}}})
  fun mic():Boolean{val need=arrayOf(perm,Manifest.permission.RECORD_AUDIO).filter{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED};if(need.isNotEmpty())requestPermissions(need.toTypedArray(),2);return need.isEmpty()}
  call.onAnswer({if(mic())link.accept()},{link.decline()})
  box.addView(Ui.button(Button(this).apply{text="📞  কল করুন";setOnClickListener{if(!mic())return@setOnClickListener
   if(link.connected){link.ring();return@setOnClickListener}
   val dest=pin.text.toString().trim().lowercase();m.requestConnectionInfo(ch){info->when{info==null||!info.groupFormed->if(LanBeacon.available() && !auto.on && dest.matches(Regex("[0-9a-f]{64}"))){link.startLan(dest);note("Linking over Wi-Fi/hotspot… tap Call again when it says Linked")}
     else note("Connect both phones first: Wi-Fi Direct (Home → Discover → Connect) or join the same Wi-Fi hotspot")
    auto.on->note("Turn off Auto relay for calls");!dest.matches(Regex("[0-9a-f]{64}"))->note("Pick a contact on Home first")
    else->{link.start(info,dest);note("Linking with the contact… tap Call again when it says Linked")}}}}},Ui.GREEN))
  Ui.button(btn("কল কাটুন"){link.hangUp()},Ui.RED)
  label("দুই ফোনেই কন্টাক্ট থেকে একে অপরকে বাছুন আর হোম থেকে যুক্ত হন। উপরে \"Linked\" লেখা এলে কল চাপুন, অন্য ফোনে রিং বাজবে।")
  call.addTo(box)
  WalletScreen(this,walletPage,::note)
  box=home;box.addView(Ui.section(this,"একই ওয়াইফাই বা হটস্পটে থাকলে"));btn("📶  ওয়াইফাই/হটস্পট দিয়ে যুক্ত হন"){val d=pin.text.toString().trim().lowercase();when{!LanBeacon.available()->note("Join the same Wi-Fi or phone hotspot as the contact first")
   auto.on->note("Turn off Auto relay first");!d.matches(Regex("[0-9a-f]{64}"))->note("Pick a contact first");else->{link.startLan(d);note("Looking for the contact on this Wi-Fi…")}}}
  btn("সংযোগ বিচ্ছিন্ন করুন"){ link.stop();session?.close();session=null;m.removeGroup(ch,action("সংযোগ বন্ধ হয়েছে"))}
  box.addView(Button(this).apply{text="⬇  অ্যাপ আপডেট করুন";setOnClickListener{runCatching{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(WebScreen.APK)))}}})
  box.addView(Button(this).apply{text="⚙  উন্নত সেটিংস দেখান";setOnClickListener{val open=adv.visibility!=android.view.View.VISIBLE;adv.visibility=if(open)android.view.View.VISIBLE else android.view.View.GONE;text=if(open)"⚙  উন্নত সেটিংস লুকান" else "⚙  উন্নত সেটিংস দেখান"}})
  box.addView(adv)
 }
 @Deprecated("Back handling") override fun onBackPressed(){if(!(::web.isInitialized && web.back()))super.onBackPressed()}
 override fun onStart(){super.onStart();Radio.appVisible=true;Radio.bg?.close();if(!::ch.isInitialized)return;val filter=IntentFilter().apply{addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)};if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,filter,Context.RECEIVER_EXPORTED) else registerReceiver(rx,filter);registered=true;if(::auto.isInitialized && auto.on)auto.start()}
 override fun onStop(){Radio.appVisible=false;if(::auto.isInitialized)auto.stop();if(registered){unregisterReceiver(rx);registered=false};if(!(::link.isInitialized && link.owns(session))){session?.close();session=null};super.onStop()}   // the link stays up so calls can still ring
 @Deprecated("Activity result API") override fun onActivityResult(r:Int,c:Int,d:Intent?){if(!::qr.isInitialized||!qr.result(r,c,d))super.onActivityResult(r,c,d)}
 override fun onDestroy(){if(::link.isInitialized)link.stop();if(::ch.isInitialized)ch.close();super.onDestroy()}
}