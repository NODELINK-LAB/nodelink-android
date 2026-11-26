package org.nodelink

import android.net.wifi.p2p.WifiP2pInfo
import java.io.*
import java.net.*
import javax.net.ssl.*

class Session: Closeable {
 @Volatile private var closed=false
 private var listener:ServerSocket?=null
 private var channel:Socket?=null
 @Synchronized private fun track(s:Socket){if(closed){s.close();error("Cancelled")}; channel=s}
 @Synchronized private fun track(s:ServerSocket){if(closed){s.close();error("Cancelled")}; listener=s}
 override fun close(){ synchronized(this){closed=true;listener?.close();channel?.close()} }
 fun probe(info:WifiP2pInfo,pin:String,tls:SSLContext?=null,exchange:((DataInputStream,DataOutputStream)->Unit)?=null):String {
  require(info.groupFormed)
  val ctx=tls?:Identity.tls(pin);val owner=info.groupOwnerAddress
  try {
   val socket=if(info.isGroupOwner){
    val server=ctx.serverSocketFactory.createServerSocket() as SSLServerSocket
    track(server);server.needClientAuth=true;server.enabledProtocols=arrayOf("TLSv1.3");server.soTimeout=60000
    server.bind(InetSocketAddress(owner,39428),1);server.accept() as SSLSocket
   }else{val s=ctx.socketFactory.createSocket() as SSLSocket;track(s);s.connect(InetSocketAddress(owner,39428),8000);s}
   track(socket);socket.soTimeout=10000;socket.enabledProtocols=arrayOf("TLSv1.3");socket.startHandshake()
   val input=DataInputStream(socket.inputStream);val out=DataOutputStream(socket.outputStream)
   if(exchange!=null){exchange(input,out);return "Message exchange finished. See delivery status below."}
   if(info.isGroupOwner) Probe.answer(input,out) else Probe.challenge(input,out)
   return "Authenticated probe exchanged. Test session closed."
  }finally{close()}
 }
 // Through a relay phone (the group owner running RelayHub): ask it to join us with [pin], then run pinned mutual
 // TLS 1.3 end to end over the joined stream. The relay sees ciphertext only. The lower fingerprint is the TLS client.
 fun relayed(info:WifiP2pInfo,pin:String,exchange:(DataInputStream,DataOutputStream)->Unit):String{
  require(info.groupFormed && !info.isGroupOwner);val me=Identity.fingerprint();require(pin.matches(Regex("[0-9a-f]{64}")) && pin!=me)
  try{
   val raw=Socket();track(raw);raw.connect(InetSocketAddress(info.groupOwnerAddress,RELAY_PORT),8000);raw.tcpNoDelay=true;raw.soTimeout=70000
   val o=DataOutputStream(raw.getOutputStream());o.writeInt(RELAY_MAGIC);o.write(me.toByteArray(Charsets.US_ASCII));o.write(pin.toByteArray(Charsets.US_ASCII));o.flush()
   require(raw.getInputStream().read()==1){"Relay refused or contact not there"}
   val s=Identity.tls(pin).socketFactory.createSocket(raw,info.groupOwnerAddress.hostAddress,RELAY_PORT,true) as SSLSocket
   track(s);s.useClientMode=me<pin;if(!s.useClientMode)s.needClientAuth=true;s.enabledProtocols=arrayOf("TLSv1.3");s.soTimeout=10000;s.startHandshake()
   exchange(DataInputStream(s.inputStream),DataOutputStream(s.outputStream));return "Relayed session finished"
  }finally{close()}
 }
 // Over an ordinary Wi-Fi network or any phone's built-in hotspot: the hotspot phone forwards packets between its
 // clients without NODELINK. The higher fingerprint listens; the lower one connects to the address its beacon announced.
 fun lan(pin:String,exchange:(DataInputStream,DataOutputStream)->Unit):String{
  val me=Identity.fingerprint();require(pin.matches(Regex("[0-9a-f]{64}")) && pin!=me);val ctx=Identity.tls(pin)
  try{
   val socket=if(me>pin){val server=ctx.serverSocketFactory.createServerSocket() as SSLServerSocket
    track(server);server.reuseAddress=true;server.needClientAuth=true;server.enabledProtocols=arrayOf("TLSv1.3");server.soTimeout=30000
    server.bind(InetSocketAddress(LanBeacon.LINK_PORT),1);server.accept() as SSLSocket}
   else{val addr=LanBeacon.find(pin,20000)?:error("Contact not seen on this Wi-Fi");val s=ctx.socketFactory.createSocket() as SSLSocket;track(s)
    LanBeacon.wifi()?.let{n->runCatching{n.bindSocket(s)}};s.connect(InetSocketAddress(addr,LanBeacon.LINK_PORT),8000);s}
   track(socket);socket.tcpNoDelay=true;socket.soTimeout=10000;socket.enabledProtocols=arrayOf("TLSv1.3");socket.startHandshake()
   exchange(DataInputStream(socket.inputStream),DataOutputStream(socket.outputStream));return "Wi-Fi session finished"
  }finally{close()}
 }
 companion object{const val RELAY_PORT=39430;const val RELAY_MAGIC=0x4e4c0501}
}