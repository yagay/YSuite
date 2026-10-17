package com.yagay.YFloat;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.yagay.yui.YViewLayout;

/** YUI-only controls for the separately installed PP-StructureV3 layout model. */
final class PaddleStructureSettingsV2 {
    static void add(SettingsActivity activity,LinearLayout parent){
        boolean zh=java.util.Locale.getDefault().getLanguage().startsWith("zh");
        TextView status=YViewLayout.rowSubtitle(activity,"");
        MaterialButton install=YViewLayout.compactButton(activity,
                zh?"下载版面模型":"Download layout model");
        MaterialButton remove=YViewLayout.compactButton(activity,
                zh?"删除":"Remove");
        MaterialButton enable=YViewLayout.compactButton(activity,
                zh?"启用版面分析":"Enable layout analysis");
        LinearLayout row=YViewLayout.baseRow(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(status);
        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        YViewLayout.addAction(actions,install);
        YViewLayout.addAction(actions,remove);
        row.addView(actions);
        row.addView(enable);
        YViewLayout.addRow(parent,row);
        Runnable refresh=()->{
            boolean ready=PaddleStructureModelV2.isReady(activity);
            boolean active=PaddleStructureModelV2.enabled(activity);
            status.setText("PP-StructureV3 / PP-DocLayout-S (23 layouts) — "+
                    (ready?(active?(zh?"已启用":"Enabled"):(zh?"已安装":"Installed"))
                        :(zh?"未安装":"Not installed")));
            install.setEnabled(!PaddleStructureModelV2.isDownloading());
            remove.setEnabled(ready&&!PaddleStructureModelV2.isDownloading());
            enable.setEnabled(ready&&!PaddleStructureModelV2.isDownloading());
            enable.setText(active?(zh?"关闭版面分析":"Disable layout analysis")
                :(zh?"启用版面分析":"Enable layout analysis"));
        };
        install.setOnClickListener(v->{
            install.setEnabled(false);
            PaddleStructureModelV2.download(activity,new PaddleStructureModelV2.Callback(){
                @Override public void onProgress(int percent){
                    status.setText("PP-DocLayout-S "+percent+"%");
                }
                @Override public void onSuccess(){
                    Toast.makeText(activity,zh?"版面模型下载完成":"Layout model installed",
                            Toast.LENGTH_SHORT).show();
                    refresh.run();
                }
                @Override public void onFailure(String reason){
                    Toast.makeText(activity,reason,Toast.LENGTH_LONG).show();
                    refresh.run();
                }
            });
        });
        remove.setOnClickListener(v->{PaddleStructureModelV2.delete(activity);refresh.run();});
        enable.setOnClickListener(v->{
            PaddleStructureModelV2.setEnabled(activity,
                    !PaddleStructureModelV2.enabled(activity));
            refresh.run();
        });
        refresh.run();
    }
    private PaddleStructureSettingsV2(){}
}
