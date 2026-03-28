package com.example.gerenciadordeparquinho.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    currentPass: String,
    currentUser: String,
    onConfirm: () -> Unit,
    isLightMode: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var user by remember { mutableStateOf("") }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    
    var oldPassVisible by remember { mutableStateOf(false) }
    var newPassVisible by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = IntenseGreen,
        unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        cursorColor = IntenseGreen
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(60.dp))
        
        Text(
            text = "ALTERAR SENHA",
            color = IntenseGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            style = highlightStyle
        )
        
        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Usuário", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = IntenseGreen) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = fieldColors,
            readOnly = false
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = oldPass,
            onValueChange = { oldPass = it },
            label = { Text("Senha Atual", color = if(isLightMode) Color.Black else IntenseGreen) },
            visualTransformation = if (oldPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
            trailingIcon = {
                IconButton(onClick = { oldPassVisible = !oldPassVisible }) {
                    Icon(imageVector = if (oldPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = fieldColors
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = newPass,
            onValueChange = { newPass = it },
            label = { Text("Nova Senha", color = if(isLightMode) Color.Black else IntenseGreen) },
            visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
            trailingIcon = {
                IconButton(onClick = { newPassVisible = !newPassVisible }) {
                    Icon(imageVector = if (newPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = fieldColors
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (user.isBlank()) {
                    Toast.makeText(context, "DIGITE O USUÁRIO", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    val account = db.userDao().getUserByUsername(user)
                    if (account == null) {
                        Toast.makeText(context, "USUÁRIO NÃO ENCONTRADO", Toast.LENGTH_SHORT).show()
                    } else if (account.pass != oldPass) {
                        Toast.makeText(context, "SENHA ATUAL INCORRETA", Toast.LENGTH_SHORT).show()
                    } else if (newPass.isBlank()) {
                        Toast.makeText(context, "DIGITE A NOVA SENHA", Toast.LENGTH_SHORT).show()
                    } else {
                        db.userDao().updatePassword(user, newPass)
                        Toast.makeText(context, "SENHA ALTERADA COM SUCESSO!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(28.dp),
            border = buttonBorder
        ) {
            Text("SALVAR NOVA SENHA", fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text("CANCELAR", color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
