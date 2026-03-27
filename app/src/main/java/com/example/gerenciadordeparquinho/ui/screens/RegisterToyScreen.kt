package com.example.gerenciadordeparquinho.ui.screens
import com.example.gerenciadordeparquinho.utils.base64ToBitmap
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.data.model.Toy
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun RegisterToyScreen(isLightMode: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryColor = if (isLightMode) Color.DarkGray else Color.Gray
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var editingToy by remember { mutableStateOf<Toy?>(null) }
    var toyName by remember { mutableStateOf("") }
    var toyValue by remember { mutableStateOf("") }
    var toyMinutes by remember { mutableStateOf("") }
    var isAlwaysFree by remember { mutableStateOf(false) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val toys by db.toyDao().getAllToys().collectAsState(initial = emptyList())

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
                selectedImageBitmap = bitmap
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = IntenseGreen,
        unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen.copy(alpha = 0.6f),
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        cursorColor = IntenseGreen
    )

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (editingToy == null) "CADASTRAR BRINQUEDO" else "EDITAR BRINQUEDO", 
            color = IntenseGreen, 
            fontSize = 24.sp, 
            fontWeight = FontWeight.ExtraBold,
            style = highlightStyle
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .border(1.dp, if(isLightMode) Color.Black else IntenseGreen, RoundedCornerShape(12.dp))
                    .background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageBitmap != null) {
                    Image(bitmap = selectedImageBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.AddAPhoto, null, tint = if(isLightMode) Color.Black else IntenseGreen, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = toyName,
                    onValueChange = { toyName = it },
                    label = { Text("Nome", fontSize = 12.sp, color = if(isLightMode) Color.Black else IntenseGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = toyValue,
                        onValueChange = { toyValue = it },
                        label = { Text("Valor R$", fontSize = 11.sp, color = if(isLightMode) Color.Black else IntenseGreen) },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = toyMinutes,
                        onValueChange = { toyMinutes = it },
                        label = { Text("Minutos", fontSize = 11.sp, color = if(isLightMode) Color.Black else IntenseGreen) },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isAlwaysFree, onCheckedChange = { isAlwaysFree = it }, colors = CheckboxDefaults.colors(checkedColor = IntenseGreen))
            Text("Sempre Livre (Múltiplas crianças)", color = textColor, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (toyName.isNotBlank() && toyValue.isNotBlank()) {
                    scope.launch {
                        var base64Image: String? = null
                        selectedImageBitmap?.let { bitmap ->
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 70, outputStream)
                            base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                        }

                        val toyToSave = editingToy?.copy(
                            name = toyName,
                            price = toyValue.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            timeMinutes = toyMinutes.toIntOrNull() ?: 0,
                            isAlwaysFree = isAlwaysFree,
                            imageBase64 = base64Image ?: editingToy?.imageBase64
                        ) ?: Toy(
                            name = toyName,
                            price = toyValue.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            timeMinutes = toyMinutes.toIntOrNull() ?: 0,
                            isAlwaysFree = isAlwaysFree,
                            imageBase64 = base64Image
                        )

                        db.toyDao().insertToy(toyToSave)
                        editingToy = null
                        toyName = ""; toyValue = ""; toyMinutes = ""; isAlwaysFree = false; selectedImageBitmap = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(28.dp),
            border = buttonBorder
        ) {
            Text(if (editingToy == null) "SALVAR BRINQUEDO" else "ATUALIZAR BRINQUEDO", fontWeight = FontWeight.Black)
        }

        if (editingToy != null) {
            TextButton(onClick = {
                editingToy = null
                toyName = ""; toyValue = ""; toyMinutes = ""; isAlwaysFree = false; selectedImageBitmap = null
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("CANCELAR EDIÇÃO", color = secondaryColor, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        toys.forEach { toy ->
            ToyItemUI(
                toy = toy,
                textColor = textColor,
                secondaryColor = secondaryColor,
                isLightMode = isLightMode,
                onDelete = { scope.launch { db.toyDao().deleteToy(toy) } },
                onEdit = {
                    editingToy = toy
                    toyName = toy.name
                    toyValue = toy.price.toString()
                    toyMinutes = toy.timeMinutes.toString()
                    isAlwaysFree = toy.isAlwaysFree
                    selectedImageBitmap = base64ToBitmap(toy.imageBase64)
                }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ToyItemUI(toy: Toy, textColor: Color, secondaryColor: Color, isLightMode: Boolean, onDelete: () -> Unit, onEdit: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                val bitmap = base64ToBitmap(toy.imageBase64)
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Toys, null, tint = IntenseGreen, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = toy.name.uppercase(), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "R$ %.2f | ${toy.timeMinutes} MIN".format(toy.price), color = IntenseGreen, fontSize = 13.sp, fontWeight = if(isLightMode) FontWeight.Black else FontWeight.Normal)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = IntenseGreen) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
        }
        HorizontalDivider(color = secondaryColor.copy(alpha = 0.3f))
    }
}
