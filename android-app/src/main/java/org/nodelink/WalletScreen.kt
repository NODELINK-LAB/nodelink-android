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
class WalletScreen(private val a: Activity, box: LinearLayout, private val note: (String) -> Unit, back: () -> Unit) {
 private val vault = Vault(a, "wallet"); private val chain = WalletChain(a)
 private val address = Ui.caption(a, "").apply { setTextIsSelectable(true) }; private val status = Ui.title(a, "", 27f); private val list = TextView(a)
 private val assetBalance = Ui.title(a,"",15f).apply{gravity=android.view.Gravity.END}
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
  group.addView(Ui.card(a).apply{background=Ui.gradient(a,Ui.RAISED,Ui.SURFACE,17,0xFF50A846.toInt())
   addView(LinearLayout(a).apply{gravity=android.view.Gravity.CENTER_VERTICAL;addView(NeonIcon(a,"wallet"),Ui.lp(Ui.dp(a,34),Ui.dp(a,34)))
    addView(LinearLayout(a).apply{orientation=1;setPadding(Ui.dp(a,14),0,0,0);addView(Ui.title(a,"NODE WALLET",16f));addView(Ui.caption(a,"nodelink-1 (chain 778001)"))},Ui.lp(0,-2,1f))
    addView(NeonIcon(a,"next").apply{tint=Ui.TEXT},Ui.lp(Ui.dp(a,18),Ui.dp(a,18)))})
   addView(FrameLayout(a).apply{addView(NeonPanel.wave(a),FrameLayout.LayoutParams(Ui.dp(a,155),-1,android.view.Gravity.END));addView(LinearLayout(a).apply{orientation=1;addView(Ui.caption(a,"Balance"));addView(homeBalance)})},Ui.lp().apply{topMargin=Ui.dp(a,12)});setOnClickListener{open()}})
  button("↶  Show Recovery Phrase",group,Ui.BRAND){recovery.performClick()}
  button("↶  Restore Wallet",group){restore.performClick()}
  fun openNetwork(){settings.visibility=android.view.View.VISIBLE;open()}
  group.addView(NeonPanel.row(a,"shield","Validator EVM RPC",homeNetwork,::openNetwork))
  group.addView(NeonPanel.row(a,"refresh","Save Network"){if(chain.rpc.isEmpty())openNetwork() else network.performClick()})
  home.addView(group,index,Ui.lp())
 }
 private fun secure(d: AlertDialog) { d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); d.show() }
 private fun background(start: String, f: () -> String) {
  note(start)
  Thread { val r = runCatching(f).getOrElse { "Failed: ${it.message}" }; a.runOnUiThread { refresh(); note(r) } }.start()
 }

 init {
  box.addView(NeonPanel.header(a,"Wallet",back=back))
  box.addView(Ui.card(a).apply{background=Ui.gradient(a,0xFF123E23.toInt(),Ui.BG,18,0xFF60B850.toInt());addView(Ui.caption(a,"Your Balance"));addView(status);addView(Ui.caption(a,"NODE · nodelink-1"));addView(NeonPanel.wave(a),Ui.lp(-1,Ui.dp(a,42)))})
  val sendPanel=Ui.card(a)
  val actions=LinearLayout(a)
  action(actions,"Send","send"){sendPanel.visibility=if(sendPanel.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}
  action(actions,"Receive","receive"){AlertDialog.Builder(a).setTitle("Receive NODE").setMessage(keys.address).setPositiveButton("Copy address"){_,_->
   a.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("NODE address",keys.address));note("Wallet address copied.")}.setNegativeButton("Close",null).show()}
  box.addView(Ui.section(a,"Your Assets"))
  box.addView(Ui.card(a).apply{addView(LinearLayout(a).apply{gravity=android.view.Gravity.CENTER_VERTICAL
   addView(NeonIcon(a,"wallet"),Ui.lp(Ui.dp(a,23),Ui.dp(a,23)));addView(Ui.title(a,"NODE",16f),Ui.lp(0,-2,1f).apply{leftMargin=Ui.dp(a,9)});addView(assetBalance,Ui.lp(-2,-2))});addView(address,Ui.lp().apply{topMargin=Ui.dp(a,6)})})
  box.addView(sendPanel);box.addView(settings)
  recovery=button("Show recovery phrase", settings,Ui.BRAND) {
   val words = EvmWallet.recoveryPhrase(seed()).split(' ').mapIndexed { i, word -> "${i+1}. $word" }.chunked(3).joinToString("\n") { it.joinToString("    ") }
   secure(AlertDialog.Builder(a).setTitle("24-word NODELINK recovery phrase").setMessage(words + "\n\nWrite the words down in order and keep them offline. Anyone with this phrase can spend your funds. Restore it in NODELINK only.").setPositiveButton("Done", null).create())
  }
  restore=button("Restore wallet", settings) {
   val input = EditText(a).apply {
    hint = "24 words or legacy recovery code"
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
    filters = arrayOf(android.text.InputFilter.LengthFilter(512))
   }
   val dialog = AlertDialog.Builder(a).setTitle("Restore wallet").setMessage("Use a NODELINK backup only. This replaces the wallet on this phone. Back up your current wallet first.").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Restore", null).create()
   secure(dialog)
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
    runCatching { val s = EvmWallet.recoverSeed(input.text.toString()); val restored = EvmWallet.fromSeed(s); vault.put("seed", Codec.hex(s)); vault.put("evm_sent", "[]"); keys = restored; balance = null; refresh() }
     .onSuccess { input.text.clear(); dialog.dismiss(); note("Wallet restored. Tap Refresh to load its balance.") }
     .onFailure { input.error = it.message ?: "Restore failed" }
   }
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
  status.text=amount?:"— NODE";homeBalance.text=status.text;assetBalance.text=balance?.let{EvmWallet.formatNode(it)}?:"—"
  homeNetwork.text=chain.rpc.ifEmpty{"Not configured · open Wallet to set up"}
  val s = sent()
  list.text = if (s.isEmpty()) "No transfers sent yet" else s.reversed().joinToString("\n") { "${it.getString("amount")} NODE to ${it.getString("to").take(10)}… · ${it.getString("state")}" }
 }
}