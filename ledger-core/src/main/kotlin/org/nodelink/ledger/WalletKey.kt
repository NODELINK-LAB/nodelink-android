package org.nodelink.ledger

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*

// Recoverable wallet key: a 32-byte seed deterministically gives one P-256 key pair.
// The seed is written down as a 55-character recovery code with a 16-bit checksum against typos.
object WalletKey {
 private val p=BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",16)
 private val n=BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",16)
 private val a=p-BigInteger.valueOf(3)
 private val g=BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296",16) to BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",16)
 private const val ABC="0123456789ABCDEFGHJKMNPQRSTVWXYZ"
 private fun add(P:Pair<BigInteger,BigInteger>?,Q:Pair<BigInteger,BigInteger>?):Pair<BigInteger,BigInteger>?{if(P==null)return Q;if(Q==null)return P
  val (x1,y1)=P;val (x2,y2)=Q
  val l=if(x1==x2){if((y1+y2).mod(p)==ZERO)return null;(x1*x1*BigInteger.valueOf(3)+a)*(y1+y1).modInverse(p)}else (y2-y1)*(x2-x1).mod(p).modInverse(p)
  val m=l.mod(p);val x3=(m*m-x1-x2).mod(p);return x3 to (m*(x1-x3)-y1).mod(p)}
 private fun mul(k:BigInteger):Pair<BigInteger,BigInteger>{var r:Pair<BigInteger,BigInteger>?=null;for(i in k.bitLength()-1 downTo 0){r=add(r,r);if(k.testBit(i))r=add(r,g)};return r!!}
 private fun check(seed:ByteArray)=Codec.sha("NDL-CODE-1".toByteArray()+seed).copyOf(2)
 fun fromSeed(seed:ByteArray):KeyPair{require(seed.size==32)
  val d=BigInteger(1,Codec.sha("NDL-WALLET-1".toByteArray()+seed)).mod(n-ONE)+ONE;val (x,y)=mul(d)
  val params=(KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair().public as ECPublicKey).params
  val f=KeyFactory.getInstance("EC");return KeyPair(f.generatePublic(ECPublicKeySpec(ECPoint(x,y),params)),f.generatePrivate(ECPrivateKeySpec(d,params)))}
 fun code(seed:ByteArray):String{val b=seed+check(seed);var buf=0;var bits=0;val sb=StringBuilder()
  for(x in b){buf=(buf shl 8) or (x.toInt() and 255);bits+=8;while(bits>=5){sb.append(ABC[(buf shr (bits-5)) and 31]);bits-=5;buf=buf and ((1 shl bits)-1)}}
  if(bits>0)sb.append(ABC[(buf shl (5-bits)) and 31]);return sb.chunked(5).joinToString("-")}
 fun seedOf(code:String):ByteArray{val s=code.uppercase().replace('O','0').replace('I','1').replace('L','1').filter{it.isLetterOrDigit()}
  require(s.length==55){"The recovery code must have 55 characters"}
  val out=ByteArrayOutputStream();var buf=0;var bits=0
  for(ch in s){val v=ABC.indexOf(ch);require(v>=0){"Invalid character $ch"};buf=(buf shl 5) or v;bits+=5;if(bits>=8){out.write((buf shr (bits-8)) and 255);bits-=8;buf=buf and ((1 shl bits)-1)}}
  val b=out.toByteArray();val seed=b.copyOf(32);require(b.size==34 && check(seed).contentEquals(b.copyOfRange(32,34))){"The recovery code has a typo"};return seed}
}