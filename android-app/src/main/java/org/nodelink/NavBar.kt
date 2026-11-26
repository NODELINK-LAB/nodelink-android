package org.nodelink

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

class NavBar(c:Context,items:List<Pair<String,String>>,private val pick:(Int)->Unit){
 private data class Cell(val icon:NeonIcon,val label:TextView,val marker:android.view.View)
 private val cells=mutableListOf<Cell>()
 private val bar=LinearLayout(c).apply{background=Ui.gradient(c,Ui.SURFACE,Ui.BG,22,Ui.LINE);setPadding(Ui.dp(c,5),Ui.dp(c,5),Ui.dp(c,5),0)}
 val view=FrameLayout(c).apply{setBackgroundColor(Ui.BG);setPadding(Ui.dp(c,2),Ui.dp(c,3),Ui.dp(c,2),0);addView(bar)}
 init{items.forEachIndexed{i,(icon,label)->
  val ic=NeonIcon(c,icon);val marker=android.view.View(c).apply{background=Ui.round(c,Ui.BRAND,3)}
  val tx=TextView(c).apply{text=label;textSize=10f;gravity=Gravity.CENTER;maxLines=1}
  bar.addView(LinearLayout(c).apply{orientation=1;gravity=Gravity.CENTER;minimumHeight=Ui.dp(c,50);contentDescription=label;addView(ic,Ui.lp(Ui.dp(c,22),Ui.dp(c,22)));addView(tx);addView(marker,Ui.lp(Ui.dp(c,28),Ui.dp(c,3)).apply{topMargin=Ui.dp(c,3)});setOnClickListener{pick(i)}},Ui.lp(0,-2,1f))
  cells.add(Cell(ic,tx,marker))};select(0)}
 fun select(i:Int)=cells.forEachIndexed{n,(ic,tx,marker)->val on=n==i
  ic.tint=if(on)Ui.BRAND else Ui.SOFT
  tx.setTextColor(if(on)Ui.BRAND else Ui.SOFT);tx.typeface=if(on)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
  marker.visibility=if(on)android.view.View.VISIBLE else android.view.View.INVISIBLE
  (ic.parent as android.view.View).isSelected=on}
}