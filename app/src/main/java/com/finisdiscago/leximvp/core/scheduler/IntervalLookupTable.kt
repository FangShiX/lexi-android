package com.finisdiscago.leximvp.core.scheduler

/** Bundled interval lookup with direction and difficulty fallback. */
object IntervalLookupTable {
    private val rows: Map<String, Map<String, Int>> by lazy {
        val stream = checkNotNull(IntervalLookupTable::class.java.getResourceAsStream(
            "/scheduler/intervals.tsv"
        )) { "Bundled reference interval table is missing" }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val columns = checkNotNull(reader.readLine()).split('\t')
            reader.lineSequence().associate { line ->
                val values = line.split('\t')
                check(values.size == columns.size) { "Invalid reference interval row" }
                values.first() to columns.indices.drop(1).associate { columns[it] to values[it].toInt() }
            }
        }
    }

    // studyMethod: 0 = EN_CN, 1 = CN_EN; history is the original MMDB stage key.
    fun intervalByDay(studyMethod: Int, difficulty: Int, responseHistory: String): Int {
        val row = rows[responseHistory] ?: return 0
        val direction = if (studyMethod == 1) "ce" else "ec"
        val specific = row["interval_${direction}_dif$difficulty"] ?: 0
        val directional = row["interval_${direction}_baseline"] ?: 0
        val interval = when {
            specific != 0 -> specific
            directional != 0 -> directional
            else -> row["interval_baseline"] ?: 0
        }
        return interval.coerceAtLeast(0)
    }
}
