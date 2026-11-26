package org.nodelink

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.UUID

class MessageWireTest {
 private val n=Note(UUID.randomUUID(),System.currentTimeMillis()+600000,"বাংলা")
 private fun ack(id:UUID)=ByteArrayOutputStream().also{val o=DataOutputStream(it);o.writeInt(0x4e4c0202);o.writeLong(id.mostSignificantBits);o.writeLong(id.leastSignificantBits)}.toByteArray()
 private fun frame():ByteArray{val b=ByteArrayOutputStream();MessageWire.send(listOf(n),DataInputStream(ack(n.id).inputStream()),DataOutputStream(b)){assertEquals(n.id,it)};return b.toByteArray()}
 @Test fun roundTrip(){val out=ByteArrayOutputStream();var saved=false;MessageWire.receive(DataInputStream(frame().inputStream()),DataOutputStream(out)){assertEquals(n,it);assertEquals(0,out.size());saved=true};assertTrue(saved);assertArrayEquals(ack(n.id),out.toByteArray())}
 @Test fun diskFailureIsNotAcknowledged(){val out=ByteArrayOutputStream();assertThrows(IOException::class.java){MessageWire.receive(DataInputStream(frame().inputStream()),DataOutputStream(out)){throw IOException("storage full")}};assertEquals(0,out.size())}
 @Test fun wrongAckIsNotDelivery(){assertThrows(IllegalArgumentException::class.java){MessageWire.send(listOf(n),DataInputStream(ack(UUID.randomUUID()).inputStream()),DataOutputStream(ByteArrayOutputStream())){fail("Wrong peer acknowledgment accepted")}}}
}