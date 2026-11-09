package com.example.canvas.bench

/**
 * Categorization of benchmark stroke sizes.
 */
enum class StrokeType {
    /** Bounding box spans ~2% to 10% of total canvas area */
    SMALL,

    /** Bounding box spans ~40% to 100% of total canvas area */
    LARGE,

    /** An empty rectangular frame spanning ~40% to 100% of the canvas: a huge bounding box, almost no ink. */
    HOLLOW,

    /** Alternates small and large strokes: steps of very different sizes in the same history. */
    MIXED,

    /**
     * A drawing session that looks like a person at work: horizontal, vertical and diagonal lines, hatching,
     * circles and spirals, hand-written wandering strokes, broad flat bands, short sketch curves and white
     * "erase" strokes, at many sizes. Not one repeated shape.
     */
    DIVERSE
}
