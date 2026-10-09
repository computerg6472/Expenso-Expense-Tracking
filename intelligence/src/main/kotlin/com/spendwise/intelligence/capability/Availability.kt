package com.spendwise.intelligence.capability

enum class Availability(val badgeLabel: String) {
    /**
     * Local ML / on-device model ready (e.g. Gemini Nano / AICore / TFLite / ONNX).
     */
    AVAILABLE("AI Powered"),

    /**
     * Fallback heuristics / smart deterministic rules active.
     */
    FALLBACK_ONLY("Smart Rules"),

    /**
     * Hardware or component not supported or disabled.
     */
    UNAVAILABLE("Unavailable")
}
