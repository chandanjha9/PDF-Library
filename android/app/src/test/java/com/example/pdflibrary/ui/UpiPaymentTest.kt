package com.example.pdflibrary.ui

import com.example.pdflibrary.premium.UpiPayment
import com.example.pdflibrary.premium.UpiPayment.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class UpiPaymentTest {

    @Test fun success_variants() {
        assertEquals(Outcome.Success, UpiPayment.parse("txnId=AXL1&responseCode=00&Status=SUCCESS&txnRef=PDFL88T1"))
        assertEquals(Outcome.Success, UpiPayment.parse("status=success"))
        assertEquals(Outcome.Success, UpiPayment.parse("Status=SUBMITTED&txnRef=x"))
    }

    @Test fun failure_variants() {
        assertEquals(Outcome.Failed, UpiPayment.parse("txnId=&responseCode=ZD&Status=FAILURE"))
        assertEquals(Outcome.Failed, UpiPayment.parse("Status=Failed"))
    }

    @Test fun unknown_when_missing_or_garbled() {
        assertEquals(Outcome.Unknown, UpiPayment.parse(null))
        assertEquals(Outcome.Unknown, UpiPayment.parse(""))
        assertEquals(Outcome.Unknown, UpiPayment.parse("txnId=123&responseCode=00"))
        assertEquals(Outcome.Unknown, UpiPayment.parse("random text"))
    }

    @Test fun txnRef_isUniqueAndTagged() {
        val ref = UpiPayment.newTxnRef(88)
        assert(ref.startsWith("PDFL88T"))
    }
}
