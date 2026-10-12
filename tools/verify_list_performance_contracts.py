#!/usr/bin/env python3
"""Guard against expensive per-row work and eager UI assembly.

Static source contracts, NOT measured device-side launch/scroll benchmarks.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def check(path: str, required: tuple[str, ...], banned: tuple[str, ...] = ()) -> None:
    source = (ROOT / path).read_text(encoding="utf-8")
    for token in required:
        if token not in source:
            raise AssertionError(f"{path} missing {token!r}")
    for token in banned:
        if token in source:
            raise AssertionError(f"{path} regressed to eager work: {token!r}")

def main() -> None:
    check("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java",
          ("RecyclerView", "ListAdapter<AppRow, AppHolder>", "DiffUtil.ItemCallback",
           "iconLoader.execute(", "InstalledAppCatalog.icon(", "snapshot",
           "new HashSet<>(ProfileStore.get(this).getEnabledPackages())",
           "ui.postDelayed(pendingSearch, 180L)"),
          ("list.removeAllViews()", "getProfile(a.packageName)", "Map<String, Drawable> icons"))
    # Real screen regression: RecyclerView children must occupy the full viewport,
    # or WRAP_CONTENT row measurement shifts the checkbox/actions with label length.
    check("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java",
          ("new RecyclerView.LayoutParams(", "ViewGroup.LayoutParams.MATCH_PARENT,",
           "list.setPreserveFocusAfterLayout(false)",
           "scrollToPositionWithOffset(0, 0)",
           "YViewLayout.secondaryButton(", "new LinearLayout.LayoutParams(touch, touch)",
           "row.trailing.setGravity(Gravity.END"))
    main_source = (ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java").read_text(encoding="utf-8")
    if "row.subtitle.setMaxLines(" in main_source or "row.title.setMaxLines(" in main_source:
        raise AssertionError("YPower list descriptions must grow with localized text")
    check("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt",
          ("fun listActionButton(", "secondaryButton(context, label)"))
    check("apps/YPower/feature/build.gradle.kts", ("libs.androidx.recyclerview",))
    check("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/data/ProcessRepository.kt",
          ("suspend fun listProcesses():", "listProcessesFromPs()",
           "suspend fun listDetailedProcesses()", "icon = null"),
          ("InstalledAppCatalog.icon(appContext",))
    check("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/MainViewModel.kt",
          ("scheduleDetailedSample()", "lastDetailSampleMs < 5_000L",
           "processResult = processDeferred.await()", "processes = visible"))
    # CPU collection must not block or starve the visible process list.
    proc = (ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/data/ProcessRepository.kt").read_text(encoding="utf-8")
    fast = proc.split("private suspend fun listProcessesFromPs()", 1)[1].split(
        "suspend fun loadDetails(", 1)[0]
    if "__CPU_TICKS__" in fast or "quickCpu.sample(" in fast:
        raise AssertionError("Fast process list still blocks on CPU collection")
    for marker in ('"process-list"', "suspend fun sampleCpu():", "private val cpuShell = RootShell()"):
        if marker not in proc:
            raise AssertionError(f"TaskManager missing independent CPU/list path: {marker}")
    gateway = (ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/SuiteRootGateway.kt").read_text(encoding="utf-8")
    if 'safeOperation != "process-list"' not in gateway:
        raise AssertionError("Fast list must bypass busy isolated scan slots")
    check("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt",
          ("LaunchedEffect(drawable, packageName, px)", "InstalledAppCatalog.iconBitmap("))
    check("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/data/NetworkRepository.kt",
          ("preferredRootProbe", "lastFullProbeMs < 60_000L",
           "if (selected.isEmpty())"), ())
    print("List performance source contracts: OK virtualized YPower, staged TaskManager and lazy icons")

if __name__ == "__main__":
    try:
        main()
    except (FileNotFoundError, AssertionError) as error:
        print(f"List performance source contracts: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
