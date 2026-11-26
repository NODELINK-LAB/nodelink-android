package org.nodelink

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

// Bottom navigation bar: one icon and label per screen, the current one highlighted.
class NavBar(c:Context,items:List<Pair<String,String>>,private val pick:(Int)->Unit){
 private val cells=mutableListOf<Pair<TextView,TextView>>()
 val view=LinearLayout(c).apply{setBackgroundColor(Ui.SURFACE);setPadding(0,Ui.dp(c,6),0,Ui.dp(c,8))}
 init{items.forEachIndexed{i,(icon,label)->
  val ic=TextView(c).apply{tag=Ui.DONE;text=icon;textSize=20f;gravity=Gravity.CENTER;setPadding(Ui.dp(c,16),Ui.dp(c,3),Ui.dp(c,16),Ui.dp(c,3))}
  val tx=TextView(c).apply{tag=Ui.DONE;text=label;textSize=11f;gravity=Gravity.CENTER}
  view.addView(LinearLayout(c).apply{orientation=1;gravity=Gravity.CENTER;addView(ic);addView(tx);setOnClickListener{pick(i)}},Ui.lp(0,-2,1f))
  cells.add(ic to tx)};select(0)}
 fun select(i:Int)=cells.forEachIndexed{n,(ic,tx)->val on=n==i
  ic.background=if(on)Ui.round(ic.context,0x33C1F683,14) else null;ic.alpha=if(on)1f else .55f
  tx.setTextColor(if(on)Ui.BRAND else Ui.MUTED);tx.typeface=if(on)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
}