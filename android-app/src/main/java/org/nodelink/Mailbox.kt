package org.nodelink

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import org.json.*
import java.security.KeyStore
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

// Unsent messages expire after 7 days; delivered and received history stays. Keeps the latest 256 messages:
// when full, the oldest delivered or received one is dropped (unsent ones are never dropped).
class Mailbox(private val context:Context){
 private val prefs=context.getSharedPreferences("mailbox",Context.MODE_PRIVATE)
 private val alias="nodelink.mailbox.v1"
 private val key:SecretKey= (KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 private fun rows():MutableList<JSONObject>{val value=prefs.getString("sealed",null)?:return mutableListOf();val bytes=Base64.decode(value,Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));val arr=JSONArray(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8));return (0 until arr.length()).map{arr.getJSONObject(it)}.filter{it.getString("state")!="Queued"||it.getLong("until")>System.currentTimeMillis()}.toMutableList()}
 private fun write(list:List<JSONObject>){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);check(c.iv.size==12);val bytes=c.iv+c.doFinal(JSONArray(list).toString().toByteArray());check(prefs.edit().putString("sealed",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()){"Message storage failed"}}
 @Synchronized fun add(peer:String,n:Note,state:String){require(peer.matches(Regex("[a-f0-9]{64}")));if(state.startsWith("Received") && Blocklist.has(peer))return;val list=rows();val old=list.firstOrNull{it.getString("peer")==peer && it.getString("id")==n.id.toString()};if(old!=null){require(old.getString("state")==state && old.getString("text")==n.text && old.getLong("until")==n.until);return};if(list.size>=256){val drop=list.firstOrNull{it.getString("state")!="Queued"};require(drop!=null){"Mailbox full: 256 unsent messages"};list.remove(drop)};list.add(JSONObject().put("peer",peer).put("id",n.id.toString()).put("until",n.until).put("text",n.text).put("state",state));write(list);if(state.startsWith("Received"))Notify.message(context,peer)}
 @Synchronized fun pending(peer:String)=rows().filter{it.getString("peer")==peer && it.getString("state")=="Queued"}.take(16).map{Note(UUID.fromString(it.getString("id")),it.getLong("until"),it.getString("text"))}
 @Synchronized fun ack(peer:String,id:UUID){val list=rows();list.first{it.getString("peer")==peer && it.getString("id")==id.toString() && it.getString("state")=="Queued"}.put("state","Delivered");write(list)}
 @Synchronized fun mark(peer:String,id:UUID,state:String){val list=rows();val row=list.firstOrNull{it.getString("peer")==peer && it.getString("id")==id.toString()}?:return;if(row.getString("state")==state)return;row.put("state",state);write(list)}
 @Synchronized fun thread(peer:String)=rows().filter{it.getString("peer")==peer}.takeLast(50).map{it.getString("text") to it.getString("state")}
 @Synchronized fun history(peer:String)=rows().filter{it.getString("peer")==peer}.takeLast(30).joinToString("\n\n"){it.getString("state")+": "+it.getString("text")}.ifEmpty{"No messages for this fingerprint."}
}