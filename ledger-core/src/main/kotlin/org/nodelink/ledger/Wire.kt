package org.nodelink.ledger

import java.io.*

// Binary framing for transactions, blocks and commit votes; shared by validators and, later, phones.
object Wire {
 const val TX=1;const val VOTE=3;const val GET=4;const val BLOCK=5;const val NONE=6
 private const val MAX=1 shl 16
 private fun DataInputStream.blob():ByteArray{val n=readInt();require(n in 0..MAX){"Bad length"};return ByteArray(n).also{readFully(it)}}
 private fun DataOutputStream.blob(b:ByteArray){writeInt(b.size);write(b)}
 private fun DataInputStream.count()=readInt().also{require(it in 0..1000){"Bad count"}}
 fun writeTx(o:DataOutputStream,t:Tx){o.writeUTF(t.chain);o.blob(t.from);o.writeUTF(t.to);o.writeLong(t.amount);o.writeLong(t.fee);o.writeLong(t.nonce);o.blob(t.sig)}
 fun readTx(i:DataInputStream)=Tx(i.readUTF(),i.blob(),i.readUTF(),i.readLong(),i.readLong(),i.readLong(),i.blob())
 fun writeBlock(o:DataOutputStream,b:Block){o.writeUTF(b.chain);o.writeLong(b.height);o.writeUTF(b.prev);o.writeLong(b.time);o.writeInt(b.txs.size);b.txs.forEach{writeTx(o,it)}}
 fun readBlock(i:DataInputStream):Block{val c=i.readUTF();val h=i.readLong();val p=i.readUTF();val t=i.readLong();return Block(c,h,p,t,List(i.count()){readTx(i)})}
 fun writeVotes(o:DataOutputStream,v:List<Vote>){o.writeInt(v.size);v.forEach{o.blob(it.validator);o.blob(it.sig)}}
 fun readVotes(i:DataInputStream)=List(i.count()){Vote(i.blob(),i.blob())}
}