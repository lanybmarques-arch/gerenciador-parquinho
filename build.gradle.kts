// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false // ID DIRETO COM VERSÃO
    id("com.google.gms.google-services") version "4.4.2" apply false
}
