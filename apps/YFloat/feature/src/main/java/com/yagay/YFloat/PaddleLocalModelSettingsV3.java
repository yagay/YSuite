package com.yagay.YFloat;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.yagay.yui.YViewLayout;

/** YUI controls for real local ONNX downloads; no remote image-processing mode. */
final class PaddleLocalModelSettingsV3 {
    static void add(SettingsActivity activity,LinearLayout container){
        addRow(activity,container,PaddleLocalModelsV3.DOC_ORIENTATION);
        addRow(activity,container,PaddleLocalModelsV3.TABLE_STRUCTURE);
        addRow(activity,container,PaddleLocalModelsV3.UV_DOC);
        addRow(activity,container,PaddleLocalModelsV3.SEAL_DETECTION);
    }
    private static void addRow(SettingsActivity activity,LinearLayout parent,int id){
        boolean zh=java.util.Locale.getDefault().getLanguage().startsWith("zh");
        LinearLayout box=YViewLayout.baseRow(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView status=YViewLayout.rowSubtitle(activity,"");
        MaterialButton download=YViewLayout.compactButton(activity,zh?"下载模型":"Download model");
        MaterialButton toggle=YViewLayout.compactButton(activity,zh?"启用":"Enable");
        MaterialButton remove=YViewLayout.compactButton(activity,zh?"删除":"Remove");
        box.addView(status);
        LinearLayout actions=YViewLayout.buttonRow(activity);
        YViewLayout.addAction(actions,download);
        YViewLayout.addAction(actions,toggle);
        YViewLayout.addAction(actions,remove);
        box.addView(actions);
        YViewLayout.addRow(parent,box);
        Runnable refresh=()->{
            boolean ready=PaddleLocalModelsV3.installed(activity,id);
            boolean active=PaddleLocalModelsV3.enabled(activity,id);
            boolean busy=PaddleLocalModelsV3.busy(id);
            status.setText(PaddleLocalModelsV3.name(id)+" — "+
                (ready?(active?(zh?"已启用":"Enabled"):(zh?"已安装":"Installed"))
                     :(zh?"未安装":"Not installed")));
            download.setEnabled(!busy);
            toggle.setEnabled(ready&&!busy);
            remove.setEnabled(ready&&!busy);
            toggle.setText(active?(zh?"停用":"Disable"):(zh?"启用":"Enable"));
        };
        download.setOnClickListener(v->{
            download.setEnabled(false);
            PaddleLocalModelsV3.install(activity,id,new PaddleLocalModelsV3.Callback(){
                @Override public void onProgress(int p){
                    status.setText(PaddleLocalModelsV3.name(id)+" — "+p+"%");
                }
                @Override public void onSuccess(){
                    Toast.makeText(activity,zh?"模型下载完成":"Model installed",Toast.LENGTH_SHORT).show();
                    refresh.run();
                }
                @Override public void onFailure(String why){
                    Toast.makeText(activity,why,Toast.LENGTH_LONG).show();
                    refresh.run();
                }
            });
        });
        toggle.setOnClickListener(v->{
            PaddleLocalModelsV3.setEnabled(activity,id,
                !PaddleLocalModelsV3.enabled(activity,id));
            refresh.run();
        });
        remove.setOnClickListener(v->{
            PaddleLocalModelsV3.delete(activity,id);
            refresh.run();
        });
        refresh.run();
    }
    private PaddleLocalModelSettingsV3(){}
}
