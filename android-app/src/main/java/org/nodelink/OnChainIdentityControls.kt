package org.nodelink

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.widget.*
import org.nodelink.ledger.EvmWallet
import org.web3j.crypto.Credentials
import java.util.Locale

class OnChainIdentityControls(private val a:Activity,box:LinearLayout,chain:WalletChain,private val wallet:()->Credentials) {
 private val registry=IdentityRegistry(chain)
 private val prefs=a.getSharedPreferences("onchain_identity",Context.MODE_PRIVATE)
 init {
  box.addView(NeonPanel.row(a,"shield","On-chain Name",Ui.caption(a,"Register a public name for your wallet"),::open))
 }
 private fun open(){
  val key=wallet()
  val body=Ui.card(a)
  val contract=EditText(a).apply{hint="Trusted NODELINK registry address";isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(42));setText(prefs.getString("registry",""))}
  val name=EditText(a).apply{hint="Name (e.g. node-alice)";isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(24))}
  val status=Ui.caption(a,"Check the registry or register a name. The name is public, permanent and not exclusive.")
  body.addView(Ui.caption(a,key.address).apply{setTextIsSelectable(true)});body.addView(contract);body.addView(name);body.addView(status)
  Ui.style(body)
  val dialog=AlertDialog.Builder(a).setTitle("On-chain Name").setView(ScrollView(a).apply{addView(body)}).setNeutralButton("Check",null).setPositiveButton("Register",null).setNegativeButton("Close",null).show()
  val check=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);val register=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
  fun <T> work(task:()->T,done:(T)->Unit){
   check.isEnabled=false;register.isEnabled=false;contract.isEnabled=false;name.isEnabled=false;status.text="Contacting NODELINK…"
   Thread{val result=runCatching(task);a.runOnUiThread{if(dialog.isShowing){
    check.isEnabled=true;register.isEnabled=true;contract.isEnabled=true;name.isEnabled=true
    result.onSuccess(done).onFailure{status.text=it.message?:"Request failed"}
   }}}.start()
  }
  check.setOnClickListener{
   val address=contract.text.toString().trim()
   work({registry.read(address,key.address)}){found->prefs.edit().putString("registry",address).apply();status.text=if(found.isEmpty())"No name confirmed yet" else "Registered on NODELINK: $found"}
  }
  register.setOnClickListener{
   val address=contract.text.toString().trim();val chosen=name.text.toString().trim().lowercase(Locale.ROOT)
   work({registry.quote(address,key.address,chosen)}){quote->
    prefs.edit().putString("registry",address).apply();status.text="Review registration for $chosen"
    AlertDialog.Builder(a).setTitle("Register $chosen?").setMessage("This permanently publishes the name with ${key.address}. Maximum fee: ${EvmWallet.formatNode(quote.fee,18)} NODE.").setNegativeButton("Cancel",null).setPositiveButton("Sign and register"){_,_->
     work({registry.submit(address,key,quote)}){hash->status.text="Submitted, not yet confirmed. Tap Check after a block.\n$hash";status.setTextIsSelectable(true)}
    }.show()
   }
  }
 }
}