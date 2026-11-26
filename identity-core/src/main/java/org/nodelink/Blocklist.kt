package org.nodelink

import android.content.*
import java.io.*
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

// Blocked identities and revocation codes. A blocked fingerprint cannot open any link, relay or mesh session with this
// phone (checked inside the TLS handshake), its messages are dropped and nothing can be sent to it.
// A revocation code is signed by the identity it revokes, so only its owner can make one; codes travel with every mesh
// sync and every phone that sees a valid code blocks that identity. Create the code in advance and keep it off the phone.
object Blocklist {
 private const val TAG="NODELINK-REVOKE-v1:";private const val PREFIX="nodelink-revoke:v1:"
 private lateinit var p:SharedPreferences
 fun load(c:Context){if(!::p.isInitialized)p=c.applicationContext.getSharedPreferences("trust",Context.MODE_PRIVATE)}
 private fun map():Map<String,String> =if(::p.isInitialized)p.getStringSet("blocked",emptySet())!!.associate{it.substringBefore(' ') to it.substringAfter(' ',"")} else emptyMap()
 fun has(fp:String)=map().containsKey(fp.lowercase())
 fun list()=map()
 @Synchronized fun block(fp:String,why:String){val f=fp.lowercase();require(f.matches(Regex("[0-9a-f]{64}")));p.edit().putStringSet("blocked",map().filterKeys{it!=f}.map{"${it.key} ${it.value}"}.toSet()+"$f $why").apply()}
 @Synchronized fun unblock(fp:String){p.edit().putStringSet("blocked",map().filterKeys{it!=fp}.map{"${it.key} ${it.value}"}.toSet()).apply()}
 fun codes():List<String> =if(::p.isInitialized)p.getStringSet("codes",emptySet())!!.toList() else emptyList()
 fun create():String{val pub=Identity.publicKey();val e=Base64.getEncoder()
  return PREFIX+e.encodeToString(pub)+":"+e.encodeToString(Identity.sign((TAG+Identity.digest(pub)).toByteArray()))}
 // Verifies a code, blocks the identity it revokes and keeps the code for gossip. Returns the revoked fingerprint.
 @Synchronized fun accept(code:String):String{val c=code.trim();require(c.startsWith(PREFIX) && c.length<1000){"Not a NODELINK revocation code"}
  val parts=c.removePrefix(PREFIX).split(":");require(parts.size==2){"Damaged revocation code"}
  val pub=Base64.getDecoder().decode(parts[0]);val fp=Identity.digest(pub)
  val ok=Signature.getInstance("SHA256withECDSA").run{initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pub)));update((TAG+fp).toByteArray());verify(Base64.getDecoder().decode(parts[1]))}
  require(ok){"Revocation signature is invalid"}
  if(!codes().contains(c))p.edit().putStringSet("codes",(codes()+c).takeLast(256).toSet()).apply()
  block(fp,"revoked by its owner");return fp}
 fun writeCodes(o:DataOutputStream,list:List<String>){o.writeInt(list.size);list.forEach{o.writeUTF(it)};o.flush()}
 fun readCodes(i:DataInputStream){val n=i.readInt();require(n in 0..64){"Too many revocations"};repeat(n){val c=i.readUTF();runCatching{accept(c)}}}
}