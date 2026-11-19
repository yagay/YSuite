package com.yagay.YNotify.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.yagay.YNotify.R;
import com.yagay.YNotify.data.EventFilters;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.NotificationFilterCatalog;
import com.yagay.yui.YViewFilterBar;
import com.yagay.yui.YViewLayout;
import java.util.List;

/**
 * Shared, progressive notification filter UI for timeline and per-app history.
 * Seven primary chips. The 27 detailed kinds, presentation and state remain in
 * one collapsible YUI section instead of crowding both history screens.
 */
final class NotificationFilterPanel {
    static final class Selection {
        final NotificationFilterCatalog.Group group;
        final NotificationFilterCatalog.Kind detail;
        final String presentation;
        final NotificationFilterCatalog.Status status;

        Selection(NotificationFilterCatalog.Group group,
                  NotificationFilterCatalog.Kind detail,String presentation,
                  NotificationFilterCatalog.Status status) {
            this.group=group;this.detail=detail;
            this.presentation=presentation;this.status=status;
        }
        boolean matches(EventRecord record) {
            return EventFilters.matchesNotification(record,group,detail,presentation,status);
        }
    }

    final LinearLayout view;
    private final Context context;
    private final Runnable onChange;
    private final YViewFilterBar primary;
    private final MaterialButton advancedToggle;
    private final LinearLayout advanced;
    private NotificationFilterCatalog.Group group=NotificationFilterCatalog.Group.ALL;
    private NotificationFilterCatalog.Kind detail=NotificationFilterCatalog.Kind.ALL;
    private String presentation=EventFilters.NOTIFICATIONS;
    private NotificationFilterCatalog.Status status=NotificationFilterCatalog.Status.ANY;
    private boolean expanded=false;
    private boolean building=false;

    NotificationFilterPanel(Context context,Runnable onChange) {
        this.context=context;
        this.onChange=onChange;
        view=new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        primary=YViewLayout.filterBar(context,
                NotificationFilterCatalog.groupLabels(context),0);
        view.addView(primary.view);
        advancedToggle=YViewLayout.secondaryButton(context,
                context.getString(R.string.ynotify_filter_advanced));
        view.addView(advancedToggle);
        advanced=new LinearLayout(context);
        advanced.setOrientation(LinearLayout.VERTICAL);
        view.addView(advanced);
        advanced.setVisibility(View.GONE);
        advancedToggle.setOnClickListener(v -> {
            expanded=!expanded;
            advanced.setVisibility(expanded?View.VISIBLE:View.GONE);
            updateToggleLabel();
        });
        primary.group.setOnCheckedStateChangeListener((chipGroup,ids) -> {
            if(ids.isEmpty())return;
            NotificationFilterCatalog.Group chosen=NotificationFilterCatalog.groupAt(
                    primary.indexForId(ids.get(0)));
            if(chosen==group)return;
            group=chosen;
            // A new primary category must never silently inherit an incompatible
            // hidden advanced filter and produce an apparently empty list.
            detail=NotificationFilterCatalog.Kind.ALL;
            presentation=EventFilters.NOTIFICATIONS;
            status=NotificationFilterCatalog.Status.ANY;
            rebuildAdvanced();
            dispatch();
        });
        rebuildAdvanced();
    }

    Selection snapshot() {
        return new Selection(group,detail,presentation,status);
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible?View.VISIBLE:View.GONE);
    }

    private void rebuildAdvanced() {
        building=true;
        advanced.removeAllViews();
        List<NotificationFilterCatalog.Kind> allowed=NotificationFilterCatalog.kindsFor(group);
        if(!allowed.contains(detail))detail=NotificationFilterCatalog.Kind.ALL;
        advanced.addView(YViewLayout.spinnerSetting(context,
                context.getString(R.string.ynotify_filter_detail_kind),
                NotificationFilterCatalog.kindLabels(context,allowed),
                allowed.indexOf(detail),true,index -> {
                    if(building || index<0 || index>=allowed.size())return;
                    NotificationFilterCatalog.Kind next=allowed.get(index);
                    if(next!=detail) {
                        detail=next;
                        dispatch();
                    }
                }));
        String[] presentationNames={
                context.getString(R.string.ynotify_filter_surface_any),
                context.getString(R.string.ynotify_filter_heads_up),
                context.getString(R.string.ynotify_filter_bubble),
                context.getString(R.string.ynotify_filter_full_screen)
        };
        String[] presentationIds={
                EventFilters.NOTIFICATIONS,EventFilters.HEADS_UP,
                EventFilters.BUBBLE,EventFilters.FULL_SCREEN
        };
        int presentationIndex=0;
        for(int i=0;i<presentationIds.length;i++)
            if(presentationIds[i].equals(presentation))presentationIndex=i;
        advanced.addView(YViewLayout.spinnerSetting(context,
                context.getString(R.string.ynotify_filter_surface_section),
                presentationNames,presentationIndex,true,index -> {
                    if(building || index<0 || index>=presentationIds.length)return;
                    String next=presentationIds[index];
                    if(!next.equals(presentation)) {
                        presentation=next;
                        dispatch();
                    }
                }));
        NotificationFilterCatalog.Status[] states=NotificationFilterCatalog.Status.values();
        advanced.addView(YViewLayout.spinnerSetting(context,
                context.getString(R.string.ynotify_filter_status),
                NotificationFilterCatalog.statusLabels(context),
                status.ordinal(),true,index -> {
                    if(building || index<0 || index>=states.length)return;
                    NotificationFilterCatalog.Status next=states[index];
                    if(next!=status) {
                        status=next;
                        dispatch();
                    }
                }));
        MaterialButton reset=YViewLayout.secondaryButton(context,
                context.getString(R.string.ynotify_filter_reset));
        reset.setOnClickListener(v -> {
            boolean changed=detail!=NotificationFilterCatalog.Kind.ALL
                    || !EventFilters.NOTIFICATIONS.equals(presentation)
                    || status!=NotificationFilterCatalog.Status.ANY;
            detail=NotificationFilterCatalog.Kind.ALL;
            presentation=EventFilters.NOTIFICATIONS;
            status=NotificationFilterCatalog.Status.ANY;
            rebuildAdvanced();
            if(changed)dispatch();
        });
        advanced.addView(reset);
        building=false;
        updateToggleLabel();
    }

    private void updateToggleLabel() {
        boolean active=detail!=NotificationFilterCatalog.Kind.ALL
                || !EventFilters.NOTIFICATIONS.equals(presentation)
                || status!=NotificationFilterCatalog.Status.ANY;
        String label=context.getString(expanded
                ? R.string.ynotify_filter_collapse
                : R.string.ynotify_filter_advanced);
        advancedToggle.setText(active?label+" •":label);
    }

    private void dispatch() {
        updateToggleLabel();
        if(onChange!=null)onChange.run();
    }
}
