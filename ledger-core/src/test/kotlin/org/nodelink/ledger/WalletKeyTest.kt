package org.nodelink.ledger

import org.junit.Assert.*
import org.junit.Test
import java.security.*

class WalletKeyTest {
 private val seed=ByteArray(32){it.toByte()}
 @Test fun derivedKeySignsAndVerifies(){val k=WalletKey.fromSeed(seed);val body="hello".toByteArray()
  val sig=Signature.getInstance("SHA256withECDSA").run{initSign(k.private);update(body);sign()};assertTrue(Codec.verify(k.public.encoded,body,sig))}
 @Test fun sameSeedSameAddress()=assertEquals(Codec.address(WalletKey.fromSeed(seed).public.encoded),Codec.address(WalletKey.fromSeed(seed.copyOf()).public.encoded))
 @Test fun differentSeedDifferentAddress(){val other=seed.copyOf().also{it[0]=9};assertNotEquals(Codec.address(WalletKey.fromSeed(seed).public.encoded),Codec.address(WalletKey.fromSeed(other).public.encoded))}
 @Test fun codeRoundTrip(){val c=WalletKey.code(seed);assertEquals(55+10,c.length);assertArrayEquals(seed,WalletKey.seedOf(c.lowercase().replace("-"," ")))}
 @Test fun typoDetected(){val c=WalletKey.code(seed).toCharArray();c[3]=if(c[3]=='A')'B' else 'A';assertTrue(runCatching{WalletKey.seedOf(String(c))}.isFailure)}
 @Test fun shortCodeRejected()=assertTrue(runCatching{WalletKey.seedOf("ABC")}.isFailure)
}