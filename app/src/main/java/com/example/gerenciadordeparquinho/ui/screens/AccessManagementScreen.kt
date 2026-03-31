package com.example.gerenciadordeparquinho.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.data.model.UserAccount
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import kotlinx.coroutines.launch

@Composable
fun AccessManagementScreen(
    isLightMode: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var searchInput by rememberSaveable { mutableStateOf("") }
    var selectedUsername by rememberSaveable { mutableStateOf("") }
    
    val allUsers by db.userDao().getAllUsers().collectAsState(initial = emptyList())
    val selectedUser = allUsers.find { it.username == selectedUsername }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "GESTÃO DE ACESSOS", 
            color = IntenseGreen, 
            fontSize = 24.sp, 
            fontWeight = FontWeight.ExtraBold, 
            style = highlightStyle
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it.lowercase().trim() },
            label = { Text("Digite o usuário", color = if(isLightMode) Color.Black else IntenseGreen) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen,
                unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen.copy(alpha = 0.5f),
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val found = allUsers.find { it.username == searchInput }
                if (found == null) {
                    Toast.makeText(context, "NÃO ENCONTRADO", Toast.LENGTH_SHORT).show()
                    selectedUsername = ""
                } else {
                    selectedUsername = found.username
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(27.dp),
            border = buttonBorder
        ) {
            Text("BUSCAR USUÁRIO", fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(32.dp))

        selectedUser?.let { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)),
                border = BorderStroke(1.dp, if(isLightMode) Color.Black else IntenseGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = user.username.uppercase(), color = textColor, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        if (user.role == "ADMIN" || user.username == "admin") {
                            Badge(containerColor = IntenseGreen, contentColor = Color.Black) { Text("ADMIN") }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("PERMISSÕES DE MENU", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionSwitch("Menu Cadastrar", user.canRegister, isLightMode) { 
                        scope.launch { db.userDao().updateUser(user.copy(canRegister = it)) }
                    }
                    PermissionSwitch("Menu Relatório", user.canReport, isLightMode) { 
                        scope.launch { db.userDao().updateUser(user.copy(canReport = it)) }
                    }
                    PermissionSwitch("Menu Config", user.canSettings, isLightMode) { 
                        scope.launch { db.userDao().updateUser(user.copy(canSettings = it)) }
                    }
                    PermissionSwitch("Menu Layout", user.canLayout, isLightMode) { 
                        scope.launch { db.userDao().updateUser(user.copy(canLayout = it)) }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showBlockConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.isBlocked) Color.Gray else Color(0xFFFF9800)
                            ),
                            enabled = user.username != "admin",
                            border = buttonBorder
                        ) {
                            Text(if (user.isBlocked) "DESBLOQUEAR USUÁRIO" else "BLOQUEAR USUÁRIO", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            enabled = user.username != "admin",
                            border = buttonBorder
                        ) {
                            Text("EXCLUIR USUÁRIO", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        Text("TODOS OS USUÁRIOS", color = if(isLightMode) Color.Black else Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(8.dp))
        
        allUsers.forEach { u ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { 
                        searchInput = u.username
                        selectedUsername = u.username 
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(u.username.uppercase(), color = if (u.isBlocked) Color.Red else textColor, fontWeight = FontWeight.Bold)
                    Text(if (u.isBlocked) "Bloqueado" else "Ativo", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 11.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                }
                Icon(Icons.Default.ChevronRight, null, tint = IntenseGreen)
            }
            HorizontalDivider(color = if(isLightMode) Color.Black.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
        }
        
        Spacer(Modifier.height(40.dp))
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF212121),
            title = { Text("CONFIRMAÇÃO", color = IntenseGreen, style = highlightStyle) },
            text = { Text("Deseja realmente ${if(selectedUser?.isBlocked == true) "desbloquear" else "bloquear"} este usuário?", color = textColor) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedUser?.let {
                            db.userDao().updateUser(it.copy(isBlocked = !it.isBlocked))
                            Toast.makeText(context, "AÇÃO REALIZADA!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showBlockConfirm = false
                }) { Text("SIM", color = IntenseGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("CANCELAR", color = if(isLightMode) Color.Black else Color.Gray) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF212121),
            title = { Text("ALERTA MÁXIMO", color = Color.Red) },
            text = { Text("Tem certeza que deseja EXCLUIR permanentemente este usuário? Esta ação não pode ser desfeita.", color = textColor) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedUser?.let {
                            db.userDao().deleteUser(it)
                            selectedUsername = ""
                            searchInput = ""
                            Toast.makeText(context, "USUÁRIO EXCLUÍDO!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showDeleteConfirm = false
                }) { Text("EXCLUIR AGORA", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCELAR", color = if(isLightMode) Color.Black else Color.Gray) } }
        )
    }
}

@Composable
fun PermissionSwitch(label: String, checked: Boolean, isLightMode: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
        
        val switchColors = if (isLightMode) {
            // MODO CLARO: Preto sólido ao acionar
            SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.Black.copy(alpha = 0.3f),
                checkedBorderColor = Color.Black,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f),
                uncheckedBorderColor = Color.Gray
            )
        } else {
            // MODO ESCURO: Verde intenso (Padrão original)
            SwitchDefaults.colors(
                checkedThumbColor = IntenseGreen,
                checkedTrackColor = IntenseGreen.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.DarkGray,
                uncheckedTrackColor = Color.Black
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors
        )
    }
}
