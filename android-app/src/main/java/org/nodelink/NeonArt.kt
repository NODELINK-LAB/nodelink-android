package org.nodelink

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class NeonArt(c:Context, private val mode:String):View(c){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas); if(width==0||height==0)return
  val a=canvas; a.save(); a.scale(width/320f,height/160f)
  val cx=if(mode=="wallet")160f else 205f
  val glow=if(mode=="wallet")75f else 83f
  p.style=Paint.Style.FILL;p.shader=RadialGradient(cx,79f,glow,intArrayOf(0x7739FF25,0x2800DA42,0),null,Shader.TileMode.CLAMP)
  a.drawRect(0f,0f,320f,160f,p);p.shader=null
  val r=if(mode=="wallet")75f else 91f
  for(y in 0..31)for(x in 0..56){
   val u=(x-28)/28.0;val v=(y-15)/16.0
   if(u*u+v*v>.99||sin(u*11+v*5)+cos(v*14-u*4)+sin(u*20+v*12)<.15)continue
   p.color=0x0000EA4A or ((30+((1-v*v)*100).toInt()) shl 24)
   a.drawCircle(cx+u.toFloat()*r,83f+v.toFloat()*r,.66f,p)
  }
  p.style=Paint.Style.STROKE;p.strokeWidth=1.1f
  repeat(3){i->a.save();a.rotate(if(mode=="wallet")-9f else -22f,160f,80f);p.color=(0x4200FF74+i*0x17000000);a.drawOval(30f+i*8,53f+i*11,290f-i*8,111f+i*5,p);a.restore()}
  p.style=Paint.Style.FILL
  if(mode=="wallet"){
   p.shader=LinearGradient(102f,41f,221f,125f,intArrayOf(0xFF25493E.toInt(),0xFF031612.toInt(),0xFF145C39.toInt()),null,Shader.TileMode.CLAMP)
   a.drawRoundRect(96f,42f,224f,124f,15f,15f,p);p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=0xFF74FF44.toInt();a.drawRoundRect(96f,42f,224f,124f,15f,15f,p);p.style=Paint.Style.FILL
  }
  if(mode=="identity"){
   p.style=Paint.Style.FILL;p.color=Ui.RAISED;a.drawCircle(160f,78f,49f,p);p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=Ui.BRAND;a.drawCircle(160f,78f,49f,p);p.style=Paint.Style.FILL
  }
  run{
   a.translate(160f,when(mode){"wallet"->82f;"identity"->78f;else->72f});val scale=when(mode){"wallet"->.69f;"identity"->.89f;else->1.27f};a.scale(scale,scale)
   val mark=Path().apply{moveTo(-23f,20f);lineTo(-23f,-19f);lineTo(-11f,-26f);lineTo(15f,-3f);lineTo(15f,-22f);lineTo(24f,-27f);lineTo(24f,16f);lineTo(13f,24f);lineTo(-13f,1f);lineTo(-13f,26f);close()}
   p.shader=LinearGradient(0f,-26f,4f,26f,intArrayOf(0xFFE1FF79.toInt(),0xFF92FF2E.toInt(),0xFF12B93B.toInt()),null,Shader.TileMode.CLAMP);a.drawPath(mark,p);p.shader=null
   p.style=Paint.Style.STROKE;p.strokeWidth=1.1f;p.color=0xC5C5FF75.toInt();a.drawPath(mark,p);p.style=Paint.Style.FILL
  }
  a.restore()
 }
}