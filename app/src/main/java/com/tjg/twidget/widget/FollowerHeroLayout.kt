package com.tjg.twidget.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Fits actual glyphs and font axes to a hero rectangle. Each occurrence owns its styling. */
internal class FollowerHeroLayout(
    private val words: List<String>,
    private val makePaint: (index: Int, fullness: Float) -> Paint,
) {
    internal data class Run(val index: Int, val text: String, val paint: Paint)
    internal data class Layout(val lines: List<List<Run>>, val size: Float, val bounds: List<RectF>) {
        val ascent: Float get() = bounds.minOf { it.top }
        fun advance(gap: Float): Float = bounds.maxOf { it.bottom } - ascent + gap
        fun height(gap: Float): Float = bounds.last().bottom - bounds.first().top + (lines.size - 1) * advance(gap)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, padding: Float, wordGap: Float, lineGap: Float) {
        if (words.isEmpty() || width <= 1f || height <= 1f) return
        val layout = fit(width, height, wordGap, lineGap)
        layout.lines.forEachIndexed { lineIndex, line ->
            var x = padding - layout.bounds[lineIndex].left
            val baseline = padding - layout.bounds.first().top + lineIndex * layout.advance(lineGap)
            line.forEach { run ->
                run.paint.textSize = layout.size
                canvas.drawText(run.text, x, baseline, run.paint)
                x += run.paint.measureText(run.text) + wordGap
            }
        }
    }

    internal fun fit(width: Float, height: Float, wordGap: Float, lineGap: Float): Layout {
        require(words.isNotEmpty() && width > 1f && height > 1f)
        // Leave a pixel for raster rounding/antialiasing. Reuse paints while
        // probing sizes during this render; never keep an unbounded global cache.
        val maxWidth = width - 1f
        val maxHeight = height - 1f
        val paints = mutableMapOf<Pair<Int, Float>, Paint>()
        fun runs(fullness: Float, indices: List<Int> = words.indices.toList()) = indices.map { index ->
            Run(index, words[index], paints.getOrPut(index to fullness) { makePaint(index, fullness) })
        }
        var best: Layout? = null
        var bestFullness = 0f
        var bestScore = 0f
        // Compare readable, moderately condensed settings with natural proportions.
        // Shrinking the font alone can leave an entire line's worth of unused space.
        for (fullness in listOf(0f, -0.5f, -1f)) {
            val candidateRuns = runs(fullness)
            val layout = fitSize(maxWidth, maxHeight, wordGap, lineGap) { size ->
                wrap(candidateRuns, size, maxWidth, wordGap)
            }
            val score = layout.size * (1f + fullness * 0.10f)
            if (score > bestScore) {
                best = layout
                bestFullness = fullness
                bestScore = score
            }
        }
        val chosen = checkNotNull(best)
        // Open up each line independently, including repeated occurrences of the
        // same word. Width changes use the font's outlines, never Canvas scaling.
        val expanded = chosen.lines.map { line ->
            var low = bestFullness
            var high = 1.5f
            var result = line
            repeat(6) {
                val fullness = (low + high) / 2f
                val candidate = runs(fullness, line.map { it.index })
                if (measure(candidate, chosen.size, wordGap).width() <= maxWidth) {
                    result = candidate
                    low = fullness
                } else high = fullness
            }
            result
        }
        // Changing axes can also alter ascent/descent; verify the final outlines
        // against both dimensions while preserving the chosen line breaks.
        return fitSize(maxWidth, maxHeight, wordGap, lineGap, chosen.size) { expanded }
    }

    private fun fitSize(
        width: Float,
        height: Float,
        wordGap: Float,
        lineGap: Float,
        sizeLimit: Float = height * 2f,
        linesAt: (Float) -> List<List<Run>>,
    ): Layout {
        fun layout(size: Float): Layout {
            val lines = linesAt(size)
            return Layout(lines, size, lines.map { measure(it, size, wordGap) })
        }
        var low = 0.01f
        var high = sizeLimit
        repeat(16) {
            val size = (low + high) / 2f
            val candidate = layout(size)
            if (candidate.bounds.all { it.width() <= width } && candidate.height(lineGap) <= height) low = size
            else high = size
        }
        return layout(low)
    }

    private fun wrap(runs: List<Run>, size: Float, width: Float, gap: Float): List<List<Run>> {
        val lines = mutableListOf<List<Run>>()
        var current = emptyList<Run>()
        runs.forEach { run ->
            val next = current + run
            if (current.isNotEmpty() && measure(next, size, gap).width() > width) {
                lines += current
                current = listOf(run)
            } else current = next
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun measure(runs: List<Run>, size: Float, gap: Float): RectF {
        val bounds = RectF()
        val ink = Rect()
        var x = 0f
        runs.forEach { run ->
            run.paint.textSize = size
            run.paint.getTextBounds(run.text, 0, run.text.length, ink)
            bounds.union(x + ink.left, ink.top.toFloat(), x + ink.right, ink.bottom.toFloat())
            x += run.paint.measureText(run.text) + gap
        }
        return bounds
    }
}
