package org.nodelink

import android.content.Context
import android.graphics.*
import android.view.View

class NeonIcon(c:Context,private val kind:String):View(c){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.85f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
 var tint=Ui.BRAND;set(value){field=value;invalidate()}
 init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas);val s=minOf(width,height)/24f;if(s<=0f)return
  canvas.save();canvas.translate((width-24*s)/2,(height-24*s)/2);canvas.scale(s,s);p.color=tint
  fun lines(vararg pt:Float){val path=Path();path.moveTo(pt[0],pt[1]);for(i in 2 until pt.size step 2)path.lineTo(pt[i],pt[i+1]);canvas.drawPath(path,p)}
  fun ring(x:Float,y:Float,r:Float){canvas.drawCircle(x,y,r,p)}
  fun box(l:Float,t:Float,r:Float,b:Float){canvas.drawRoundRect(l,t,r,b,3f,3f,p)}
  when(kind){
   "home"->{lines(2f,11f,12f,3f,22f,11f);lines(5f,10f,5f,21f,9f,21f,9f,15f,15f,15f,15f,21f,19f,21f,19f,10f)}
   "chat"->{canvas.drawArc(3f,4f,21f,20f,77f,323f,false,p);lines(8f,19f,4f,21f,5f,16f);for(i in 0..2)ring(8f+4*i,12f,.45f)}
   "call"->{val arc=Path().apply{moveTo(8f,3f);lineTo(11f,8f);lineTo(8f,11f);quadTo(10f,15f,14f,16f);lineTo(17f,13f);lineTo(22f,17f);quadTo(22f,22f,18f,22f);cubicTo(10f,21f,3f,14f,3f,7f);quadTo(3f,3f,8f,3f)};canvas.drawPath(arc,p)}
   "contacts"->{ring(9f,7f,3f);ring(17f,8f,2.5f);canvas.drawArc(2f,13f,16f,23f,180f,180f,false,p);canvas.drawArc(13f,14f,23f,22f,224f,136f,false,p)}
   "wallet"->{box(3f,7f,21f,21f);lines(8f,7f,8f,4f,16f,4f,16f,7f);ring(12f,13f,1.3f)}
   "explorer"->{ring(12f,12f,9f);lines(8f,16f,10f,10f,16f,8f,14f,14f,8f,16f)}
   "send"->{lines(3f,10f,21f,3f,15f,21f,11f,13f,3f,10f);lines(11f,13f,21f,3f)}
   "receive"->{lines(12f,3f,12f,15f);lines(7f,10f,12f,15f,17f,10f);lines(4f,16f,4f,21f,20f,21f,20f,16f)}
   "refresh"->{canvas.drawArc(4f,4f,20f,20f,25f,305f,false,p);lines(20f,5f,20f,11f,14f,11f)}
   "more"->{ring(5f,12f,1f);ring(12f,12f,1f);ring(19f,12f,1f)}
  };canvas.restore()
 }
}