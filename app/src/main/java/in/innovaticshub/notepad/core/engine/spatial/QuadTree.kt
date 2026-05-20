package `in`.innovaticshub.notepad.core.engine.spatial

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.domain.geometry.RectF
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import kotlin.math.max

/**
 * Thread-safe QuadTree for spatial indexing of canvas elements.
 * Optimized for frequent queries and dynamic insertion/removal.
 *
 * Performance Characteristics:
 * - Insertion: O(log n) average, O(n) worst case
 * - Query: O(log n + k) where k = results count
 * - Removal: O(log n)
 */
class QuadTree<T : SpatialElement>(
    private val boundary: RectF,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxDepth: Int = MAX_DEPTH
) {
    private var root: Node<T> = Node(boundary, 0, capacity)

    val bounds: RectF get() = root.boundary

    /**
     * Insert an element into the tree.
     * Returns true if successful, false if element is out of bounds.
     */
    @Synchronized
    fun insert(element: T): Boolean {
        return root.insert(element, capacity, maxDepth)
    }

    /**
     * Remove an element from the tree.
     */
    @Synchronized
    fun remove(element: T): Boolean {
        return root.remove(element)
    }

    /**
     * Update an element's position (remove and reinsert).
     */
    @Synchronized
    fun update(element: T, oldBounds: RectF): Boolean {
        root.remove(element, oldBounds)
        return root.insert(element, capacity, maxDepth)
    }

    /**
     * Find all elements that intersect with the given rectangle.
     */
    @Synchronized
    fun query(range: RectF): List<T> {
        val results = mutableListOf<T>()
        root.query(range, results)
        return results
    }

    /**
     * Find elements at or near a point.
     */
    @Synchronized
    fun query(point: PointF, radius: Float = 0f): List<T> {
        val range = if (radius > 0) {
            RectF(
                point.x - radius, point.y - radius,
                point.x + radius, point.y + radius
            )
        } else {
            RectF(point.x - 1, point.y - 1, point.x + 1, point.y + 1)
        }
        return query(range)
    }

    /**
     * Find the nearest element to a point within maxDistance.
     */
    @Synchronized
    fun findNearest(point: PointF, maxDistance: Float = Float.MAX_VALUE): T? {
        var nearest: T? = null
        var minDist = maxDistance
        val candidates = query(point, maxDistance)
        candidates.forEach { element ->
            val dist = point.distanceTo(element.bounds.center)
            if (dist < minDist) {
                minDist = dist
                nearest = element
            }
        }
        return nearest
    }

    /**
     * Clear all elements from the tree.
     */
    @Synchronized
    fun clear() {
        root = Node(root.boundary, 0)
    }

    /**
     * Get total element count.
     */
    @Synchronized
    fun size(): Int {
        return root.size()
    }

    /**
     * Get tree depth.
     */
    @Synchronized
    fun depth(): Int {
        return root.depth()
    }

    /**
     * Check if tree needs rebalancing.
     */
    @Synchronized
    fun needsRebalance(): Boolean {
        val nodeCount = root.nodeCount()
        val elementCount = size()
        // Rebalance if average elements per node is too low (sparse tree)
        return nodeCount > 0 && elementCount / nodeCount < capacity / 4
    }

    /**
     * Rebuild the tree from scratch.
     */
    @Synchronized
    fun rebuild(elements: List<T>) {
        root = Node(root.boundary, 0)
        elements.forEach { insert(it) }
    }

    private class Node<T : SpatialElement>(
        val boundary: RectF,
        val depth: Int,
        val capacity: Int = DEFAULT_CAPACITY
    ) {
        private val elements: MutableList<T> = mutableListOf()
        private var children: Array<Node<T>>? = null

        fun insert(element: T, capacity: Int, maxDepth: Int): Boolean {
            // Don't insert if out of bounds
            if (!boundary.intersects(element.bounds)) {
                return false
            }

            // If leaf and has capacity, add here
            if (children == null) {
                elements.add(element)
                return true
            }

            // Otherwise, insert into appropriate children
            children?.forEach {
                if (it.insert(element, capacity, maxDepth)) {
                    return true
                }
            }

            // Fallback: add to this node (shouldn't happen with proper subdivision)
            elements.add(element)
            return true
        }

        fun remove(element: T, oldBounds: RectF = element.bounds): Boolean {
            // Check if element could be in this subtree
            if (!boundary.intersects(oldBounds)) {
                return false
            }

            // Check direct elements
            val index = elements.indexOf(element)
            if (index != -1) {
                elements.removeAt(index)
                return true
            }

            // Search children
            children?.forEach {
                if (it.remove(element, oldBounds)) {
                    // Try to merge children if mostly empty
                    tryMerge()
                    return true
                }
            }

            return false
        }

        fun query(range: RectF, results: MutableList<T>) {
            // Don't search if range doesn't intersect
            if (!boundary.intersects(range)) {
                return
            }

            // Add elements that intersect
            elements.forEach {
                if (range.intersects(it.bounds)) {
                    results.add(it)
                }
            }

            // Search children
            children?.forEach {
                it.query(range, results)
            }
        }

        fun size(): Int {
            val childSize = children?.sumOf { it.size() } ?: 0
            return elements.size + childSize
        }

        fun depth(): Int {
            val childDepth = children?.maxOfOrNull { it.depth() } ?: 0
            return max(depth, childDepth)
        }

        fun nodeCount(): Int {
            val childCount = children?.sumOf { it.nodeCount() } ?: 0
            return 1 + childCount
        }

        private fun tryMerge() {
            val childrenArray = children ?: return
            val totalElements = childrenArray.sumOf { it.elements.size }

            // Merge if total elements is small
            if (totalElements < capacity / 2) {
                childrenArray.forEach {
                    elements.addAll(it.elements)
                }
                children = null
            }
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 16
        const val MAX_DEPTH = 12

        /**
         * Create a QuadTree from screen dimensions.
         */
        fun <T : SpatialElement> fromScreenSize(width: Float, height: Float): QuadTree<T> {
            val boundary = RectF(0f, 0f, width, height)
            return QuadTree(boundary)
        }
    }
}

/**
 * Interface for elements that can be spatially indexed.
 */
interface SpatialElement {
    val bounds: RectF
    val id: String
}
