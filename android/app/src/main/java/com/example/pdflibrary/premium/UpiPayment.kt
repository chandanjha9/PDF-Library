package com.example.pdflibrary.premium

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Direct UPI deep link (NPCI "upi://pay" intent) to the owner's UPI ID.
 *
 * LIMITATION (by design choice): there is no payment gateway, so the only
 * signal is the text the UPI app returns. It can be missing (some apps return
 * nothing) and cannot be verified server-side. Reconcile `txnRef`s from
 * GET /admin/unlocks against your bank statement.
 */
object UpiPayment {
    const val UPI_ID = "9576840671@axl"
    const val PAYEE_NAME = "PDF Library"
    const val AMOUNT = "10.00"

    fun newTxnRef(bookId: Int): String = "PDFL${bookId}T${System.currentTimeMillis()}"

    fun intent(bookId: Int, txnRef: String): Intent {
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", UPI_ID)
            .appendQueryParameter("pn", PAYEE_NAME)
            .appendQueryParameter("am", AMOUNT)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Unlock book $bookId")
            .appendQueryParameter("tr", txnRef)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun hasUpiApp(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()

    enum class Outcome { Success, Failed, Unknown }

    /**
     * Parses the UPI app's reply, e.g.
     * "txnId=AXL123&responseCode=00&Status=SUCCESS&txnRef=PDFL88T..."
     */
    fun parse(response: String?): Outcome {
        if (response.isNullOrBlank()) return Outcome.Unknown
        val fields = response.split('&').mapNotNull {
            val k = it.substringBefore('=', "").trim().lowercase()
            if (k.isEmpty()) null else k to it.substringAfter('=').trim()
        }.toMap()
        val status = fields["status"]?.lowercase()
        return when {
            status == "success" || status == "submitted" -> Outcome.Success
            status == "failure" || status == "failed" -> Outcome.Failed
            else -> Outcome.Unknown
        }
    }
}
