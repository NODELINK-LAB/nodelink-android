package org.nodelink

import android.app.*
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import org.json.*
import org.nodelink.ledger.*
import java.math.BigInteger
import java.security.SecureRandom

// NODE wallet on nodelink-1 (EVM chain 778001). The key comes from the recovery seed kept encrypted on the phone;
// transfers are signed here and sent through a validator's JSON-RPC.
class WalletScreen(private val a: Activity, box: LinearLayout, private val note: (String) -> Unit) {
 private val vault = Vault(a, "wallet"); private val chain = WalletChain(a)
 private val address = TextView(a).apply { setTextIsSelectable(true) }; private val status = TextView(a); private val list = TextView(a)
 private fun seed() = vault.get("seed")?.let { Codec.unhex(it) } ?: ByteArray(32).also { SecureRandom().nextBytes(it); vault.put("seed", Codec.hex(it)) }
 private var keys = EvmWallet.fromSeed(seed())
 private var balance: BigInteger? = null
 private fun sent() = JSONArray(vault.get("evm_sent") ?: "[]").let { p -> (0 until p.length()).map { p.getJSONObject(it) } }
 private fun saveSent(l: List<JSONObject>) = vault.put("evm_sent", JSONArray(l.takeLast(20)).toString())
 private fun button(s: String, box: LinearLayout, f: () -> Unit) = box.addView(Button(a).apply { text = s; setOnClickListener { f() } })
 private fun secure(d: AlertDialog) { d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); d.show() }
 private fun background(start: String, f: () -> String) {
  note(start)
  Thread { val r = runCatching(f).getOrElse { "Failed: ${it.message}" }; a.runOnUiThread { refresh(); note(r) } }.start()
 }

 init {
  box.addView(TextView(a).apply { text = "NODE WALLET · nodelink-1 (chain 778001)" }); box.addView(address); box.addView(status)
  button("Show recovery code", box) {
   secure(AlertDialog.Builder(a).setTitle("Recovery code").setMessage(WalletKey.code(seed()) + "\n\nWrite this on paper and keep it offline. Anyone with it can spend this wallet. Nobody can recover it for you.").setPositiveButton("Done", null).create())
  }
  button("Restore from recovery code", box) {
   val input = EditText(a).apply { hint = "55-character recovery code" }
   secure(AlertDialog.Builder(a).setTitle("Restore wallet").setMessage("This replaces the wallet on this phone. Back up its recovery code first.").setView(input).setNegativeButton("Cancel", null)
    .setPositiveButton("Restore") { _, _ ->
     runCatching { val s = WalletKey.seedOf(input.text.toString()); vault.put("seed", Codec.hex(s)); vault.put("evm_sent", "[]"); keys = EvmWallet.fromSeed(s); balance = null; refresh(); note("Wallet restored. Tap Refresh to load its balance.") }
      .onFailure { note(it.message ?: "Restore failed") }
    }.create())
  }
  val rpc = EditText(a).apply { hint = "Validator EVM RPC, e.g. http://192.168.1.10:8545"; isSingleLine = true; setText(chain.rpc) }; box.addView(rpc)
  button("Save network", box) {
   runCatching { chain.configure(rpc.text.toString().trim()) }
    .onSuccess { background("Checking network…") { chain.checkChain(); balance = chain.balance(keys.address); "Connected to NODELINK." } }
    .onFailure { note(it.message ?: "Invalid RPC") }
  }
  val to = EditText(a).apply { hint = "Recipient address (0x…)"; isSingleLine = true }; box.addView(to)
  val amount = EditText(a).apply { hint = "Amount in NODE, e.g. 1.5"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }; box.addView(amount)
  button("Send", box) {
   val dest = to.text.toString().trim()
   runCatching { require(EvmWallet.isAddress(dest)) { "Recipient must be a 0x address (40 hex characters)" }; EvmWallet.parseNode(amount.text.toString()) }
    .onFailure { note(it.message ?: "Check the transfer") }
    .onSuccess { v ->
     AlertDialog.Builder(a).setTitle("Send ${EvmWallet.formatNode(v)} NODE?").setMessage("To $dest\n\nA small fee is paid from your balance. A confirmed transfer cannot be undone.")
      .setNegativeButton("Cancel", null).setPositiveButton("Send") { _, _ ->
       to.setText(""); amount.setText("")
       background("Signing and sending…") {
        chain.checkChain()
        val price = chain.gasPrice(); val b = chain.balance(keys.address)
        require(b >= v + price * EvmWallet.GAS) { "Balance too low for amount plus fee" }
        val hash = chain.send(EvmWallet.signTransfer(keys, chain.nonce(keys.address), price, dest, v))
        saveSent(sent() + JSONObject().put("hash", hash).put("to", dest).put("amount", EvmWallet.formatNode(v)).put("state", "Pending"))
        balance = chain.balance(keys.address)
        "Sent. Tap Refresh in a few seconds to see it confirmed."
       }
      }.show()
    }
  }
  button("Refresh", box) {
   background("Contacting validator…") {
    chain.checkChain(); balance = chain.balance(keys.address)
    saveSent(sent().map { j -> if (j.getString("state") != "Pending") j else chain.receipt(j.getString("hash"))?.let { j.put("state", if (it) "Confirmed" else "Failed") } ?: j })
    "Balance updated."
   }
  }
  box.addView(list); refresh()
 }

 private fun refresh() {
  address.text = "Address: " + keys.address
  status.text = when { chain.rpc.isEmpty() -> "No network set. Enter a validator RPC and tap Save network."
   balance == null -> "Balance unknown. Tap Refresh."
   else -> "Balance ${EvmWallet.formatNode(balance!!)} NODE" }
  val s = sent()
  list.text = if (s.isEmpty()) "No transfers sent yet" else s.reversed().joinToString("\n") { "${it.getString("amount")} NODE to ${it.getString("to").take(10)}… · ${it.getString("state")}" }
 }
}