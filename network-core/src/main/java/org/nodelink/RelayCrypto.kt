package org.nodelink

import java.io.*
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.*

// Card: identity public key + relay encryption key, signed by the identity key. Safe to gossip.
class Card(val identity:ByteArray,val agree:ByteArray,val sig:ByteArray){fun fp()=RelayCrypto.hex(identity)}
// Relays see only id, destination fingerprint, expiry and ciphertext. No hop limit.
class Envelope(val id:UUID,val dest:String,val until:Long,val box:ByteArray)
// kind 0 = message, kind 1 = delivery receipt for msgId. Sender card travels inside so receipts can be returned.
class Opened(val from:Card,val msgId:UUID,val kind:Int,val text:String)

object RelayCrypto{
 fun hex(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 private fun pub(b:ByteArray)=(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(b)) as ECPublicKey).also{require(it.params.order.bitLength()==256)}
 fun cardBody(agree:ByteArray)="NLCARD1".toByteArray()+agree
 fun verify(c:Card)=runCatching{pub(c.agree);Signature.getInstance("SHA256withECDSA").run{initVerify(pub(c.identity));update(cardBody(c.agree));verify(c.sig)}}.getOrDefault(false)
 fun meta(id:UUID,dest:String,until:Long)=ByteArrayOutputStream().also{DataOutputStream(it).run{writeBytes("NLENV2");writeLong(id.mostSignificantBits);writeLong(id.leastSignificantBits);writeBytes(dest);writeLong(until)}}.toByteArray()
 private fun signed(m:ByteArray,msgId:UUID,kind:Int,text:ByteArray)=m+ByteArrayOutputStream().also{DataOutputStream(it).run{writeLong(msgId.mostSignificantBits);writeLong(msgId.leastSignificantBits);writeByte(kind)}}.toByteArray()+text
 private fun aes(shared:ByteArray,eph:ByteArray,to:ByteArray)=SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("NLRELAY2".toByteArray()+shared+eph+to),"AES")
 private fun put(o:DataOutputStream,b:ByteArray){o.writeShort(b.size);o.write(b)}
 private fun take(i:DataInputStream,max:Int)=ByteArray(i.readUnsignedShort().also{require(it in 1..max)}).also{i.readFully(it)}
 fun seal(to:Card,from:Card,sign:(ByteArray)->ByteArray,id:UUID,until:Long,msgId:UUID,kind:Int,body:String):ByteArray{
  require(verify(to)){"Invalid recipient key"};require(kind in 0..1);val text=body.toByteArray(Charsets.UTF_8);require(text.size in 1..4096){"Message must be 1-4096 bytes"}
  val m=meta(id,to.fp(),until);val sig=sign(signed(m,msgId,kind,text))
  val kp=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
  val shared=KeyAgreement.getInstance("ECDH").run{init(kp.private);doPhase(pub(to.agree),true);generateSecret()}
  val plain=ByteArrayOutputStream().also{val o=DataOutputStream(it);put(o,from.identity);put(o,from.agree);put(o,from.sig);o.writeLong(msgId.mostSignificantBits);o.writeLong(msgId.leastSignificantBits);o.writeByte(kind);put(o,sig);put(o,text)}.toByteArray()
  val iv=ByteArray(12).also{SecureRandom().nextBytes(it)};val c=Cipher.getInstance("AES/GCM/NoPadding")
  c.init(Cipher.ENCRYPT_MODE,aes(shared,kp.public.encoded,to.agree),GCMParameterSpec(128,iv));c.updateAAD(m)
  return ByteArrayOutputStream().also{val o=DataOutputStream(it);put(o,kp.public.encoded);o.write(iv);o.write(c.doFinal(plain))}.toByteArray()
 }
 fun open(own:PrivateKey,ownAgree:ByteArray,e:Envelope):Opened{
  val i=DataInputStream(e.box.inputStream());val eph=take(i,256);val iv=ByteArray(12).also{i.readFully(it)};val rest=i.readBytes()
  val shared=KeyAgreement.getInstance("ECDH").run{init(own);doPhase(pub(eph),true);generateSecret()}
  val m=meta(e.id,e.dest,e.until);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,aes(shared,eph,ownAgree),GCMParameterSpec(128,iv));c.updateAAD(m)
  val p=DataInputStream(c.doFinal(rest).inputStream());val from=Card(take(p,512),take(p,512),take(p,128));require(verify(from)){"Sender card invalid"}
  val msgId=UUID(p.readLong(),p.readLong());val kind=p.readUnsignedByte();require(kind in 0..1);val sig=take(p,128);val text=take(p,4096)
  require(Signature.getInstance("SHA256withECDSA").run{initVerify(pub(from.identity));update(signed(m,msgId,kind,text));verify(sig)}){"Sender signature invalid"}
  return Opened(from,msgId,kind,Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(text)).toString())
 }
}