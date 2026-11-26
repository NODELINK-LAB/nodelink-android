package org.nodelink

import android.content.Context
import android.net.*
import android.net.wifi.WifiManager
import java.net.*
import java.util.concurrent.ConcurrentHashMap

// Finds NODELINK phones on the same ordinary Wi-Fi network: any phone's built-in hotspot (no NODELINK, no SIM and no
// internet needed on that phone) or any router. Every 2 s it broadcasts "NLB1"+fingerprint on UDP 39431 and records
// who it hears. A beacon is only a hint: the connection that follows is pinned mutual TLS, so a fake beacon can only fail.
object LanBeacon {
 const val PORT=39431;const val LINK_PORT=39432
 private val seen=ConcurrentHashMap<String,Pair<InetAddress,Long>>()
 private var app:Context?=null;private var thread:Thread?=null;private var sock:DatagramSocket?=null
 fun init(c:Context){app=c.applicationContext}
 fun wifi():Network?{val cm=app?.getSystemService(ConnectivityManager::class.java)?:return null
  return cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}}
 private fun broadcasts()=NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp && !it.isLoopback}.flatMap{it.interfaceAddresses.mapNotNull{a->a.broadcast}}
 // True when this phone is on a Wi-Fi network or is hosting a hotspot itself.
 fun available()=runCatching{broadcasts().isNotEmpty()}.getOrDefault(false)
 @Synchronized fun start(){if(thread!=null)return
  val lock=app?.getSystemService(WifiManager::class.java)?.createMulticastLock("nodelink")?.apply{acquire()}
  val s=DatagramSocket(null).apply{reuseAddress=true;broadcast=true;bind(InetSocketAddress(PORT))};sock=s
  val me=("NLB1"+Identity.fingerprint()).toByteArray(Charsets.US_ASCII)
  thread=Thread{
   val rx=Thread{val buf=ByteArray(128);runCatching{while(true){val p=DatagramPacket(buf,buf.size);s.receive(p);val t=String(p.data,0,p.length,Charsets.US_ASCII)
    if(p.length==68 && t.startsWith("NLB1")){val fp=t.substring(4);if(fp.matches(Regex("[0-9a-f]{64}")))seen[fp]=p.address to System.currentTimeMillis()}}}}.apply{isDaemon=true;start()}
   try{while(true){runCatching{broadcasts()}.getOrDefault(emptyList()).forEach{b->runCatching{s.send(DatagramPacket(me,me.size,b,PORT))}};Thread.sleep(2000)}}
   catch(_:Exception){}finally{s.close();rx.interrupt();runCatching{lock?.release()}}
  }.apply{isDaemon=true;start()}}
 @Synchronized fun stop(){thread?.interrupt();sock?.close();thread=null;sock=null;seen.clear()}
 fun find(fp:String,waitMs:Long):InetAddress?{val end=System.currentTimeMillis()+waitMs
  while(System.currentTimeMillis()<end){seen[fp]?.let{if(System.currentTimeMillis()-it.second<10000)return it.first};Thread.sleep(300)};return null}
}