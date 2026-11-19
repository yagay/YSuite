package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** External action bridges. Other apps retain ownership of their windows and state. */
final class ExternalGestureActionBridge {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String ATLAS = "uk.wort.atlas";
    private static final String NEXTEP = "io.github.lujinxin.nextep";

    static void openSuite(Context context) {
        launch(context, "com.yagay.YSuite");
    }

    static void openAtlasPanel(Context context) {
        if (!installed(context, ATLAS)) {
            unavailable(context);
            return;
        }
        try {
            Intent open = new Intent("uk.wort.atlas.overlay.OPEN_PANEL");
            open.setClassName(ATLAS, "uk.wort.atlas.overlay.AtlasExternalActionReceiver");
            context.sendBroadcast(open);
            DiagnosticLog.i(context, "EXTERNAL_ACTION", "atlas open panel requested");
        } catch (Exception e) {
            DiagnosticLog.i(context, "EXTERNAL_ACTION", "atlas failed " + e);
            unavailable(context);
        }
    }

    static void toggleNeXtep(Context context) {
        if (!installed(context, NEXTEP)) {
            unavailable(context);
            return;
        }
        IO.execute(() -> {
            RootCommandExecutor.Result state =
                    RootCommandExecutor.runText("settings get global nextep_workspace_active_v1", 3, 1024);
            if (!state.success()) {
                failed(context, "NeXtep state", state);
                return;
            }
            String current = state.text();
            if (!"0".equals(current) && !"1".equals(current) && !"null".equals(current)) {
                DiagnosticLog.i(context, "EXTERNAL_ACTION", "NeXtep unexpected state=" + current);
                unavailable(context);
                return;
            }
            String request = "1".equals(current) ? "false" : "true";
            String command = "am broadcast -n "
                    + "io.github.lujinxin.nextep/.trigger.WorkspaceControlBrokerReceiver"
                    + " -a io.github.lujinxin.nextep.action.HOST_SET_REQUEST"
                    + " --ez io.github.lujinxin.nextep.extra.REQUESTED_ACTIVE " + request;
            RootCommandExecutor.Result result = RootCommandExecutor.runText(command, 5, 2048);
            if (!result.success()) {
                failed(context, "NeXtep toggle", result);
            } else {
                DiagnosticLog.i(context, "EXTERNAL_ACTION", "NeXtep requestedActive=" + request);
            }
        });
    }

    /** Trigger Google Circle to Search through the official MiCTS launcher entry. */
    static void googleCircle(Context context) {
        final String pkg = "com.parallelc.micts";
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(pkg, "com.parallelc.micts.ui.activity.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                DiagnosticLog.i(context, "EXTERNAL_ACTION", "MiCTS launcher unavailable");
                unavailable(context);
                return;
            }
            context.startActivity(intent);
            DiagnosticLog.i(context, "EXTERNAL_ACTION", "MiCTS Circle to Search requested");
        } catch (Exception e) {
            DiagnosticLog.i(context, "EXTERNAL_ACTION",
                    "MiCTS launch failed=" + e.getClass().getSimpleName());
            unavailable(context);
        }
    }

    private static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static void launch(Context c, String pkg) {
        try {
            Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) { unavailable(c); return; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception e) {
            DiagnosticLog.i(c, "EXTERNAL_ACTION", "launch failed pkg=" + pkg + " " + e);
            unavailable(c);
        }
    }

    private static void failed(Context context, String action, RootCommandExecutor.Result result) {
        DiagnosticLog.i(context, "EXTERNAL_ACTION", action + " failed="
                + result.failureMessage("timeout"));
        unavailable(context);
    }

    private static void unavailable(Context context) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> Toast.makeText(context.getApplicationContext(),
                        R.string.yfloat_external_action_unavailable, Toast.LENGTH_SHORT).show());
    }

    private ExternalGestureActionBridge() {}
}
