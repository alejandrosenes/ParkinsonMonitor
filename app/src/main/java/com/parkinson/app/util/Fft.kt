package com.parkinson.app.util

/**
 * In-place iterative radix-2 FFT. Input arrays length must be power of two.
 * Returns magnitude spectrum (size n/2).
 */
object Fft {
    fun compute(samples: DoubleArray): DoubleArray {
        val n = samples.size
        require(n > 0 && (n and (n - 1)) == 0) { "sample count must be power of two" }
        val re = samples.copyOf()
        val im = DoubleArray(n)
        // bit reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = Math.cos(ang)
            val wIm = Math.sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                var k = 0
                while (k < len / 2) {
                    val idx1 = i + k
                    val idx2 = idx1 + len / 2
                    val tRe = wR * re[idx2] - wI * im[idx2]
                    val tIm = wR * im[idx2] + wI * re[idx2]
                    re[idx2] = re[idx1] - tRe
                    im[idx2] = im[idx1] - tIm
                    re[idx1] += tRe
                    im[idx1] += tIm
                    val nWRe = wR * wRe - wI * wIm
                    wI = wR * wIm + wI * wRe
                    wR = nWRe
                    k++
                }
                i += len
            }
            len = len shl 1
        }
        val half = n / 2
        val mag = DoubleArray(half)
        for (i in 0 until half) {
            mag[i] = kotlin.math.hypot(re[i], im[i])
        }
        return mag
    }

    /**
     * Returns the dominant frequency (Hz) in (low..high) given sample rate.
     */
    fun dominantFrequency(samples: DoubleArray, sampleRate: Double, low: Double, high: Double): Pair<Double, Double> {
        val n = largestPowerOfTwo(samples.size)
        if (n < 4) return 0.0 to 0.0
        val trimmed = samples.take(n).toDoubleArray()
        val mag = compute(trimmed)
        val binWidth = sampleRate / n
        var bestIdx = 0
        var bestMag = 0.0
        for (i in 1 until mag.size) {
            val freq = i * binWidth
            if (freq in low..high && mag[i] > bestMag) {
                bestMag = mag[i]
                bestIdx = i
            }
        }
        val freq = bestIdx * binWidth
        // severity proxy: magnitude of tremor band relative to total
        val total = mag.sum().coerceAtLeast(1e-9)
        val band = mag.indices.filter { val f = it * binWidth; f in low..high }.sumOf { mag[it] }
        val severity = (band / total).toFloat().coerceIn(0f, 1f)
        return freq to severity.toDouble()
    }

    private fun largestPowerOfTwo(n: Int): Int {
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }
}
