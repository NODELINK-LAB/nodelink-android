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
 private val address = Ui.caption(a, "").apply { setTextIsSelectable(true) }; private val status = Ui.title(a, "", 27f); private val list = TextView(a)
 private val homeBalance = Ui.title(a, "", 21f); private val homeNetwork = Ui.caption(a, "")
 private lateinit var recovery:Button; private lateinit var restore:Button; private lateinit var network:Button
 private val settings=Ui.card(a).apply{visibility=android.view.View.GONE}
 private fun seed() = vault.get("seed")?.let { Codec.unhex(it) } ?: ByteArray(32).also { SecureRandom().nextBytes(it); vault.put("seed", Codec.hex(it)) }
 private var keys = EvmWallet.fromSeed(seed())
 private var balance: BigInteger? = null
 private fun sent() = JSONArray(vault.get("evm_sent") ?: "[]").let { p -> (0 until p.length()).map { p.getJSONObject(it) } }
 private fun saveSent(l: List<JSONObject>) = vault.put("evm_sent", JSONArray(l.takeLast(20)).toString())
 private fun button(s: String, box: LinearLayout, fill:Int=Ui.RAISED, f: () -> Unit):Button = Button(a).apply { text = s; setOnClickListener { f() };box.addView(this);Ui.button(this,fill) }

 private fun action(row:LinearLayout,label:String,icon:String,run:()->Unit){
  val tile=LinearLayout(a).apply{orientation=1;gravity=android.view.Gravity.CENTER;minimumHeight=Ui.dp(a,64);contentDescription=label
   background=Ui.gradient(a,Ui.RAISED,Ui.SURFACE,16,Ui.LINE);setOnClickListener{run()}
   addView(NeonIcon(a,icon),Ui.lp(Ui.dp(a,23),Ui.dp(a,23)));addView(Ui.title(a,label,11f).apply{gravity=android.view.Gravity.CENTER},Ui.lp(-1,-2).apply{topMargin=Ui.dp(a,4)})}
  row.addView(tile,Ui.lp(0,-2,1f).apply{setMargins(Ui.dp(a,3),Ui.dp(a,4),Ui.dp(a,3),Ui.dp(a,4))})
 }

 fun addHomeSummary(home:LinearLayout,index:Int,open:()->Unit){
  val group=LinearLayout(a).apply{orientation=1}
  group.addView(Ui.card(a).apply{background=Ui.gradient(a,Ui.RAISED,Ui.SURFACE,17,0xFF50A846.toInt());addView(Ui.title(a,"NODE WALLET  ›",16f));addView(Ui.caption(a,"nodelink-1 (chain 778001)"));addView(Ui.caption(a,"Balance"));addView(homeBalance);setOnClickListener{open()}})
  button("↶  Show Recovery Code",group,Ui.BRAND){recovery.performClick()}
  button("↶  Restore from Recovery Code",group){restore.performClick()}
  fun openNetwork(){settings.visibility=android.view.View.VISIBLE;open()}
  group.addView(Ui.card(a).apply{addView(Ui.title(a,"Validator EVM RPC  ›",15f));addView(homeNetwork);setOnClickListener{openNetwork()}})
  button("Save Network  ›",group){if(chain.rpc.isEmpty())openNetwork() else network.performClick()}
  home.addView(group,index,Ui.lp())
 }
 private fun secure(d: AlertDialog) { d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); d.show() }
 private fun background(start: String, f: () -> String) {
  note(start)
  Thread { val r = runCatching(f).getOrElse { "Failed: ${it.message}" }; a.runOnUiThread { refresh(); note(r) } }.start()
 }

 init {
  box.addView(Ui.card(a).apply{background=Ui.gradient(a,0xFF123E23.toInt(),Ui.BG,18,0xFF60B850.toInt());addView(Ui.caption(a,"Your Balance"));addView(status);addView(Ui.caption(a,"NODE · nodelink-1"))})
  val sendPanel=Ui.card(a)
  val actions=LinearLayout(a)
  action(actions,"Send","send"){sendPanel.visibility=if(sendPanel.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}
  action(actions,"Receive","receive"){AlertDialog.Builder(a).setTitle("Receive NODE").setMessage(keys.address).setPositiveButton("Copy address"){_,_->
   a.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("NODE address",keys.address));note("Wallet address copied.")}.setNegativeButton("Close",null).show()}
  box.addView(Ui.section(a,"Your Assets"))
  box.addView(Ui.card(a).apply{addView(Ui.title(a,"◈  NODE",16f));addView(address)})
  box.addView(sendPanel);box.addView(settings)
  recovery=button("Show recovery code", settings,Ui.BRAND) {
   secure(AlertDialog.Builder(a).setTitle("Recovery code").setMessage(WalletKey.code(seed()) + "\n\nWrite this on paper and keep it offline. Anyone with it can spend this wallet. Nobody can recover it for you.").setPositiveButton("Done", null).create())
  }
  restore=button("Restore from recovery code", settings) {
   val input = EditText(a).apply { hint = "55-character recovery code" }
   secure(AlertDialog.Builder(a).setTitle("Restore wallet").setMessage("This replaces the wallet on this phone. Back up its recovery code first.").setView(input).setNegativeButton("Cancel", null)
    .setPositiveButton("Restore") { _, _ ->
     runCatching { val s = WalletKey.seedOf(input.text.toString()); vault.put("seed", Codec.hex(s)); vault.put("evm_sent", "[]"); keys = EvmWallet.fromSeed(s); balance = null; refresh(); note("Wallet restored. Tap Refresh to load its balance.") }
      .onFailure { note(it.message ?: "Restore failed") }
    }.create())
  }
  val rpc = EditText(a).apply { hint = "Validator EVM RPC, e.g. http://192.168.1.10:8545"; isSingleLine = true; setText(chain.rpc) }; settings.addView(Ui.caption(a,"Validator EVM RPC"));settings.addView(rpc)
  network=button("Save network", settings) {
   runCatching { chain.configure(rpc.text.toString().trim()) }
    .onSuccess { background("Checking network…") { chain.checkChain(); balance = chain.balance(keys.address); "Connected to NODELINK." } }
    .onFailure { note(it.message ?: "Invalid RPC") }
  }
  sendPanel.visibility=android.view.View.GONE
  val to = EditText(a).apply { hint = "Recipient address (0x…)"; isSingleLine = true }; sendPanel.addView(to)
  val amount = EditText(a).apply { hint = "Amount in NODE, e.g. 1.5"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }; sendPanel.addView(amount)
  button("Send NODE", sendPanel,Ui.BRAND) {
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
  action(actions,"Refresh","refresh") {
   background("Contacting validator…") {
    chain.checkChain(); balance = chain.balance(keys.address)
    saveSent(sent().map { j -> if (j.getString("state") != "Pending") j else chain.receipt(j.getString("hash"))?.let { j.put("state", if (it) "Confirmed" else "Failed") } ?: j })
    "Balance updated."
   }
  }
  action(actions,"More","more"){settings.visibility=if(settings.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}
  box.addView(actions,2,Ui.lp());box.addView(Ui.section(a,"Recent Transfers"));box.addView(list); refresh()
 }

 private fun refresh() {
  address.text = "Address: " + keys.address
  val amount=balance?.let{ "${EvmWallet.formatNode(it)} NODE" }
  status.text=amount?:"— NODE";homeBalance.text=status.text
  homeNetwork.text=chain.rpc.ifEmpty{"Not configured · open Wallet to set up"}
  val s = sent()
  list.text = if (s.isEmpty()) "No transfers sent yet" else s.reversed().joinToString("\n") { "${it.getString("amount")} NODE to ${it.getString("to").take(10)}… · ${it.getString("state")}" }
 }
}