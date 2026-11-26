package org.nodelink

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

// Floating bottom navigation: a rounded bar with one icon and label per screen; the current one sits on a lime pill.
class NavBar(c:Context,items:List<Pair<String,String>>,private val pick:(Int)->Unit){
 private val cells=mutableListOf<Pair<TextView,TextView>>()
 private val bar=LinearLayout(c).apply{background=Ui.round(c,Ui.SURFACE,28,Ui.LINE);setPadding(Ui.dp(c,4),Ui.dp(c,6),Ui.dp(c,4),Ui.dp(c,6))}
 val view=FrameLayout(c).apply{setBackgroundColor(Ui.BG);setPadding(Ui.dp(c,10),Ui.dp(c,4),Ui.dp(c,10),Ui.dp(c,10));addView(bar)}
 init{items.forEachIndexed{i,(icon,label)->
  val ic=TextView(c).apply{tag=Ui.DONE;text=icon;textSize=19f;gravity=Gravity.CENTER;setPadding(Ui.dp(c,12),Ui.dp(c,3),Ui.dp(c,12),Ui.dp(c,3))}
  val tx=TextView(c).apply{tag=Ui.DONE;text=label;textSize=10f;gravity=Gravity.CENTER;maxLines=1}
  bar.addView(LinearLayout(c).apply{orientation=1;gravity=Gravity.CENTER;minimumHeight=Ui.dp(c,48);contentDescription=label;addView(ic);addView(tx);setOnClickListener{pick(i)}},Ui.lp(0,-2,1f))
  cells.add(ic to tx)};select(0)}
 fun select(i:Int)=cells.forEachIndexed{n,(ic,tx)->val on=n==i
  ic.setTextColor(if(on)Ui.BRAND else Ui.SOFT);ic.alpha=if(on)1f else .8f
  tx.setTextColor(if(on)Ui.BRAND else Ui.MUTED);tx.typeface=if(on)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
  (ic.parent as android.view.View).isSelected=on}
}