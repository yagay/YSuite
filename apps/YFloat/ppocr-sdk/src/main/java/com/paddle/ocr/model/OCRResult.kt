// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0

package com.paddle.ocr.model

data class OCRResult(
    val box: OCRBox,
    val text: String,
    val confidence: Float,
    /** Character-level geometry aligned from the CTC recognition time axis. */
    val characters: List<OCRCharacter> = emptyList(),
    /** Explicit CTC-derived words, never mistaken for raw character boxes. */
    val words: List<OCRWord> = OCRWordSegmenter.segment(characters),
    /** Kept for API compatibility; now contains genuine grouped word quads. */
    val wordBoxes: List<OCRBox>? = words.map {it.box}.takeIf {it.isNotEmpty()},
)
