package sk.orszagh.financemaster.parser

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

class ConservativeReceiptParser : ReceiptParser {
    override fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map(String::trim).filter(String::isNotEmpty)
        val normalized = lines.map(::normalize)
        val merchants = lines.mapNotNull { line ->
            Regex("^(?:obchodnik|merchant|predajca)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
                .find(normalize(line))?.let { line.substringAfter(':').trim().takeIf(String::isNotBlank) }
        }
        val dates = normalized.flatMap { line ->
            datePattern.findAll(line).mapNotNull { match -> parseDate(match.value) }.toList()
        }
        val times = normalized.flatMap { line ->
            timePattern.findAll(line).mapNotNull { match ->
                runCatching { LocalTime.parse(match.value).toString() }.getOrNull()
            }.toList()
        }
        val totals = normalized.mapNotNull { line ->
            totalPattern.matchEntire(line)?.groupValues?.get(1)?.let(::parseAmount)
        }
        val currencies = normalized.flatMap { line ->
            currencyPattern.findAll(line).map { match ->
                when (val token = match.value.uppercase()) {
                    "€" -> "EUR"
                    "KC" -> "CZK"
                    else -> token
                }
            }.toList()
        }
        return ParsedReceipt(
            merchant = merchants.uniqueOrNull(),
            purchaseDate = dates.uniqueOrNull(),
            purchaseTime = times.uniqueOrNull(),
            totalAmount = totals.uniqueOrNull(),
            currency = currencies.uniqueOrNull(),
        )
    }

    private fun parseDate(value: String): String? = runCatching {
        if (value.contains('-')) LocalDate.parse(value).toString()
        else {
            val parts = value.split('.')
            LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt()).toString()
        }
    }.getOrNull()

    private fun parseAmount(value: String): String? = runCatching {
        // Desatinný reťazec zachová presnosť; neodhadujeme oddeľovače tisícov ani menu.
        BigDecimal(value.replace(',', '.')).setScale(2).toPlainString()
    }.getOrNull()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase()

    private fun <T> List<T>.uniqueOrNull(): T? = distinct().singleOrNull()

    private val datePattern = Regex("(?<![\\d/.-])(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\d{1,2}\\.\\d{4})(?![\\d/.-])")
    private val timePattern = Regex("(?<![\\d:])(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?![\\d:])")
    private val totalPattern = Regex(
        "^(?:spolu|celkom|total|grand total|celkova suma|suma celkom|k uhrade)\\s*:?\\s*" +
            "(\\d+[.,]\\d{2})\\s*(?:eur|€|czk|kc|usd|gbp|chf|pln)?$",
    )
    private val currencyPattern = Regex("(?<![a-z])(?:eur|czk|kc|usd|gbp|chf|pln)(?![a-z])|€")
}
