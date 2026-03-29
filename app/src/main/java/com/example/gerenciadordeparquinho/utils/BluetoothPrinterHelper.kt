package com.example.gerenciadordeparquinho.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.gerenciadordeparquinho.data.model.PlaySession
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object BluetoothPrinterHelper {
    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Comandos ESC/POS
    private val RESET = byteArrayOf(0x1B, 0x40)
    private val CHARSET_16 = byteArrayOf(0x1B, 0x74, 0x10)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_STRIKE_ON = byteArrayOf(0x1B, 0x47, 0x01)
    private val DOUBLE_STRIKE_OFF = byteArrayOf(0x1B, 0x47, 0x00)
    
    private val NORMAL_FONT = byteArrayOf(0x1D, 0x21, 0x00) // 1x1
    private val TALL_FONT = byteArrayOf(0x1D, 0x21, 0x01)   // 1x2 (Dobra altura)
    private val BIG_FONT = byteArrayOf(0x1D, 0x21, 0x11)    // 2x2 (Dobra largura e altura)
    private val HUGE_FONT = byteArrayOf(0x1D, 0x21, 0x22)   // 3x3 (Triplica largura e altura)
    
    private val CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val LEFT = byteArrayOf(0x1B, 0x61, 0x00)

    private fun runOnMainThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    private fun decodeBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    private fun printBitmap(out: OutputStream, originalBitmap: Bitmap, size: String) {
        val width = if (size == "58mm") 384 else 576
        val ratio = width.toFloat() / originalBitmap.width
        val height = (originalBitmap.height * ratio).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE) 

        val scaledSrc = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        canvas.drawBitmap(scaledSrc, 0f, 0f, null)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val byteWidth = bitmap.width / 8
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, byteWidth.toByte(), 0x00, (bitmap.height % 256).toByte(), (bitmap.height / 256).toByte()))

        for (y in 0 until bitmap.height) {
            for (x in 0 until byteWidth) {
                var byte = 0
                for (bit in 0 until 8) {
                    val px = pixels[y * bitmap.width + (x * 8 + bit)]
                    val gray = ((px shr 16 and 0xFF) + (px shr 8 and 0xFF) + (px and 0xFF)) / 3
                    if (gray < 128) byte = byte or (1 shl (7 - bit))
                }
                out.write(byte)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun printEntranceTicket(
        macAddress: String, 
        session: PlaySession, 
        size: String = "58mm",
        logoBase64: String? = null,
        customMessage: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (macAddress.isEmpty()) {
            onResult(false, "Nenhuma impressora selecionada!")
            return
        }
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("Bluetooth não disponível")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream

                out.write(RESET)
                out.write(CHARSET_16)
                out.write(CENTER)

                logoBase64?.let { base64 ->
                    decodeBitmap(base64)?.let { bmp ->
                        printBitmap(out, bmp, size)
                        out.write("\n\n".toByteArray())
                    }
                }

                if (!customMessage.isNullOrBlank()) {
                    out.write(CENTER)
                    out.write(BOLD_ON)
                    out.write(DOUBLE_STRIKE_ON)
                    out.write(BIG_FONT)
                    out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1))
                }

                out.write(BOLD_ON)
                out.write(HUGE_FONT)
                val title = if (session.isFinished) "TICKET\nDE\nSAIDA" else "TICKET\nDE\nENTRADA"
                out.write("$title\n\n".toByteArray(Charsets.ISO_8859_1))

                out.write(NORMAL_FONT)
                out.write("--------------------------------\n".toByteArray())

                out.write(CENTER)
                out.write(BOLD_ON)
                out.write(DOUBLE_STRIKE_ON)
                
                // CLIENTE (LINHA 1: RÓTULO | LINHA 2: NOME)
                out.write(TALL_FONT) // REDUZIDO PARA 1x2 CONFORME SOLICITADO
                out.write("CLIENTE\n".toByteArray(Charsets.ISO_8859_1))
                out.write("${session.personName.uppercase()}\n\n".toByteArray(Charsets.ISO_8859_1))
                
                // BRINQUEDO (LINHA 1: RÓTULO | LINHA 2: NOME)
                out.write("BRINQUEDO\n".toByteArray(Charsets.ISO_8859_1))
                out.write("${session.toyName.uppercase()}\n\n".toByteArray(Charsets.ISO_8859_1))
                
                // VALOR, INICIO E FIM (MANTIDO 2x2 ORIGINAL CONFORME SOLICITADO)
                out.write(BIG_FONT)
                val valToPrint = if (session.isFinished) session.totalValueAccumulated else session.toyPrice
                out.write("VALOR: R$ %.2f\n".format(valToPrint).toByteArray(Charsets.ISO_8859_1))
                out.write("INICIO: ${session.startTime}\n".toByteArray(Charsets.ISO_8859_1))

                val endTimeToShow = if (!session.endTime.isNullOrEmpty()) {
                    session.endTime
                } else {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val cal = Calendar.getInstance()
                    try {
                        sdf.parse(session.startTime)?.let { cal.time = it }
                        cal.add(Calendar.MINUTE, session.toyTimeMinutes)
                    } catch (e: Exception) {}
                    sdf.format(cal.time)
                }
                out.write("FIM: $endTimeToShow\n".toByteArray(Charsets.ISO_8859_1))

                out.write(BOLD_OFF)
                out.write(DOUBLE_STRIKE_OFF)
                out.write(NORMAL_FONT)
                out.write(CENTER)
                out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush()
                runOnMainThread { onResult(true, "Impresso com sucesso!") }
            } catch (e: Exception) {
                runOnMainThread { onResult(false, "Erro: ${e.message}") }
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printChildSummary(
        macAddress: String,
        childName: String,
        history: List<PlaySession>,
        total: Double,
        size: String = "58mm",
        logoBase64: String? = null,
        customMessage: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("Bluetooth não disponível")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream

                out.write(RESET)
                out.write(CHARSET_16)
                out.write(CENTER)

                logoBase64?.let { base64 ->
                    decodeBitmap(base64)?.let { bmp ->
                        printBitmap(out, bmp, size)
                        out.write("\n\n".toByteArray())
                    }
                }

                if (!customMessage.isNullOrBlank()) {
                    out.write(CENTER)
                    out.write(BOLD_ON)
                    out.write(DOUBLE_STRIKE_ON)
                    out.write(BIG_FONT)
                    out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1))
                }

                out.write(BOLD_ON)
                out.write(BIG_FONT)
                out.write("RESUMO PARA\n${childName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1))
                out.write(NORMAL_FONT)
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                out.write("DATA: $dateStr\n\n".toByteArray(Charsets.ISO_8859_1))

                out.write(CENTER)
                out.write(BOLD_ON)
                out.write(DOUBLE_STRIKE_ON)

                history.forEach { session ->
                    out.write(TALL_FONT)
                    out.write("BRINQUEDO\n".toByteArray(Charsets.ISO_8859_1))
                    out.write("${session.toyName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1))

                    out.write("INI: ${session.startTime} | FIM: ${session.endTime ?: "--:--:--"}\n".toByteArray(Charsets.ISO_8859_1))
                    out.write("VALOR: R$ %.2f\n".format(session.totalValueAccumulated).toByteArray(Charsets.ISO_8859_1))
                    out.write(NORMAL_FONT)
                    out.write("--------------------------------\n".toByteArray())
                    out.write(TALL_FONT)
                    out.write(BOLD_ON)
                    out.write(DOUBLE_STRIKE_ON)
                }

                out.write("\n".toByteArray())
                out.write(CENTER)
                out.write(BOLD_ON)
                out.write(BIG_FONT)
                out.write("TOTAL GERAL: R$ %.2f\n".format(total).toByteArray(Charsets.ISO_8859_1))
                
                out.write(NORMAL_FONT)
                out.write(BOLD_OFF)
                out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush()
                runOnMainThread { onResult(true, "Resumo impresso!") }
            } catch (e: Exception) {
                runOnMainThread { onResult(false, "Erro: ${e.message}") }
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printReport(
        macAddress: String,
        history: List<PlaySession>,
        total: Double,
        size: String = "58mm",
        logoBase64: String? = null,
        customMessage: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("Bluetooth não disponível")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream

                out.write(RESET)
                out.write(CHARSET_16)
                out.write(CENTER)

                logoBase64?.let { base64 ->
                    decodeBitmap(base64)?.let { bmp ->
                        printBitmap(out, bmp, size)
                        out.write("\n\n".toByteArray())
                    }
                }

                if (!customMessage.isNullOrBlank()) {
                    out.write(CENTER)
                    out.write(BOLD_ON)
                    out.write(BIG_FONT)
                    out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1))
                }

                out.write(BOLD_ON)
                out.write(BIG_FONT)
                if (size == "58mm") {
                    out.write("RELATORIO\nDE\nIMPRESSAO\n".toByteArray(Charsets.ISO_8859_1))
                } else {
                    out.write("RELATORIO DE IMPRESSAO\n".toByteArray(Charsets.ISO_8859_1))
                }

                out.write(NORMAL_FONT)
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                out.write("DATA: $dateStr\n\n".toByteArray(Charsets.ISO_8859_1))

                out.write(LEFT)
                out.write("--------------------------------\n".toByteArray())

                history.forEach { session ->
                    out.write("${session.personName.uppercase()} - ${session.toyName.uppercase()} - R$ %.2f\n".format(session.totalValueAccumulated).toByteArray(Charsets.ISO_8859_1))
                    out.write("Ini: ${session.startTime}  Fim: ${session.endTime ?: "--:--:--"}\n".toByteArray(Charsets.ISO_8859_1))
                    out.write("--------------------------------\n".toByteArray())
                }

                out.write("\n".toByteArray())
                out.write(CENTER)
                out.write(BOLD_ON)
                out.write(BIG_FONT)
                out.write("TOTAL: R$ %.2f\n".format(total).toByteArray(Charsets.ISO_8859_1))
                
                out.write(NORMAL_FONT)
                out.write(BOLD_OFF)
                out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush()
                runOnMainThread { onResult(true, "Relatório impresso!") }
            } catch (e: Exception) {
                runOnMainThread { onResult(false, "Erro: ${e.message}") }
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printTest(macAddress: String, onResult: (Boolean, String) -> Unit) {
        if (macAddress.isEmpty()) {
            onResult(false, "Selecione uma impressora nas configurações!")
            return
        }
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("Bluetooth não disponível")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                
                val out = socket.outputStream
                out.write(RESET)
                out.write(CENTER)
                out.write(BOLD_ON)
                out.write(BIG_FONT)
                out.write("\nTESTE OK!\n\n".toByteArray(Charsets.ISO_8859_1))
                out.write(NORMAL_FONT)
                out.write("CONEXÃO ESTABELECIDA\nCOM SUCESSO!\n\n\n\n".toByteArray(Charsets.ISO_8859_1))
                out.flush()
                
                runOnMainThread { onResult(true, "Teste concluído com sucesso!") }
            } catch (e: Exception) {
                runOnMainThread { onResult(false, "Erro ao conectar: ${e.message}") }
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
    }
}
