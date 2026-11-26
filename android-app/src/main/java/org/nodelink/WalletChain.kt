package org.nodelink

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

// nodelink-1 client over a validator's Ethereum JSON-RPC (EVM chain id 778001).
// The RPC is trusted for balances and confirmations; it can never spend for you, because transfers are signed on the phone.
class WalletChain(c: Context) {
 private val prefs = c.getSharedPreferences("wallet_evm", Context.MODE_PRIVATE)
 var rpc: String = prefs.getString("rpc", "") ?: ""; private set

 fun configure(url: String) {
  require(url.matches(Regex("https?://\\S+"))) { "RPC must start with http:// or https://" }
  prefs.edit().putString("rpc", url).apply(); rpc = url
 }

 // Network calls below: run off the main thread.
 private fun call(method: String, vararg params: Any): Any {
  require(rpc.isNotEmpty()) { "Save the network first" }
  val con = (URL(rpc).openConnection() as HttpURLConnection).apply {
   requestMethod = "POST"; connectTimeout = 5000; readTimeout = 10000; doOutput = true
   setRequestProperty("content-type", "application/json")
  }
  try {
   val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", JSONArray(params.toList()))
   con.outputStream.use { it.write(body.toString().toByteArray()) }
   val r = JSONObject(con.inputStream.bufferedReader().readText())
   r.optJSONObject("error")?.let { error(it.optString("message", "RPC error")) }
   return r.get("result")
  } finally { con.disconnect() }
 }
 private fun big(method: String, vararg p: Any) = BigInteger((call(method, *p) as String).removePrefix("0x").ifEmpty { "0" }, 16)

 fun checkChain() { val id = big("eth_chainId").toLong(); require(id == 778001L) { "This RPC is chain $id, not NODELINK (778001)" } }
 fun balance(a: String) = big("eth_getBalance", a, "latest")
 fun nonce(a: String) = big("eth_getTransactionCount", a, "pending")
 fun gasPrice(): BigInteger = big("eth_gasPrice").max(org.nodelink.ledger.EvmWallet.MIN_GAS_PRICE)
 fun contractRead(to:String,data:String) = call("eth_call",JSONObject().put("to",to).put("data",data),"latest") as String
 fun contractGas(from:String,to:String,data:String) = big("eth_estimateGas",JSONObject().put("from",from).put("to",to).put("data",data))
 fun send(raw: String) = call("eth_sendRawTransaction", raw) as String
 // null while not yet in a block, true if it succeeded, false if it reverted.
 fun receipt(hash: String): Boolean? = (call("eth_getTransactionReceipt", hash) as? JSONObject)?.let { it.getString("status") == "0x1" }
}