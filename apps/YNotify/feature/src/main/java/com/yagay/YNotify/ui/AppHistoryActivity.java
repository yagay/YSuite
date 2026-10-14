package com.yagay.YNotify.ui;

import android.os.Bundle;
import android.view.View;
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
import com.yagay.YNotify.data.EventFilters;
import com.yagay.YNotify.data.NotificationFilterCatalog;
import com.yagay.YNotify.data.NotifyDatabase;
import com.yagay.yui.YView;
import com.yagay.yui.YViewFilterBar;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewDialogs;
import com.yagay.yui.YViewPage;

import java.util.List;

public class AppHistoryActivity extends AppCompatActivity {
    private final EventAdapter adapter = new EventAdapter();
    private LiveData<List<EventRecord>> source;
    private String pkg;
    private String selectedType = EventFilters.ALL;
    private String selectedGroup = EventFilters.ALL;
    private SwitchCompat switchIgnore;
    private SwitchCompat switchRedact;
    private RecyclerView list;
    private YViewFilterBar groupFilters;
    private YViewFilterBar notificationFilters;
    private YViewFilterBar notificationKindFilters;
    private View notificationKindTitle;
    private View notificationSurfaceTitle;
    private NotificationFilterCatalog.Kind selectedNotificationKind = NotificationFilterCatalog.Kind.ALL;
    private String selectedNotificationPresentation = EventFilters.NOTIFICATIONS;
    private YViewFilterBar uiFilters;

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

        groupFilters = YViewLayout.filterBar(this, List.of(
                getString(R.string.ynotify_filter_all),
                getString(R.string.ynotify_filter_notification),
                getString(R.string.ynotify_group_ui_events)), 0);
        notificationKindFilters = YViewLayout.filterBar(this,
                NotificationFilterCatalog.labels(this), 0);
        notificationFilters = YViewLayout.filterBar(this, List.of(
                getString(R.string.ynotify_filter_surface_any),
                getString(R.string.ynotify_filter_heads_up),
                getString(R.string.ynotify_filter_bubble),
                getString(R.string.ynotify_filter_full_screen)), 0);
        uiFilters = YViewLayout.filterBar(this, List.of(
                getString(R.string.ynotify_filter_all_ui_events),
                getString(R.string.ynotify_filter_toast),
                getString(R.string.ynotify_filter_dialog),
                getString(R.string.ynotify_filter_popup),
                getString(R.string.ynotify_filter_snackbar),
                getString(R.string.ynotify_type_system_ui),
                getString(R.string.ynotify_class_other_ui)), 0);
        groupFilters.group.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            selectedGroup = EventFilters.groupAt(groupFilters.indexForId(ids.get(0)));
            selectedType = EventFilters.NOTIFICATIONS.equals(selectedGroup)
                    ? EventFilters.NOTIFICATIONS
                    : EventFilters.selectedTypeForGroup(selectedGroup,0,
                            uiFilters.indexForId(uiFilters.group.getCheckedChipId()));
            updateFilterVisibility();
            observe();
        });
        notificationFilters.group.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty() || !EventFilters.NOTIFICATIONS.equals(selectedGroup)) return;
            selectedNotificationPresentation =
                    EventFilters.notificationAt(notificationFilters.indexForId(ids.get(0)));
            observe();
        });
        notificationKindFilters.group.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty() || !EventFilters.NOTIFICATIONS.equals(selectedGroup)) return;
            selectedNotificationKind = NotificationFilterCatalog.at(
                    notificationKindFilters.indexForId(ids.get(0)));
            observe();
        });
        uiFilters.group.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty() || !EventFilters.UI_EVENTS.equals(selectedGroup)) return;
            selectedType = EventFilters.uiAt(uiFilters.indexForId(ids.get(0)));
            observe();
        });
        LinearLayout filterColumn = new LinearLayout(this);
        filterColumn.setOrientation(LinearLayout.VERTICAL);
        filterColumn.addView(groupFilters.view);
        notificationKindTitle = YViewLayout.sectionNote(this,
                getString(R.string.ynotify_filter_kind_section));
        notificationSurfaceTitle = YViewLayout.sectionNote(this,
                getString(R.string.ynotify_filter_surface_section));
        filterColumn.addView(notificationKindTitle);
        filterColumn.addView(notificationKindFilters.view);
        filterColumn.addView(notificationSurfaceTitle);
        filterColumn.addView(notificationFilters.view);
        filterColumn.addView(uiFilters.view);
        body.addView(filterColumn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        updateFilterVisibility();

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button clear = YViewLayout.secondaryButton(this, getString(R.string.ynotify_clear_app_history));
        clear.setOnClickListener(v -> YViewDialogs.builder(this)
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

    private void updateFilterVisibility() {
        boolean showNotification = EventFilters.NOTIFICATIONS.equals(selectedGroup);
        notificationKindTitle.setVisibility(showNotification
                ? android.view.View.VISIBLE : android.view.View.GONE);
        notificationKindFilters.view.setVisibility(showNotification
                ? android.view.View.VISIBLE : android.view.View.GONE);
        notificationSurfaceTitle.setVisibility(showNotification
                ? android.view.View.VISIBLE : android.view.View.GONE);
        notificationFilters.view.setVisibility(showNotification
                ? android.view.View.VISIBLE : android.view.View.GONE);
        uiFilters.view.setVisibility(
                EventFilters.UI_EVENTS.equals(selectedGroup)
                        ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void observe() {
        if (source != null) source.removeObservers(this);
        var dao = NotifyDatabase.get(this).eventDao();
        source = switch (selectedType) {
            case EventFilters.ALL -> dao.observePackage(pkg);
            case EventFilters.NOTIFICATIONS -> dao.observePackageNotifications(pkg);
            case EventFilters.UI_EVENTS -> dao.observePackageUiEvents(pkg);
            case EventFilters.HEADS_UP -> dao.observePackageHeadsUp(pkg);
            case EventFilters.BUBBLE -> dao.observePackageBubbles(pkg);
            case EventFilters.FULL_SCREEN -> dao.observePackageFullScreens(pkg);
            case EventTypes.NOTIFICATION -> dao.observePackageNotifications(pkg);
            default -> dao.observePackageType(pkg, selectedType);
        };
        final String filter = selectedType;
        final NotificationFilterCatalog.Kind kind = selectedNotificationKind;
        final String presentation = selectedNotificationPresentation;
        source.observe(this, rows -> {
            if (!EventFilters.NOTIFICATIONS.equals(filter)) {
                adapter.submit(rows);
                return;
            }
            java.util.ArrayList<EventRecord> filtered = new java.util.ArrayList<>();
            if (rows != null) for (EventRecord record : rows) {
                if (EventFilters.matchesNotification(record,kind,presentation))
                    filtered.add(record);
            }
            adapter.submit(filtered);
        });
    }
}
