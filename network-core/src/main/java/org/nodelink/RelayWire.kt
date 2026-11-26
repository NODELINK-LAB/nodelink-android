package org.nodelink

import java.io.*
import java.util.UUID

// Pass-on relay: offer ids, peer picks unknown ones, each is ACKed after durable storage. Unlimited hops.
object RelayWire{
 private const val CARDS=0x4e4c0403;private const val ACK=0x4e4c0402
 private fun put(o:DataOutputStream,b:ByteArray){o.writeShort(b.size);o.write(b)}
 private fun take(i:DataInputStream,max:Int)=ByteArray(i.readUnsignedShort().also{require(it in 1..max)}).also{i.readFully(it)}
 private fun id(o:DataOutputStream,u:UUID){o.writeLong(u.mostSignificantBits);o.writeLong(u.leastSignificantBits)}
 fun writeCards(o:DataOutputStream,cards:List<Card>){require(cards.size<=64);o.writeInt(CARDS);o.writeShort(cards.size);cards.forEach{put(o,it.identity);put(o,it.agree);put(o,it.sig)};o.flush()}
 fun readCards(i:DataInputStream):List<Card>{require(i.readInt()==CARDS){"Peer is not in mesh mode"};val n=i.readUnsignedShort();require(n<=64);return List(n){Card(take(i,512),take(i,512),take(i,128))}.filter(RelayCrypto::verify)}
 fun send(items:List<Envelope>,i:DataInputStream,o:DataOutputStream,handed:(Envelope)->Unit){
  require(items.size<=64);o.writeShort(items.size);items.forEach{id(o,it.id)};o.flush()
  val want=List(items.size){i.readBoolean()}
  items.filterIndexed{k,_->want[k]}.forEach{e->id(o,e.id);o.writeBytes(e.dest);o.writeLong(e.until);o.writeInt(e.box.size);o.write(e.box);o.flush()
   require(i.readInt()==ACK && UUID(i.readLong(),i.readLong())==e.id){"Relay acknowledgment mismatch"};handed(e)}
 }
 fun receive(i:DataInputStream,o:DataOutputStream,have:(UUID)->Boolean,save:(Envelope)->Unit){
  val n=i.readUnsignedShort();require(n<=64);val ids=List(n){UUID(i.readLong(),i.readLong())};require(ids.toSet().size==n)
  val wanted=ids.filter{!have(it)}.toSet();ids.forEach{o.writeBoolean(it in wanted)};o.flush()
  ids.filter{it in wanted}.forEach{expected->
   require(UUID(i.readLong(),i.readLong())==expected){"Unexpected envelope"}
   val dest=ByteArray(64).also{i.readFully(it)}.toString(Charsets.US_ASCII);require(dest.matches(Regex("[0-9a-f]{64}")))
   val until=i.readLong();val now=System.currentTimeMillis();require(until in (now+1)..(now+MessageWire.LIFE+60000)){"Expired envelope or clock difference"}
   val size=i.readInt();require(size in 1..8192)
   val box=ByteArray(size).also{i.readFully(it)};save(Envelope(expected,dest,until,box))
   o.writeInt(ACK);id(o,expected);o.flush()
  }
 }
}