package com.example.canvas.history

import com.example.canvas.core.Patch

/**
 * Insertion-ordered set of patches with O(1) add / remove / contains / pop-oldest.
 *
 * The stores used `ArrayDeque.remove(patch)` and `contains(patch)`, which scan the whole queue (and compare
 * data classes field by field): with an unlimited history of thousands of patches every undo, prefetch and
 * demotion paid O(n). Keyed by [Patch.uid], which is unique per instance.
 */
internal class PatchQueue : Iterable<Patch> {
    private val map = LinkedHashMap<Long, Patch>()

    val size: Int get() = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun addLast(patch: Patch) {
        map.remove(patch.uid)   // re-adding moves it to the end, like a fresh arrival
        map[patch.uid] = patch
    }

    fun remove(patch: Patch): Boolean {
        val existing = map[patch.uid] ?: return false
        if (existing !== patch) return false
        map.remove(patch.uid)
        return true
    }

    fun removeFirst(): Patch {
        val it = map.entries.iterator()
        val e = it.next()
        it.remove()
        return e.value
    }

    fun contains(patch: Patch): Boolean = map[patch.uid] === patch

    fun clear() = map.clear()

    override fun iterator(): Iterator<Patch> = map.values.toList().iterator()
}
