package org.nodelink.ledger

import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

// nodelink-1 wallet key. The same 32-byte recovery seed (and 55-character code) as before now gives an
// Ethereum-style secp256k1 key, so the address works on the NODELINK EVM (chain id 778001), in MetaMask
// and in the web wallet. Transfers are EIP-155 signed for 778001, so they cannot be replayed on another chain.
object EvmWallet {
 const val CHAIN_ID = 778001L
 val GAS: BigInteger = BigInteger.valueOf(21000)
 val MIN_GAS_PRICE: BigInteger = BigInteger.valueOf(2_000_000_000)   // genesis minimum, in anode
 private val WEI = BigDecimal.TEN.pow(18)
 private val N = BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)

 fun fromSeed(seed: ByteArray): Credentials {
  require(seed.size == 32)
  val d = BigInteger(1, Codec.sha("NDL-EVM-1".toByteArray() + seed)).mod(N - BigInteger.ONE) + BigInteger.ONE
  return Credentials.create(ECKeyPair.create(d))
 }

 fun isAddress(a: String) = a.matches(Regex("0x[0-9a-fA-F]{40}"))

 // "1.5" -> 1500000000000000000 anode. Exact, no floating point.
 fun parseNode(s: String): BigInteger {
  val t = s.trim()
  require(t.matches(Regex("\\d+(\\.\\d{1,18})?"))) { "Enter an amount like 1.5 (up to 18 decimals)" }
  val v = BigDecimal(t).multiply(WEI).toBigIntegerExact()
  require(v.signum() > 0) { "Amount must be more than 0" }
  return v
 }

 fun formatNode(v: BigInteger, decimals: Int = 6): String =
  BigDecimal(v).divide(WEI).setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString()

 // Signed raw transfer, ready for eth_sendRawTransaction.
 fun signTransfer(c: Credentials, nonce: BigInteger, gasPrice: BigInteger, to: String, value: BigInteger): String {
  require(isAddress(to)) { "Recipient must be a 0x address (40 hex characters)" }
  val tx = RawTransaction.createEtherTransaction(nonce, gasPrice, GAS, to, value)
  return Numeric.toHexString(TransactionEncoder.signMessage(tx, CHAIN_ID, c))
 }
}