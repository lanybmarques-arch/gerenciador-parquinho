package com.example.gerenciadordeparquinho

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.example.gerenciadordeparquinho.data.model.UserAccount
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.screens.*
import com.example.gerenciadordeparquinho.ui.theme.AppThemeMode
import com.example.gerenciadordeparquinho.ui.theme.BrincandoTheme
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.utils.TimerService
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            
            val savedTheme = sharedPrefs.getString("app_theme", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
            var themeMode by rememberSaveable { mutableStateOf(AppThemeMode.valueOf(savedTheme)) }
            
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
            
            LaunchedEffect(Unit) {
                val permissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.CAMERA)
                }
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            }

            BrincandoTheme(appThemeMode = themeMode) {
                MainApp(
                    themeMode = themeMode, 
                    onThemeChange = { 
                        themeMode = it
                        sharedPrefs.edit().putString("app_theme", it.name).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainApp(themeMode: AppThemeMode, onThemeChange: (AppThemeMode) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    var currentScreen by rememberSaveable { mutableStateOf("login") }
    var isLogged by rememberSaveable { mutableStateOf(false) }
    var isAdmin by rememberSaveable { mutableStateOf(false) }
    var loggedUser by remember { mutableStateOf<UserAccount?>(null) }
    var isSoundEnabled by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("is_sound_enabled", true)) }
    
    var loginTitle by remember { mutableStateOf(sharedPrefs.getString("login_title", "BRINCANDO NA PRAÇA") ?: "BRINCANDO NA PRAÇA") }
    var printerMessage by remember { mutableStateOf(sharedPrefs.getString("printer_message", "") ?: "") }
    
    var titleColorArgb by remember { mutableIntStateOf(sharedPrefs.getInt("title_color", IntenseGreen.toArgb())) }
    var hasOutline by remember { mutableStateOf(sharedPrefs.getBoolean("has_outline", false)) }
    var outlineColorArgb by remember { mutableIntStateOf(sharedPrefs.getInt("outline_color", Color.Black.toArgb())) }
    var isLogoLocked by remember { mutableStateOf(sharedPrefs.getBoolean("is_logo_locked", false)) }
    
    var loginLogoBase64 by remember { mutableStateOf(sharedPrefs.getString("login_logo_base64", null)) }
    var printerLogoBase64 by remember { mutableStateOf(sharedPrefs.getString("printer_logo_base64", null)) }
    
    var showLogoInLogin by remember { mutableStateOf(sharedPrefs.getBoolean("show_logo_login", false)) }
    var showPrinterLogo by remember { mutableStateOf(sharedPrefs.getBoolean("show_printer_logo", false)) }
    var showPrinterMessage by remember { mutableStateOf(sharedPrefs.getBoolean("show_printer_message", false)) }
    
    var autoPrintEntrance by remember { mutableStateOf(sharedPrefs.getBoolean("auto_print_entrance", false)) }
    var autoPrintExit by remember { mutableStateOf(sharedPrefs.getBoolean("auto_print_exit", false)) }
    var autoPrintSDR by remember { mutableStateOf(sharedPrefs.getBoolean("auto_print_sdr", false)) }
    var autoPrintScannerSummary by remember { mutableStateOf(sharedPrefs.getBoolean("auto_print_scanner_summary", false)) }
    var isPreAutoEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("is_pre_auto_enabled", false)) }
    
    var printerMac by rememberSaveable { mutableStateOf(sharedPrefs.getString("last_printer_mac", "") ?: "") }
    var printerSize by rememberSaveable { mutableStateOf(sharedPrefs.getString("last_printer_size", "58mm") ?: "58mm") }

    LaunchedEffect(Unit) {
        val stayLogged = sharedPrefs.getBoolean("stay_logged_in", false)
        val savedUser = sharedPrefs.getString("saved_username", "") ?: ""
        if (stayLogged && savedUser.isNotEmpty()) {
            scope.launch {
                val account = if (savedUser == "admin") {
                    UserAccount(username = "admin", pass = "102030aa", role = "ADMIN")
                } else {
                    db.userDao().getUserByUsername(savedUser)
                }
                
                if (account != null) {
                    loggedUser = account
                    isAdmin = account.role == "ADMIN" || account.username == "admin"
                    isLogged = true
                    currentScreen = "home"
                }
            }
        }
    }

    val printerLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                printerLogoBase64 = base64
                sharedPrefs.edit().putString("printer_logo_base64", base64).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val loginLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                loginLogoBase64 = base64
                showLogoInLogin = true
                sharedPrefs.edit().putString("login_logo_base64", base64).putBoolean("show_logo_login", true).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

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
                        
                        if (isAdmin || loggedUser?.canLayout == true) {
                            navItems.add(Triple("layout", Icons.Default.Palette, "Layout"))
                        }
                        
                        if (isAdmin) {
                            navItems.add(Triple("access", Icons.Default.Group, "Acessos"))
                        }
                        navItems.forEach { (screen, icon, label) ->
                            Column(modifier = Modifier.width(90.dp).fillMaxHeight().clickable { currentScreen = screen }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                val isSelected = currentScreen == screen
                                val contentColor = if (isSelected) IntenseGreen else if(isLightMode) Color.Black else Color.Gray
                                Box(modifier = Modifier.size(48.dp).background(if (isSelected) IntenseGreen.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = if (screen == "home") Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { 
                                        isLogged = false
                                        currentScreen = "login"
                                        sharedPrefs.edit().putBoolean("stay_logged_in", false).remove("saved_username").apply()
                                    }, onTap = { currentScreen = "home" }) } else Modifier)
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
                    "login" -> LoginScreen(
                        appName = loginTitle, 
                        titleColor = Color(titleColorArgb), 
                        hasOutline = hasOutline, 
                        outlineColor = Color(outlineColorArgb), 
                        customLogo = if (showLogoInLogin) loginLogoBase64 else null, 
                        onLoginSuccess = { acc, stay -> 
                            loggedUser = acc
                            isLogged = true
                            isAdmin = acc.username == "admin" || acc.role == "ADMIN"
                            currentScreen = "home"
                            sharedPrefs.edit().putBoolean("stay_logged_in", stay).putString("saved_username", acc.username).apply()
                        }, 
                        onForgot = { currentScreen = "forgot" }, 
                        onChange = { currentScreen = "change" }, 
                        onRegister = { currentScreen = "register" }, 
                        storedPass = "102030aa", 
                        isLightMode = isLightMode
                    )
                    "register" -> RegisterScreen(customLogo = if (showLogoInLogin) loginLogoBase64 else null, onBack = { currentScreen = "login" }, isLightMode = isLightMode)
                    "forgot" -> ForgotPasswordScreen(onBack = { currentScreen = "login" }, isLightMode = isLightMode)
                    "change" -> ChangePasswordScreen(onBack = { currentScreen = "login" }, currentPass = "102030aa", currentUser = "admin", onConfirm = { currentScreen = "login" }, isLightMode = isLightMode)
                }
            } else {
                when (currentScreen) {
                    "home" -> HomeScreen(
                        appName = loginTitle,
                        isSoundEnabled = isSoundEnabled, 
                        printerMac = printerMac, 
                        printerSize = printerSize, 
                        logoBase64 = if(showPrinterLogo) printerLogoBase64 else null, 
                        isLightMode = isLightMode,
                        autoPrintEntrance = autoPrintEntrance,
                        onAutoPrintEntranceChange = { 
                            autoPrintEntrance = it
                            sharedPrefs.edit().putBoolean("auto_print_entrance", it).apply()
                        },
                        autoPrintExit = autoPrintExit,
                        onAutoPrintExitChange = {
                            autoPrintExit = it
                            sharedPrefs.edit().putBoolean("auto_print_exit", it).apply()
                        },
                        autoPrintSDR = autoPrintSDR,
                        onAutoPrintSDRChange = {
                            autoPrintSDR = it
                            sharedPrefs.edit().putBoolean("auto_print_sdr", it).apply()
                        },
                        autoPrintScannerSummary = autoPrintScannerSummary,
                        onAutoPrintScannerSummaryChange = {
                            autoPrintScannerSummary = it
                            sharedPrefs.edit().putBoolean("auto_print_scanner_summary", it).apply()
                        },
                        isPreAutoEnabled = isPreAutoEnabled,
                        onPreAutoToggle = {
                            isPreAutoEnabled = it
                            sharedPrefs.edit().putBoolean("is_pre_auto_enabled", it).apply()
                        },
                        isAdmin = isAdmin
                    )
                    "register_toy" -> RegisterToyScreen(isLightMode = isLightMode)
                    "report" -> ReportScreen(
                        printerMessage = printerMessage, 
                        printerMac = printerMac, 
                        printerSize = printerSize, 
                        logoBase64 = if(showPrinterLogo) printerLogoBase64 else null, 
                        onSearchClick = { currentScreen = "search_report" }, 
                        isLightMode = isLightMode,
                        isAdmin = isAdmin
                    )
                    "search_report" -> SearchReportScreen(appName = printerMessage, printerMac = printerMac, printerSize = printerSize, logoBase64 = if(showPrinterLogo) printerLogoBase64 else null, onBack = { currentScreen = "report" }, isLightMode = isLightMode)
                    "settings" -> SettingsScreen(
                        appName = printerMessage, 
                        onAppNameChange = { 
                            printerMessage = it
                            sharedPrefs.edit().putString("printer_message", it).apply()
                        }, 
                        showAppName = showPrinterMessage, 
                        onShowAppNameChange = { 
                            showPrinterMessage = it
                            sharedPrefs.edit().putBoolean("show_printer_message", it).apply()
                        }, 
                        printerMac = printerMac, 
                        onPrinterChange = { 
                            printerMac = it
                            sharedPrefs.edit().putString("last_printer_mac", it).apply()
                        }, 
                        printerSize = printerSize, 
                        onPrinterSizeChange = { 
                            printerSize = it
                            sharedPrefs.edit().putString("last_printer_size", it).apply()
                        }, 
                        isSoundEnabled = isSoundEnabled, 
                        onSoundToggle = { 
                            isSoundEnabled = it
                            sharedPrefs.edit().putBoolean("is_sound_enabled", it).apply()
                        }, 
                        showLogo = showPrinterLogo, 
                        onShowLogoChange = { 
                            showPrinterLogo = it
                            sharedPrefs.edit().putBoolean("show_printer_logo", it).apply()
                        }, 
                        logoBase64 = printerLogoBase64, 
                        onSelectLogo = { printerLogoLauncher.launch("image/*") }, 
                        onNavigateToLayout = { currentScreen = "layout" }, 
                        isLightMode = isLightMode
                    )
                    "layout" -> LayoutScreen(
                        isAdmin = isAdmin, 
                        currentTheme = themeMode,
                        onThemeChange = onThemeChange, 
                        appName = loginTitle, 
                        onAppNameChange = { 
                            loginTitle = it
                            sharedPrefs.edit().putString("login_title", it).apply()
                        }, 
                        titleColor = Color(titleColorArgb), 
                        onTitleColorChange = { 
                            titleColorArgb = it.toArgb()
                            sharedPrefs.edit().putInt("title_color", it.toArgb()).apply()
                        }, 
                        hasOutline = hasOutline, 
                        onOutlineToggle = { 
                            hasOutline = it
                            sharedPrefs.edit().putBoolean("has_outline", it).apply()
                        }, 
                        outlineColor = Color(outlineColorArgb), 
                        onOutlineColorChange = { 
                            outlineColorArgb = it.toArgb()
                            sharedPrefs.edit().putInt("outline_color", it.toArgb()).apply()
                        }, 
                        isLogoLocked = isLogoLocked, 
                        onLogoLockToggle = { 
                            isLogoLocked = it
                            sharedPrefs.edit().putBoolean("is_logo_locked", it).apply()
                        }, 
                        logoBase64 = loginLogoBase64, 
                        onSelectLogo = { loginLogoLauncher.launch("image/*") }, 
                        onResetLogo = {
                            loginLogoBase64 = null
                            showLogoInLogin = false
                            sharedPrefs.edit().remove("login_logo_base64").putBoolean("show_logo_login", false).apply()
                        },
                        onBack = { currentScreen = "settings" }, 
                        isLightMode = isLightMode
                    )
                    "access" -> AccessManagementScreen(isLightMode = isLightMode)
                }
            }
        }
    }
}
