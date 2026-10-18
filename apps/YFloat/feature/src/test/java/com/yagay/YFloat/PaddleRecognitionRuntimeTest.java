package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Offline Paddle model selection is shared by normal OCR and Circle. */
public final class PaddleRecognitionRuntimeTest {
    @Test public void normalPickerMapsEveryPaddleModel(){
        assertEquals(0,PaddleRecognitionRuntime.normalModel(0));
        assertEquals(OcrModelManager.MEDIUM,PaddleRecognitionRuntime.normalModel(1));
        assertEquals(OcrModelManager.SMALL,PaddleRecognitionRuntime.normalModel(2));
        assertEquals(OcrModelManager.MOBILE,PaddleRecognitionRuntime.normalModel(5));
        assertEquals(OcrModelManager.TINY,PaddleRecognitionRuntime.normalModel(6));
    }
    @Test public void oldMlKitAndCloudModesCannotActivateExternalEngines(){
        assertEquals(0,PaddleRecognitionRuntime.normalModel(3));
        assertEquals(0,PaddleRecognitionRuntime.normalModel(4));
        assertEquals(0,PaddleRecognitionRuntime.circleModel(0));
        assertEquals(0,PaddleRecognitionRuntime.circleModel(5));
    }
    @Test public void circlePickerMapsEveryPaddleModel(){
        assertEquals(OcrModelManager.TINY,PaddleRecognitionRuntime.circleModel(1));
        assertEquals(OcrModelManager.SMALL,PaddleRecognitionRuntime.circleModel(2));
        assertEquals(OcrModelManager.MEDIUM,PaddleRecognitionRuntime.circleModel(3));
        assertEquals(OcrModelManager.MOBILE,PaddleRecognitionRuntime.circleModel(4));
    }
}
