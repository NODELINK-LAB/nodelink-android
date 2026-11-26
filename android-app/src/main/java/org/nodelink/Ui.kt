package org.nodelink

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

// The app's single visual theme: dark surfaces, the NODELINK lime accent, rounded cards, fields and buttons,
// section headers and contact avatars. style() themes every control in a view tree that is not styled yet, so all
// screens built in code look the same. Pure UI: nothing here changes behaviour.
object Ui{
 const val BG=0xFF030D0B.toInt();const val SURFACE=0xFF0A1915.toInt();const val RAISED=0xFF102B1D.toInt();const val LINE=0xFF235836.toInt()
 const val TEXT=0xFFF4FFF7.toInt();const val SOFT=0xFFD3E1D9.toInt();const val MUTED=0xFFA4BCAD.toInt()
 const val BRAND=0xFFA2FF56.toInt();const val AMBER=0xFFFFC857.toInt();const val INK=0xFF06110E.toInt();const val GREEN=0xFF66F052.toInt();const val RED=0xFFFF5A5F.toInt()
 const val DONE="ui"
 private val tints=intArrayOf(0xFF6366F1.toInt(),0xFF0EA5E9.toInt(),0xFF14B8A6.toInt(),0xFFF59E0B.toInt(),0xFFEC4899.toInt(),0xFF8B5CF6.toInt())
 fun dp(c:Context,v:Int)=(v*c.resources.displayMetrics.density).toInt()
 fun lp(w:Int=-1,h:Int=-2,weight:Float=0f)=LinearLayout.LayoutParams(w,h,weight)
 fun round(c:Context,color:Int,radius:Int,stroke:Int=0)=GradientDrawable().apply{setColor(color);cornerRadius=dp(c,radius).toFloat();if(stroke!=0)setStroke(dp(c,1),stroke)}
 fun oval(color:Int)=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(color)}
 fun nl(fp:String)="NL-"+fp.take(12).uppercase().chunked(4).joinToString("-")

 // Filled button. fill gives the role: BRAND primary, GREEN call/answer, RED hang up/decline, RAISED neutral.
 fun button(b:Button,fill:Int=RAISED,ink:Int=if(fill==RAISED)TEXT else INK):Button{val c=b.context;b.tag=DONE
  b.isAllCaps=false;b.setTextColor(ink);b.textSize=14f;b.typeface=Typeface.DEFAULT_BOLD;b.background=when(fill){BRAND,GREEN->gradient(c,0xFF49DC42.toInt(),0xFFB6FF61.toInt(),24,LINE);RAISED->gradient(c,RAISED,SURFACE,17,LINE);else->round(c,fill,24)};b.stateListAnimator=null
  b.minHeight=dp(c,46);b.minimumHeight=dp(c,46);b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8))
  val row=(b.parent as? LinearLayout)?.orientation==LinearLayout.HORIZONTAL;val side=dp(c,if(row)4 else 0)
  b.layoutParams=(if(row)lp(0,-2,1f) else lp()).apply{setMargins(side,dp(c,5),side,dp(c,5))};return b}
 fun row(c:Context,vararg v:View)=LinearLayout(c).apply{v.forEach{addView(it,lp(0,-2,1f).apply{setMargins(dp(c,4),dp(c,5),dp(c,4),dp(c,5))})}}
 fun title(c:Context,s:String,size:Float=18f)=TextView(c).apply{tag=DONE;text=s;setTextColor(TEXT);textSize=size;typeface=Typeface.DEFAULT_BOLD}
 fun caption(c:Context,s:String)=TextView(c).apply{tag=DONE;text=s;setTextColor(MUTED);textSize=13f;setLineSpacing(0f,1.2f)}
 fun card(c:Context)=LinearLayout(c).apply{orientation=1;background=gradient(c,RAISED,SURFACE,17,LINE)
  setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));layoutParams=lp().apply{setMargins(0,dp(c,6),0,dp(c,6))}}
 fun paint(v:TextView,name:String){v.text=name.trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.take(2).joinToString(""){it.take(1).uppercase()}.ifEmpty{"?"}
  v.background=oval(tints[Math.floorMod(name.hashCode(),tints.size)])}
 fun avatar(c:Context,name:String,size:Int)=TextView(c).apply{tag=DONE;gravity=Gravity.CENTER;setTextColor(-1);typeface=Typeface.DEFAULT_BOLD;textSize=size/2.6f;layoutParams=lp(dp(c,size),dp(c,size));paint(this,name)}
 fun gradient(c:Context,start:Int,end:Int,radius:Int,stroke:Int=0)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(start,end)).apply{cornerRadius=dp(c,radius).toFloat();if(stroke!=0)setStroke(dp(c,1),stroke)}
 fun heading(c:Context,s:String)=title(c,s,23f).apply{gravity=Gravity.CENTER;setPadding(0,dp(c,9),0,dp(c,13))}
 fun topBar(c:Context,status:TextView)=LinearLayout(c).apply{orientation=1;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(c,16),0,dp(c,16),dp(c,12))
  addView(NeonArt(c,"home"),lp(-1,dp(c,96)));addView(title(c,"NODELINK",31f).apply{letterSpacing=.08f;gravity=Gravity.CENTER})
  status.tag=DONE;status.setTextColor(TEXT);status.textSize=12f;status.background=gradient(c,SURFACE,RAISED,24,0xFF54A342.toInt());status.setPadding(dp(c,15),dp(c,7),dp(c,15),dp(c,7));status.gravity=Gravity.CENTER
  addView(status,lp(-2,-2).apply{topMargin=dp(c,6)})}

 // Section header for any language (Bengali has no capitals, so header() cannot detect it).
 fun section(c:Context,s:String)=TextView(c).apply{tag=DONE;text="▍"+s;setTextColor(BRAND);textSize=14f;typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(c,24),0,dp(c,8))}
 // Numbered help card: a title and short steps, shown at the top of a screen.
 fun steps(c:Context,t:String,vararg s:String)=card(c).apply{addView(title(c,t,16f));s.forEachIndexed{i,x->addView(caption(c,"${i+1}.  $x").apply{setTextColor(SOFT);textSize=14f;setPadding(0,dp(c,6),0,0)})}}
 // A label whose first word is in capitals ("MESH RELAY · …") becomes a section header.
 private fun header(t:String)=t.substringBefore(" ·").substringBefore("\n").let{w->w.length in 4..30 && w.any{it.isLetter()} && w.all{it.isUpperCase()||it==' '||it=='-'||it=='&'}}
 fun style(v:View){if(v.tag==DONE)return;val c=v.context
  when(v){
   is CompoundButton->{v.setTextColor(TEXT);v.textSize=15f;v.setPadding(0,dp(c,12),0,dp(c,12));v.tag=DONE}
   is Button->button(v)
   is EditText->{v.setTextColor(TEXT);v.setHintTextColor(MUTED);v.textSize=15f;v.background=round(c,SURFACE,18,LINE);v.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14))
    (v.layoutParams as? LinearLayout.LayoutParams)?.let{it.setMargins(0,dp(c,6),0,dp(c,6));v.layoutParams=it};v.tag=DONE}
   is TextView->{if(header(v.text.toString())){v.setTextColor(BRAND);v.textSize=12f;v.typeface=Typeface.DEFAULT_BOLD;v.letterSpacing=.08f;v.setPadding(0,dp(c,22),0,dp(c,6))}
    else{v.setTextColor(SOFT);v.textSize=14f;v.setLineSpacing(0f,1.2f);v.setPadding(0,dp(c,3),0,dp(c,3))};v.tag=DONE}
   is ViewGroup->for(i in 0 until v.childCount)style(v.getChildAt(i))
  }}
}