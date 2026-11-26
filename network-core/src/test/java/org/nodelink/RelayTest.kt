package org.nodelink

import org.junit.Assert.*
import org.junit.Test
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class RelayTest {
 private fun kp()=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
 private fun signer(k:PrivateKey)={b:ByteArray->Signature.getInstance("SHA256withECDSA").run{initSign(k);update(b);sign()}}
 private fun card(id:KeyPair,agree:KeyPair)=Card(id.public.encoded,agree.public.encoded,signer(id.private)(RelayCrypto.cardBody(agree.public.encoded)))
 private val sender=kp();private val sagree=kp();private val rid=kp();private val ragree=kp()
 private val from=card(sender,sagree);private val to=card(rid,ragree);private val msg=UUID.randomUUID()
 private fun env(kind:Int=0):Envelope{val id=UUID.randomUUID();val until=System.currentTimeMillis()+60000;return Envelope(id,to.fp(),until,RelayCrypto.seal(to,from,signer(sender.private),id,until,msg,kind,"মেশ বার্তা"))}
 @Test fun recipientOpensAndLearnsSenderCard(){val o=RelayCrypto.open(ragree.private,ragree.public.encoded,env());assertEquals(from.fp(),o.from.fp());assertEquals(msg,o.msgId);assertEquals(0,o.kind);assertEquals("মেশ বার্তা",o.text);assertTrue(RelayCrypto.verify(o.from))}
 @Test fun receiptKindIsAuthenticated(){assertEquals(1,RelayCrypto.open(ragree.private,ragree.public.encoded,env(1)).kind)}
 @Test fun relayCannotAlterExpiry(){val e=env();assertThrows(Exception::class.java){RelayCrypto.open(ragree.private,ragree.public.encoded,Envelope(e.id,e.dest,e.until+1,e.box))}}
 @Test fun relayCannotRead(){val relay=kp();assertThrows(Exception::class.java){RelayCrypto.open(relay.private,relay.public.encoded,env())}}
 @Test fun forgedCardRejected(){assertTrue(RelayCrypto.verify(to));assertFalse(RelayCrypto.verify(Card(rid.public.encoded,kp().public.encoded,to.sig)))}
}