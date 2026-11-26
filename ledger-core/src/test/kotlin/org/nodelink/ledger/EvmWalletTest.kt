package org.nodelink.ledger

import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.*
import java.math.BigInteger

class EvmWalletTest {
 private val seed = ByteArray(32) { it.toByte() }

 @Test fun knownKeyGivesKnownAddress() =
  assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf", Credentials.create(ECKeyPair.create(BigInteger.ONE)).address)
 @Test fun sameSeedSameAddress() = assertEquals(EvmWallet.fromSeed(seed).address, EvmWallet.fromSeed(seed.copyOf()).address)
 @Test fun differentSeedDifferentAddress() =
  assertNotEquals(EvmWallet.fromSeed(seed).address, EvmWallet.fromSeed(seed.copyOf().also { it[0] = 9 }).address)
 @Test fun recoveryCodeRestoresSameAddress() =
  assertEquals(EvmWallet.fromSeed(seed).address, EvmWallet.fromSeed(WalletKey.seedOf(WalletKey.code(seed))).address)

 @Test fun recoveryPhrasePreservesExistingAddress() {
  val phrase = EvmWallet.recoveryPhrase(seed)
  assertEquals(24, phrase.split(' ').size)
  assertArrayEquals(seed, EvmWallet.recoverSeed(phrase.replace(" ", "\n")))
  assertEquals(EvmWallet.fromSeed(seed).address, EvmWallet.fromSeed(EvmWallet.recoverSeed(phrase)).address)
  assertArrayEquals(seed, EvmWallet.recoverSeed(WalletKey.code(seed)))
 }

 @Test fun invalidMnemonicChecksumIsRejected() {
  val invalid = List(24) { "abandon" }.joinToString(" ")
  assertTrue(runCatching { EvmWallet.recoverSeed(invalid) }.isFailure)
 }

 @Test fun signedTransferRecoversSenderChainAndAmount() {
  val c = EvmWallet.fromSeed(seed)
  val hex = EvmWallet.signTransfer(c, BigInteger.ZERO, EvmWallet.MIN_GAS_PRICE, "0x" + "11".repeat(20), EvmWallet.parseNode("1.5"))
  val t = TransactionDecoder.decode(hex) as SignedRawTransaction
  assertEquals(c.address, t.from)
  assertEquals(778001L, t.chainId)
  assertEquals(BigInteger("1500000000000000000"), t.value)
 }

 @Test fun amountsAreExact() {
  assertEquals(BigInteger.ONE, EvmWallet.parseNode("0.000000000000000001"))
  assertEquals("1.5", EvmWallet.formatNode(EvmWallet.parseNode("1.5")))
  assertTrue(runCatching { EvmWallet.parseNode("1.0000000000000000001") }.isFailure)
  assertTrue(runCatching { EvmWallet.parseNode("0") }.isFailure)
  assertTrue(runCatching { EvmWallet.parseNode("-1") }.isFailure)
 }

 @Test fun badRecipientRefused() = assertTrue(runCatching {
  EvmWallet.signTransfer(EvmWallet.fromSeed(seed), BigInteger.ZERO, EvmWallet.MIN_GAS_PRICE, "ndl1234", BigInteger.ONE)
 }.isFailure)
}