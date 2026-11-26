package org.nodelink

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.*

object Identity {
 private const val alias = "nodelink.identity.v1"
 private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
 fun prepare() {
  if(store().containsAlias(alias)) return
  KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
   initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512).build())
  }.generateKeyPair()
 }
 // After revocation: the old key is destroyed and a new identity created.
 fun reset() { store().deleteEntry(alias); prepare() }
 fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
 fun fingerprint() = digest(store().getCertificate(alias).publicKey.encoded)
 fun publicKey(): ByteArray = store().getCertificate(alias).publicKey.encoded
 fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run { initSign(store().getKey(alias,null) as PrivateKey); update(data); sign() }
 fun tls(pin: String): SSLContext {
  require(pin.matches(Regex("[a-fA-F0-9]{64}"))) { "Enter the peer's full 64-digit fingerprint" }
  require(!pin.equals(fingerprint(),true)) { "Expected a different device" }
  return context { if(!pin.equals(it,true)) throw java.security.cert.CertificateException("Peer fingerprint mismatch") }
 }
 // Relay-only channel: any other NODELINK identity is accepted. Safe because relayed content is end-to-end encrypted and signed.
 fun tlsAny(): SSLContext = context { if(it.equals(fingerprint(),true)) throw java.security.cert.CertificateException("Own identity") }
 private fun context(accept: (String) -> Unit): SSLContext {
  val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store(),null) }
  val trust = object: X509TrustManager {
   override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
   private fun verify(chain: Array<X509Certificate>) {
    val leaf=chain.firstOrNull() ?: throw java.security.cert.CertificateException("No identity")
    leaf.checkValidity()
    val fp=digest(leaf.publicKey.encoded)
    if(Blocklist.has(fp)) throw java.security.cert.CertificateException("Blocked identity")
    accept(fp)
   }
   override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String)=verify(chain)
   override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String)=verify(chain)
  }
  return SSLContext.getInstance("TLSv1.3").apply { init(km.keyManagers,arrayOf<TrustManager>(trust),SecureRandom()) }
 }
}