package com.example.canvas.bench

/**
 * Categorization of benchmark stroke sizes.
 */
enum class StrokeType {
    /** Bounding box spans ~2% to 10% of total canvas area */
    SMALL,

    /** Bounding box spans ~40% to 100% of total canvas area */
    LARGE
}
