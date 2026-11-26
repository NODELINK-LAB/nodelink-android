package org.nodelink

import java.io.*
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.*

// Real-time relay for two other phones that are both connected to this phone (this phone hosts the Wi-Fi Direct group).
// Each side says who it is and whom it wants; when both match, the two streams are joined byte for byte. The two ends
// then run pinned mutual TLS 1.3 through the join (Session.relayed), so this phone sees only ciphertext, sizes and timing.
// Claimed fingerprints are not trusted: a liar only fails the end-to-end TLS check. Limits: 8 waiting, 4 joined, 60 s wait.
class RelayHub(private val note:(String)->Unit){
 private class Waiting(val s:Socket,val me:String,val want:String)
 private val waiting=mutableListOf<Waiting>();@Volatile private var server:ServerSocket?=null
 private val pairs=AtomicInteger();private val bytes=AtomicLong()
 @Volatile var on=false;private set
 fun start(){if(on)return;on=true
  Thread{runCatching{ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(Session.RELAY_PORT),8)}.also{server=it}.use{srv->
   while(on){val s=srv.accept();Thread{runCatching{handle(s)}.onFailure{runCatching{s.close()}}}.start()}}}.onFailure{if(on)note("Relay stopped: ${it.message}")};on=false}.start()}
 fun stop(){on=false;runCatching{server?.close()};synchronized(waiting){waiting.forEach{runCatching{it.s.close()}};waiting.clear()}}
 fun status()="Relay ${if(on)"on" else "off"} · joined ${pairs.get()} · waiting ${synchronized(waiting){waiting.size}} · relayed ${bytes.get()/1024} KB"
 private fun handle(s:Socket){s.soTimeout=10000;val i=DataInputStream(s.getInputStream())
  require(i.readInt()==Session.RELAY_MAGIC){"Not a NODELINK relay request"}
  fun fp()=ByteArray(64).also{i.readFully(it)}.toString(Charsets.US_ASCII).also{require(it.matches(Regex("[0-9a-f]{64}")))}
  val me=fp();val want=fp();require(me!=want);require(!Blocklist.has(me) && !Blocklist.has(want)){"Blocked identity"};val w=Waiting(s,me,want)
  val mate=synchronized(waiting){waiting.removeAll{it.s.isClosed}
   val m=waiting.firstOrNull{it.me==want && it.want==me}
   if(m!=null){waiting.remove(m);if(pairs.get()>=4){m.s.close();error("Relay busy")}}
   else{waiting.filter{it.me==me && it.want==want}.forEach{runCatching{it.s.close()};waiting.remove(it)};require(waiting.size<8){"Relay queue full"};waiting.add(w)}
   m}
  if(mate==null){Thread.sleep(60000);synchronized(waiting){if(waiting.remove(w))s.close()};return}   // partner never came
  pairs.incrementAndGet();note("Relay: joined two phones")
  try{for(x in listOf(s,mate.s)){x.soTimeout=30000;x.tcpNoDelay=true;x.getOutputStream().apply{write(1);flush()}}
   val done=CountDownLatch(1)
   fun pipe(a:Socket,b:Socket)=Thread{runCatching{val buf=ByteArray(8192);val inp=a.getInputStream();val out=b.getOutputStream()
    while(true){val n=inp.read(buf);if(n<0)break;out.write(buf,0,n);out.flush();bytes.addAndGet(n.toLong())}};done.countDown()}.apply{start()}
   pipe(s,mate.s);pipe(mate.s,s);done.await()
  }finally{runCatching{s.close()};runCatching{mate.s.close()};pairs.decrementAndGet();note("Relay: a relayed session ended")}
 }
}