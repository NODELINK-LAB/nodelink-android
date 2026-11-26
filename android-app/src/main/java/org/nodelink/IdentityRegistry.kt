package org.nodelink

import java.math.BigInteger
import org.nodelink.ledger.EvmWallet
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.*
import org.web3j.utils.Numeric

class IdentityRegistry(private val chain:WalletChain) {
 fun read(contract:String,wallet:String):String {
  require(EvmWallet.isAddress(contract)) { "Enter the NODELINK registry address" }
  chain.checkChain()
  val version = Function("registryVersion", emptyList(), listOf(object:TypeReference<Uint256>(){}))
  require(readValue(contract,version)==BigInteger.ONE) { "This is not a compatible identity registry" }
  val getter = Function("nameOf",listOf(Address(wallet)),listOf(object:TypeReference<Utf8String>(){}))
  return readValue(contract,getter) as? String ?: error("Unable to read the registered name")
 }

 private fun readValue(contract:String,function:Function):Any? =
  FunctionReturnDecoder.decode(chain.contractRead(contract,FunctionEncoder.encode(function)),function.outputParameters).singleOrNull()?.value

 fun quote(contract:String,owner:String,name:String):Quote {
  require(validName(name)) { "Use 3–24 lowercase letters, numbers, hyphens or underscores" }
  require(read(contract,owner).isEmpty()) { "This wallet already has a registered name" }
  val data = FunctionEncoder.encode(Function("register",listOf(Utf8String(name)),emptyList()))
  val gas = chain.contractGas(owner,contract,data) * BigInteger.valueOf(12) / BigInteger.TEN
  require(gas.signum()>0 && gas<=BigInteger.valueOf(150_000)) { "Unexpected registration gas estimate" }
  return Quote(data,gas,chain.gasPrice())
 }

 fun submit(contract:String,key:Credentials,quote:Quote):String {
  require(read(contract,key.address).isEmpty()) { "This wallet already has a registered name" }
  require(chain.balance(key.address)>=quote.fee) { "Not enough NODE for the registration fee" }
  val tx=RawTransaction.createTransaction(chain.nonce(key.address),quote.price,quote.gas,contract,BigInteger.ZERO,quote.data)
  return chain.send(Numeric.toHexString(TransactionEncoder.signMessage(tx,EvmWallet.CHAIN_ID,key)))
 }
 data class Quote(val data:String,val gas:BigInteger,val price:BigInteger){ val fee get()=gas*price }
 companion object { fun validName(name:String)=name.matches(Regex("[a-z0-9][a-z0-9_-]{1,22}[a-z0-9]")) }
}