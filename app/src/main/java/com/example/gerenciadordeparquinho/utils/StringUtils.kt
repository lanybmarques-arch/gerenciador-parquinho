package com.example.gerenciadordeparquinho.utils

import java.text.Normalizer

/**
 * Remove acentos e caracteres especiais de uma string, retornando-a em caixa alta.
 * Ex: "João" -> "JOAO"
 */
fun String.normalizeName(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    return regex.replace(normalized, "")
        .uppercase()
}
