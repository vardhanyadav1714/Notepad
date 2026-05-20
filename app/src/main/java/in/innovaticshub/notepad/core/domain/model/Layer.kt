package `in`.innovaticshub.notepad.core.domain.model

import `in`.innovaticshub.notepad.core.domain.util.generateId

/**
 * Layer system for canvas organization.
 *
 * Layers provide:
 * - Z-ordering control
 * - Grouping of related elements
 * - Selective visibility/locking
 * - Blend modes and opacity
 * - Export filtering
 */
data class Layer(
    val id: String = generateId("layer"),
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.SrcOver,
    val zIndex: Int = 0
) {
    val isEditable: Boolean get() = isVisible && !isLocked

    fun withVisibility(visible: Boolean) = copy(isVisible = visible)
    fun withLocking(locked: Boolean) = copy(isLocked = locked)
    fun withOpacity(alpha: Float) = copy(opacity = alpha.coerceIn(0f, 1f))
    fun withBlendMode(mode: LayerBlendMode) = copy(blendMode = mode)
    fun withZIndex(index: Int) = copy(zIndex = index)

    companion object {
        val Default = Layer(
            id = DEFAULT_LAYER_ID,
            name = "Layer 1",
            isVisible = true,
            isLocked = false,
            opacity = 1f,
            zIndex = 0
        )

        const val DEFAULT_LAYER_ID = "default_layer"
    }
}

/**
 * Layer group for organizing multiple layers.
 */
data class LayerGroup(
    val id: String = generateId("layer_group"),
    val name: String,
    val layers: List<Layer> = emptyList(),
    val isExpanded: Boolean = true
) {
    val allLayerIds: List<String> get() = layers.map { it.id }

    fun withLayer(layer: Layer) = copy(
        layers = layers + layer
    )

    fun withLayerUpdated(layerId: String, update: (Layer) -> Layer) = copy(
        layers = layers.map {
            if (it.id == layerId) update(it) else it
        }
    )

    fun withLayerRemoved(layerId: String) = copy(
        layers = layers.filterNot { it.id == layerId }
    )

    fun withReorderedLayers(layerIds: List<String>) = copy(
        layers = layerIds.mapNotNull { id ->
            layers.find { it.id == id }
        }
    )
}

/**
 * Layer manager handles layer operations and ordering.
 */
class LayerManager {
    private val mutableLayers: MutableMap<String, Layer> = mutableMapOf(
        Layer.DEFAULT_LAYER_ID to Layer.Default
    )
    private val mutableLayerOrder: MutableList<String> = mutableListOf(Layer.DEFAULT_LAYER_ID)

    val layers: List<Layer> get() = mutableLayerOrder.mapNotNull { mutableLayers[it] }
    val layerIds: List<String> get() = mutableLayerOrder.toList()

    fun createLayer(
        name: String,
        index: Int? = null
    ): Layer {
        val layer = Layer(name = name)
        mutableLayers[layer.id] = layer

        val insertIndex = index ?: mutableLayerOrder.size
        mutableLayerOrder.add(insertIndex.coerceIn(0, mutableLayerOrder.size), layer.id)

        return layer
    }

    fun getLayer(id: String): Layer? = mutableLayers[id]

    fun updateLayer(id: String, update: (Layer) -> Layer): Layer? {
        val existing = mutableLayers[id] ?: return null
        val updated = update(existing)
        mutableLayers[id] = updated
        return updated
    }

    fun deleteLayer(id: String): Boolean {
        if (id == Layer.DEFAULT_LAYER_ID) return false // Can't delete default
        mutableLayers.remove(id)
        mutableLayerOrder.remove(id)
        return true
    }

    fun moveLayer(id: String, newIndex: Int): Boolean {
        val currentIndex = mutableLayerOrder.indexOf(id)
        if (currentIndex == -1) return false

        mutableLayerOrder.removeAt(currentIndex)
        mutableLayerOrder.add(newIndex.coerceIn(0, mutableLayerOrder.size), id)
        return true
    }

    fun reorderLayers(layerIds: List<String>) {
        mutableLayerOrder.clear()
        mutableLayerOrder.addAll(layerIds.filter { it in mutableLayers })
    }

    fun getZIndex(id: String): Int {
        return mutableLayerOrder.indexOf(id)
    }

    fun getTopmostLayer(): Layer? {
        return mutableLayers[mutableLayerOrder.lastOrNull()]
    }

    fun getBottommostLayer(): Layer? {
        return mutableLayers[mutableLayerOrder.firstOrNull()]
    }

    fun clear() {
        mutableLayers.clear()
        mutableLayerOrder.clear()
        mutableLayers[Layer.DEFAULT_LAYER_ID] = Layer.Default
        mutableLayerOrder.add(Layer.DEFAULT_LAYER_ID)
    }

    fun getLayersAbove(id: String): List<Layer> {
        val index = mutableLayerOrder.indexOf(id)
        if (index == -1) return emptyList()
        return (index + 1 until mutableLayerOrder.size).mapNotNull {
            mutableLayers[mutableLayerOrder[it]]
        }
    }

    fun getLayersBelow(id: String): List<Layer> {
        val index = mutableLayerOrder.indexOf(id)
        if (index == -1) return emptyList()
        return (0 until index).mapNotNull {
            mutableLayers[mutableLayerOrder[it]]
        }.reversed()
    }
}

/**
 * Blend modes for layer composition.
 */
enum class LayerBlendMode {
    /** Normal blending */
    SrcOver,

    /** Multiply colors (darkens) */
    Multiply,

    /** Screen colors (lightens) */
    Screen,

    /** Overlay (contrast) */
    Overlay,

    /** Darken (min) */
    Darken,

    /** Lighten (max) */
    Lighten,

    /** Color dodge */
    ColorDodge,

    /** Color burn */
    ColorBurn,

    /** Hard light */
    HardLight,

    /** Soft light */
    SoftLight,

    /** Difference */
    Difference,

    /** Exclusion */
    Exclusion,

    /** Additive */
    Plus,

    /** Erase destination */
    DstOut
}

/**
 * Layer composition settings.
 */
data class LayerComposition(
    val background: LayerBackground = LayerBackground.Transparent,
    val baseOpacity: Float = 1f
)

sealed class LayerBackground {
    data object Transparent : LayerBackground()
    data object White : LayerBackground()
    data object Black : LayerBackground()
    data class Color(val color: androidx.compose.ui.graphics.Color) : LayerBackground()
    data class Custom(val color: Int) : LayerBackground()
}
