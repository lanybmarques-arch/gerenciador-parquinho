package com.example.gerenciadordeparquinho

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.data.model.UserAccount
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.screens.*
import com.example.gerenciadordeparquinho.ui.theme.AppThemeMode
import com.example.gerenciadordeparquinho.ui.theme.BrincandoTheme
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.utils.TimerService
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            val savedTheme = remember { sharedPrefs.getString("theme_mode", AppThemeMode.DARK.name) }
            var appThemeMode by rememberSaveable { mutableStateOf(AppThemeMode.valueOf(savedTheme ?: AppThemeMode.DARK.name)) }

            BrincandoTheme(appThemeMode = appThemeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(themeMode = appThemeMode, onThemeChange = { appThemeMode = it; sharedPrefs.edit().putString("theme_mode", it.name).apply() })
                }
            }
        }
    }
}

@Composable
fun MainApp(themeMode: AppThemeMode, onThemeChange: (AppThemeMode) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    var currentScreen by rememberSaveable { mutableStateOf("login") }
    var isLogged by rememberSaveable { mutableStateOf(false) }
    var isAdmin by rememberSaveable { mutableStateOf(false) }
    var loggedUser by remember { mutableStateOf<UserAccount?>(null) }
    var isSoundEnabled by rememberSaveable { mutableStateOf(true) }
    
    var loginTitle by rememberSaveable { mutableStateOf("BRINCANDO NA PRAÇA") }
    var printerMessage by rememberSaveable { mutableStateOf("") }
    
    var titleColorArgb by remember { mutableIntStateOf(IntenseGreen.toArgb()) }
    var hasOutline by rememberSaveable { mutableStateOf(false) }
    var outlineColorArgb by remember { mutableIntStateOf(Color.Black.toArgb()) }
    var isLogoLocked by rememberSaveable { mutableStateOf(false) }
    var customLogoBase64 by rememberSaveable { mutableStateOf<String?>(null) }
    var showLogo by rememberSaveable { mutableStateOf(false) }
    var showPrinterMessage by rememberSaveable { mutableStateOf(false) }
    
    var printerMac by rememberSaveable { mutableStateOf(sharedPrefs.getString("last_printer_mac", "") ?: "") }
    var printerSize by rememberSaveable { mutableStateOf(sharedPrefs.getString("last_printer_size", "58mm") ?: "58mm") }

    // INICIA/PARA O SERVIÇO DE MONITORAMENTO EM BACKGROUND
    LaunchedEffect(isLogged) {
        val serviceIntent = Intent(context, TimerService::class.java)
        if (isLogged) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            context.stopService(serviceIntent)
        }
    }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                customLogoBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                showLogo = true
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val isLightMode = themeMode == AppThemeMode.LIGHT

    Scaffold(
        bottomBar = {
            if (isLogged && currentScreen !in listOf("search_report")) {
                Surface(color = if(isLightMode) Color.White else Color.Black, modifier = Modifier.fillMaxWidth().height(105.dp), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val navItems = mutableListOf<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, String>>()
                        navItems.add(Triple("home", Icons.Default.Home, "Controle"))
                        if (isAdmin || loggedUser?.canRegister == true) navItems.add(Triple("register_toy", Icons.Default.Add, "Cadastrar"))
                        if (isAdmin || loggedUser?.canReport == true) navItems.add(Triple("report", Icons.Default.Assessment, "Relatório"))
                        if (isAdmin || loggedUser?.canSettings == true) navItems.add(Triple("settings", Icons.Default.Settings, "Config"))
                        if (isAdmin) {
                            navItems.add(Triple("layout", Icons.Default.Palette, "Layout"))
                            navItems.add(Triple("access", Icons.Default.Group, "Acessos"))
                        }
                        navItems.forEach { (screen, icon, label) ->
                            Column(modifier = Modifier.width(90.dp).fillMaxHeight().clickable { currentScreen = screen }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                val isSelected = currentScreen == screen
                                val contentColor = if (isSelected) IntenseGreen else if(isLightMode) Color.Black else Color.Gray
                                Box(modifier = Modifier.size(48.dp).background(if (isSelected) IntenseGreen.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = if (screen == "home") Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { isLogged = false; currentScreen = "login" }, onTap = { currentScreen = "home" }) } else Modifier)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = label, color = contentColor, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!isLogged) {
                when (currentScreen) {
                    "login" -> LoginScreen(appName = loginTitle, titleColor = Color(titleColorArgb), hasOutline = hasOutline, outlineColor = Color(outlineColorArgb), customLogo = customLogoBase64, onLoginSuccess = { acc -> loggedUser = acc; isLogged = true; isAdmin = acc.role == "ADMIN" || acc.username == "admin"; currentScreen = "home" }, onForgot = { currentScreen = "forgot" }, onChange = { currentScreen = "change" }, onRegister = { currentScreen = "register" }, storedPass = "102030aa", isLightMode = isLightMode)
                    "register" -> RegisterScreen(customLogo = customLogoBase64, onBack = { currentScreen = "login" }, isLightMode = isLightMode)
                    "forgot" -> ForgotPasswordScreen(onBack = { currentScreen = "login" }, isLightMode = isLightMode)
                    "change" -> ChangePasswordScreen(onBack = { currentScreen = "login" }, currentPass = "102030aa", currentUser = "admin", onConfirm = { currentScreen = "login" }, isLightMode = isLightMode)
                }
            } else {
                when (currentScreen) {
                    "home" -> HomeScreen(appName = printerMessage, isSoundEnabled = isSoundEnabled, printerMac = printerMac, printerSize = printerSize, logoBase64 = if (showLogo) customLogoBase64 else null, isLightMode = isLightMode)
                    "register_toy" -> RegisterToyScreen(isLightMode = isLightMode)
                    "report" -> ReportScreen(printerMessage = printerMessage, printerMac = printerMac, printerSize = printerSize, logoBase64 = if (showLogo) customLogoBase64 else null, onSearchClick = { currentScreen = "search_report" }, isLightMode = isLightMode)
                    "search_report" -> SearchReportScreen(printerMac = printerMac, printerSize = printerSize, logoBase64 = if (showLogo) customLogoBase64 else null, onBack = { currentScreen = "report" }, isLightMode = isLightMode)
                    "settings" -> SettingsScreen(appName = printerMessage, onAppNameChange = { printerMessage = it }, showAppName = showPrinterMessage, onShowAppNameChange = { showPrinterMessage = it }, printerMac = printerMac, onPrinterChange = { printerMac = it; sharedPrefs.edit().putString("last_printer_mac", it).apply() }, printerSize = printerSize, onPrinterSizeChange = { printerSize = it; sharedPrefs.edit().putString("last_printer_size", it).apply() }, isSoundEnabled = isSoundEnabled, onSoundToggle = { isSoundEnabled = it }, showLogo = showLogo, onShowLogoChange = { showLogo = it }, logoBase64 = customLogoBase64, onSelectLogo = { logoLauncher.launch("image/*") }, onNavigateToLayout = { currentScreen = "layout" }, isLightMode = isLightMode)
                    "layout" -> LayoutScreen(isAdmin = isAdmin, currentTheme = themeMode, onThemeChange = onThemeChange, appName = loginTitle, onAppNameChange = { loginTitle = it }, titleColor = Color(titleColorArgb), onTitleColorChange = { titleColorArgb = it.toArgb() }, hasOutline = hasOutline, onOutlineToggle = { hasOutline = it }, outlineColor = Color(outlineColorArgb), onOutlineColorChange = { outlineColorArgb = it.toArgb() }, isLogoLocked = isLogoLocked, onLogoLockToggle = { isLogoLocked = it }, onSelectLogo = { logoLauncher.launch("image/*") }, onBack = { currentScreen = "settings" }, isLightMode = isLightMode)
                    "access" -> AccessManagementScreen(isLightMode = isLightMode)
                }
            }
        }
    }
}
