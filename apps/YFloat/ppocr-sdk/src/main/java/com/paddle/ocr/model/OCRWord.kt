package com.paddle.ocr.model

/**
 * OCR word boundary inferred from CTC-aligned symbols. A word is NOT a
 * separate detector prediction; geometry is a union of original symbol quads.
 */
data class OCRWord(
    val text: String,
    val box: OCRBox,
    val characters: List<OCRCharacter>,
    val confidence: Float,
)
