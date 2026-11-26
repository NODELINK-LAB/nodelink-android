package org.nodelink

import android.app.Activity
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import android.widget.*

// Home screen switch for phone B in A → B → C: hosts a Wi-Fi Direct group that A and C both join (they tap Connect
// on this phone), and runs RelayHub so A and C can link, message and call each other through it.
class RelayControls(a:Activity,box:LinearLayout,m:WifiP2pManager,ch:WifiP2pManager.Channel,allowed:()->Boolean,note:(String)->Unit){
 private val hub=RelayHub{s->a.runOnUiThread{note(s)}};private val h=Handler(Looper.getMainLooper());private val info=TextView(a)
 private val tick=object:Runnable{override fun run(){info.text=hub.status();if(hub.on)h.postDelayed(this,2000)}}
 init{
  box.addView(Switch(a).apply{text="Relay calls and messages for others (this phone hosts the group; Auto relay off)"
   setOnCheckedChangeListener{_,v->if(!v){hub.stop();m.removeGroup(ch,null);info.text=hub.status();note("Relay off");return@setOnCheckedChangeListener}
    if(!allowed()){isChecked=false;note("Grant Nearby devices and turn off Auto relay first");return@setOnCheckedChangeListener}
    hub.start();h.removeCallbacks(tick);h.post(tick)
    m.createGroup(ch,object:WifiP2pManager.ActionListener{override fun onSuccess(){note("Relay on: the other two phones tap Connect on this phone")}
     override fun onFailure(n:Int){note("Relay on, using the existing group (create group: $n)")}})}})
  box.addView(info);info.text=hub.status()
 }
}