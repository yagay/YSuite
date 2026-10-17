package com.yagay.YFloat.hook;

import android.graphics.RectF;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * APK-verified selection ABI for Google App 17.66.12.ve.
 *
 * This is an observer of Google's existing selection model, NOT a headless OCR
 * entrypoint. R8 symbols must be checked against the live ClassLoader before
 * installing any method hook. An APK update falls back to the structural
 * resolver without running this outdated mapping.
 */
final class GoogleLens1766Profile {
    static final String NAME = "17.66.12.ve";
    static final String CONTROLLER = "dwqi";
    static final String METADATA = "dwpz";
    static final String USER_SELECTION = "dxzm";
    static final String WORD_SELECTION_A = "dxzh";
    static final String WORD_SELECTION_B = "dxzo";
    static final String TEXT_SELECTION = "dxzi";
    static final String REGION_SELECTION = "dxzk";
    static final String WORD_DATA = "dyjx";

    static String selectionValidationError(ClassLoader loader) {
        if (loader == null) return "classLoader=null";
        try {
            Class<?> controller=Class.forName(CONTROLLER,false,loader);
            Class<?> metadata=Class.forName(METADATA,false,loader);
            Class<?> user=Class.forName(USER_SELECTION,false,loader);
            Class<?> wordA=Class.forName(WORD_SELECTION_A,false,loader);
            Class<?> wordB=Class.forName(WORD_SELECTION_B,false,loader);
            Class<?> data=Class.forName(WORD_DATA,false,loader);
            Class.forName(TEXT_SELECTION,false,loader);
            Class.forName(REGION_SELECTION,false,loader);
            Field slot=metadata.getDeclaredField("a");
            if(slot.getType()!=user)return "17.66 metadata.a type mismatch";
            if(wordA.getDeclaredField("a").getType()!=data
                    ||wordB.getDeclaredField("a").getType()!=data)
                return "17.66 word selection data mismatch";
            if(data.getDeclaredField("a").getType()!=String.class
                    ||!List.class.isAssignableFrom(data.getDeclaredField("b").getType()))
                return "17.66 word data text/list contract mismatch";
            if(!hasNoArg(user,"k",String.class)
                    ||!hasNoArg(user,"b",RectF.class))
                return "17.66 UserSelection text/bounds accessor mismatch";
            for(Executable method:HiddenApiBypass.getDeclaredMethods(controller))
                if(method instanceof Method m && isSelectionMethod(m))return "";
            return "17.66 controller selection callback missing";
        }catch(Throwable error){
            return "17.66 ABI unavailable: "+error.getClass().getSimpleName();
        }
    }

    static Method findSelectionMethod(ClassLoader loader) {
        if(!selectionValidationError(loader).isBlank())return null;
        try{
            Class<?> cls=Class.forName(CONTROLLER,false,loader);
            for(Executable executable:HiddenApiBypass.getDeclaredMethods(cls)){
                if(executable instanceof Method method && isSelectionMethod(method))
                    return method;
            }
        }catch(Throwable ignored){}
        return null;
    }

    static boolean isSelectionMethod(Method method) {
        if(method==null||!"z".equals(method.getName())
                ||method.getReturnType()!=void.class)return false;
        Class<?>[] params=method.getParameterTypes();
        return params.length==2&&METADATA.equals(params[0].getName())
                &&params[1]==boolean.class;
    }

    private static boolean hasNoArg(Class<?> cls,String name,Class<?> returnType) {
        for(Method method:GoogleReflection.methodsInHierarchy(cls))
            if(name.equals(method.getName())&&method.getParameterCount()==0
                    &&method.getReturnType()==returnType)return true;
        return false;
    }

    static boolean isDirectRegionSelectionClass(String className){
        return REGION_SELECTION.equals(className);
    }

    static SelectionSnapshot selection(Object metadata) {
        if(metadata==null)return new SelectionSnapshot("",
                null,"","metadata=null",false);
        Object user=GoogleReflection.readField(metadata,"a",USER_SELECTION);
        if(user==null)return new SelectionSnapshot("",null,"",
                "profile="+NAME+" metadataUser=missing",false);
        String kind=user.getClass().getName();
        String value="";
        Object direct=GoogleReflection.invokeNoArg(user,"k");
        if(direct instanceof String selected)value=selected.trim();
        String source="userSelection.k";

        // These concrete 17.66 WordSelection models deliberately implement
        // k() and b() as null. The OCR text lives in dyjx.a instead.
        if(value.isBlank()&&(WORD_SELECTION_A.equals(kind)
                ||WORD_SELECTION_B.equals(kind))){
            Object wordData=GoogleReflection.readField(user,"a",WORD_DATA);
            value=textFromWordData(wordData);
            source="wordData.a";
        }

        RectF raw=null;
        Object candidate=GoogleReflection.invokeNoArg(user,"b");
        if(candidate instanceof RectF bounds
                &&Float.isFinite(bounds.left)&&Float.isFinite(bounds.top)
                &&Float.isFinite(bounds.right)&&Float.isFinite(bounds.bottom)
                &&bounds.width()>0f&&bounds.height()>0f)
            raw=new RectF(bounds);

        // Do NOT fabricate word boxes from dyjx.b: its list element geometry
        // has not yet been verified. The separate live TextSelectionView hook
        // supplies screen-projected word bounds when available.
        return new SelectionSnapshot(value,raw,kind,
                "profile="+NAME+" selection="+kind+" chars="+value.length()
                        +" source="+source+" bounds="+(raw!=null),
                isDirectRegionSelectionClass(kind));
    }

    /** Called only on the validated dyjx field, never on arbitrary Google data. */
    static String textFromWordData(Object data) {
        if(data==null)return "";
        Object candidate=GoogleReflection.readField(data,"a",String.class.getName());
        Object words=GoogleReflection.readNamedField(data,"b");
        if(!(words instanceof List<?>) || !(candidate instanceof String text))
            return "";
        return text.trim();
    }

    static final class SelectionSnapshot {
        final String text;
        final RectF rawBounds;
        final String selectionClass;
        final String detail;
        final boolean regionModel;
        SelectionSnapshot(String text,RectF rawBounds,String selectionClass,
                          String detail,boolean regionModel){
            this.text=text==null?"":text;
            this.rawBounds=rawBounds;
            this.selectionClass=selectionClass==null?"":selectionClass;
            this.detail=detail==null?"":detail;
            this.regionModel=regionModel;
        }
    }
    private GoogleLens1766Profile(){}
}
