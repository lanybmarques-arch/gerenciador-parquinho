package com.example.gerenciadordeparquinho.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class PaidItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)

object BluetoothPrinterHelper {
    private val PRINTER_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val RESET = byteArrayOf(0x1B, 0x40)
    private val CHARSET_16 = byteArrayOf(0x1B, 0x74, 0x10)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    
    private val NORMAL_FONT = byteArrayOf(0x1D, 0x21, 0x00)
    private val TALL_FONT = byteArrayOf(0x1D, 0x21, 0x01)
    private val BIG_FONT = byteArrayOf(0x1D, 0x21, 0x11)
    private val HUGE_FONT = byteArrayOf(0x1D, 0x21, 0x22)
    
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

    private fun generateQRCodeBitmap(content: String, size: Int): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun printBitmap(out: OutputStream, originalBitmap: Bitmap, size: String) {
        val width = if (size == "58mm") 384 else 576
        val ratio = width.toFloat() / originalBitmap.width
        val height = (originalBitmap.height * ratio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) 
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

    private fun centerText(text: String, width: Int = 32): String {
        val trimmed = text.take(width)
        val padding = width - trimmed.length
        val left = padding / 2
        val right = padding - left
        return " ".repeat(left) + trimmed + " ".repeat(right)
    }

    private fun formatLine(left: String, right: String, width: Int = 32): String {
        val safeRight = right.take(width)
        val maxLeft = (width - safeRight.length).coerceAtLeast(0)
        val safeLeft = left.take(maxLeft)
        val spaces = width - safeLeft.length - safeRight.length
        return safeLeft + " ".repeat(spaces.coerceAtLeast(0)) + safeRight
    }

    private fun money(value: Double): String {
        return "R$ %.2f".format(value).replace(".", ",")
    }

    @SuppressLint("MissingPermission")
    fun printEntranceTicket(macAddress: String, session: PlaySession, size: String = "58mm", logoBase64: String? = null, customMessage: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (macAddress.isEmpty()) { onResult(false, "Erro impressora"); return }
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(RESET); out.write(CHARSET_16); out.write(CENTER)
                logoBase64?.let { b64 -> decodeBitmap(b64)?.let { bmp -> printBitmap(out, bmp, size); out.write("\n\n".toByteArray()) } }
                if (!customMessage.isNullOrBlank()) { out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1)) }
                out.write(BOLD_ON); out.write(HUGE_FONT)
                val title = if (session.isFinished) "TICKET\nDE\nSAIDA" else "TICKET\nDE\nENTRADA"
                out.write("$title\n\n".toByteArray(Charsets.ISO_8859_1))
                out.write(NORMAL_FONT); out.write("--------------------------------\n".toByteArray())
                out.write(CENTER); out.write(BOLD_ON); out.write(TALL_FONT) 
                out.write("CLIENTE\n".toByteArray(Charsets.ISO_8859_1)); out.write("${session.personName.uppercase()}\n\n".toByteArray(Charsets.ISO_8859_1))
                out.write("BRINQUEDO\n".toByteArray(Charsets.ISO_8859_1)); out.write("${session.toyName.uppercase()}\n\n".toByteArray(Charsets.ISO_8859_1))
                out.write(BIG_FONT)
                val valToP = if (session.isFinished) session.totalValueAccumulated else session.toyPrice
                out.write("VALOR: R$ %.2f\n".format(valToP).toByteArray(Charsets.ISO_8859_1))
                out.write("INICIO: ${session.startTime}\n".toByteArray(Charsets.ISO_8859_1))
                out.write(BOLD_OFF); out.write(NORMAL_FONT); out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush(); runOnMainThread { onResult(true, "Impresso!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printCheckoutReceipt(
        macAddress: String, childName: String, sessions: List<PlaySession>,
        totalBruto: Double, alreadyPaid: Double, cash: Double, pix: Double, card: Double, change: Double,
        size: String = "58mm", logoBase64: String? = null, customMessage: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                
                val lineWidth = if (size == "58mm") 32 else 48
                val divider = "-".repeat(lineWidth)
                
                // Agrupamento dos itens para simplificar a nota
                val groupedItems = sessions.groupBy { it.toyName to it.toyPrice }
                    .map { (key, list) -> 
                        PaidItem(name = key.first, quantity = list.size, unitPrice = key.second) 
                    }

                out.write(RESET); out.write(CHARSET_16); out.write(CENTER)
                
                logoBase64?.let { b64 -> decodeBitmap(b64)?.let { bmp -> printBitmap(out, bmp, size); out.write("\n".toByteArray()) } }
                
                val companyName = customMessage ?: "BRINCANDO NA PRAÇA"
                out.write(BOLD_ON)
                out.write("${centerText(companyName, lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                out.write("${centerText("COMPROVANTE DE PAGAMENTO", lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                out.write(BOLD_OFF)
                out.write("$divider\n".toByteArray())
                
                out.write(LEFT)
                out.write("Cliente: ${childName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1))
                out.write("Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n".toByteArray(Charsets.ISO_8859_1))
                out.write("$divider\n\n".toByteArray())
                
                out.write(BOLD_ON)
                out.write("ITENS PAGOS\n".toByteArray(Charsets.ISO_8859_1))
                out.write(BOLD_OFF)
                out.write("$divider\n".toByteArray())

                groupedItems.forEach { item ->
                    val itemTotal = item.quantity * item.unitPrice
                    out.write("${item.name.uppercase()}\n".toByteArray(Charsets.ISO_8859_1))
                    out.write("${formatLine("  ${item.quantity} x ${money(item.unitPrice)}", money(itemTotal), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                }

                out.write("$divider\n".toByteArray())
                out.write(BOLD_ON)
                out.write("${formatLine("TOTAL BRUTO:", money(totalBruto), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                if (alreadyPaid > 0) {
                    out.write("${formatLine("PAGO ANTECIPADO:", money(alreadyPaid), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                }
                
                val saldoAPagar = totalBruto - alreadyPaid
                out.write(BIG_FONT)
                out.write("${formatLine("SALDO PAGO:", money(saldoAPagar), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                out.write(NORMAL_FONT)
                out.write(BOLD_OFF)
                out.write("$divider\n\n".toByteArray())

                out.write("FORMA DE PAGAMENTO:\n".toByteArray(Charsets.ISO_8859_1))
                if (cash > 0) out.write("${formatLine("DINHEIRO:", money(cash), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                if (pix > 0) out.write("${formatLine("PIX:", money(pix), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                if (card > 0) out.write("${formatLine("CARTÃO:", money(card), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                if (change > 0) out.write("${formatLine("TROCO:", money(change), lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                
                out.write("\n".toByteArray())
                out.write(CENTER)
                out.write(BOLD_ON)
                out.write("${centerText("OBRIGADO E VOLTE SEMPRE!", lineWidth)}\n".toByteArray(Charsets.ISO_8859_1))
                out.write(BOLD_OFF)
                out.write("$divider\n\n\n\n\n".toByteArray())

                out.flush(); runOnMainThread { onResult(true, "Recibo impresso!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printSTRQRCode(macAddress: String, session: PlaySession, size: String = "58mm", onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(RESET); out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("SDR - REGISTRO\n\n".toByteArray(Charsets.ISO_8859_1))
                val qrContent = "SDR|${session.personName.trim().uppercase()}|${session.date}"
                generateQRCodeBitmap(qrContent, if (size == "58mm") 350 else 500)?.let { printBitmap(out, it, size) }
                out.write("\n".toByteArray()); out.write(CENTER); out.write(BOLD_ON); out.write(TALL_FONT); out.write("CLIENTE: ${session.personName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1)); out.write("DATA: ${session.date}\n".toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush(); runOnMainThread { onResult(true, "SDR!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro SDR: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printChildSummary(macAddress: String, childName: String, date: String, history: List<PlaySession>, total: Double, size: String = "58mm", logoBase64: String? = null, customMessage: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(RESET); out.write(CHARSET_16); out.write(CENTER)
                logoBase64?.let { b64 -> decodeBitmap(b64)?.let { bmp -> printBitmap(out, bmp, size); out.write("\n\n".toByteArray()) } }
                if (!customMessage.isNullOrBlank()) { out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1)) }
                out.write(BOLD_ON); out.write(BIG_FONT); out.write("RESUMO PARA\n${childName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("DATA: $date\n\n".toByteArray(Charsets.ISO_8859_1)); out.write(CENTER)
                history.forEach { s -> out.write(TALL_FONT); out.write("${s.toyName.uppercase()}\n".toByteArray(Charsets.ISO_8859_1)); val vs = if(s.isFinished) s.totalValueAccumulated else s.calculateCurrentProportionalValue(); out.write("VALOR: R$ %.2f\n".format(vs).toByteArray(Charsets.ISO_8859_1)); if(s.isPaid) out.write("SITUAÇÃO: PAGO\n".toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("--------------------------------\n".toByteArray()) }
                out.write("\n".toByteArray()); out.write(BOLD_ON); out.write(BIG_FONT); out.write("SALDO A PAGAR: R$ %.2f\n".format(total).toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush(); runOnMainThread { onResult(true, "Resumo impresso!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printReport(macAddress: String, history: List<PlaySession>, total: Double, size: String = "58mm", logoBase64: String? = null, customMessage: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (macAddress.isEmpty()) return
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(RESET); out.write(CHARSET_16); out.write(CENTER)
                logoBase64?.let { b64 -> decodeBitmap(b64)?.let { bmp -> printBitmap(out, bmp, size); out.write("\n\n".toByteArray()) } }
                if (!customMessage.isNullOrBlank()) { out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("$customMessage\n\n".toByteArray(Charsets.ISO_8859_1)) }
                out.write(BOLD_ON); out.write(BIG_FONT); out.write("RELATORIO DO DIA\n".toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("DATA: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}\n\n".toByteArray(Charsets.ISO_8859_1))
                out.write(LEFT); out.write("--------------------------------\n".toByteArray())
                history.forEach { s -> out.write("${s.personName.uppercase()} - R$ %.2f\n".format(s.totalValueAccumulated).toByteArray(Charsets.ISO_8859_1)) }
                out.write("\n".toByteArray()); out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("TOTAL: R$ %.2f\n".format(total).toByteArray(Charsets.ISO_8859_1)); out.write(NORMAL_FONT); out.write("\n--------------------------------\n\n\n\n\n".toByteArray())
                out.flush(); runOnMainThread { onResult(true, "Relatório impresso!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun printTest(macAddress: String, onResult: (Boolean, String) -> Unit) {
        if (macAddress.isEmpty()) { onResult(false, "Impressora?"); return }
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("BT Off")
                val device = adapter.getRemoteDevice(macAddress)
                socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(RESET); out.write(CENTER); out.write(BOLD_ON); out.write(BIG_FONT); out.write("\nTESTE OK!\n\n\n\n".toByteArray(Charsets.ISO_8859_1)); out.flush()
                runOnMainThread { onResult(true, "OK!") }
            } catch (e: Exception) { runOnMainThread { onResult(false, "Erro ao conectar: ${e.message}") } } finally { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }
}
