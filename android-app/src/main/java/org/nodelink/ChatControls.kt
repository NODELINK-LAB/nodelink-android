package org.nodelink

import android.app.Activity
import java.io.*

// Direct (one-hop) message store and exchange. One exchange drains both queues in rounds of 16 (at most 16 rounds);
// both phones stop after a round in which neither side had anything to send. The chat screen shows the conversation.
class ChatControls(private val a:Activity,private val changed:()->Unit){
 val store=Mailbox(a)
 fun exchange(p:String,owner:Boolean,i:DataInputStream,o:DataOutputStream){val dest=p.lowercase()
  fun send()=store.pending(dest).also{MessageWire.send(it,i,o){id->store.ack(dest,id)}}.size
  fun receive()=MessageWire.receive(i,o){store.add(dest,it,"Received")}
  try{for(round in 1..16){val moved=if(owner){receive()+send()}else{send()+receive()};if(moved==0)break}}finally{a.runOnUiThread{changed()}}
 }
}