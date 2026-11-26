package org.nodelink.validator

import org.nodelink.ledger.*
import java.io.*
import java.net.*
import java.security.*
import java.security.spec.*
import java.util.concurrent.Executors

fun loadKey(dir:File):KeyPair{val f=KeyFactory.getInstance("EC");return KeyPair(f.generatePublic(X509EncodedKeySpec(File(dir,"key.public").readBytes())),f.generatePrivate(PKCS8EncodedKeySpec(File(dir,"key.private").readBytes())))}
fun sign(k:KeyPair,b:ByteArray):ByteArray=Signature.getInstance("SHA256withECDSA").run{initSign(k.private);update(b);sign()}
fun loadGenesis(f:File)=parseGenesis(f.readText())

// One validator. Safety rule: it signs at most one block per height, ever (recorded on disk before the vote leaves),
// so with at least 3f+1 validators and at most f faulty, two different blocks can never both reach a 2/3 commit.
class Node(private val dir:File){
 private val key=loadKey(dir);private val me=key.public.encoded;private val g=loadGenesis(File(dir,"genesis.txt"))
 private val peers=File(dir,"peers.txt").takeIf{it.exists()}?.readLines()?.map{it.trim()}?.filter{it.isNotEmpty()}?.map{val (h,p)=it.split(":");InetSocketAddress(h,p.toInt())}?:emptyList()
 private val ledger=Ledger(g);private val chainFile=File(dir,"chain.bin");private val votedFile=File(dir,"voted.txt")
 private val chain=mutableListOf<Pair<Block,List<Vote>>>();private val mempool=LinkedHashMap<String,Tx>();private val votes=HashMap<String,MutableMap<String,Vote>>()
 private var round=0;private var roundStart=System.currentTimeMillis();private var proposed=-1
 private val pool=Executors.newFixedThreadPool(8)
 init{require(g.validators.any{it.contentEquals(me)}){"This key is not a genesis validator"}
  if(chainFile.exists())DataInputStream(BufferedInputStream(FileInputStream(chainFile))).use{i->while(i.available()>0){val b=Wire.readBlock(i);val v=Wire.readVotes(i);ledger.apply(b,v);chain+=b to v}}
  println("Chain ${g.chain} at height ${ledger.height}")}
 private fun proposer(h:Long,r:Int)=g.validators[((h+r)%g.validators.size).toInt()]
 private fun votedHeight()=votedFile.takeIf{it.exists()}?.readText()?.split(" ")?.get(0)?.toLong()?:0L
 private fun send(a:InetSocketAddress,f:(DataOutputStream)->Unit)=pool.execute{runCatching{Socket().use{s->s.connect(a,3000);val o=DataOutputStream(BufferedOutputStream(s.getOutputStream()));f(o);o.flush()}}}
 private fun broadcast(b:Block,vs:List<Vote>)=peers.forEach{p->send(p){o->o.writeByte(Wire.VOTE);Wire.writeBlock(o,b);Wire.writeVotes(o,vs)}}
 @Synchronized fun onTx(t:Tx){require(t.chain==g.chain && t.valid()){"Invalid transaction"};require(t.nonce>=ledger.nextNonce(t.sender())){"Stale nonce"}
  if(mempool.size<10000 && mempool.putIfAbsent(t.id(),t)==null)peers.forEach{p->send(p){o->o.writeByte(Wire.TX);Wire.writeTx(o,t)}}}
 @Synchronized fun onVotes(b:Block,vs:List<Vote>){
  if(b.height!=ledger.height+1)return
  val h=b.hash();val body=b.voteBody();val set=votes.getOrPut(h){mutableMapOf()}
  vs.filter{v->g.validators.any{it.contentEquals(v.validator)} && Codec.verify(v.validator,body,v.sig)}.forEach{set[Codec.hex(it.validator)]=it}
  if(set.isEmpty()){votes.remove(h);return}
  val fromProposer=(0..round).any{set.containsKey(Codec.hex(proposer(b.height,it)))}
  if(fromProposer && votedHeight()<b.height && runCatching{ledger.check(b)}.isSuccess){
   votedFile.writeText("${b.height} $h");set[Codec.hex(me)]=Vote(me,sign(key,body));broadcast(b,set.values.toList())}
  if(set.size*3>g.validators.size*2)commit(b,set.values.toList())
 }
 @Synchronized fun commit(b:Block,vs:List<Vote>){
  if(b.height!=ledger.height+1)return
  ledger.apply(b,vs);chain+=b to vs
  DataOutputStream(FileOutputStream(chainFile,true)).use{Wire.writeBlock(it,b);Wire.writeVotes(it,vs)}
  mempool.values.removeIf{it.nonce<ledger.nextNonce(it.sender())};votes.clear();round=0;roundStart=System.currentTimeMillis();proposed=-1
  println("Committed height ${b.height}: ${b.txs.size} transaction(s), ${vs.size} votes");broadcast(b,vs)
 }
 @Synchronized private fun tick(){val now=System.currentTimeMillis()
  if(now-roundStart>15000){round++;roundStart=now}
  if(proposed==round || !proposer(ledger.height+1,round).contentEquals(me) || votedHeight()>ledger.height || mempool.isEmpty())return
  proposed=round;val txs=mutableListOf<Tx>()
  mempool.values.sortedBy{it.nonce}.forEach{t->if(txs.size<200 && runCatching{ledger.check(Block(g.chain,ledger.height+1,ledger.tip,now,txs+t))}.isSuccess)txs+=t}
  if(txs.isEmpty())return
  val b=Block(g.chain,ledger.height+1,ledger.tip,now,txs);println("Proposing height ${b.height} with ${txs.size} transaction(s)")
  onVotes(b,listOf(Vote(me,sign(key,b.voteBody()))))
 }
 // Catch-up: ask peers for the next committed block; it is applied only with a valid 2/3 commit.
 private fun fetch(a:InetSocketAddress)=runCatching{Socket().use{s->s.connect(a,3000);s.soTimeout=5000
  val o=DataOutputStream(s.getOutputStream());o.writeByte(Wire.GET);o.writeLong(ledger.height+1);o.flush()
  val i=DataInputStream(BufferedInputStream(s.getInputStream()));if(i.readByte().toInt()==Wire.BLOCK)commit(Wire.readBlock(i),Wire.readVotes(i))}}
 private fun handle(c:Socket){c.soTimeout=5000;val i=DataInputStream(BufferedInputStream(c.getInputStream()));val o=DataOutputStream(c.getOutputStream())
  when(i.readByte().toInt()){
   Wire.TX->o.writeBoolean(runCatching{onTx(Wire.readTx(i))}.isSuccess)
   Wire.VOTE->onVotes(Wire.readBlock(i),Wire.readVotes(i))
   Wire.GET->{val h=i.readLong();val e=synchronized(this){chain.getOrNull((h-1).toInt())};if(e==null)o.writeByte(Wire.NONE) else{o.writeByte(Wire.BLOCK);Wire.writeBlock(o,e.first);Wire.writeVotes(o,e.second)}}}
  o.flush()}
 fun run(port:Int){
  Thread{while(true){runCatching{tick()};Thread.sleep(1000)}}.apply{isDaemon=true}.start()
  Thread{while(true){peers.forEach{fetch(it)};Thread.sleep(5000)}}.apply{isDaemon=true}.start()
  println("Validator listening on port $port with ${peers.size} peer(s)")
  ServerSocket(port).use{s->while(true){val c=s.accept();pool.execute{c.use{runCatching{handle(it)}}}}}
 }
}

fun main(args:Array<String>){
 when(args.getOrNull(0)){
  "keygen"->{val dir=File(args[1]);dir.mkdirs();require(!File(dir,"key.private").exists()){"A key already exists here"}
   val k=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
   File(dir,"key.private").writeBytes(k.private.encoded);File(dir,"key.public").writeBytes(k.public.encoded)
   println("validator "+Codec.hex(k.public.encoded));println("address "+Codec.address(k.public.encoded))}
  "run"->Node(File(args[1])).run(args.getOrNull(2)?.toInt()?:39500)
  "transfer"->{val k=loadKey(File(args[1]));val (h,p)=args[2].split(":")
   val t=Tx(args[3],k.public.encoded,args[4],args[5].toLong(),1,args[6].toLong()).let{it.copy(sig=sign(k,it.body()))}
   Socket(h,p.toInt()).use{s->val o=DataOutputStream(s.getOutputStream());o.writeByte(Wire.TX);Wire.writeTx(o,t);o.flush();println(if(DataInputStream(s.getInputStream()).readBoolean())"Accepted into mempool: ${t.id()}" else "Rejected")}}
  else->println("usage: keygen <dir> | run <dir> [port] | transfer <keydir> <host:port> <chain> <to> <amount> <nonce>")
 }
}