package com.example.gerenciadordeparquinho.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.ui.theme.AppThemeMode
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle

@Composable
fun LayoutScreen(
    isAdmin: Boolean,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    appName: String,
    onAppNameChange: (String) -> Unit,
    titleColor: Color,
    onTitleColorChange: (Color) -> Unit,
    hasOutline: Boolean,
    onOutlineToggle: (Boolean) -> Unit,
    outlineColor: Color,
    onOutlineColorChange: (Color) -> Unit,
    isLogoLocked: Boolean,
    onLogoLockToggle: (Boolean) -> Unit,
    logoBase64: String?, // NOVO PARÂMETRO PARA PRÉVIA
    onSelectLogo: () -> Unit,
    onBack: () -> Unit,
    isLightMode: Boolean = false
) {
    val scrollState = rememberScrollState()
    val highlightStyle = getHighlightStyle(isLightMode)
    
    val colorOptions = listOf(
        IntenseGreen, Color.Yellow, Color.White, 
        Color.Cyan, Color(0xFFFF5722), Color(0xFFE91E63), 
        Color.Red, Color.Green, Color.Black
    )

    var showTextColorPicker by remember { mutableStateOf(false) }
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    Scaffold(
        containerColor = bgColor,
        topBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "PERSONALIZAÇÃO",
                    color = IntenseGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = highlightStyle
                )
                Text(
                    text = "Configure a identidade visual do seu app",
                    color = if(isLightMode) Color.DarkGray else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            LayoutSection(title = "IDENTIDADE DA MARCA", isLightMode = isLightMode) {
                // ÁREA DE PRÉVIA DA LOGO DO LOGIN
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(if(isLightMode) Color(0xFFF0F0F0) else Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .border(1.dp, if(isLightMode) Color.Black else IntenseGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = remember(logoBase64) {
                        if (logoBase64 != null) {
                            try {
                                val bytes = Base64.decode(logoBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) { null }
                        } else null
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxHeight().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSelectLogo,
                    enabled = isAdmin || !isLogoLocked,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogoLocked && !isAdmin) Color.DarkGray else if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if(isLogoLocked) BorderStroke(1.dp, Color.Red) else if(isLightMode) BorderStroke(1.dp, Color.Black) else BorderStroke(1.dp, IntenseGreen.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = if (isLogoLocked && !isAdmin) Icons.Default.Lock else Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = if (isLogoLocked && !isAdmin) Color.Gray else if(isLightMode) Color.Black else IntenseGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isLogoLocked && !isAdmin) "ALTERAÇÃO BLOQUEADA" else "TROCAR LOGO (PNG)",
                        color = if (isLogoLocked && !isAdmin) Color.Gray else if(isLightMode) Color.Black else IntenseGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isLogoLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isLogoLocked) Color.Red else IntenseGreen
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TRAVAR LOGOTIPO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Sincroniza a trava via Firebase", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isLogoLocked,
                            onCheckedChange = onLogoLockToggle,
                            colors = SwitchDefaults.colors(checkedTrackColor = Color.Red)
                        )
                    }
                }
            }

            LayoutSection(title = "ESTILO DO TÍTULO NO LOGIN", isLightMode = isLightMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.Black, RoundedCornerShape(16.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if(appName.isBlank()) "SUA MARCA AQUI" else appName.uppercase(),
                        style = TextStyle(
                            color = titleColor,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            shadow = if (hasOutline) Shadow(color = outlineColor, blurRadius = 12f) else null
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("NOME DO ESTABELECIMENTO", color = if(isLightMode) Color.Black else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = appName,
                    onValueChange = onAppNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IntenseGreen,
                        unfocusedBorderColor = if(isLightMode) Color.Black else Color.DarkGray,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("COR DO TEXTO", color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Alterar cor da fonte principal", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 11.sp)
                    }
                    Switch(checked = showTextColorPicker, onCheckedChange = { showTextColorPicker = it })
                }

                if (showTextColorPicker) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ColorCircleSingle(
                            color = titleColor,
                            onClick = {
                                val currentIndex = colorOptions.indexOf(titleColor)
                                val nextIndex = (currentIndex + 1) % colorOptions.size
                                onTitleColorChange(colorOptions[nextIndex])
                            }
                        )
                        Text("Toque na bolinha para mudar", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CONTORNO (OUTLINE)", color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Adiciona borda às letras", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 11.sp)
                    }
                    Switch(checked = hasOutline, onCheckedChange = onOutlineToggle)
                }

                if (hasOutline) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ColorCircleSingle(
                            color = outlineColor,
                            onClick = {
                                val currentIndex = colorOptions.indexOf(outlineColor)
                                val nextIndex = (currentIndex + 1) % colorOptions.size
                                onOutlineColorChange(colorOptions[nextIndex])
                            }
                        )
                        Text("Toque na bolinha para mudar", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            LayoutSection(title = "TEMA DA INTERFACE", isLightMode = isLightMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeCard(
                        label = "MODO ESCURO",
                        isSelected = currentTheme == AppThemeMode.DARK,
                        onClick = { onThemeChange(AppThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeCard(
                        label = "MODO CLARO",
                        isSelected = currentTheme == AppThemeMode.LIGHT,
                        onClick = { onThemeChange(AppThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(28.dp),
                border = buttonBorder
            ) {
                Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ColorCircleSingle(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape)
            .clickable { onClick() }
    )
}

@Composable
fun LayoutSection(title: String, isLightMode: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = IntenseGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            style = getHighlightStyle(isLightMode)
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = if(isLightMode) Color.Black.copy(alpha = 0.1f) else Color.DarkGray.copy(alpha = 0.5f))
    }
}

@Composable
fun ThemeCard(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier
            .height(60.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) IntenseGreen else Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
