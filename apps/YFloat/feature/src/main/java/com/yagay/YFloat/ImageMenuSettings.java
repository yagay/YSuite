package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Image actions only: appearance, layout and menu count use the shared YFloat/YUI pipeline. */
final class ImageMenuSettings {
    static final String DONE="done", FIND="find", COPY="copy",
            SHARE="share", SAVE="save", RESET="reset";
    private static final String FILE="yfloat_image_menu", ORDER="order_v1", HIDDEN="hidden_v1";
    private static final List<String> ALL=List.of(DONE,FIND,COPY,SHARE,SAVE,RESET);

    static List<String> ordered(Context context) {
        String raw=prefs(context).getString(ORDER,"");
        return normalized(raw==null || raw.isBlank() ? List.of() : Arrays.asList(raw.split(",")));
    }

    static List<String> normalized(List<String> stored) {
        ArrayList<String> out=new ArrayList<>();
        if(stored!=null) for(String id:stored) if(ALL.contains(id)&&!out.contains(id)) out.add(id);
        for(String id:ALL) if(!out.contains(id)) out.add(id);
        return out;
    }

    static boolean isVisible(Context c,String id) {
        return !prefs(c).getStringSet(HIDDEN,Set.of()).contains(id);
    }

    static void setVisible(Context c,String id,boolean enabled) {
        if(!ALL.contains(id))return;
        Set<String> hidden=new HashSet<>(prefs(c).getStringSet(HIDDEN,Set.of()));
        if(enabled)hidden.remove(id);else hidden.add(id);
        prefs(c).edit().putStringSet(HIDDEN,hidden).apply();
    }

    static void move(Context c,String id,int step) {
        List<String> list=ordered(c);
        int from=list.indexOf(id),to=from+step;
        if(from<0||to<0||to>=list.size()||step==0)return;
        list.remove(from);list.add(to,id);
        prefs(c).edit().putString(ORDER,String.join(",",list)).apply();
    }

    static void reset(Context c) {
        prefs(c).edit().remove(ORDER).remove(HIDDEN).apply();
    }

    static List<String> visibleActions(Context c,boolean crop) {
        Set<String> hidden=prefs(c).getStringSet(HIDDEN,Set.of());
        ArrayList<String> out=new ArrayList<>();
        for(String id:ordered(c)) {
            if(!crop&&(DONE.equals(id)||RESET.equals(id)))continue;
            if(!hidden.contains(id))out.add(id);
        }
        if(out.isEmpty())out.add(crop?DONE:COPY);
        return out;
    }

    static int primaryCount(Context c,int total) {
        return primaryCount(total,TextMenuSettings.mainItemCount(c));
    }

    static int primaryCount(int available,int preferred) {
        return Math.min(Math.max(0,available),Math.max(2,preferred));
    }

    static int index(String id){return ALL.indexOf(id);}

    static String label(String id) {
        boolean zh=Locale.getDefault().getLanguage().equals("zh");
        return switch(id) {
            case DONE -> zh?"完成":"Done";
            case FIND -> zh?"识图":"Find";
            case COPY -> zh?"复制":"Copy";
            case SHARE -> zh?"分享":"Share";
            case SAVE -> zh?"保存":"Save";
            case RESET -> zh?"重选":"Reset";
            default -> id;
        };
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    private ImageMenuSettings(){}
}
