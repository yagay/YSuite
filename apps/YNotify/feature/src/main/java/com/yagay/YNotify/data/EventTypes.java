package com.yagay.YNotify.data;

public final class EventTypes {
    private EventTypes() {}
    public static final String NOTIFICATION = "notification";
    public static final String TOAST = "toast";
    public static final String DIALOG = "dialog";
    public static final String SNACKBAR = "snackbar";
    public static final String POPUP = "popup";
    public static final String SYSTEM_UI = "system_ui";
    public static final String OTHER_UI = "other_ui";
    public static final String CUSTOM_TOAST_MARKER = "__YNOTIFY_CUSTOM_TOAST__";
    public static final String EMPTY_UI_MARKER = "__YNOTIFY_EMPTY_UI__";
    public static final String CLIPBOARD_SHOWN_MARKER = "__YNOTIFY_CLIPBOARD_SHOWN__";
    public static final String CLIPBOARD_SUPPRESSED_MARKER = "__YNOTIFY_CLIPBOARD_SUPPRESSED__";
}
