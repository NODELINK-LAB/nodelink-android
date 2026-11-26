package org.nodelink

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class ProbeTest {
 private fun reply(frame:ByteArray):ByteArray {val out=ByteArrayOutputStream();Probe.answer(DataInputStream(frame.inputStream()),DataOutputStream(out));return out.toByteArray()}
 private val nonce=ByteArray(32){it.toByte()}
 @Test fun echoesExactChallenge(){assertArrayEquals(byteArrayOf(78,76,1,2)+nonce,reply(byteArrayOf(78,76,1,1)+nonce))}
 @Test(expected=IllegalArgumentException::class) fun rejectsWrongType(){reply(byteArrayOf(78,76,1,9)+nonce)}
 @Test(expected=EOFException::class) fun rejectsTruncation(){reply(byteArrayOf(78,76,1,1,0))}
 @Test(expected=IllegalArgumentException::class) fun rejectsStaleAck(){Probe.challenge(DataInputStream((byteArrayOf(78,76,1,2)+nonce).inputStream()),DataOutputStream(ByteArrayOutputStream()))}
}