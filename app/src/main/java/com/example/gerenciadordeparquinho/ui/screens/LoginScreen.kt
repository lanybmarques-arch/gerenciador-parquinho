package com.example.gerenciadordeparquinho.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.R
import com.example.gerenciadordeparquinho.data.model.UserAccount
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    appName: String,
    titleColor: Color,
    hasOutline: Boolean,
    outlineColor: Color,
    customLogo: String? = null,
    onLoginSuccess: (UserAccount) -> Unit,
    onForgot: () -> Unit,
    onChange: () -> Unit,
    onRegister: () -> Unit,
    storedPass: String,
    isLightMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var passVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryTextColor = if (isLightMode) Color.DarkGray else Color.Gray

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 28.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        if (!customLogo.isNullOrEmpty()) {
            val bitmap = remember(customLogo) {
                try {
                    val bytes = Base64.decode(customLogo, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) { null }
            }
            bitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Logo", modifier = Modifier.size(220.dp), contentScale = ContentScale.Fit)
            }
        } else {
            Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(220.dp), contentScale = ContentScale.Fit)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appName.uppercase(),
            style = TextStyle(
                color = titleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                shadow = if (hasOutline || isLightMode) Shadow(color = if(isLightMode && !hasOutline) Color.Black else outlineColor, blurRadius = 8f) else null
            )
        )
        
        Text(text = "CONTROLE DE ACESSO", color = secondaryTextColor, fontSize = 13.sp, fontWeight = if(isLightMode) FontWeight.ExtraBold else FontWeight.Medium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it.lowercase().trim(); error = "" },
            label = { Text("Usuário", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = IntenseGreen) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen,
                unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = IntenseGreen
            )
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it; error = "" },
            label = { Text("Senha", color = if(isLightMode) Color.Black else IntenseGreen) },
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(imageVector = if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen,
                unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = IntenseGreen
            )
        )

        if (error.isNotEmpty()) Text(text = error, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (user.isEmpty() || pass.isEmpty()) {
                    error = "PREENCHA OS CAMPOS"
                } else {
                    scope.launch {
                        try {
                            if (user == "admin" && pass == "102030aa") {
                                onLoginSuccess(UserAccount(username = "admin", pass = pass, role = "ADMIN"))
                                return@launch
                            }
                            val account = db.userDao().getUserByUsername(user)
                            if (account != null && account.pass == pass) {
                                if (account.isBlocked) error = "USUÁRIO BLOQUEADO" else onLoginSuccess(account)
                            } else error = "DADOS INCORRETOS"
                        } catch (e: Exception) {
                            if (user == "admin" && pass == "102030aa") onLoginSuccess(UserAccount(username = "admin", pass = pass, role = "ADMIN"))
                            else error = "ERRO AO ACESSAR BANCO"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "ACESSAR SISTEMA", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "NÃO TEM CONTA? ", color = secondaryTextColor, fontSize = 13.sp, fontWeight = if(isLightMode) FontWeight.Black else FontWeight.Normal)
            TextButton(onClick = onRegister) { Text(text = "CRIAR CONTA", color = IntenseGreen, fontWeight = FontWeight.Bold, style = if(isLightMode) TextStyle(shadow = Shadow(Color.Black, blurRadius = 2f)) else TextStyle.Default) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onForgot) {
                Text(text = "ESQUECI A SENHA", color = secondaryTextColor, fontSize = 12.sp, fontWeight = if(isLightMode) FontWeight.Black else FontWeight.Normal)
            }
            TextButton(onClick = onChange) {
                Text(text = "ALTERAR SENHA", color = IntenseGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, style = if(isLightMode) TextStyle(shadow = Shadow(Color.Black, blurRadius = 2f)) else TextStyle.Default)
            }
        }
    }
}
