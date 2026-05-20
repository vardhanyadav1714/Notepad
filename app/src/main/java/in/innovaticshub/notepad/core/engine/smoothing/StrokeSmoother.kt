package `in`.innovaticshub.notepad.core.engine.smoothing

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import kotlin.math.sqrt

/**
 * Advanced stroke smoothing algorithms for premium drawing experience.
 */
interface StrokeSmoother {
    fun smooth(points: List<PointF>, pressures: List<Float> = emptyList()): SmoothedStroke
}

data class SmoothedStroke(
    val points: List<PointF>,
    val pressures: List<Float>,
    val velocities: List<Float>
)

/**
 * Velocity-based adaptive smoothing.
 */
class VelocitySmoother(
    private val minSmoothing: Float = 0.2f,
    private val maxSmoothing: Float = 0.8f,
    private val velocityThreshold: Float = 2000f,
    private val minSampleDistance: Float = 2f
) : StrokeSmoother {

    override fun smooth(points: List<PointF>, pressures: List<Float>): SmoothedStroke {
        if (points.size < 3) {
            return SmoothedStroke(points, pressures, emptyList())
        }

        // Calculate velocities for each point
        val velocities = calculateVelocities(points)

        // Apply adaptive smoothing
        val smoothedPoints = mutableListOf<PointF>()
        val smoothedPressures = mutableListOf<Float>()

        // Always include first point
        smoothedPoints.add(points[0])
        if (pressures.isNotEmpty()) smoothedPressures.add(pressures[0])

        var i = 1
        while (i < points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]
            val velocity = velocities[i]

            // Skip points that are too close
            if (i > 1 && curr.distanceTo(smoothedPoints.last()) < minSampleDistance) {
                i++
                continue
            }

            // Calculate smoothing factor based on velocity
            val smoothingFactor = calculateSmoothingFactor(velocity)

            // Apply weighted smoothing
            val smoothed = applyWeightedSmoothing(prev, curr, next, smoothingFactor)
            smoothedPoints.add(smoothed)

            // Smooth pressure
            if (pressures.isNotEmpty() && i < pressures.size) {
                val smoothedPressure = applyWeightedSmoothing(
                    pressures.getOrElse(i - 1) { pressures[i] },
                    pressures[i],
                    pressures.getOrElse(i + 1) { pressures[i] },
                    smoothingFactor
                )
                smoothedPressures.add(smoothedPressure)
            }

            i++
        }

        // Always include last point
        smoothedPoints.add(points.last())
        if (pressures.isNotEmpty() && pressures.isNotEmpty()) {
            smoothedPressures.add(pressures.last())
        }

        return SmoothedStroke(smoothedPoints, smoothedPressures, velocities)
    }

    private fun calculateVelocities(points: List<PointF>): List<Float> {
        val velocities = mutableListOf<Float>()
        velocities.add(0f)

        for (i in 1 until points.size) {
            val dist = points[i].distanceTo(points[i - 1])
            val dt = 1f / 60f
            velocities.add(dist / dt)
        }

        return velocities
    }

    private fun calculateSmoothingFactor(velocity: Float): Float {
        val normalizedVelocity = (velocity / velocityThreshold).coerceIn(0f, 1f)
        return maxSmoothing - (normalizedVelocity * (maxSmoothing - minSmoothing))
    }

    private fun applyWeightedSmoothing(prev: PointF, curr: PointF, next: PointF, factor: Float): PointF {
        val weightCurrent = 1f - factor
        val weightNeighbors = factor / 2f

        return PointF(
            x = prev.x * weightNeighbors + curr.x * weightCurrent + next.x * weightNeighbors,
            y = prev.y * weightNeighbors + curr.y * weightCurrent + next.y * weightNeighbors
        )
    }

    private fun applyWeightedSmoothing(prev: Float, curr: Float, next: Float, factor: Float): Float {
        val weightCurrent = 1f - factor
        val weightNeighbors = factor / 2f
        return prev * weightNeighbors + curr * weightCurrent + next * weightNeighbors
    }
}

/**
 * Chaikin's corner cutting algorithm.
 */
class ChaikinSmoother(
    private val iterations: Int = 2,
    private val cornerCutFactor: Float = 0.25f
) : StrokeSmoother {

    override fun smooth(points: List<PointF>, pressures: List<Float>): SmoothedStroke {
        if (points.size < 3) {
            return SmoothedStroke(points, pressures, emptyList())
        }

        var currentPoints = points
        var currentPressures = pressures

        repeat(iterations) {
            val result = chaikinIteration(currentPoints, cornerCutFactor)
            currentPoints = result.first
            currentPressures = if (currentPressures.isNotEmpty()) {
                chaikinIterationValues(currentPressures, cornerCutFactor)
            } else {
                emptyList()
            }
        }

        return SmoothedStroke(currentPoints, currentPressures, emptyList())
    }

    private fun chaikinIteration(points: List<PointF>, factor: Float): Pair<List<PointF>, List<PointF>> {
        if (points.size < 2) return points to emptyList()

        val smoothed = mutableListOf<PointF>()
        smoothed.add(points.first())

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val q = PointF(
                x = (1 - factor) * p0.x + factor * p1.x,
                y = (1 - factor) * p0.y + factor * p1.y
            )
            val r = PointF(
                x = factor * p0.x + (1 - factor) * p1.x,
                y = factor * p0.y + (1 - factor) * p1.y
            )

            smoothed.add(q)
            smoothed.add(r)
        }

        smoothed.add(points.last())
        return smoothed to emptyList()
    }

    private fun chaikinIterationValues(values: List<Float>, factor: Float): List<Float> {
        if (values.size < 2) return values

        val smoothed = mutableListOf<Float>()
        smoothed.add(values.first())

        for (i in 0 until values.size - 1) {
            val v0 = values[i]
            val v1 = values[i + 1]
            smoothed.add((1 - factor) * v0 + factor * v1)
            smoothed.add(factor * v0 + (1 - factor) * v1)
        }

        smoothed.add(values.last())
        return smoothed
    }
}

/**
 * Real-time stroke builder with progressive smoothing.
 */
class RealtimeStrokeBuilder(
    private val smoother: StrokeSmoother = VelocitySmoother()
) {
    private val rawPoints = mutableListOf<PointF>()
    private val rawPressures = mutableListOf<Float>()
    private val rawTimestamps = mutableListOf<Long>()

    private val smoothedPoints = mutableListOf<PointF>()
    private val smoothedPressures = mutableListOf<Float>()

    private var lastSmoothedIndex = 0

    fun addPoint(point: PointF, pressure: Float = 1f, timestamp: Long = System.nanoTime()) {
        rawPoints.add(point)
        rawPressures.add(pressure)
        rawTimestamps.add(timestamp)

        lastSmoothedIndex = maxOf(0, rawPoints.size - 10)
    }

    fun getSmoothedStroke(): SmoothedStroke {
        val recentPoints = rawPoints.drop(lastSmoothedIndex)
        val recentPressures = rawPressures.drop(lastSmoothedIndex)

        if (recentPoints.size < 3) {
            return SmoothedStroke(rawPoints.toList(), rawPressures.toList(), emptyList())
        }

        val recentSmoothed = smoother.smooth(recentPoints, recentPressures)

        val combinedPoints = smoothedPoints.take(lastSmoothedIndex).toMutableList()
        combinedPoints.addAll(recentSmoothed.points)

        val combinedPressures = smoothedPressures.take(lastSmoothedIndex).toMutableList()
        combinedPressures.addAll(recentSmoothed.pressures)

        return SmoothedStroke(combinedPoints, combinedPressures, recentSmoothed.velocities)
    }

    fun finish(): SmoothedStroke {
        return smoother.smooth(rawPoints.toList(), rawPressures.toList())
    }

    fun clear() {
        rawPoints.clear()
        rawPressures.clear()
        rawTimestamps.clear()
        smoothedPoints.clear()
        smoothedPressures.clear()
        lastSmoothedIndex = 0
    }

    val isEmpty: Boolean get() = rawPoints.isEmpty()
    val pointCount: Int get() = rawPoints.size
}
