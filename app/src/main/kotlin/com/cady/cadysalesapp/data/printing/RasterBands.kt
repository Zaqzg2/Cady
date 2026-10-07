package com.cady.cadysalesapp.data.printing

import kotlin.math.abs

/**
 * Decides where a receipt picture is cut into the horizontal bands that are sent to
 * the printer as separate GS v 0 raster commands.
 *
 * A band can't be a whole receipt (cheap ESC/POS controllers mishandle very tall
 * raster blocks), so cuts are unavoidable — but where they fall matters. Between two
 * commands the printer may pause for a moment, and the head, cooling during the
 * pause, prints the next dot-row paler: a cut through a line of text leaves a pale,
 * white-looking streak across the letters. So every cut is placed inside blank paper
 * (the gap between two lines, a margin), where a pause leaves nothing to damage.
 */
internal object RasterBands {
    /** The height a band is aimed at. */
    const val NOMINAL_ROWS = 200

    /** A band is never shorter than this, so cuts can't pile up in a busy area. */
    const val MIN_ROWS = 96

    /** …and never taller than this — the same ceiling as before, kept for the picky printers. */
    const val MAX_ROWS = 255

    /**
     * The exclusive end row of every band, in order; the last is always
     * [inkPerRow].size. [inkPerRow] holds, for each dot-row, how many black dots it has.
     */
    fun cuts(
        inkPerRow: IntArray,
        nominal: Int = NOMINAL_ROWS,
        minBand: Int = MIN_ROWS,
        maxBand: Int = MAX_ROWS,
    ): List<Int> {
        val height = inkPerRow.size
        val result = ArrayList<Int>()
        var start = 0
        while (start < height) {
            if (height - start <= maxBand) {
                result.add(height)
                break
            }
            val cut = bestCut(inkPerRow, start, nominal, minBand, maxBand)
            result.add(cut)
            start = cut
        }
        return result
    }

    private fun bestCut(ink: IntArray, start: Int, nominal: Int, minBand: Int, maxBand: Int): Int {
        val lo = start + minBand
        val hi = start + maxBand
        val target = start + nominal

        // Three degrees of "blank enough" — rows blank on both sides of the cut, then a
        // little less, then only above it. The first degree that has any candidate wins,
        // and within it the candidate nearest the nominal height.
        val degrees = arrayOf(intArrayOf(2, 2), intArrayOf(1, 1), intArrayOf(1, 0))
        for (degree in degrees) {
            val before = degree[0]
            val after = degree[1]
            var distance = 0
            while (target - distance >= lo || target + distance <= hi) {
                val earlier = target - distance
                if (earlier >= lo && isBlank(ink, earlier, before, after)) return earlier
                val later = target + distance
                if (distance > 0 && later <= hi && isBlank(ink, later, before, after)) return later
                distance++
            }
        }

        // No blank gap anywhere in range (a tall table whose borders run all the way down):
        // cut where the fewest dots are crossed — at worst a hairline gap in a border.
        var best = target
        var bestInk = Int.MAX_VALUE
        for (cut in lo..hi) {
            val crossed = ink[cut - 1]
            if (crossed < bestInk || (crossed == bestInk && abs(cut - target) < abs(best - target))) {
                best = cut
                bestInk = crossed
            }
        }
        return best
    }

    /** True when every row from [before] rows above the cut to [after] rows below it is blank. */
    private fun isBlank(ink: IntArray, cut: Int, before: Int, after: Int): Boolean {
        val from = maxOf(0, cut - before)
        val to = minOf(ink.size, cut + after)
        for (row in from until to) if (ink[row] != 0) return false
        return true
    }
}
