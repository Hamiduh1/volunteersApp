package com.example.volunteersApp.wallet

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECEIPT_COMPANY_NAME = "Volunteers App"
private const val RECEIPT_COMPANY_LEGAL = "Soft Solutions Tech"
private const val RECEIPT_SUPPORT_EMAIL = "support@softsolutionstech.com"
private const val RECEIPT_WEBSITE = "https://softsolutionstech.com"
private const val RECEIPT_DOCUMENT_TITLE = "Transfer Receipt"

/**
 * Customer-facing receipt / statement body used by share, PDF, and print.
 * Keeps provider branding and raw API payloads out of the document.
 */
fun walletReceiptPrintText(receipt: WalletReceiptUi): String {
    val issuedAt = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date())
    val status = sanitizeCustomerFacingProviderText(receipt.statusLine)
    val recipient = sanitizeCustomerFacingProviderText(receipt.recipientLine)
    val funding = sanitizeCustomerFacingProviderText(receipt.fundingLine)
    val delivery = sanitizeCustomerFacingProviderText(receipt.deliveryLine)
    val totalFee = receipt.totalFeeLine?.takeIf { it.isNotBlank() }
    val totalPaid = receipt.totalDebitLine?.takeIf { it.isNotBlank() }
    val note = receipt.noteLine
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeCustomerFacingProviderText(it) }
    val message = receipt.messageLine
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeCustomerFacingProviderText(it) }

    return buildString {
        appendLine(RECEIPT_COMPANY_NAME)
        appendLine(RECEIPT_COMPANY_LEGAL)
        appendLine(RECEIPT_DOCUMENT_TITLE)
        appendLine("────────────────────────────────")
        appendLine("Issued: $issuedAt")
        appendLine("Transaction date: ${receipt.dateTimeLine}")
        appendLine("Reference: ${receipt.referenceLine.ifBlank { "Pending sync" }}")
        appendLine("Status: $status")
        appendLine()
        appendLine("Amount")
  appendLine(receipt.amountLine)
        totalFee?.let { appendLine("Total fee: $it") }
        totalPaid?.let { appendLine("Total paid: $it") }
        appendLine()
        appendLine("Transaction details")
        appendLine("Recipient: $recipient")
        appendLine("Funding method: $funding")
        appendLine("Delivery route: $delivery")
        note?.let {
            appendLine("Note: $it")
        }
        message?.let {
            appendLine("Message: $it")
        }
        appendLine()
        appendLine("────────────────────────────────")
        appendLine("This document is an official transfer receipt from $RECEIPT_COMPANY_NAME.")
        appendLine("Keep it for your records. It is not a bank statement.")
        appendLine("Support: $RECEIPT_SUPPORT_EMAIL")
        appendLine("Website: $RECEIPT_WEBSITE")
}.trim()
}

fun writeWalletReceiptPdf(context: Context, receipt: WalletReceiptUi): File {
  val document = PdfDocument()
  val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
  val page = document.startPage(pageInfo)
  val canvas = page.canvas

    val titlePaint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 20f
        isAntiAlias = true
        isFakeBoldText = true
    }
    val subtitlePaint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 12f
        isAntiAlias = true
    }
    val sectionPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        isAntiAlias = true
        isFakeBoldText = true
    }
    val bodyPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 12.5f
        isAntiAlias = true
    }
    val mutedPaint = Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 11f
        isAntiAlias = true
    }
    val amountPaint = Paint().apply {
    color = android.graphics.Color.BLACK
        textSize = 18f
        isAntiAlias = true
        isFakeBoldText = true
    }
    val linePaint = Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 1.2f
    isAntiAlias = true
  }

    val left = 48f
    val right = 547f
    var y = 52f

    fun drawLine() {
        canvas.drawLine(left, y, right, y, linePaint)
        y += 18f
    }

    fun drawWrapped(text: String, paint: Paint, maxWidth: Float = right - left) {
        val words = text.split(' ')
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (y > 800f) return
                canvas.drawText(line, left, y, paint)
                y += paint.textSize + 8f
                line = word
            }
        }
        if (line.isNotEmpty() && y <= 800f) {
            canvas.drawText(line, left, y, paint)
            y += paint.textSize + 8f
        }
    }

    val issuedAt = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date())
    val status = sanitizeCustomerFacingProviderText(receipt.statusLine)
    val recipient = sanitizeCustomerFacingProviderText(receipt.recipientLine)
    val funding = sanitizeCustomerFacingProviderText(receipt.fundingLine)
    val delivery = sanitizeCustomerFacingProviderText(receipt.deliveryLine)
    val totalFee = receipt.totalFeeLine?.takeIf { it.isNotBlank() }
    val totalPaid = receipt.totalDebitLine?.takeIf { it.isNotBlank() }
    val note = receipt.noteLine
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeCustomerFacingProviderText(it) }
    val message = receipt.messageLine
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeCustomerFacingProviderText(it) }

    canvas.drawText(RECEIPT_COMPANY_NAME, left, y, titlePaint)
    y += 22f
    canvas.drawText(RECEIPT_COMPANY_LEGAL, left, y, subtitlePaint)
    y += 20f
    canvas.drawText(RECEIPT_DOCUMENT_TITLE, left, y, sectionPaint)
    y += 16f
    drawLine()

    canvas.drawText("Issued: $issuedAt", left, y, bodyPaint)
    y += 18f
    canvas.drawText("Transaction date: ${receipt.dateTimeLine}", left, y, bodyPaint)
    y += 18f
    canvas.drawText("Reference: ${receipt.referenceLine.ifBlank { "Pending sync" }}", left, y, bodyPaint)
    y += 18f
    canvas.drawText("Status: $status", left, y, bodyPaint)
    y += 24f

    canvas.drawText("Amount", left, y, sectionPaint)
    y += 22f
    canvas.drawText(receipt.amountLine, left, y, amountPaint)
  y += 28f
    totalFee?.let {
        canvas.drawText("Total fee: $it", left, y, bodyPaint)
        y += 18f
    }
    totalPaid?.let {
        canvas.drawText("Total paid: $it", left, y, bodyPaint)
        y += 18f
    }
    drawLine()

    canvas.drawText("Transaction details", left, y, sectionPaint)
    y += 22f
    drawWrapped("Recipient: $recipient", bodyPaint)
    drawWrapped("Funding method: $funding", bodyPaint)
    drawWrapped("Delivery route: $delivery", bodyPaint)
    note?.let { drawWrapped("Note: $it", bodyPaint) }
    message?.let { drawWrapped("Message: $it", bodyPaint) }
    y += 8f
    drawLine()

    drawWrapped(
        "This document is an official transfer receipt from $RECEIPT_COMPANY_NAME. Keep it for your records. It is not a bank statement.",
        mutedPaint
    )
    canvas.drawText("Support: $RECEIPT_SUPPORT_EMAIL", left, y, mutedPaint)
    y += 16f
    canvas.drawText("Website: $RECEIPT_WEBSITE", left, y, mutedPaint)

  document.finishPage(page)
  val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val safeRef = receipt.referenceLine
        .ifBlank { "receipt" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    val file = File(dir, "VolunteersApp_Transfer_Receipt_$safeRef.pdf")
  FileOutputStream(file).use { document.writeTo(it) }
  document.close()
  return file
}

fun shareWalletReceiptPdf(context: Context, receipt: WalletReceiptUi) {
  val file = writeWalletReceiptPdf(context, receipt)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, "$RECEIPT_COMPANY_NAME $RECEIPT_DOCUMENT_TITLE")
        putExtra(Intent.EXTRA_TEXT, walletReceiptPrintText(receipt))
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(intent, "Share PDF"))
}

fun printWalletReceipt(context: Context, receipt: WalletReceiptUi) {
  val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
  val webView = WebView(context)
    val body = walletReceiptPrintText(receipt)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>")
    val html = """
        <html>
          <head>
            <meta charset="utf-8" />
            <style>
              body {
                font-family: -apple-system, Segoe UI, Roboto, sans-serif;
                color: #111;
                padding: 28px;
                font-size: 13px;
                line-height: 1.5;
              }
            </style>
          </head>
          <body>$body</body>
        </html>
    """.trimIndent()
  webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    val adapter = webView.createPrintDocumentAdapter("$RECEIPT_COMPANY_NAME $RECEIPT_DOCUMENT_TITLE")
    printManager.print(
        "$RECEIPT_COMPANY_NAME $RECEIPT_DOCUMENT_TITLE",
        adapter,
        PrintAttributes.Builder().build()
    )
}
