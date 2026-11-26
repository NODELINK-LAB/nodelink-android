package org.nodelink

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.p2p.*
import android.os.*

// Background mesh relay: a foreground service with a visible notification that keeps syncing with nearby phones
// while the app is closed or the screen is off. Idle while the app itself is on screen.
class RelayService:Service(){
 private lateinit var m:WifiP2pManager;private lateinit var ch:WifiP2pManager.Channel;private lateinit var auto:AutoSync
 private lateinit var wake:PowerManager.WakeLock;private lateinit var card:Notification.Builder;private var registered=false
 private val perm get()=if(Build.VERSION.SDK_INT>=33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
 private val rx=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(checkSelfPermission(perm)!=PackageManager.PERMISSION_GRANTED||Radio.appVisible)return
  if(i.action==WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)m.requestPeers(ch){auto.peers(it.deviceList)}
  if(i.action==WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)m.requestConnectionInfo(ch){if(it!=null && it.groupFormed)auto.connected(it) else Radio.bg?.close()}
 }}
 private fun show(s:String){getSystemService(NotificationManager::class.java).notify(7,card.setContentText(s).build())}
 override fun onCreate(){super.onCreate();Blocklist.load(this)
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("relay","Mesh relay",NotificationManager.IMPORTANCE_LOW))
  card=Notification.Builder(this,"relay").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Nodelix relay on").setOngoing(true)
   .setContentText("Passing encrypted messages to nearby phones").setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
  startForeground(7,card.build(),if(Build.VERSION.SDK_INT>=33)ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
  wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"nodelink:relay").apply{acquire()}
  m=getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager;ch=m.initialize(this,mainLooper,null)
  auto=AutoSync(m,ch,MeshRelay(this,Mailbox(this)),{if(Radio.appVisible||Radio.bg!=null)null else Session().also{Radio.bg=it}},{r->if(Radio.bg===r)Radio.bg=null},{Radio.appVisible||Radio.bg!=null},::show,{})
  val filter=IntentFilter().apply{addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)}
  if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,filter,Context.RECEIVER_EXPORTED) else registerReceiver(rx,filter);registered=true
  auto.on=true;auto.start()
 }
 override fun onStartCommand(i:Intent?,f:Int,id:Int)=START_STICKY
 override fun onBind(i:Intent?):IBinder?=null
 override fun onDestroy(){auto.on=false;auto.stop();if(registered)unregisterReceiver(rx);Radio.bg?.close();Radio.bg=null;if(wake.isHeld)wake.release();ch.close();super.onDestroy()}
}