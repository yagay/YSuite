package com.yagay.YNotify.ui;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.YNotify.R;
import com.yagay.YNotify.data.CapturePolicy;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.NotifyDatabase;
import com.yagay.yui.YView;
import com.yagay.yui.YViewFilterBar;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewPage;

import java.util.List;

public class AppHistoryActivity extends AppCompatActivity {
    private final EventAdapter adapter = new EventAdapter();
    private LiveData<List<EventRecord>> source;
    private String pkg;
    private String selectedType = "all";
    private SwitchCompat switchIgnore;
    private SwitchCompat switchRedact;
    private RecyclerView list;
    private YViewFilterBar filters;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        pkg = getIntent().getStringExtra("package");
        String label = getIntent().getStringExtra("label");
        if (pkg == null) { finish(); return; }

        YViewPage page = YViewLayout.installPage(this, label == null ? pkg : label, pkg);
        page.toolbar.setNavigationIcon(R.drawable.ic_back);
        page.toolbar.setNavigationContentDescription(R.string.ynotify_back);
        page.toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout body = YViewLayout.contentColumn(this, true);
        page.content.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        switchIgnore = YViewLayout.switchRow(
                body,
                getString(R.string.ynotify_pause_app),
                null,
                CapturePolicy.isIgnored(this, pkg),
                (button, checked) -> {
                    CapturePolicy.setIgnored(this, pkg, checked);
                    switchRedact.setEnabled(!checked);
                });
        switchRedact = YViewLayout.switchRow(
                body,
                getString(R.string.ynotify_metadata_only),
                null,
                CapturePolicy.isRedacted(this, pkg),
                (button, checked) -> CapturePolicy.setRedacted(this, pkg, checked));
        switchRedact.setEnabled(!switchIgnore.isChecked());

        filters = YViewLayout.filterBar(
                this,
                List.of(
                        getString(R.string.ynotify_filter_all),
                        getString(R.string.ynotify_filter_notification),
                        getString(R.string.ynotify_filter_toast),
                        getString(R.string.ynotify_filter_dialog),
                        getString(R.string.ynotify_filter_popup),
                        getString(R.string.ynotify_filter_snackbar)),
                0);
        filters.group.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                selectedType = typeForIndex(filters.indexForId(ids.get(0)));
                observe();
            }
        });
        body.addView(filters.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button clear = YViewLayout.secondaryButton(this, getString(R.string.ynotify_clear_app_history));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.ynotify_clear_app_confirm)
                .setMessage(pkg)
                .setNegativeButton(R.string.ynotify_cancel, null)
                .setPositiveButton(R.string.ynotify_clear, (d, w) -> EventStore.deletePackage(this, pkg))
                .show());
        body.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        observe();
    }

    private void observe() {
        if (source != null) source.removeObservers(this);
        source = "all".equals(selectedType)
                ? NotifyDatabase.get(this).eventDao().observePackage(pkg)
                : NotifyDatabase.get(this).eventDao().observePackageType(pkg, selectedType);
        source.observe(this, adapter::submit);
    }

    private String typeForIndex(int index) {
        return switch (index) {
            case 1 -> EventTypes.NOTIFICATION;
            case 2 -> EventTypes.TOAST;
            case 3 -> EventTypes.DIALOG;
            case 4 -> EventTypes.POPUP;
            case 5 -> EventTypes.SNACKBAR;
            default -> "all";
        };
    }
}
