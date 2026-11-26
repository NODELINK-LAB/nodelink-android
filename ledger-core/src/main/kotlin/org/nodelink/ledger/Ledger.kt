package org.nodelink.ledger

import java.io.*
import java.security.*
import java.security.spec.X509EncodedKeySpec

// NDL ledger core: signed transfers, blocks finalized only by a 2/3 validator commit, account nonces against replay.
// Independent of the messaging transport. Pure JVM so it can run on phones and on validator nodes.
object Codec {
 fun sha(b:ByteArray):ByteArray=MessageDigest.getInstance("SHA-256").digest(b)
 fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it)}
 fun unhex(s:String)=s.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
 fun address(pub:ByteArray)="ndl"+hex(sha(pub)).take(40)
 fun verify(pub:ByteArray,body:ByteArray,sig:ByteArray)=runCatching{Signature.getInstance("SHA256withECDSA").run{initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pub)));update(body);verify(sig)}}.getOrDefault(false)
 fun bytes(f:DataOutputStream.()->Unit):ByteArray=ByteArrayOutputStream().also{DataOutputStream(it).f()}.toByteArray()
}
private fun DataOutputStream.blob(b:ByteArray){writeInt(b.size);write(b)}

data class Tx(val chain:String,val from:ByteArray,val to:String,val amount:Long,val fee:Long,val nonce:Long,val sig:ByteArray=ByteArray(0)){
 fun body()=Codec.bytes{writeUTF("NDL-TX-1");writeUTF(chain);blob(from);writeUTF(to);writeLong(amount);writeLong(fee);writeLong(nonce)}
 fun id()=Codec.hex(Codec.sha(body()+sig))
 fun sender()=Codec.address(from)
 fun valid()=amount>0 && fee>=0 && nonce>=0 && to.matches(Regex("ndl[0-9a-f]{40}")) && to!=sender() && Codec.verify(from,body(),sig)
}
data class Block(val chain:String,val height:Long,val prev:String,val time:Long,val txs:List<Tx>){
 fun hash()=Codec.hex(Codec.sha(Codec.bytes{writeUTF("NDL-BLOCK-1");writeUTF(chain);writeLong(height);writeUTF(prev);writeLong(time);writeInt(txs.size);txs.forEach{blob(it.body());blob(it.sig)}}))
 fun voteBody()=Codec.bytes{writeUTF("NDL-COMMIT-1");writeUTF(chain);writeUTF(hash())}
}
class Vote(val validator:ByteArray,val sig:ByteArray)
class Genesis(val chain:String,val validators:List<ByteArray>,val allocations:Map<String,Long>)
// genesis.txt: "chain <id>", "validator <hex public key>", "alloc <address> <amount>"; # starts a comment.
fun parseGenesis(text:String):Genesis{var chain="";val vals=mutableListOf<ByteArray>();val alloc=mutableMapOf<String,Long>()
 text.lines().map{it.trim()}.filter{it.isNotEmpty() && !it.startsWith("#")}.map{it.split(Regex("\\s+"))}.forEach{p->
  if(p[0]=="chain")chain=p[1] else if(p[0]=="validator")vals+=Codec.unhex(p[1]) else if(p[0]=="alloc")alloc[p[1]]=p[2].toLong() else error("Unknown genesis line ${p[0]}")}
 require(chain.isNotEmpty()){"Genesis needs a chain line"};return Genesis(chain,vals,alloc)}

class Ledger(private val g:Genesis){
 private val set=g.validators.map{Codec.hex(it)}.toSet()
 init{require(g.validators.size>=4){"Need at least 4 independent validators"};require(set.size==g.validators.size){"Duplicate validator"};require(g.allocations.values.all{it>=0})}
 val chain get()=g.chain
 var height=0L;private set
 var tip=Codec.hex(Codec.sha(g.chain.toByteArray()));private set
 private val balances=g.allocations.toMutableMap();private val nonces=mutableMapOf<String,Long>()
 fun balance(a:String)=balances[a]?:0L
 fun nextNonce(a:String)=nonces[a]?:0L
 private fun quorum(b:Block,votes:List<Vote>):Boolean{val body=b.voteBody()
  val signed=votes.filter{Codec.hex(it.validator) in set && Codec.verify(it.validator,body,it.sig)}.map{Codec.hex(it.validator)}.toSet()
  return signed.size*3>g.validators.size*2}
 // All-or-nothing: a block with any bad transaction changes nothing. Fees are burned.
 fun apply(b:Block,votes:List<Vote>){
  require(quorum(b,votes)){"No 2/3 validator commit"};val (bal,non)=next(b)
  balances.clear();balances.putAll(bal);nonces.clear();nonces.putAll(non);height=b.height;tip=b.hash()
 }
 // Validates a candidate block against the current state without changing it (used by validators before voting).
 fun check(b:Block){next(b)}
 private fun next(b:Block):Pair<Map<String,Long>,Map<String,Long>>{
  require(b.chain==g.chain){"Wrong chain"};require(b.height==height+1){"Expected height ${height+1}"};require(b.prev==tip){"Block does not extend the tip"}
  require(b.txs.size<=1000){"Block too large"}
  val bal=balances.toMutableMap();val non=nonces.toMutableMap()
  b.txs.forEach{t->require(t.chain==g.chain && t.valid()){"Invalid transaction ${t.id()}"};val s=t.sender();require((non[s]?:0L)==t.nonce){"Bad nonce"}
   val cost=Math.addExact(t.amount,t.fee);require((bal[s]?:0L)>=cost){"Insufficient balance"}
   bal[s]=(bal[s]?:0L)-cost;bal[t.to]=Math.addExact(bal[t.to]?:0L,t.amount);non[s]=t.nonce+1}
  return bal to non
 }
}