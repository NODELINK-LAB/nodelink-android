package org.nodelink

import android.net.wifi.p2p.WifiP2pInfo
import android.os.SystemClock
import java.io.*
import java.util.concurrent.*

// Always-on authenticated link to the selected contact while both phones share a Wi-Fi Direct group.
// One pinned mutual-TLS session carries keepalives (a dead link is noticed within 10 s), ringing, accept/decline,
// the voice call itself, and message and mesh exchanges. It reconnects while the group exists and gives up after
// 3 minutes without a successful handshake (wrong contact, or NODELINK not open on the other phone).
// Frame order is safe because a side stops sending keepalives before it hands the stream to a call or an exchange.
class Link(private val begin:()->Session?,private val end:(Session)->Unit,private val ui:Events){
 interface Events{fun state(s:String);fun ringing(peer:String);fun ringStop(why:String);fun callLive();fun callEnded()
  fun exchange(kind:Int,peer:String,owner:Boolean,i:DataInputStream,o:DataOutputStream):String}
 private enum class Mode{IDLE,CALLING,RINGING,ACCEPTED,BUSY}
 companion object{const val MESSAGES=1;const val MESH=2
  private const val MAGIC=0x4e4c0401;private const val PING=1;private const val RING=2;private const val ACCEPT=3;private const val DECLINE=4
  private const val CANCEL=5;private const val JOB=6;private const val JOB_GO=7;private const val JOB_NO=8;private const val CALL_GO=9}
 private val tx=Executors.newSingleThreadScheduledExecutor()
 @Volatile private var current:Session?=null;@Volatile private var out:DataOutputStream?=null;@Volatile private var mode=Mode.IDLE
 @Volatile private var wanted=false;@Volatile private var gen=0;@Volatile private var info:WifiP2pInfo?=null;@Volatile private var requested=0;@Volatile private var ringId=0
 @Volatile var peer="";private set
 val connected get()=out!=null
 fun owns(s:Session?)=s!=null && s===current

 @Volatile var lan=false;private set   // true: over an ordinary Wi-Fi network or a phone's hotspot (LanBeacon)
 fun start(i:WifiP2pInfo,pin:String)=go(pin,false){info=i}
 fun startLan(pin:String)=go(pin,true){info=null}
 private fun go(pin:String,l:Boolean,set:()->Unit){if(Blocklist.has(pin)){ui.state("This contact is blocked");return}
  val same=wanted && peer==pin && lan==l;set();if(same)return;stop();lan=l;if(l)LanBeacon.start();peer=pin;wanted=true;val g=gen;Thread{loop(g)}.start()}
 fun stop(){wanted=false;gen++;current?.close();if(lan){LanBeacon.stop();lan=false}}
 fun ring()=act{if(out==null||mode!=Mode.IDLE){ui.state("Not linked yet, or busy");return@act};mode=Mode.CALLING;frame(RING);ui.state("Ringing…");val id=++ringId
  tx.schedule(Runnable{act{if(mode==Mode.CALLING && ringId==id){mode=Mode.IDLE;frame(CANCEL);ui.ringStop("No answer")}}},45,TimeUnit.SECONDS)}
 fun accept()=act{if(mode==Mode.RINGING){mode=Mode.ACCEPTED;frame(ACCEPT)}}
 fun decline()=act{if(mode==Mode.RINGING){mode=Mode.IDLE;frame(DECLINE);ui.ringStop("Declined")}}
 fun hangUp()=act{when(mode){Mode.CALLING->{mode=Mode.IDLE;frame(CANCEL);ui.ringStop("Cancelled")};Mode.RINGING->{mode=Mode.IDLE;frame(DECLINE);ui.ringStop("Declined")};Mode.ACCEPTED->current?.close();else->ui.state("No call")}}
 fun job(kind:Int)=act{if(out!=null && mode==Mode.IDLE){mode=Mode.BUSY;requested=kind;frame(JOB,kind)}}

 private fun act(f:()->Unit){tx.execute{runCatching{synchronized(this){f()}}}}   // UI actions never touch the network on the main thread
 private fun frame(t:Int,k:Int?=null)=synchronized(this){val o=out?:return@synchronized
  if(t==PING && mode!=Mode.IDLE && mode!=Mode.CALLING && mode!=Mode.RINGING)return@synchronized
  o.writeByte(t);if(k!=null)o.writeByte(k);o.flush()}

 private fun loop(g:Int){var lastOk=SystemClock.elapsedRealtime()
  var relay=false   // a group client alternates: direct to the group owner, then through the group owner's relay (RelayHub)
  while(wanted && g==gen){val l=lan;val i=info;if(i==null && !l)break;val s=begin();if(s==null){Thread.sleep(2000);continue}
   current=s;ui.state(when{l->"Link: looking for the contact on this Wi-Fi or hotspot";i!!.isGroupOwner->"Link: waiting for the other phone";relay->"Link: looking for the contact through the relay phone";else->"Link: connecting"});var talked=false
   val r=relay;runCatching{when{l->s.lan(peer){a,b->talked=true;talk(Identity.fingerprint()>peer,a,b,"over Wi-Fi/hotspot")}
    r->s.relayed(i!!,peer){a,b->talked=true;talk(Identity.fingerprint()>peer,a,b,"via relay")}
    else->s.probe(i!!,peer){a,b->talked=true;talk(i.isGroupOwner,a,b,"direct")}}}
   if(!talked && !l && !i!!.isGroupOwner)relay=!relay
   s.close();synchronized(this){out=null;current=null};end(s);if(talked)lastOk=SystemClock.elapsedRealtime()
   when(mode){Mode.ACCEPTED->ui.callEnded();Mode.CALLING,Mode.RINGING->ui.ringStop("Link lost");else->{}};mode=Mode.IDLE;requested=0
   if(SystemClock.elapsedRealtime()-lastOk>180000){wanted=false;ui.state("Link stopped: the connected phone is not the selected contact, or NODELINK is not open there. Tap Call to retry.");break}
   if(wanted && g==gen){ui.state("Link lost, reconnecting…");Thread.sleep(if(l)2000 else if(i!!.isGroupOwner)300 else 3000)}}
 }

 private fun talk(owner:Boolean,i:DataInputStream,o:DataOutputStream,via:String){
  o.writeInt(MAGIC);o.flush();require(i.readInt()==MAGIC){"Peer is not in link mode"}
  synchronized(this){mode=Mode.IDLE;out=o};ui.state("Linked $via · calls ring and messages go automatically");job(MESSAGES)
  val pinger=Thread{try{while(true){Thread.sleep(3000);frame(PING)}}catch(_:Exception){}}.apply{isDaemon=true;start()}
  try{while(true)when(val t=i.readUnsignedByte()){
   PING->{}
   RING->synchronized(this){if(mode==Mode.IDLE){mode=Mode.RINGING;ui.ringing(peer)}else frame(DECLINE)}
   CANCEL->synchronized(this){if(mode==Mode.RINGING||mode==Mode.ACCEPTED){mode=Mode.IDLE;ui.ringStop("Missed call")}}
   DECLINE->synchronized(this){if(mode==Mode.CALLING){mode=Mode.IDLE;ui.ringStop("Declined or busy")}}
   ACCEPT->{val go=synchronized(this){if(mode==Mode.CALLING){mode=Mode.ACCEPTED;frame(CALL_GO);true}else false};if(go)voice(i,o)}
   CALL_GO->{require(mode==Mode.ACCEPTED){"Unexpected call start"};voice(i,o)}
   JOB->{val k=i.readUnsignedByte()
    val go=synchronized(this){when{mode==Mode.BUSY && requested!=0 && !owner->false   // both asked at once: the group owner answers
     mode==Mode.IDLE||(mode==Mode.BUSY && requested!=0)->{mode=Mode.BUSY;frame(JOB_GO,k);true}
     else->{frame(JOB_NO);false}}}
    if(go)work(k,owner,i,o)}
   JOB_GO->work(i.readUnsignedByte(),owner,i,o)
   JOB_NO->synchronized(this){if(mode==Mode.BUSY){mode=Mode.IDLE;requested=0}}
   else->error("Unknown link frame $t")}
  }finally{pinger.interrupt()}
 }
 private fun voice(i:DataInputStream,o:DataOutputStream){ui.callLive();VoiceCall.run(i,o)}   // ends by exception when either side hangs up
 private fun work(k:Int,owner:Boolean,i:DataInputStream,o:DataOutputStream){try{ui.state(ui.exchange(k,peer,owner,i,o))}finally{synchronized(this){mode=Mode.IDLE;requested=0}}}
}