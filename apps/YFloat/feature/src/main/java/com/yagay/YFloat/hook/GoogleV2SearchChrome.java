package com.yagay.YFloat.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * V2 scene-only native search chrome owner. Hides Lens search surfaces only
 * after a verified YFloat text/region selection and restores their previous
 * visibility at session end. Does not hide handles or frozen screenshot.
 */
final class GoogleV2SearchChrome {
    private static final String ACTION_MENU =
            "com.google.android.libraries.lens.view.actionmenu.ActionMenuView";
    private static final String INFO_PANEL =
            "com.google.android.libraries.lens.view.infopanel.InfoPanelView";
    private static final String OMNIBOX =
            "com.google.android.libraries.lens.view.omnibox.OmniBoxView";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final BooleanSupplier shouldHide;
    private final BooleanSupplier selectionVerified;
    private final BiConsumer<String,String> log;
    private final IdentityHashMap<View, Integer> originals = new IdentityHashMap<>();
    private View root;
    private ViewTreeObserver.OnPreDrawListener predraw;
    private int lastHidden;

    GoogleV2SearchChrome(BooleanSupplier shouldHide,
            BooleanSupplier selectionVerified, BiConsumer<String,String> log) {
        this.shouldHide=shouldHide;this.selectionVerified=selectionVerified;this.log=log;
    }

    void attach(Activity owner) {
        if(owner==null||owner.getWindow()==null)return;
        ui.post(()->{
            if(owner.isFinishing()||owner.isDestroyed())return;
            View decor=owner.getWindow().getDecorView();
            if(decor==null)return;
            if(decor==root&&predraw!=null)return;
            detachInternal();
            root=decor;
            predraw=()->{
                if(shouldHide.getAsBoolean())hideNativeSearch();
                return true;
            };
            ViewTreeObserver observer=decor.getViewTreeObserver();
            if(observer.isAlive())observer.addOnPreDrawListener(predraw);
            if(shouldHide.getAsBoolean())hideNativeSearch();
        });
    }

    void onSelection(){ui.post(this::hideNativeSearch);}

    private void hideNativeSearch(){
        if(!shouldHide.getAsBoolean()||root==null)return;
        int[] inspected={0};
        int hidden=hideTree(root,inspected);
        if(hidden>0&&hidden!=lastHidden){
            log.accept("NATIVE_SEARCH_CHROME","hidden="+hidden);
            lastHidden=hidden;
        }
    }

    private int hideTree(View v,int[] scanned){
        if(v==null||++scanned[0]>320)return 0;
        String name=v.getClass().getName();
        // Preserve Google's text-action surface until YFloat has received a
        // real text/region callback. Otherwise a structurally matching but
        // inactive Hook could remove the ONLY selectable menu from the user.
        if((ACTION_MENU.equals(name)&&selectionVerified.getAsBoolean())
                ||INFO_PANEL.equals(name)||OMNIBOX.equals(name)){
            if(!originals.containsKey(v))originals.put(v,v.getVisibility());
            if(v.getVisibility()!=View.GONE)v.setVisibility(View.GONE);
            return 1;
        }
        int count=0;
        if(v instanceof ViewGroup group){
            for(int i=0;i<group.getChildCount()&&scanned[0]<=320;i++)
                count+=hideTree(group.getChildAt(i),scanned);
        }
        return count;
    }

    void reset(){ui.post(this::detachInternal);}

    private void detachInternal(){
        View old=root;
        ViewTreeObserver.OnPreDrawListener listener=predraw;
        root=null;predraw=null;lastHidden=0;
        if(old!=null&&listener!=null) {
            try{
                ViewTreeObserver observer=old.getViewTreeObserver();
                if(observer.isAlive())observer.removeOnPreDrawListener(listener);
            }catch(Throwable ignored){}
        }
        for(Map.Entry<View,Integer> e:originals.entrySet()) {
            try{
                if(e.getKey()!=null)e.getKey().setVisibility(e.getValue());
            }catch(Throwable ignored){}
        }
        originals.clear();
    }
}
