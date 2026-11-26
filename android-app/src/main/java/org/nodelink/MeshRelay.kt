package org.nodelink

import android.content.Context
import android.util.Base64
import org.json.*
import java.io.*
import java.security.*
import java.security.spec.*
import java.util.UUID

class MeshRelay(context:Context,private val mail:Mailbox){
 private val RETRY=6L*60*60*1000
 private val vault=Vault(context,"relay")
 private fun b(x:ByteArray)=Base64.encodeToString(x,Base64.NO_WRAP);private fun u(s:String)=Base64.decode(s,Base64.NO_WRAP)
 private val agree:KeyPair=vault.get("agree")?.let{val j=JSONObject(it);val f=KeyFactory.getInstance("EC");KeyPair(f.generatePublic(X509EncodedKeySpec(u(j.getString("pub")))),f.generatePrivate(PKCS8EncodedKeySpec(u(j.getString("priv")))))}
  ?: KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair().also{vault.put("agree",JSONObject().put("pub",b(it.public.encoded)).put("priv",b(it.private.encoded)).toString())}
 private val me=Identity.fingerprint()
 private val card=Card(Identity.publicKey(),agree.public.encoded,Identity.sign(RelayCrypto.cardBody(agree.public.encoded)))
 private fun arr(k:String)=JSONArray(vault.get(k)?:"[]").let{a->(0 until a.length()).map{a.getJSONObject(it)}}
 private fun live(k:String)=arr(k).filter{it.getLong("until")>System.currentTimeMillis()}
 private fun save(k:String,list:List<JSONObject>)=vault.put(k,JSONArray(list).toString())
 private fun contacts()=arr("contacts").map{Card(u(it.getString("i")),u(it.getString("a")),u(it.getString("s")))}
 private fun cardOf(fp:String)=contacts().firstOrNull{it.fp()==fp}
 private fun env(j:JSONObject)=Envelope(UUID.fromString(j.getString("id")),j.getString("dest"),j.getLong("until"),u(j.getString("box")))
 private fun json(e:Envelope)=JSONObject().put("id",e.id.toString()).put("dest",e.dest).put("until",e.until).put("box",b(e.box))
 private fun seenAdd(id:UUID,until:Long)=save("seen",(live("seen")+JSONObject().put("id",id.toString()).put("until",until)).takeLast(2048))
 @Synchronized fun status()="Known keys: ${contacts().size} · Waiting to pass on: ${live("queue").size} · Awaiting receipt: ${live("mine").size}"
 @Synchronized private fun learn(cards:List<Card>){val all=contacts().associateBy{it.fp()}.toMutableMap();cards.filter{it.fp()!=me}.forEach{all.remove(it.fp());all[it.fp()]=it};save("contacts",all.values.toList().takeLast(512).map{JSONObject().put("i",b(it.identity)).put("a",b(it.agree)).put("s",b(it.sig))})}
 // Own messages are tagged with msg so a retry replaces the earlier local copy instead of duplicating it.
 private fun enqueue(to:Card,msgId:UUID,until:Long,kind:Int,text:String){
  val q=live("queue").filter{kind==1||it.optString("msg")!=msgId.toString()};require(q.size<512){"Relay queue full"};val id=UUID.randomUUID()
  save("queue",q+json(Envelope(id,to.fp(),until,RelayCrypto.seal(to,card,Identity::sign,id,until,msgId,kind,text))).apply{if(kind==0)put("msg",msgId.toString())})
 }
 @Synchronized fun compose(dest:String,text:String){
  val d=dest.trim().lowercase();require(d.matches(Regex("[0-9a-f]{64}")) && d!=me){"Enter the recipient's full fingerprint"}
  val to=cardOf(d)?:error("No verified key for this recipient yet. Sync with a phone that has met them.")
  val msgId=UUID.randomUUID();val until=System.currentTimeMillis()+MessageWire.LIFE
  enqueue(to,msgId,until,0,text);mail.add(d,Note(msgId,until,text),"Sent via mesh")
  save("mine",live("mine")+JSONObject().put("id",msgId.toString()).put("dest",d).put("until",until).put("text",text).put("sent",System.currentTimeMillis()))
 }
 // Re-seals undelivered messages under a fresh envelope id (relays refuse ids they already carried).
 @Synchronized fun retry(force:Boolean):Int{val now=System.currentTimeMillis();var n=0
  val mine=live("mine").onEach{j->val to=cardOf(j.getString("dest"));if(to!=null && (force||now-j.getLong("sent")>=RETRY) && runCatching{enqueue(to,UUID.fromString(j.getString("id")),j.getLong("until"),0,j.getString("text"))}.isSuccess){n++;j.put("sent",now)}}
  save("mine",mine);return n}
 @Synchronized private fun has(id:UUID)=(live("queue")+live("seen")).any{it.getString("id")==id.toString()}
 @Synchronized private fun store(e:Envelope){
  if(e.dest!=me){val q=live("queue");require(q.size<512){"Relay queue full"};save("queue",q+json(e));return}
  val o=runCatching{RelayCrypto.open(agree.private,agree.public.encoded,e)}.getOrNull();val from=o?.from?.fp()
  if(o!=null && from!=me){learn(listOf(o.from))
   if(o.kind==0){mail.add(from!!,Note(o.msgId,e.until,o.text),"Received via mesh");runCatching{enqueue(o.from,o.msgId,e.until,1,"delivered")}}
   else{save("mine",live("mine").filter{it.getString("id")!=o.msgId.toString()||it.getString("dest")!=from});mail.mark(from!!,o.msgId,"Delivered via mesh")}}
  seenAdd(e.id,e.until)
 }
 // After one ACKed handoff the copy is deleted and its id remembered, so this phone never carries it again.
 @Synchronized private fun passedOn(e:Envelope){save("queue",live("queue").filter{it.getString("id")!=e.id.toString()});seenAdd(e.id,e.until)}
 @Synchronized private fun outbox(peer:String)=live("queue").map(::env).sortedBy{if(it.dest==peer)0 else 1}.take(64)
 fun sync(peer:String,owner:Boolean,i:DataInputStream,o:DataOutputStream):String{
  val p=peer.trim().lowercase();retry(false);val mine=listOf(card)+contacts().take(63);var got=0;var gave=0
  learn(if(owner)RelayWire.readCards(i).also{RelayWire.writeCards(o,mine)} else {RelayWire.writeCards(o,mine);RelayWire.readCards(i)})
  val rv=Blocklist.codes().take(64);if(owner){Blocklist.readCodes(i);Blocklist.writeCodes(o,rv)}else{Blocklist.writeCodes(o,rv);Blocklist.readCodes(i)}   // revocations spread with every sync
  val recv={RelayWire.receive(i,o,::has){store(it);got++}};val send={RelayWire.send(outbox(p),i,o){gave++;passedOn(it)}}
  if(owner){recv();send()}else{send();recv()}
  return "Mesh sync done · received $got, handed over $gave"
 }
}