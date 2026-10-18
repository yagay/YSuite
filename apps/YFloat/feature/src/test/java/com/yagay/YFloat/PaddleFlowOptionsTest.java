package com.yagay.YFloat;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Isolated stage-policy tests: no Android runtime, OCR model or device required. */
public final class PaddleFlowOptionsTest {
    private static SharedPreferences prefs(Map<String,Boolean> values){
        return (SharedPreferences)Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy,method,args)->{
                    if("getBoolean".equals(method.getName()))
                        return values.getOrDefault((String)args[0],(Boolean)args[1]);
                    if("contains".equals(method.getName()))
                        return values.containsKey((String)args[0]);
                    return null;
                });
    }

    @Test public void defaultCircleIsFastTextOnly(){
        PaddleFlowOptions circle=new PaddleFlowOptions(prefs(Map.of()),true);
        assertTrue(circle.circle);
        assertTrue(circle.fastDetection);
        assertTrue(circle.spacing);
        assertFalse(circle.extended);
        assertFalse(circle.layout);
        assertFalse(circle.tables);
        assertFalse(circle.uvdoc);
        assertFalse(circle.formulas);
        assertFalse(circle.seals);
        assertFalse(circle.charts);
        assertFalse(circle.documentOrientation);
        assertFalse(circle.lineOrientation);
        assertFalse(circle.rotationFallback);
    }

    @Test public void regularDocumentRetainsOptionalStages(){
        PaddleFlowOptions doc=new PaddleFlowOptions(prefs(Map.of()),false);
        assertFalse(doc.circle);
        assertFalse(doc.fastDetection);
        assertTrue(doc.extended);
        assertTrue(doc.layout);
        assertTrue(doc.tables);
        assertTrue(doc.uvdoc);
        assertTrue(doc.formulas);
        assertTrue(doc.seals);
        assertTrue(doc.charts);
        assertFalse(doc.rotationFallback);
    }

    @Test public void disabledMasterPreservesOverridesButSkipsExecution(){
        Map<String,Boolean> settings=new HashMap<>();
        settings.put(PaddleFlowOptions.key(true,"extended"),false);
        settings.put(PaddleFlowOptions.key(true,"layout"),true);
        settings.put(PaddleFlowOptions.key(true,"tables"),true);
        settings.put(PaddleFlowOptions.key(true,"spacing"),false);
        PaddleFlowOptions circle=new PaddleFlowOptions(prefs(settings),true);
        assertFalse(circle.extended);
        assertFalse(circle.layout);
        assertFalse(circle.tables);
        assertFalse(circle.spacing);
        settings.put(PaddleFlowOptions.key(true,"extended"),true);
        circle=new PaddleFlowOptions(prefs(settings),true);
        assertTrue(circle.layout);
        assertTrue(circle.tables);
        assertFalse(circle.spacing);
    }

    @Test public void profilesAreIsolatedAndOptionalStagesAreIndependent(){
        Map<String,Boolean> prefs=new HashMap<>();
        prefs.put(PaddleFlowOptions.key(true,"extended"),true);
        prefs.put(PaddleFlowOptions.key(true,"tables"),false);
        prefs.put(PaddleFlowOptions.key(true,"document_orientation"),true);
        prefs.put(PaddleFlowOptions.key(true,"line_orientation"),true);
        prefs.put(PaddleFlowOptions.key(false,"uvdoc"),false);
        PaddleFlowOptions circle=new PaddleFlowOptions(prefs(prefs),true);
        PaddleFlowOptions doc=new PaddleFlowOptions(prefs(prefs),false);
        assertFalse(circle.tables);
        assertTrue(circle.documentOrientation);
        assertTrue(circle.lineOrientation);
        assertTrue(doc.tables);
        assertFalse(doc.uvdoc);
        assertFalse(doc.lineOrientation);
    }

    @Test public void legacySpacingChoiceIsMigratedButCanBeOverridden(){
        Map<String,Boolean> values=new HashMap<>();
        values.put("paddle_v2_spacing",false);
        PaddleFlowOptions a=new PaddleFlowOptions(prefs(values),true);
        assertFalse(a.spacing);
        values.put(PaddleFlowOptions.key(true,"spacing"),true);
        assertTrue(new PaddleFlowOptions(prefs(values),true).spacing);
    }

    @Test public void preferenceKeysDoNotOverlap(){
        assertEquals("paddle_flow_circle_extended",PaddleFlowOptions.key(true,"extended"));
        assertEquals("paddle_flow_document_extended",PaddleFlowOptions.key(false,"extended"));
        assertNotEquals(PaddleFlowOptions.key(true,"layout"),
                        PaddleFlowOptions.key(false,"layout"));
    }
}
