package org.nodelink

import android.net.wifi.p2p.*
import android.os.*

// Auto relay loop, used by the open app and by the background RelayService: discovers nearby phones,
// connects to one not tried in 10 minutes, runs a mesh sync with any NODELINK identity, then leaves the group.
class AutoSync(private val m:WifiP2pManager,private val ch:WifiP2pManager.Channel,private val relay:MeshRelay,
 private val begin:()->Session?,private val end:(Session)->Unit,private val busy:()->Boolean,private val note:(String)->Unit,private val after:()->Unit){
 var on=false
 private val tried=mutableMapOf<String,Long>();private val h=Handler(Looper.getMainLooper())
 private val tick=object:Runnable{override fun run(){if(on && !busy())m.discoverPeers(ch,null);h.postDelayed(this,45000)}}
 fun start(){h.removeCallbacks(tick);h.post(tick)}
 fun stop(){h.removeCallbacks(tick)}
 fun peers(list:Collection<WifiP2pDevice>){
  if(!on||busy())return;val now=SystemClock.elapsedRealtime()
  val d=list.firstOrNull{it.status==WifiP2pDevice.AVAILABLE && now-(tried[it.deviceAddress]?:-600000L)>=600000}?:return
  tried[d.deviceAddress]=now;m.connect(ch,WifiP2pConfig().apply{deviceAddress=d.deviceAddress},null);note("Auto relay: connecting to ${d.deviceName}")
 }
 fun connected(info:WifiP2pInfo){
  if(!on)return;val run=begin()?:return
  Thread{val r=runCatching{if(!info.isGroupOwner)Thread.sleep(2000);var out="";run.probe(info,"",Identity.tlsAny()){i,o->out=relay.sync("",info.isGroupOwner,i,o)};out}.getOrElse{"sync failed: ${it.message}"}
   h.post{end(run);after();note("Auto relay: $r");m.removeGroup(ch,null)}}.start()
 }
}