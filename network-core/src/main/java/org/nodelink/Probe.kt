package org.nodelink

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom

object Probe {
 fun answer(input:DataInputStream,out:DataOutputStream){
  require(input.readInt()==0x4e4c0101){"Unexpected frame"}
  val nonce=ByteArray(32);input.readFully(nonce)
  out.writeInt(0x4e4c0102);out.write(nonce);out.flush()
 }
 fun challenge(input:DataInputStream,out:DataOutputStream){
  val nonce=ByteArray(32);SecureRandom().nextBytes(nonce)
  out.writeInt(0x4e4c0101);out.write(nonce);out.flush()
  require(input.readInt()==0x4e4c0102){"Unexpected response"}
  val reply=ByteArray(32);input.readFully(reply);require(nonce.contentEquals(reply)){"Nonce mismatch"}
 }
}