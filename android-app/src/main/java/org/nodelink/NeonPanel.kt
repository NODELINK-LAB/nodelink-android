package org.nodelink

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.widget.*

object NeonPanel {
 fun header(c:Context,title:String,back:(()->Unit)?=null,icon:String?=null,action:(()->Unit)?=null)=LinearLayout(c).apply{
  gravity=Gravity.CENTER_VERTICAL
  fun edge(kind:String?,label:String,click:(()->Unit)?)=FrameLayout(c).apply{
   if(kind!=null && click!=null){contentDescription=label;setOnClickListener{click()};addView(NeonIcon(c,kind),FrameLayout.LayoutParams(Ui.dp(c,22),Ui.dp(c,22),Gravity.CENTER))}
  }
  addView(edge(if(back!=null)"back" else null,"Back",back),Ui.lp(Ui.dp(c,44),Ui.dp(c,48)))
  addView(Ui.heading(c,title),Ui.lp(0,-2,1f))
  addView(edge(icon,if(icon=="plus")"New contact" else "Refresh balance",action),Ui.lp(Ui.dp(c,44),Ui.dp(c,48)))
 }
 fun row(c:Context,icon:String,title:String,detail:TextView?=null,click:()->Unit)=Ui.card(c).apply{
  orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
  addView(NeonIcon(c,icon),Ui.lp(Ui.dp(c,28),Ui.dp(c,28)))
  addView(LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(c,13),0,Ui.dp(c,6),0);addView(Ui.title(c,title,15f));detail?.let{addView(it)}},Ui.lp(0,-2,1f))
  addView(NeonIcon(c,"next").apply{tint=Ui.TEXT},Ui.lp(Ui.dp(c,18),Ui.dp(c,18)));setOnClickListener{click()}
 }
 fun wave(c:Context)=object:View(c){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
  override fun onDraw(canvas:Canvas){
   val w=width.toFloat();val h=height.toFloat();if(w==0f||h==0f)return
   for(i in 0..10){val off=i*.045f;val path=Path().apply{moveTo(0f,h*(.82f+off));cubicTo(w*.20f,h*(.99f+off),w*.26f,h*(.09f+off),w*.49f,h*(.46f+off));cubicTo(w*.70f,h*(.84f+off),w*.77f,h*(.07f+off),w,h*(.2f+off))}
    paint.style=Paint.Style.STROKE;paint.strokeWidth=if(i==0)Ui.dp(c,1).toFloat() else .75f
    paint.shader=LinearGradient(0f,0f,w,0f,0x0066F052,if(i==0)Ui.GREEN else 0x4266F052,Shader.TileMode.CLAMP);canvas.drawPath(path,paint)
   }
  }
 }
}