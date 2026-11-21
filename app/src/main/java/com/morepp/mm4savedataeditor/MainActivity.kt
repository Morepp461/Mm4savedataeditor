package com.morepp.mm4savedataeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink=Color(0xFF101216)
private val Panel=Color(0xFF1B2027)
private val Red=Color(0xFFE53935)
private val Gold=Color(0xFFD9B45B)
private val Muted=Color(0xFF9CA6B2)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{EditorApp()}}
}
private data class EditTarget(val root:String,val path:List<String>,val value:JsonElement)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorApp(){
 val context=androidx.compose.ui.platform.LocalContext.current
 val scope= rememberCoroutineScope()
 var doc by remember{mutableStateOf<Mm4SaveCodec.Document?>(null)}
 var tab by remember{mutableStateOf("Save Info")}
 var query by remember{mutableStateOf("")}
 var fileName by remember{mutableStateOf("Belum ada save data")}
 var dirty by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)}
 var edit by remember{mutableStateOf<EditTarget?>(null)}
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->if(uri!=null)scope.launch{
  busy=true;error=null
  try{val loaded=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Tidak bisa membaca file.");Mm4SaveCodec.decode(bytes,uri.lastPathSegment?:"career.msv")}
   doc=loaded;fileName=uri.lastPathSegment?:"MM4 career";tab="Save Info";query="";dirty=false
  }catch(t:Throwable){error=t.message?:"Gagal membuka save."}finally{busy=false}
 }}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri:Uri?->val current=doc
  if(uri!=null&&current!=null)scope.launch{busy=true;error=null
   try{withContext(Dispatchers.IO){val bytes=Mm4SaveCodec.encode(current);val check=Mm4SaveCodec.decode(bytes,current.sourceName)
    check(check.info==current.info&&check.data==current.data){"Validasi round-trip gagal."}
    context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(bytes)}?:error("Tidak bisa menulis file.")
   };dirty=false}catch(t:Throwable){error=t.message?:"Export gagal."}finally{busy=false}
  }
 }
 MaterialTheme(colorScheme=darkColorScheme(background=Ink,surface=Panel,primary=Red,secondary=Gold,onSurface=Color.White,onBackground=Color.White)){
  Scaffold(containerColor=Ink,topBar={TopAppBar(title={Column{
   Text("MM4 SAVE DATA EDITOR",fontSize=15.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp)
   Text("CAREER DATA WORKSHOP",color=Gold,fontSize=9.sp,letterSpacing=1.3.sp)
  }},actions={IconButton(onClick={open.launch(arrayOf("*/*"))}){Icon(Icons.Default.FolderOpen,"Buka save")}
   IconButton(onClick={if(doc!=null)export.launch("MM4_Edited_Save.msv")},enabled=doc!=null){Icon(Icons.Default.FileUpload,"Export")}
  },colors=TopAppBarDefaults.topAppBarColors(containerColor=Ink,titleContentColor=Color.White))}){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    if(doc==null){
     Box(Modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
      .background(Brush.linearGradient(listOf(Color(0xFF292F39),Panel,Color(0xFF3A2024)))).padding(24.dp)){
      Icon(Icons.Default.DirectionsCar,null,tint=Red,modifier=Modifier.size(44.dp));Spacer(Modifier.height(22.dp))
      Text("YOUR CAREER.\nYOUR RULES.",fontSize=30.sp,lineHeight=33.sp,fontWeight=FontWeight.Black)
      Text("Buka save Motorsport Manager 4 untuk menjelajahi dan mengedit data karier.",color=Muted)
      Spacer(Modifier.height(20.dp))
      Button(onClick={open.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Red)){
       Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(8.dp));Text("BUKA FILE SAVE (.MSV)")
      };Spacer(Modifier.height(14.dp));Text("DYNAMIC DATA BROWSER • JSON EDITOR • LZ4 EXPORT",fontSize=9.sp,color=Gold)
     }}
    }else{
     val current=doc!!
     Row(Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF282F39),Panel,Color(0xFF382326)))).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text(current.info.get("teamName")?.asString?:"CAREER LOADED",fontWeight=FontWeight.Black)
       Text(fileName,color=Muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
       Text(if(dirty)"UNSAVED CHANGES" else "SAVE DATA READY",color=if(dirty)Gold else Color(0xFF82D7A2),fontSize=9.sp)
      };Button(onClick={export.launch("MM4_Edited_Save.msv")},colors=ButtonDefaults.buttonColors(containerColor=Red)){Text("EXPORT")}
     }
     Row(Modifier.padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf("Save Info","Game Data").forEach{FilterChip(selected=tab==it,onClick={tab=it;query=""},label={Text(it)})}
     }
     OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp),
      placeholder={Text("Cari field, ID, entitas, atau nilai…")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
     val root=if(tab=="Save Info")current.info else current.data
     val entries=remember(root,query){flatten(root).filter{query.isBlank()||it.first.joinToString(".").contains(query,true)||it.second.toString().contains(query,true)}}
     Text(tab+" • "+entries.size+" FIELD",color=Gold,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(14.dp))
     LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)){
      items(entries,key={it.first.joinToString("/")}){entry->
       Row(Modifier.fillMaxWidth().padding(vertical=3.dp).clip(RoundedCornerShape(12.dp)).background(Panel)
        .clickable{edit=EditTarget(tab,entry.first,entry.second)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(entry.first.last(),fontWeight=FontWeight.SemiBold,fontSize=13.sp)
         Text(entry.first.dropLast(1).joinToString(" › "),color=Muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
         Text(entry.second.toString(),color=Gold,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        };Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(16.dp))
       }
      }
     }
    }
   }
  }
  if(busy)AlertDialog(onDismissRequest={},confirmButton={},title={Text("Memproses save…")},text={LinearProgressIndicator(Modifier.fillMaxWidth())})
  error?.let{msg->AlertDialog(onDismissRequest={error=null},title={Text("Operasi gagal")},text={Text(msg)},confirmButton={TextButton(onClick={error=null}){Text("Tutup")}})}
  edit?.let{target->
   var raw by remember(target){mutableStateOf(target.value.toString())};var invalid by remember{mutableStateOf(false)}
   AlertDialog(onDismissRequest={edit=null},title={Text("EDIT FIELD")},text={Column{
    Text(target.path.joinToString(" › "),color=Gold,fontSize=10.sp)
    OutlinedTextField(value=raw,onValueChange={raw=it;invalid=false},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,isError=invalid,label={Text("JSON value")})
    if(invalid)Text("JSON tidak valid.",color=Red)
    Text("Contoh: 120 • true • \"Nama\" • null",color=Muted,fontSize=10.sp)
   }},confirmButton={Button(onClick={try{val replacement=JsonParser.parseString(raw);val current=doc?:return@Button
    val root=if(target.root=="Save Info")current.info else current.data
    if(setPath(root,target.path,replacement)){doc=current.copy();dirty=true;edit=null}else invalid=true
   }catch(_:Throwable){invalid=true}},colors=ButtonDefaults.buttonColors(containerColor=Red)){Text("SIMPAN")}},
    dismissButton={TextButton(onClick={edit=null}){Text("Batal")}})
  }
 }
}
private fun flatten(root:JsonElement):List<Pair<List<String>,JsonElement>>{
 val out=mutableListOf<Pair<List<String>,JsonElement>>()
 fun walk(node:JsonElement,path:List<String>){when{
  node.isJsonObject->node.asJsonObject.entrySet().forEach{(k,v)->walk(v,path+k)}
  node.isJsonArray->node.asJsonArray.forEachIndexed{i,v->walk(v,path+"[$i]")}
  else->out+=path to node
 }}
 walk(root,emptyList());return out
}
private fun setPath(root:JsonObject,path:List<String>,value:JsonElement):Boolean{
 if(path.isEmpty())return false
 var node:JsonElement=root
 for(part in path.dropLast(1))node=child(node,part)?:return false
 val key=path.last()
 return when{
  node.isJsonObject->{node.asJsonObject.add(key,value);true}
  node.isJsonArray&&key.startsWith("[")->{val i=key.removePrefix("[").removeSuffix("]").toIntOrNull()?:return false
   if(i !in 0 until node.asJsonArray.size())false else{node.asJsonArray.set(i,value);true}}
  else->false
 }
}
private fun child(node:JsonElement,key:String):JsonElement?=when{
 node.isJsonObject->node.asJsonObject.get(key)
 node.isJsonArray&&key.startsWith("[")->key.removePrefix("[").removeSuffix("]").toIntOrNull()?.let{if(it in 0 until node.asJsonArray.size())node.asJsonArray.get(it)else null}
 else->null
}