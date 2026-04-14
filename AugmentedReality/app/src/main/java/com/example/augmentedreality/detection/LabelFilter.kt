package com.example.augmentedreality.detection

/**
 * Filters and normalises EfficientDet COCO-80 label output to the five classes
 * the app surfaces in the UI, collapsing common misdetections via aliases.
 *
 * Extracted as a top-level object so it can be unit-tested without Android
 * instrumentation.
 */
object LabelFilter {

    /** The five object classes the app surfaces in the UI. */
    val ALLOWED: Set<String> = setOf(
        "cell phone", "bottle", "person", "laptop", "chair"
    )

    /**
     * Maps raw COCO-80 label variants (and common misdetections on EfficientDet Lite2)
     * to a canonical label inside [ALLOWED].
     */
    val ALIASES: Map<String, String> = mapOf(
        "phone"         to "cell phone",
        "cellphone"     to "cell phone",
        "mobile phone"  to "cell phone",
        "remote"        to "cell phone",
        "keyboard"      to "laptop",
        "tv"            to "laptop",
        "monitor"       to "laptop",
        "book"          to "laptop",
        "refrigerator"  to "laptop",
        "refridgerator" to "laptop",   // common OCR/model typo
        "cup"           to "bottle",
        "bench"         to "chair",
        "couch"         to "chair"
    )

    /**
     * Full COCO-80 class list, used to resolve a label by index when the model
     * returns an integer category index instead of a string.
     */
    val COCO80: List<String> = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
        "toothbrush"
    )

    /**
     * Returns the canonical [ALLOWED] label for [raw], or `null` if the label
     * is not allowed (directly or via an alias).
     *
     * Comparison is case-insensitive and trims surrounding whitespace.
     */
    fun normalize(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase() ?: return null
        if (normalized.isBlank()) return null
        val canonical = ALIASES[normalized] ?: normalized
        return canonical.takeIf { it in ALLOWED }
    }
}
