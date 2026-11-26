package org.nodelink

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

// Small encrypted key-value store sealed with a non-exportable Android Keystore AES key.
class Vault(context:Context,name:String){
 private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
 private val alias="nodelink.vault.v1"
 private val key:SecretKey=(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 @Synchronized fun get(k:String):String?{val b=Base64.decode(prefs.getString(k,null)?:return null,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,b,0,12));return String(c.doFinal(b,12,b.size-12),Charsets.UTF_8)}
 @Synchronized fun put(k:String,v:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);check(c.iv.size==12);check(prefs.edit().putString(k,Base64.encodeToString(c.iv+c.doFinal(v.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).commit()){"Storage failed"}}
}