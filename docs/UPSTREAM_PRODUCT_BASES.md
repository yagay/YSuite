# Upstream product UI bases

YSuite does not invent product-page information architecture.

The design system is shared. Product structure comes from mature open-source Android projects and is adapted to YSuite data and capabilities.

| Product | Upstream base | License | What is retained |
| --- | --- | --- | --- |
| Dashboard / host | android/nowinandroid | Apache-2.0 | adaptive navigation, app-owned chrome, bounded content |
| File manager | SysAdminDoc/FileExplorer | MIT | drawer/locations, breadcrumbs, tabs, selection top bar, list/grid, dual/three-pane workflow |
| Browser | fazza-abiyyu/Yue-Browser | Apache-2.0 | address bar, tabs, browser toolbar, browser-owned content |
| Settings | alorma/Compose-Settings | MIT | SettingsGroup, switch/menu/choice semantics and grouping |
| Log viewer | darshanparajuli/LogcatReader | MIT | search + app/tag/level filters + log stream/details |
| Download manager | PBhadoo/QDM-Android | Apache-2.0 | state tabs, task rows, progress, state-dependent actions |
| Task manager | wisnukurniawan/Compose-ToDo | Apache-2.0 | collection/filter/detail task workflow |
| Automation | SysAdminDoc/OpenTasker | MIT | profile/task/action library + editor + inspector workflow |
| App/entity manager | LibChecker/LibChecker | Apache-2.0 | app/entity collection, filtering, navigation rail, detail workflow |

GPL projects such as XFiles, Material Files and Seal may be studied for interaction ideas, but their source is not copied into YSuite unless the repository license is intentionally made compatible.

## What YSuite is allowed to standardize

YSuite may standardize:
- MaterialTheme and semantic color roles
- typography
- spacing
- shape/radius system
- icon family
- dialogs, bottom sheets, snackbars and state messaging
- motion tokens
- dark/light appearance
- localization

YSuite must not standardize unrelated product geometry into one generic page.

The upstream product layout remains recognizable after theming.
