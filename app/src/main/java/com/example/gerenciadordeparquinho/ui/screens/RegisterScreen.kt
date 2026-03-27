package com.example.gerenciadordeparquinho.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.R
import com.example.gerenciadordeparquinho.data.model.UserAccount
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    customLogo: String? = null,
    onBack: () -> Unit,
    isLightMode: Boolean = false // PADRONIZAÇÃO CIRÚRGICA
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var u by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("") }
    var cp by rememberSaveable { mutableStateOf("") }
    var pVisible by rememberSaveable { mutableStateOf(false) }
    var cpVisible by rememberSaveable { mutableStateOf(false) }
    var err by rememberSaveable { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = IntenseGreen,
        unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        cursorColor = IntenseGreen
    )

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!customLogo.isNullOrEmpty()) {
            val bitmap = remember(customLogo) {
                try {
                    val bytes = Base64.decode(customLogo, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) { null }
            }
            bitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Logo", modifier = Modifier.size(150.dp), contentScale = ContentScale.Fit)
            }
        } else {
            Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(150.dp), contentScale = ContentScale.Fit)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "CRIAR NOVA CONTA", 
            fontSize = 24.sp, 
            fontWeight = FontWeight.ExtraBold, 
            color = IntenseGreen,
            style = highlightStyle
        )
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = u,
            onValueChange = { u = it.lowercase().trim(); err = "" },
            label = { Text("Usuário", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = IntenseGreen) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = p,
            onValueChange = { p = it; err = "" },
            label = { Text("Senha", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
            visualTransformation = if (pVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { pVisible = !pVisible }) {
                    Icon(imageVector = if (pVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = cp,
            onValueChange = { cp = it; err = "" },
            label = { Text("Confirmar Senha", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
            visualTransformation = if (cpVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { cpVisible = !cpVisible }) {
                    Icon(imageVector = if (cpVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors
        )
        
        if (err.isNotEmpty()) Text(err, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (u.isBlank() || p.isBlank()) {
                    err = "PREENCHA TODOS OS CAMPOS"
                } else if (p != cp) {
                    err = "AS SENHAS NÃO CONFEREM"
                } else {
                    scope.launch {
                        val existing = db.userDao().getUserByUsername(u)
                        if (existing != null) err = "USUÁRIO JÁ EXISTE"
                        else {
                            val newUser = UserAccount(username = u, pass = p)
                            db.userDao().insertUser(newUser)
                            Toast.makeText(context, "CONTA CRIADA COM SUCESSO!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            border = buttonBorder
        ) {
            Text("CADASTRAR AGORA", fontWeight = FontWeight.Black)
        }

        TextButton(onClick = onBack) { Text("VOLTAR PARA LOGIN", color = if(isLightMode) Color.Black else Color.Gray, fontWeight = FontWeight.Bold) }
    }
}
