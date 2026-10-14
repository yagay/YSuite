package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Additional image receiver activities, using the same selection menu UI as text. */
final class ImageCustomMenuStore {
    static final String SEND="send", VIEW="view";
    private static final String PREFS="yfloat_image_custom_actions";
    private static final String KEY="items_v1", PREFIX="imageapp:";
    private static final int MAX_ITEMS=128;

    static final class Item {
        final String id, label, packageName, className, mode;
        Item(String id,String label,String packageName,String className,String mode){
            this.id=id;this.label=label;this.packageName=packageName;
            this.className=className;this.mode=mode;
        }
        String key(){return mode+"|"+packageName+"|"+className;}
    }

    static boolean isCustom(String id){return id!=null&&id.startsWith(PREFIX);}
    static boolean supportedMode(String mode){return SEND.equals(mode)||VIEW.equals(mode);}

    static List<Item> load(Context c){
        ArrayList<Item> out=new ArrayList<>();
        if(c==null)return out;
        Set<String> seen=new HashSet<>();
        try {
            JSONArray array=new JSONArray(prefs(c).getString(KEY,"[]"));
            for(int i=0;i<array.length() && out.size()<MAX_ITEMS;i++){
                JSONObject o=array.optJSONObject(i);
                if(o==null)continue;
                String id=o.optString("id","");
                String label=o.optString("label","");
                String pkg=o.optString("package","");
                String cls=o.optString("class","");
                String mode=o.optString("mode","");
                if(!isCustom(id)||!seen.add(id)||!supportedMode(mode)
                        ||pkg.isBlank()||cls.isBlank())continue;
                out.add(new Item(id,label.isBlank()?pkg:label,pkg,cls,mode));
            }
        }catch(Throwable t){
            DiagnosticLog.i(c,"IMAGE_CUSTOM_MENU","load failed="+t);
        }
        return out;
    }

    static Item find(Context c,String id){
        for(Item item:load(c))if(item.id.equals(id))return item;
        return null;
    }

    static boolean add(Context c,String label,String pkg,String cls,String mode){
        if(c==null||pkg==null||pkg.isBlank()||cls==null||cls.isBlank()
                ||!supportedMode(mode))return false;
        List<Item> items=load(c);
        if(items.size()>=MAX_ITEMS)return false;
        String key=mode+"|"+pkg+"|"+cls;
        for(Item item:items)if(item.key().equals(key))return false;
        items.add(new Item(PREFIX+UUID.randomUUID(),
                label==null||label.isBlank()?pkg:label,pkg,cls,mode));
        return save(c,items);
    }

    static boolean rename(Context c,String id,String label){
        if(c==null||label==null||label.isBlank())return false;
        List<Item> items=load(c);
        for(int i=0;i<items.size();i++){
            Item item=items.get(i);
            if(item.id.equals(id)){
                items.set(i,new Item(id,label.trim(),item.packageName,item.className,item.mode));
                return save(c,items);
            }
        }
        return false;
    }

    static boolean remove(Context c,String id){
        if(c==null||id==null)return false;
        List<Item> items=load(c);
        if(!items.removeIf(item->item.id.equals(id)))return false;
        return save(c,items);
    }

    static boolean launch(Context c,String id,Bitmap image){
        Item item=find(c,id);
        return item!=null&&ImageShareUtils.openExplicit(c,image,item.packageName,item.className,
                VIEW.equals(item.mode));
    }

    private static boolean save(Context c,List<Item> items){
        try {
            JSONArray array=new JSONArray();
            for(Item item:items){
                JSONObject o=new JSONObject();
                o.put("id",item.id);
                o.put("label",item.label);
                o.put("package",item.packageName);
                o.put("class",item.className);
                o.put("mode",item.mode);
                array.put(o);
            }
            return prefs(c).edit().putString(KEY,array.toString()).commit();
        }catch(Throwable t){
            DiagnosticLog.i(c,"IMAGE_CUSTOM_MENU","save failed="+t);
            return false;
        }
    }

    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private ImageCustomMenuStore(){}
}
