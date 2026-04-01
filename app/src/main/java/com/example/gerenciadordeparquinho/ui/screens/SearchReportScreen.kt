package com.example.gerenciadordeparquinho.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
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
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SearchReportScreen(
    appName: String = "", // Mensagem customizada das configurações
    printerMac: String = "",
    printerSize: String = "58mm",
    logoBase64: String? = null,
    onBack: () -> Unit,
    isLightMode: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryColor = if (isLightMode) Color.DarkGray else Color.Gray
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var selectedDate by rememberSaveable { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var childName by rememberSaveable { mutableStateOf("") }
    
    val allSessionsByDate by db.sessionDao().getSessionsByDate(selectedDate).collectAsState(initial = emptyList())
    
    // LISTA ABAIXO: Mostra todos que começam com o nome digitado (Referência)
    val filteredSessionsForList = remember(allSessionsByDate, childName) {
        if (childName.isBlank()) {
            allSessionsByDate
        } else {
            allSessionsByDate.filter { 
                it.personName.startsWith(childName, ignoreCase = true)
            }
        }
    }
    
    // TOTAL GERAL (RESUMO PARA): Calcula apenas o valor do nome EXATO digitado
    val totalGeralExato = remember(allSessionsByDate, childName) {
        if (childName.isBlank()) {
            allSessionsByDate.sumOf { it.totalValueAccumulated }
        } else {
            allSessionsByDate.filter { 
                it.personName.equals(childName.trim(), ignoreCase = true) 
            }.sumOf { it.totalValueAccumulated }
        }
    }

    // LISTA EXATA PARA IMPRESSÃO DO RESUMO
    val sessionsForPrint = remember(allSessionsByDate, childName) {
        if (childName.isBlank()) allSessionsByDate 
        else allSessionsByDate.filter { it.personName.equals(childName.trim(), ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = IntenseGreen)
            }
            Text(
                text = "BUSCAR POR CRIANÇA",
                color = IntenseGreen,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                style = highlightStyle
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .border(if(isLightMode) BorderStroke(1.dp, Color.Black) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(12.dp))
                    .clickable {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            val calSet = Calendar.getInstance(); calSet.set(y, m, d)
                            selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calSet.time)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, null, tint = IntenseGreen, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("DATA SELECIONADA", fontSize = 10.sp, color = secondaryColor, fontWeight = FontWeight.Bold)
                    Text(selectedDate, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = {
                    if (printerMac.isNotEmpty()) {
                        BluetoothPrinterHelper.printChildSummary(
                            macAddress = printerMac,
                            childName = if(childName.isEmpty()) "TODOS" else childName,
                            date = selectedDate,
                            history = sessionsForPrint,
                            total = totalGeralExato,
                            size = printerSize,
                            logoBase64 = logoBase64,
                            customMessage = appName,
                            onResult = { success, msg -> 
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Selecione uma impressora!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .border(if(isLightMode) BorderStroke(1.dp, Color.Black) else BorderStroke(1.dp, IntenseGreen.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Print, contentDescription = "Imprimir", tint = IntenseGreen, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Nome da Criança", color = if(isLightMode) Color.Black else IntenseGreen) },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = IntenseGreen) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen,
                unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen.copy(alpha = 0.6f),
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                cursorColor = IntenseGreen
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF5F5F5) else Color.Transparent),
            border = BorderStroke(1.dp, if(isLightMode) Color.Black else IntenseGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "RESUMO PARA: ${if(childName.isEmpty()) "TODOS" else childName.uppercase()}",
                    color = IntenseGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = highlightStyle
                )
                Text(
                    text = "TOTAL GERAL: R$ %.2f".format(totalGeralExato),
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredSessionsForList) { session ->
                SearchItemUI(
                    session = session,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    isLightMode = isLightMode,
                    onPrint = {
                        if (printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printEntranceTicket(printerMac, session, printerSize, logoBase64, appName)
                        } else {
                            Toast.makeText(context, "Configure a impressora!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SearchItemUI(session: PlaySession, textColor: Color, secondaryColor: Color, isLightMode: Boolean, onPrint: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.personName.uppercase(), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "Início: ${session.startTime} | Fim: ${session.endTime ?: "--:--:--"}", color = secondaryColor, fontSize = 12.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "R$ %.2f".format(session.totalValueAccumulated), color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                IconButton(onClick = onPrint) {
                    Icon(Icons.Default.Print, null, tint = IntenseGreen, modifier = Modifier.size(24.dp))
                }
            }
        }
        HorizontalDivider(color = secondaryColor.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}
