package org.nodelink.ledger

import org.junit.Assert.*
import org.junit.Test
import java.security.*
import java.security.spec.ECGenParameterSpec

class LedgerTest {
 private fun key()=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
 private fun sign(k:KeyPair,b:ByteArray)=Signature.getInstance("SHA256withECDSA").run{initSign(k.private);update(b);sign()}
 private val vals=List(4){key()};private val alice=key();private val bob=key()
 private val a=Codec.address(alice.public.encoded);private val b=Codec.address(bob.public.encoded)
 private fun ledger()=Ledger(Genesis("test",vals.map{it.public.encoded},mapOf(a to 1000L)))
 private fun tx(amount:Long,nonce:Long,k:KeyPair=alice)=Tx("test",k.public.encoded,b,amount,1,nonce).let{it.copy(sig=sign(k,it.body()))}
 private fun block(l:Ledger,vararg t:Tx)=Block("test",l.height+1,l.tip,0,t.toList())
 private fun votes(bl:Block,n:Int)=vals.take(n).map{Vote(it.public.encoded,sign(it,bl.voteBody()))}
 private fun fails(f:()->Unit)=assertTrue(runCatching(f).isFailure)

 @Test fun transferWithQuorum(){val l=ledger();val bl=block(l,tx(100,0));l.apply(bl,votes(bl,3));assertEquals(899,l.balance(a));assertEquals(100,l.balance(b));assertEquals(1,l.height)}
 @Test fun twoOfFourIsNotFinal(){val l=ledger();val bl=block(l,tx(100,0));fails{l.apply(bl,votes(bl,2))};assertEquals(1000,l.balance(a))}
 @Test fun duplicateVotesDoNotCount(){val l=ledger();val bl=block(l,tx(100,0));val v=votes(bl,2);fails{l.apply(bl,v+v)}}
 @Test fun replayRejected(){val l=ledger();val t=tx(100,0);val b1=block(l,t);l.apply(b1,votes(b1,3));val b2=block(l,t);fails{l.apply(b2,votes(b2,3))};assertEquals(899,l.balance(a))}
 @Test fun overspendRejected(){val l=ledger();val bl=block(l,tx(1000,0));fails{l.apply(bl,votes(bl,4))}}
 @Test fun tamperedAmountRejected(){val l=ledger();val bl=block(l,tx(10,0).copy(amount=900));fails{l.apply(bl,votes(bl,4))}}
 @Test fun badBlockChangesNothing(){val l=ledger();val bl=block(l,tx(10,0),tx(5000,1));fails{l.apply(bl,votes(bl,4))};assertEquals(1000,l.balance(a));assertEquals(0,l.height)}
 @Test fun wrongParentRejected(){val l=ledger();val bl=Block("test",1,"00",0,listOf(tx(1,0)));fails{l.apply(bl,votes(bl,4))}}
 @Test fun outsiderVotesIgnored(){val l=ledger();val bl=block(l,tx(1,0));val fake=List(3){key()}.map{Vote(it.public.encoded,sign(it,bl.voteBody()))};fails{l.apply(bl,fake)}}
}