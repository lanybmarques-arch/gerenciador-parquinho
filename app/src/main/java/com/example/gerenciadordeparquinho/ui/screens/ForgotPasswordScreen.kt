package com.example.gerenciadordeparquinho.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PublishedWithChanges
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
fun ForgotPasswordScreen(
    onBack: () -> Unit,
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
    var adminPass by remember { mutableStateOf("") }
    var adminPassVisible by remember { mutableStateOf(false) }
    var revealedPassword by remember { mutableStateOf<String?>(null) }

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
        
        Icon(
            imageVector = Icons.Default.PublishedWithChanges,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = IntenseGreen
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "RECUPERAR SENHA",
            color = IntenseGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            style = highlightStyle
        )
        
        Spacer(Modifier.height(40.dp))

        if (revealedPassword == null) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it.lowercase().trim() },
                label = { Text("Usuário", color = if(isLightMode) Color.Black else IntenseGreen) },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = IntenseGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = fieldColors
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = adminPass,
                onValueChange = { adminPass = it },
                label = { Text("Senha do Admin", color = if(isLightMode) Color.Black else IntenseGreen) },
                visualTransformation = if (adminPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = IntenseGreen) },
                trailingIcon = {
                    IconButton(onClick = { adminPassVisible = !adminPassVisible }) {
                        Icon(imageVector = if (adminPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = IntenseGreen)
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
                    if (adminPass == "102030aa") {
                        scope.launch {
                            val account = db.userDao().getUserByUsername(user)
                            if (account != null) revealedPassword = account.pass
                            else Toast.makeText(context, "USUÁRIO NÃO ENCONTRADO", Toast.LENGTH_SHORT).show()
                        }
                    } else Toast.makeText(context, "SENHA ADMIN INCORRETA", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(28.dp),
                border = buttonBorder
            ) {
                Text("REVELAR SENHA", fontWeight = FontWeight.Black)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)),
                border = BorderStroke(1.dp, if(isLightMode) Color.Black else IntenseGreen),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SENHA:", color = if(isLightMode) Color.Black else Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(revealedPassword!!, color = IntenseGreen, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { revealedPassword = null; user = ""; adminPass = "" },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(28.dp),
                border = buttonBorder
            ) {
                Text("NOVA CONSULTA", fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("VOLTAR PARA LOGIN", color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
