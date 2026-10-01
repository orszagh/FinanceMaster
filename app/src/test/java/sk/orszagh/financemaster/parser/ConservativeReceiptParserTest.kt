package sk.orszagh.financemaster.parser

import org.junit.Assert.*
import org.junit.Test

class ConservativeReceiptParserTest {
    private val parser = ConservativeReceiptParser()

    @Test fun extractsExplicitSlovakFieldsWithoutLosingPrecision() {
        val result = parser.parse("Obchodník: Potraviny Test\nDátum: 1.10.2026\nČas: 14:35\nSPOLU: 19,90 EUR")
        assertEquals(ParsedReceipt("Potraviny Test", "2026-10-01", "14:35", "19.90", "EUR"), result)
    }

    @Test fun conflictingCandidatesRemainNull() {
        val result = parser.parse("Merchant: Alpha\nMerchant: Beta\n2026-10-01\n2026-10-02\n10:00\n11:00\nTotal: 10.00 EUR\nTotal: 20.00 USD")
        assertEquals(ParsedReceipt(), result)
    }

    @Test fun rejectsInvalidDatesTimesAndAmbiguousCurrency() {
        val result = parser.parse("31.2.2026\n25:70\nTotal: 12.30\n$\n10/01/2026")
        assertNull(result.purchaseDate)
        assertNull(result.purchaseTime)
        assertNull(result.currency)
        assertEquals("12.30", result.totalAmount)
    }

    @Test fun doesNotInferTotalFromSubtotalTaxOrItems() {
        val result = parser.parse("Subtotal: 12.00 EUR\nTotal VAT: 2.00 EUR\nChlieb 1.20 EUR\nCash 20.00 EUR\nChange 6.80 EUR")
        assertNull(result.totalAmount)
        assertNull(result.merchant)
        assertEquals("EUR", result.currency)
    }

    @Test fun repeatedEquivalentFieldsAreAccepted() {
        val result = parser.parse("Total: 10.00 €\nSpolu: 10,00 EUR\n1.10.2026\n2026-10-01")
        assertEquals("10.00", result.totalAmount)
        assertEquals("2026-10-01", result.purchaseDate)
        assertEquals("EUR", result.currency)
    }

    @Test fun unsupportedGroupedAmountIsNotGuessed() {
        assertNull(parser.parse("Total: 1,234.56 USD").totalAmount)
    }

    @Test fun emptyTextHasNoExtractedValues() {
        assertEquals(ParsedReceipt(), parser.parse("\n  "))
    }
}
