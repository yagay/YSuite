#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

YUI_COMPOSE = ROOT / "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt"
YUI_VIEW = ROOT / "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt"
YUI_RES = ROOT / "libs/yui/src/main/res/values/yui.xml"
YSUITE = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt"
YPOWER = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"
YMINIGUARD = ROOT / "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java"
YNOTIFY = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/MainActivity.java"
YENTRY = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainActivity.kt"
YPARAM = ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/ui/MainActivity.java"
YNFC_SCREEN = ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcAppScreen.kt"
YNFC_COMPONENTS = ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcComponents.kt"
YTASK = ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"
YDIAG = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"

# Secondary/detail screens that have been migrated to the shared contract.
YPARAM_DETAIL = ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/ui/AppDetailActivity.java"
YPOWER_DETAIL = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/AppDetailActivity.java"
YPOWER_RECOMMENDED = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/RecommendedAppsActivity.java"
YPOWER_DIAG = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/DiagnosticActivity.java"
YNOTIFY_HISTORY = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/AppHistoryActivity.java"
YNOTIFY_DETAIL = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventDetailActivity.java"
YENTRY_SCOPE = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/ScopeScreen.kt"
YENTRY_ROOT = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/RootComponentsScreen.kt"
YENTRY_TABS = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainTabs.kt"
YENTRY_PRIORITY = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/PriorityDialog.kt"
YENTRY_ROWS = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/AppListRows.kt"


def fail(message: str) -> None:
    print(f"ui-framework: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required UI framework file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(source: str, marker: str, label: str) -> None:
    if marker not in source:
        fail(f"{label} must contain {marker!r}")


def reject_hardcoded_normal_screen_colors(source: str, label: str) -> None:
    matches = sorted(set(re.findall(r"0x(?:FF|ff)[0-9A-Fa-f]{6}", source)))
    if matches:
        fail(f"{label} reintroduced hard-coded ARGB colors: {matches[:8]}")



def verify_runtime_appearance() -> None:
    """A token facade is insufficient unless real host and feature screens consume it."""
    targets = {
        "theme": "libs/yui/src/main/java/com/yagay/yui/YTheme.kt",
        "view": "libs/yui/src/main/java/com/yagay/yui/YView.kt",
        "view_layout": "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt",
        "runtime": "libs/yui/src/main/java/com/yagay/yui/YUiInitializer.kt",
        "application": "next/core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteApplication.kt",
        "float_result": "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ResultUi.java",
        "float_menu": "apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatingMenuUi.java",
        "notify_apps": "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/AppAdapter.java",
        "notify_events": "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventAdapter.java",
        "entry_controls": "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainControls.kt",
        "entry_components": "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/RootComponentsScreen.kt",
        "entry_priority": "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/PriorityDialog.kt",
        "download_dialog": "next/core/productui/src/main/java/com/yagay/ysuite/productui/download/QdmDownloadComponents.kt",
        "file_rows": "next/core/productui/src/main/java/com/yagay/ysuite/productui/filemanager/YFileManagerComponents.kt",
        "host_home": "suite/YSuite/src/main/java/com/yagay/YSuite/CompactSuiteHome.kt",
    }
    sources = {key: text(ROOT / path) for key, path in targets.items()}
    assertions = {
        "theme": ("val actualDynamic = appearance.dynamicColor", "appearance.cardRadiusDp.dp", "appearance.dialogRadiusDp.dp"),
        "view": ("dynamicDarkColorScheme(context)", "yAccentColorScheme(base, setting.accent, dark)",
                 "appearance(context).fieldRadiusDp", "applyFontScale(view)", "fun rowHeight(context",
                 "button.backgroundTintList = ColorStateList.valueOf("),
        "view_layout": ("private fun sectionGap(context: Context): Int = YView.sectionGap(context)",
                        "private fun controlGap(context: Context): Int = YView.controlGap(context)"),
        "runtime": ("val dark = YView.isDark(activity)", "activity.recreate()"),
        "application": ("YSuiteRoot {",),
        "float_result": ("YView.styleSecondaryButton(b)", "YView.dialogRadius(c)"),
        "float_menu": ("YView.fontPercent(c)", "YView.controlGap(c)"),
        "notify_apps": ("YView.rowHeight(c)", "YView.styleItemTitle(h.b.appName)"),
        "notify_events": ("YView.cardRadius(c)", "YView.surfaceContainer(c)"),
        "entry_controls": ("LocalYAppearance.current.fieldRadiusDp", "LocalYAppearance.current.rowHeightDp"),
        "entry_components": ("LocalYAppearance.current.buttonPaddingHorizontalDp",),
        "entry_priority": ("LocalYAppearance.current.rowHeightDp",),
        "download_dialog": ("LocalYAppearance.current.dialogRadiusDp.dp",),
        "file_rows": ("LocalYAppearance.current.rowHeightDp.dp",),
        "host_home": ("appearance.cardRadiusDp.dp", "appearance.rowHeightDp.dp"),
    }
    for key, markers in assertions.items():
        for marker in markers:
            require(sources[key], marker, f"runtime appearance: {key}")
    if "dynamicColor || appearance.dynamicColor" in sources["theme"]:
        fail("a deprecated dynamic-color argument is overriding user appearance preferences")
    if "darkTheme = darkTheme" in sources["application"]:
        fail("legacy AppSettings must not override the host appearance settings")
    print("ui-framework: OK unified appearance applied to Compose, View, XML, overlays, and rebuilt product screens")


def main() -> None:
    verify_runtime_appearance()
    compose = text(YUI_COMPOSE)
    view = text(YUI_VIEW)
    resources = text(YUI_RES)
    ysuite = text(YSUITE)
    ypower = text(YPOWER)
    yminiguard = text(YMINIGUARD)
    ynotify = text(YNOTIFY)
    yentry = text(YENTRY)
    yparam = text(YPARAM)
    ynfc_screen = text(YNFC_SCREEN)
    ynfc_components = text(YNFC_COMPONENTS)
    ytask = text(YTASK)
    ydiag = text(YDIAG)
    yparam_detail = text(YPARAM_DETAIL)
    ypower_detail = text(YPOWER_DETAIL)
    ypower_recommended = text(YPOWER_RECOMMENDED)
    ypower_diag = text(YPOWER_DIAG)
    ynotify_history = text(YNOTIFY_HISTORY)
    ynotify_detail = text(YNOTIFY_DETAIL)
    yentry_scope = text(YENTRY_SCOPE)
    yentry_root = text(YENTRY_ROOT)
    yentry_tabs = text(YENTRY_TABS)
    yentry_priority = text(YENTRY_PRIORITY)
    yentry_rows = text(YENTRY_ROWS)

    for marker in (
        "fun YPageScaffold(",
        "fun YSectionHeader(",
        "fun YSection(",
        "fun YEmptyMessage(",
        "fun YMetricCard(",
        "fun YStatusLine(",
        "fun YSwitchItem(",
        "fun YActionGroup(",
        "fun YHorizontalActions(",
    ):
        require(compose, marker, "Compose YUI framework")

    for marker in (
        "fun install(",
        "fun installPage(",
        "fun page(",
        "fun installFixed(",
        "fun filterBar(",
        "fun bottomNavigation(",
        "fun sectionHeader(",
        "fun card(",
        "fun statusLine(",
        "fun emptyState(",
        "fun keyValueRow(",
        "fun switchRow(",
        "fun searchField(",
        "fun listRow(",
        "fun navigationRow(",
    ):
        require(view, marker, "View YUI framework")

    for marker in (
        'name="Widget.YUI.Button.Tonal"',
        'name="Widget.YUI.Button.Outlined"',
        'name="Widget.YUI.TextInput"',
        'name="TextAppearance.YUI.SectionTitle"',
        'name="TextAppearance.YUI.Body"',
        'name="TextAppearance.YUI.Caption"',
    ):
        require(resources, marker, "XML YUI framework")

    require(ysuite, "YDashboardScaffold(", "YSuite main screen")
    require(ysuite, "YPageList(", "YSuite main screen")
    require(ysuite, "YSection(", "YSuite main screen")
    require(ypower, "YViewLayout.install(", "YPower main screen")
    require(ypower, "YViewLayout.card(", "YPower main screen")
    require(yminiguard, "YViewLayout.install(", "YMiniGuard main screen")
    require(yminiguard, "YViewLayout.switchRow(", "YMiniGuard main screen")
    require(yminiguard, "YViewLayout.navigationRow(", "YMiniGuard app-list navigation")
    require(yentry, "YCustomScaffold(", "YEntryCleaner main screen")
    require(yentry, "YAppShell(", "YEntryCleaner main navigation")
    require(yparam, "YViewLayout.installFixed(", "YParam main screen")
    require(yparam, "YViewLayout.searchField(", "YParam main screen")
    require(yparam, "YViewLayout.switchRow(", "YParam main screen")
    require(yparam, "YViewLayout.setStatus(", "YParam main screen")

    require(ynfc_screen, "YManagerScaffold(", "YNFC main screen")
    require(ynfc_screen, "YPageList(", "YNFC main screen")
    require(ynfc_screen, "YEmptyMessage(", "YNFC main screen")
    require(ynfc_components, "YSection(", "YNFC content components")
    require(ynfc_components, "YStatusLine(", "YNFC content components")

    require(ytask, "YManagerScaffold(", "YTaskManager main screen")
    require(ytask, "YAppShell(", "YTaskManager main navigation")
    require(ytask, "YSearchField(", "YTaskManager filter section")
    require(ytask, "YStatusStrip(", "YTaskManager status section")
    require(ytask, "YToggleFilterBar(", "YTaskManager type filters")
    require(ytask, "YFilterBar(", "YTaskManager sort filter")
    require(ytask, "YListItem(", "YTaskManager process rows")
    require(ytask, "YPageList(", "YTaskManager resource/network pages")
    require(ytask, "YEmptyMessage(", "YTaskManager empty states")
    require(ytask, "YStatusLine(", "YTaskManager resource metrics")

    require(ydiag, "YComposeActivity", "YDiag activity")
    require(ydiag, "YPageScaffold(", "YDiag main screen")
    require(ydiag, "YAppShell(", "YDiag main navigation")
    require(ydiag, "YPageList(", "YDiag page bodies")
    require(ydiag, "YSectionHeader(", "YDiag sections")
    require(ydiag, "YListItem(", "YDiag list rows")
    require(ydiag, "YFilterBar(", "YDiag filters")
    require(ydiag, "YCheckboxItem(", "YDiag app picker rows")
    require(ydiag, "YMetricCard(", "YDiag monitor stats")
    require(ydiag, "YEmptyMessage(", "YDiag empty states")
    require(ydiag, "YSearchField(", "YDiag app picker")

    require(ynotify, "YViewLayout.installPage(", "YNotify main screen")
    require(ynotify, "YViewLayout.filterBar(", "YNotify timeline filters")
    require(ynotify, "YViewLayout.bottomNavigation(", "YNotify main navigation")
    require(ynotify, "YViewLayout.section(", "YNotify settings sections")

    # Secondary/detail screens.
    for source, label in (
        (yparam_detail, "YParam AppDetail"),
        (ypower_detail, "YPower AppDetail"),
        (ypower_recommended, "YPower RecommendedApps"),
        (ypower_diag, "YPower Diagnostic"),
    ):
        require(source, "YViewLayout.", label)
    require(yparam_detail, "YViewLayout.sectionHeader(", "YParam AppDetail")
    require(yparam_detail, "YViewLayout.setStatus(", "YParam AppDetail")
    require(ypower_detail, "YViewLayout.sectionHeader(", "YPower AppDetail")
    require(ypower_detail, "YViewLayout.checkBoxRow(", "YPower AppDetail toggles")
    require(ypower_recommended, "YViewLayout.emptyState(", "YPower RecommendedApps")
    require(ypower_diag, "YViewLayout.install(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.card(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.actionRow(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.setStatus(", "YPower Diagnostic")

    require(ynotify_history, "YViewLayout.installPage(", "YNotify AppHistory")
    require(ynotify_history, "YViewLayout.switchRow(", "YNotify AppHistory settings")
    require(ynotify_history, "YViewLayout.filterBar(", "YNotify AppHistory filters")
    require(ynotify_detail, "YViewLayout.installPage(", "YNotify EventDetail")
    require(ynotify_detail, "YViewLayout.sectionHeader(", "YNotify EventDetail sections")

    require(yentry_scope, "YFullScreenDialog(", "YEntryCleaner scope screen")
    require(yentry_scope, "YSectionHeader(", "YEntryCleaner scope screen")
    require(yentry_scope, "YListItem(", "YEntryCleaner scope screen")
    require(yentry_scope, "YStatusItem(", "YEntryCleaner scope screen")
    require(yentry_scope, "YNotice(", "YEntryCleaner scope screen")
    require(yentry_root, "YSectionHeader(", "YEntryCleaner component screen")
    require(yentry_root, "YSection(", "YEntryCleaner component screen")
    require(yentry_root, "YStatusLine(", "YEntryCleaner component screen")
    require(yentry_root, "YEmptyMessage(", "YEntryCleaner component screen")

    # Rules/Priority keep their specialized interaction surfaces, but normal summary/status/empty
    # presentation must stay on YUI. The interaction markers below intentionally protect drag,
    # lock swipe, category filtering and search from being lost during visual refactors.
    for source, label in (
        (yentry_tabs, "YEntryCleaner RulesTab"),
        (yentry_priority, "YEntryCleaner PriorityDialog"),
    ):
        require(source, "YSection(", label)
        require(source, "YStatusLine(", label)
        require(source, "YEmptyMessage(", label)
    require(yentry_tabs, "AppRow(", "YEntryCleaner RulesTab app rows")
    require(yentry_tabs, "ListControls(", "YEntryCleaner RulesTab filters")
    require(yentry_rows, "bulkLockSwipe", "YEntryCleaner RulesTab lock interaction")
    require(yentry_priority, "detectDragGesturesAfterLongPress", "YEntryCleaner Priority drag ordering")
    require(yentry_priority, "bulkLockSwipe", "YEntryCleaner Priority lock interaction")
    require(yentry_priority, "ListControls(", "YEntryCleaner Priority filters")
    require(yentry_priority, "movePriority(kind, packageName", "YEntryCleaner Priority move buttons")

    reject_hardcoded_normal_screen_colors(ypower, "YPower MainActivity")
    reject_hardcoded_normal_screen_colors(yminiguard, "YMiniGuard MainActivity")
    reject_hardcoded_normal_screen_colors(yparam, "YParam MainActivity")
    reject_hardcoded_normal_screen_colors(ynfc_components, "YNFC normal content components")
    reject_hardcoded_normal_screen_colors(ytask, "YTaskManager main screen")
    reject_hardcoded_normal_screen_colors(ydiag, "YDiag main screen")
    reject_hardcoded_normal_screen_colors(yparam_detail, "YParam AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_detail, "YPower AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_recommended, "YPower RecommendedApps")

    # Specialized diagnostic text highlighting in YPower Diagnostic and YNFC's log console may
    # keep semantic high-contrast colors. Normal cards/status/chrome remain YUI-owned.
    # General settings must reach the shared host and preserve language on feature screens.
    general = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/SuiteGeneralSettingsScreen.kt")
    locale = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/SuiteLocaleController.kt")
    host = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/YSuiteApp.kt")
    compose_activity = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YComposeActivity.kt")
    home = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/CompactSuiteHome.kt")
    general_store = text(ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/SuiteCommonSettings.kt")
    for source, markers, label in (
        (general, ("SuiteLocaleController.set(", "HOME_HIDE_DISABLED", "HOME_SHOW_SEARCH",
                   "HOME_SHOW_DIAGNOSTICS"), "YSuite general settings"),
        (locale, ("LocaleManager::class.java", "localizedContext(", "AppCompatDelegate"),
         "YSuite locale controller"),
        (host, ("YUiLocaleBridge.install(",), "YSuite locale bootstrap"),
        (compose_activity, ("YUiLocaleBridge.wrap(",), "embedded Compose locale"),
        (home, ("hideDisabled", "if (showSearch)", "if (showDiagnostics)"),
         "general home preferences"),
        (general_store, ("globalOnly", "LANGUAGE(", "HOME_HIDE_DISABLED("),
         "validated general settings"),
    ):
        for required in markers:
            require(source, required, label)
    # All numeric visual settings must use one shared validator and reach both renderers.
    appearance_store = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YAppearanceStore.kt")
    registry = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YUiControlRegistry.kt")
    forms = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YForms.kt")
    float_settings = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatSettings.java")
    float_icon = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsIconPage.java")
    float_gesture = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsGesturePage.java")
    module_slider = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt")
    appearance_screen = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/SuiteSettingsScreen.kt")
    tokens = text(ROOT / "libs/yui/yui_tokens.json")
    for source, markers, label in (
        (appearance_store, ('BUTTON_RADIUS("button_radius", "12", 0, 512)',
                            'BUTTON_HEIGHT("button_height", "48", 16, 320)',
                            'FONT_PERCENT("font_percent", "100", 25, 400)',
                            'PAGE_VERTICAL_PADDING(', 'ICON_VISUAL_SIZE(', 'LIST_ICON_SIZE(',
                            'TOOLBAR_HEIGHT('), "single YUI appearance validator"),
        (registry, ('require(minimum == key.minimum && maximum == key.maximum)',
                    'range(YSettingKey.TOOLBAR_HEIGHT', 'range(YSettingKey.LIST_ICON_SIZE'),
         "appearance registry"),
        (appearance_screen, ('appearance_toolbar_height', 'appearance_list_icon_size',
                             'appearance_icon_visual_size', 'appearance_page_vertical_padding',
                             'appearance_exact_value'), "appearance settings editor"),
        (forms, ('yui_exact_value', 'yui_apply_value',
                 'it.roundToInt().coerceIn(range.first, range.last)', 'steps = 0'),
         "shared integer slider"),
        (module_slider, ('fun sliderSetting(', 'val precise = AppCompatEditText(context)',
                         'YView.toolbarHeight(context)', 'YView.listIconSize(context)'),
         "View slider and controls"),
        (float_settings, ('YFloatVisualSettings.readInt(context, K_SIZE, 48)',
                          'YFloatVisualSettings.readInt(context, K_LINE_WIDTH, 6)',
                          'YFloatVisualSettings.readString(context, K_LINE_COLORS',
                          'YFloatVisualSettings.writeBoolean(context, key, value)'),
         "YFloat runtime values"),
        (float_icon, ('16, 192, fs.sizeDp()',), "YFloat appearance UI"),
        (float_gesture, ('K_LINE_WIDTH, 0, 64',), "YFloat gesture UI"),
        (tokens, ('"button_radius": 12', '"button_padding_horizontal": 16',
                  '"button_padding_vertical": 4'), "generated YUI defaults"),
    ):
        for required in markers:
            require(source, required, label)
    # Feature appearance settings must have one owner and one observable runtime value.
    overlay_bridge = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatVisualSettings.java")
    float_theme = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ThemeSettings.java")
    float_service = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatService.java")
    float_migrator = text(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsMigrator.java")
    rebuilt_settings = text(ROOT / "next/core/settings/src/main/java/com/yagay/ysuite/settings/DataStoreAppSettingsRepository.kt")
    for source, markers, label in (
        (appearance_store, ("FLOAT_ICON_ALPHA(", "FLOAT_TRAIL_COLORS(",
                            "FLOAT_MENU_COUNT(", "FLOAT_BORDER_VISIBLE("),
         "YUI owns feature appearance parameters"),
        (registry, ("YControlGroup.OVERLAYS", "YControlKind.TEXT",
                    "YSettingKey.FLOAT_TRAIL_COLORS"), "overlay appearance catalog"),
        (appearance_screen, ("YControlGroup.OVERLAYS", "YControlKind.TEXT"),
         "central appearance editor"),
        (overlay_bridge, ("new YAppearanceStore(context).set(",
                          "YSettingKey.FLOAT_MENU_COUNT", "YSettingKey.FLOAT_TRAIL_COLORS",
                          "static void migrate("), "YFloat YUI bridge and migration"),
        (float_theme, ("new com.yagay.yui.YAppearanceStore(c)",),
         "YFloat theme facade"),
        (float_service, ("appearanceStore.registerPreferenceListener(this)",
                         "appearanceStore.unregisterPreferenceListener(this)",
                         "refreshAppearance();"), "YFloat runtime appearance updates"),
        (float_migrator, ("YFloatVisualSettings.migrate(app, p)",),
         "YFloat migration bootstrap"),
        (rebuilt_settings, ("YAppearanceStore(context)", "SuiteCommonSettings(context)",
                            "appearance.set(", "YSettingKey.THEME"), "rebuilt host settings compatibility"),
    ):
        for required in markers:
            require(source, required, label)
    if 'AppCompatDelegate.setDefaultNightMode' in float_theme:
        fail("YFloat must not override the host global theme")
    if 'stringPreferencesKey("theme_mode")' in rebuilt_settings:
        fail("rebuilt host must not maintain a second theme store")
    # Rebuilt product layouts and legacy View activities must consume the live unified
    # appearance rather than static shadow settings or an independent spacing scale.
    product_spacing = text(ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/theme/YSuiteTokens.kt")
    product_layout = text(ROOT / "next/core/productui/src/main/java/com/yagay/ysuite/productui/ProductLayoutTokens.kt")
    runtime = text(ROOT / "libs/yui/src/main/java/com/yagay/yui/YUiInitializer.kt")
    compact_home = text(ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/CompactSuiteHome.kt")
    for source, markers, label in (
        (appearance_store, ("CONTENT_WIDTH_SCALE(", "PANE_WIDTH_SCALE(",
                            "contentWidthPercent =", "paneWidthPercent ="),
         "canonical product layout settings"),
        (registry, ("YSettingKey.CONTENT_WIDTH_SCALE", "YSettingKey.PANE_WIDTH_SCALE"),
         "central layout catalog"),
        (appearance_screen, ("appearance_content_width_scale", "appearance_pane_width_scale"),
         "central layout editor"),
        (product_spacing, ("import com.yagay.yui.YDimens", "LocalYAppearance.current.effectiveGapDp",
                           "@Composable get()"), "live YUI workspace spacing"),
        (product_layout, ("LocalYAppearance.current.contentWidthPercent",
                          "LocalYAppearance.current.paneWidthPercent",
                          "@Composable get()"), "live YUI content and pane geometry"),
        (runtime, ("appearance.registerPreferenceListener(listener)",
                   "foregroundActivities", "schedule(activity)"), "live View appearance observer"),
        (compact_home, ("appearance.screenPaddingDp.dp", "appearance.effectiveGapDp"),
         "live home layout settings"),
    ):
        for required in markers:
            require(source, required, label)
    print(
        "ui-framework: OK compose=YUI-v2 view=YViewPage/YViewLayout page-xml=forbidden "
        "primary=YSuite,YPower,YMiniGuard,YNotify,YEntryCleaner,YParam,YNFC,YTaskManager,YDiag "
        "secondary=YParam.AppDetail,YPower.AppDetail,YPower.RecommendedApps,YPower.Diagnostic,"
        "YNotify.AppHistory,YNotify.EventDetail,YEntryCleaner.Scope,YEntryCleaner.Components,"
        "YEntryCleaner.Rules,YEntryCleaner.Priority"
    )


if __name__ == "__main__":
    main()
