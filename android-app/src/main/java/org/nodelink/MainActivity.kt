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
 private var compactStatus:TextView?=null;private var backFromDetail:(()->Unit)?=null
 private fun note(s:String){status.text="●  $s";compactStatus?.text=s}
 private fun action(ok:String)=object:WifiP2pManager.ActionListener{override fun onSuccess(){note(ok)};override fun onFailure(n:Int){note("Wi-Fi Direct request failed: $n")}}
 private val rx=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)return
  if(i.action==WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)m.requestPeers(ch){list->peers.removeAllViews();if(::auto.isInitialized)auto.peers(list.deviceList);list.deviceList.forEach{d->peers.addView(Button(this@MainActivity).apply{text="📱  Connect: ${d.deviceName}";setOnClickListener{m.connect(ch,WifiP2pConfig().apply{deviceAddress=d.deviceAddress},action("Connection requested; accept on peer"))}})}}
  if(i.action==WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)m.requestConnectionInfo(ch){if(it==null||!it.groupFormed){if(!(::link.isInitialized && link.lan)){if(::link.isInitialized)link.stop();session?.close()};note("No group formed")}else{note(if(it.isGroupOwner)"Group owner: start probe here first" else "Group client: start after owner is listening");if(::auto.isInitialized)auto.connected(it)
   val p=pin.text.toString().trim().lowercase();if(::link.isInitialized && ::auto.isInitialized && !auto.on && p.matches(Regex("[0-9a-f]{64}")))link.start(it,p)}}
 }}
 override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=Ui.BG;window.navigationBarColor=Ui.BG
  val root=LinearLayout(this).apply{orientation=1;setPadding(Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,8),Ui.dp(this@MainActivity,16),Ui.dp(this@MainActivity,24))}
  val scroll=ScrollView(this).apply{setBackgroundColor(Ui.BG);addView(root)}
  fun page(show:Boolean)=LinearLayout(this).apply{orientation=1;visibility=if(show)android.view.View.VISIBLE else android.view.View.GONE}
  val home=page(true);val chatPage=page(false);val callPage=page(false);val contactsPage=page(false);val walletLanding=page(false);val walletPage=page(false);val identityPage=page(false);var box=root;var onChat:()->Unit={};var onCall:()->Unit={}
  fun label(s:String)=TextView(this).apply{text=s;box.addView(this)}
  status=TextView(this).apply{text="●  Not connected to any phone"}
  val liveNote=Ui.caption(this,"Not connected to any phone").apply{visibility=android.view.View.GONE;gravity=android.view.Gravity.CENTER;setPadding(Ui.dp(this@MainActivity,12),Ui.dp(this@MainActivity,3),Ui.dp(this@MainActivity,12),Ui.dp(this@MainActivity,3))};compactStatus=liveNote
  val tabs=listOf(home,chatPage,callPage,contactsPage,walletLanding);val pages=tabs+listOf(walletPage,identityPage);pages.forEach{root.addView(it)};box=home
  home.addView(Ui.topBar(this,status));chatPage.addView(Ui.heading(this,"Chats"));callPage.addView(Ui.heading(this,"Calls"));contactsPage.addView(Ui.heading(this,"Contacts"))
  lateinit var nav:NavBar;web=WebScreen(this)
  fun showWeb(){backFromDetail=null;liveNote.visibility=android.view.View.GONE;scroll.visibility=android.view.View.GONE;web.view.visibility=android.view.View.VISIBLE;nav.select(5);web.open()}
  fun show(p:LinearLayout){scroll.visibility=android.view.View.VISIBLE;web.view.visibility=android.view.View.GONE;liveNote.visibility=if(p===home||p===identityPage)android.view.View.GONE else android.view.View.VISIBLE;pages.forEach{it.visibility=if(it===p)android.view.View.VISIBLE else android.view.View.GONE};nav.select(if(p===identityPage)0 else if(p===walletPage)4 else tabs.indexOf(p));scroll.scrollTo(0,0);backFromDetail=null;if(p===chatPage)onChat();if(p===callPage)onCall()}
  fun openWallet(){show(walletPage);backFromDetail={show(walletLanding)}}
  fun openIdentity(){show(identityPage);backFromDetail={show(home)}}
  nav=NavBar(this,listOf("home" to "Home","chat" to "Chats","call" to "Calls","contacts" to "Contacts","wallet" to "Wallet","explorer" to "Explorer")){if(it==5)showWeb() else show(tabs[it])}
  setContentView(LinearLayout(this).apply{orientation=1;setBackgroundColor(Ui.BG);addView(scroll,Ui.lp(-1,0,1f));addView(web.view,Ui.lp(-1,0,1f));addView(liveNote);addView(nav.view)
   setOnApplyWindowInsetsListener{v,ins->if(Build.VERSION.SDK_INT>=30){val b=ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)};ins}})
  root.viewTreeObserver.addOnGlobalLayoutListener{Ui.style(root)}   // themes controls as screens add them
  val openCall={show(callPage)}
  if(!packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)){note("Wi-Fi Direct unsupported");return}
  m=getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager;ch=m.initialize(this,mainLooper){session?.close();note("Radio channel lost. Restart the app.")}
  Blocklist.load(this);LanBeacon.init(this);Identity.prepare();if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),3)
  val adv=LinearLayout(this).apply{orientation=1;visibility=android.view.View.GONE}
  box.addView(Ui.steps(this,"How to get started","Install NODELINK on your friend's phone too.","In Contacts, scan each other's QR code and save it with a name.","Bring the phones close, tap \"Find nearby phones\" below and connect to your friend's phone.","Tap a contact's name to chat, 📞 to call. No internet needed."))
  val fp=Identity.fingerprint()
  home.addView(Ui.button(Button(this).apply{text="My Identity  ›";setOnClickListener{openIdentity()}}))
  identityPage.addView(Ui.row(this,Ui.button(Button(this).apply{text="‹  Back";setOnClickListener{show(home)}}),Ui.heading(this,"My Identity")))
  identityPage.addView(NeonArt(this,"identity"),Ui.lp(-1,Ui.dp(this,120)))
  identityPage.gravity=android.view.Gravity.CENTER_HORIZONTAL
  identityPage.addView(Ui.title(this,"My ID: ${Ui.nl(fp)}",19f).apply{gravity=android.view.Gravity.CENTER})
  qr=QrControls(this,identityPage,fp,::note)
  identityPage.addView(Ui.caption(this,"Full fingerprint:\n$fp").apply{setTextIsSelectable(true);gravity=android.view.Gravity.CENTER;textSize=12f})
  identityPage.addView(Ui.caption(this,"Show this QR only to people you trust.").apply{gravity=android.view.Gravity.CENTER})
  val contactSearch=EditText(this).apply{hint="Search friends…";isSingleLine=true};contactsPage.addView(contactSearch)
  val contactForm=Ui.card(this).apply{visibility=android.view.View.GONE}
  contactsPage.addView(Ui.button(Button(this).apply{text="＋  New contact  ›";setOnClickListener{contactForm.visibility=if(contactForm.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}}))
  contactsPage.addView(contactForm);box=contactForm;label("Scan your friend's QR code, then save it with a name.")
  pin=EditText(this).apply{hint="Friend's fingerprint (filled in by scanning)";isSingleLine=true};box.addView(pin);qr.scanButton(box,"Scan friend's QR",0x4e1,pin)
  box=home;box.addView(Ui.section(this,"Nearby phones · no internet"))
  fun btn(s:String,f:()->Unit)=Button(this).apply{text=s;setOnClickListener{if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(perm),1) else f()}}.also{box.addView(it)}
  Ui.button(btn("🔍  Find nearby phones"){m.discoverPeers(ch,action("Searching… tap a phone name when it appears below"))},Ui.BRAND);peers=LinearLayout(this).apply{orientation=1};box.addView(peers)
  box=adv;btn("Run authenticated probe"){val expected=pin.text.toString().trim();m.requestConnectionInfo(ch){info->if(info==null||!info.groupFormed)note("Create a group first") else if(session==null){val run=Session();session=run;note(if(info.isGroupOwner)"Waiting for client (60s)" else "Authenticating…");Thread{val result=runCatching{run.probe(info,expected)}.getOrElse{"Failed: ${it.message}"};runOnUiThread{if(session===run){session=null;note(result)}}}.start()}}}
  val chat=ChatControls(this){onChat()};val relay=MeshRelay(this,chat.store)
  box=chatPage;val screen=ChatScreen(this,box,{pin.text.toString()},chat.store,relay,::note){if(::link.isInitialized)link.job(Link.MESSAGES)};onChat={screen.refresh()}
  btn("Exchange messages"){if(link.connected){link.job(Link.MESSAGES);note("Exchanging messages over the link");return@btn};val dest=pin.text.toString().trim();m.requestConnectionInfo(ch){info->if(info==null||!info.groupFormed)note("Messages remain queued. Connect both phones first.") else if(session==null){val run=Session();session=run;note(if(info.isGroupOwner)"Waiting for peer to tap Exchange messages" else "Opening authenticated message session");Thread{val result=runCatching{run.probe(info,dest){i,o->chat.exchange(dest,info.isGroupOwner,i,o)}}.getOrElse{"Exchange interrupted: ${it.message}. Unacknowledged messages remain queued."};runOnUiThread{if(session===run){session=null;note(result)}}}.start()}}}
  box=adv;label("MESH & RELAY · carry messages for others");val mesh=MeshControls(this,box,relay,::note,qr);onChat={screen.refresh();mesh.refresh()}
  box=contactsPage;val contacts=ContactControls(this,box,contactForm,contactSearch,pin,mesh.dest,::note){if(it=="call")show(callPage) else show(chatPage)}
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
  box.addView(Ui.button(Button(this).apply{text="📞  Call";setOnClickListener{if(!mic())return@setOnClickListener
   if(link.connected){link.ring();return@setOnClickListener}
   val dest=pin.text.toString().trim().lowercase();m.requestConnectionInfo(ch){info->when{info==null||!info.groupFormed->if(LanBeacon.available() && !auto.on && dest.matches(Regex("[0-9a-f]{64}"))){link.startLan(dest);note("Linking over Wi-Fi/hotspot… tap Call again when it says Linked")}
     else note("Connect both phones first: Wi-Fi Direct (Home → Discover → Connect) or join the same Wi-Fi hotspot")
    auto.on->note("Turn off Auto relay for calls");!dest.matches(Regex("[0-9a-f]{64}"))->note("Pick a contact on Home first")
    else->{link.start(info,dest);note("Linking with the contact… tap Call again when it says Linked")}}}}},Ui.GREEN))
  Ui.button(btn("Hang up"){link.hangUp()},Ui.RED)
  label("On both phones, pick each other in Contacts and connect from Home. When the top shows \"Linked\", tap Call and the other phone rings.")
  call.addTo(box)
  walletLanding.addView(Ui.heading(this,"NODELINK"));walletLanding.addView(NeonArt(this,"wallet"),Ui.lp(-1,Ui.dp(this,216)))
  walletLanding.addView(Ui.heading(this,"Your Digital Wallet"));walletLanding.addView(Ui.caption(this,"Send and receive NODE on nodelink-1").apply{gravity=android.view.Gravity.CENTER})
  walletLanding.addView(Ui.button(Button(this).apply{text="Manage Wallet";setOnClickListener{openWallet()}},Ui.BRAND))
  walletPage.addView(Ui.row(this,Ui.button(Button(this).apply{text="‹  Back";setOnClickListener{show(walletLanding)}}),Ui.heading(this,"Wallet")))
  val wallet=WalletScreen(this,walletPage,::note)
  wallet.addHomeSummary(home,1,::openWallet)
  box=home;box.addView(Ui.section(this,"On the same Wi-Fi or hotspot"));btn("📶  Link over Wi-Fi / hotspot"){val d=pin.text.toString().trim().lowercase();when{!LanBeacon.available()->note("Join the same Wi-Fi or phone hotspot as the contact first")
   auto.on->note("Turn off Auto relay first");!d.matches(Regex("[0-9a-f]{64}"))->note("Pick a contact first");else->{link.startLan(d);note("Looking for the contact on this Wi-Fi…")}}}
  btn("Disconnect"){ link.stop();session?.close();session=null;m.removeGroup(ch,action("Disconnected"))}
  box.addView(Button(this).apply{text="⬇  Update app";setOnClickListener{runCatching{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(WebScreen.APK)))}}})
  box.addView(Button(this).apply{text="⚙  Show advanced settings";setOnClickListener{val open=adv.visibility!=android.view.View.VISIBLE;adv.visibility=if(open)android.view.View.VISIBLE else android.view.View.GONE;text=if(open)"⚙  Hide advanced settings" else "⚙  Show advanced settings"}})
  box.addView(adv)
 }
 @Deprecated("Back handling") override fun onBackPressed(){val back=backFromDetail;if(back!=null){back();return};if(!(::web.isInitialized && web.back()))super.onBackPressed()}
 override fun onStart(){super.onStart();Radio.appVisible=true;Radio.bg?.close();if(!::ch.isInitialized)return;val filter=IntentFilter().apply{addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)};if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,filter,Context.RECEIVER_EXPORTED) else registerReceiver(rx,filter);registered=true;if(::auto.isInitialized && auto.on)auto.start()}
 override fun onStop(){Radio.appVisible=false;if(::auto.isInitialized)auto.stop();if(registered){unregisterReceiver(rx);registered=false};if(!(::link.isInitialized && link.owns(session))){session?.close();session=null};super.onStop()}   // the link stays up so calls can still ring
 @Deprecated("Activity result API") override fun onActivityResult(r:Int,c:Int,d:Intent?){if(!::qr.isInitialized||!qr.result(r,c,d))super.onActivityResult(r,c,d)}
 override fun onDestroy(){if(::link.isInitialized)link.stop();if(::ch.isInitialized)ch.close();super.onDestroy()}
}