package org.nodelink

import java.io.*
import java.util.UUID

// Only run inside a pinned mutual-TLS channel. Receiver commits before ACK.
data class Note(val id:UUID,val until:Long,val text:String)
object MessageWire {
 const val LIFE=7L*24*60*60*1000
 fun send(items:List<Note>,i:DataInputStream,o:DataOutputStream,acked:(UUID)->Unit){
  require(items.size<=16);o.writeInt(0x4e4c0201);o.writeInt(items.size);o.flush()
  for(n in items){val bytes=n.text.toByteArray(Charsets.UTF_8);require(bytes.size in 1..4096)
   o.writeLong(n.id.mostSignificantBits);o.writeLong(n.id.leastSignificantBits);o.writeLong(n.until);o.writeInt(bytes.size);o.write(bytes);o.flush()
   require(i.readInt()==0x4e4c0202 && UUID(i.readLong(),i.readLong())==n.id){"Acknowledgment mismatch"};acked(n.id)
  }
 }
 fun receive(i:DataInputStream,o:DataOutputStream,save:(Note)->Unit):Int{
  require(i.readInt()==0x4e4c0201);val count=i.readInt();require(count in 0..16)
  repeat(count){val id=UUID(i.readLong(),i.readLong());val until=i.readLong();val size=i.readInt();require(size in 1..4096);require(until in (System.currentTimeMillis()+1)..(System.currentTimeMillis()+LIFE+60000)){"Expired message or clock difference"}
   val bytes=ByteArray(size);i.readFully(bytes);val body=Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();save(Note(id,until,body));o.writeInt(0x4e4c0202);o.writeLong(id.mostSignificantBits);o.writeLong(id.leastSignificantBits);o.flush()
  }
  return count
 }
}