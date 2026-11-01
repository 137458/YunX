# AGENTS.md — YunX（云析）AI 协作指南

面向在本仓库工作的 AI 编码代理（Claude Code / Cursor / Codex / Copilot Agent 等）。

> **两条全局规则**
>
> 1. **本项目所有代码注释、UI 文案、提交信息、PR 描述统一使用中文。**
> 2. 本文件只写「必须做什么 / 禁止做什么 / 去哪看」。长篇历史复盘已归档到
>    `docs/engineering-notes/ARCHIVE-agents-full-2026-11-01.md`（**非权威**，仅考古用；
>    与本文件或源码冲突时，以本文件与源码为准）。

---

## 1. 项目速览

**YunX（云析）**：Android 网盘分享链接解析 + 高速下载应用。粘贴分享链接 → 浏览分享内容 → 取直链 → 分片并发下载。

| 项 | 值 |
| --- | --- |
| 包名 / 源码根 | `com.yunx.app` / `app/src/main/kotlin/com/yunx/app` |
| 语言 | Kotlin 2.3.21（JVM target 17） |
| UI | Jetpack Compose + **Miuix 0.9.4**（HyperOS 风格）；仅 Expressive 专属组件用 `material3:1.5.0-alpha26` |
| 持久化 | Room 2.8.5（KSP2，**库版本 24**）+ SharedPreferences（`yunx_settings`） |
| 网络 | OkHttp 4.12.0 |
| 预览播放 | Media3 1.4.1 ExoPlayer / Coil 3.0.4 |
| 工具链 | AGP 9.1.1 / Gradle 9.3.1 / KSP 2.3.12 / Compose BOM 2026.09.00 |
| SDK | minSdk 24 / targetSdk 34 / compileSdk 37 |
| 协议 | GNU AGPL-3.0 |

- **支持 10 家网盘**：夸克、UC、迅雷、百度、139（和彩云）、123、115、光鸭、蓝奏云、蓝奏云优享版。
- **支持 7 家视频平台**：抖音、小红书、快手、微博、AcFun、哔哩哔哩、Pixiv；另支持 GitHub 仓库/Release 与磁力链接。
- **两套下载器并存**：内置 `DownloadManager`（Range 分片并发 + 断点续传）与进程内 Gopeed 引擎（用户可切换，磁力只能走引擎）。

---

## 2. 目录结构（按职责落位，新代码不要另起一级目录）

```
app/src/main/kotlin/com/yunx/app/
├── MainActivity.kt / YunXApp.kt / LaunchGate.kt   单 Activity 入口、Application 初始化、启动闸门
├── crash/                    崩溃捕获与崩溃展示页
├── util/                     LogRedactor（日志脱敏）· LogExporter · PermissionState（★ 权限/SDK 分支唯一入口）
│                             StorageDirs（★ 默认下载目录唯一来源）· AppLinks（QQ 群/仓库地址）· ByteFormat 等
├── data/
│   ├── network/              各平台 API + 常量 + 异常；★ ShareLinkParser（链接识别唯一入口）· HttpClients（客户端工厂）
│   │   ├── model/            ShareModels（ShareSession / ShareFile / DownloadLink）· ShareExpire（★ 分享有效期中性码）
│   │   └── video/            视频平台解析（★ VideoParsers 注册表 / VideoPlatform 枚举 / 各平台 Parser）
│   ├── repository/           ★ ShareResolveRepository（解析公共接口）+ 各平台 {Account,Resolve}Repository 成对存在
│   ├── db/                   Room：AppDatabase（★ version 与 Migration 集中管理）· 各 Entity/Dao · SecureAccountDaos（凭证装饰器）
│   ├── download/             ★ 下载引擎：DownloadManager / ChunkDownloader / DownloadService / HlsDownloader / DashMuxer …
│   ├── upload/               上传引擎（v1.3.1）：UploadSource（SAF 区间流）/ UploadIo / UploadSigning / UploadRunner
│   ├── gopeed/               Gopeed 进程内引擎（GopeedEngine / KernelProvisioner）
│   ├── prefs/                ★ SettingsRepository（所有设置项唯一入口）· LinkOpenTarget
│   ├── security/             CredentialCipher（凭证读写入口，明文直存）
│   ├── announcement/         应用内公告（远程列表 + 本地已读）
│   ├── backup/               认证备份（口令派生密钥 + AES-GCM）
│   └── update/               UpdateChecker
└── ui/
    ├── MainScreen.kt         ★ 主容器：底部导航 + 覆盖层式二级页面 + 全局窗口弹窗
    ├── SnackbarController.kt ★ 全局 Snackbar 通道
    ├── navigation/MainTab.kt 底部 4 Tab 枚举
    ├── screens/              一级/二级页面 + 各平台 *CloudScreen / *AccountSheet / *SaveSheet + CloudDriveScaffold（★ 网盘页共享骨架）
    ├── drive/                网盘页共享件（CloudDriveScreen 浏览主体 / CloudAccountSheet / CloudPickerSheet / DriveDirPicker / ShareFormSheet）
    ├── resolve/              解析结果页（ShareDetailScreen / ResolveFileActionSheet / DownloadLinkDialog）
    ├── login/                各平台登录页（WebView Cookie / 账密 / 手动 Cookie）
    ├── preview/              在线预览（CloudPreview / MediaPreviewScreen / PreviewLauncher / PreviewTextLoader）
    ├── viewmodel/            每个功能一个 ViewModel + 内嵌 Factory
    ├── components/           可复用组件（★ FileNameText / RemoteImage / RemoteImageLoader / 底栏三件套 / Markdown）
    ├── items/                CustomFabMenu / MultiSelectBar / PlatformLogos（★ 平台图标与可读名的唯一来源）
    └── theme/                Color / Type / Theme / ★ AppThemeManager（主题与显示开关的唯一内存态）/ Motion / SquircleShape
```

两个**不在** `app/src/main/kotlin` 下的关键路径：

- `app/libs/gopeed-classes.jar` —— Gopeed 的 gomobile Java 桥接（**已打字节码补丁**，见 §3.28，**不要用官方原版覆盖**）；
- `tools/patch-gopeed-classes.py` —— 生成上面那个 jar 的补丁脚本（**换新 AAR 时必须重跑并提交新 jar**）。

---

## 3. 硬约定（按编号引用，代码注释里的 `§3.x` 指的就是这一节）
### 3.1 ViewModel：自定义 Factory，不用 DI 框架
无 Hilt/Koin。每个 ViewModel 内嵌 `class Factory : ViewModelProvider.Factory`，依赖由 `ui/MainScreen.kt` 手工传入（样例 `ui/viewmodel/BookmarkViewModel.kt`）。

### 3.2 提示统一走全局 Snackbar
`SnackbarController.show("…")` 是唯一出口；页面用 `rememberGlobalSnackbarHostState()` / `GlobalSnackbarHost()` 渲染。
**禁止**：在 ViewModel 里持有 `SnackbarHostState`；用 Toast（唯一例外是 `MainScreen` 提前 return 的引导页，它不在 Scaffold 内）。
全屏覆盖层与 `WindowDialog` 会遮住宿主 ⇒ 它们内部必须自带 `SnackbarHost`。

### 3.3 二级页面：`AnimatedVisibility` 全屏覆盖层，不用 NavHost
`showXxx: Boolean` + `AnimatedVisibility(enter = fadeIn(tween(220)) + scaleIn(0.96f), exit = fadeOut(tween(180)) + scaleOut(0.96f))`，覆盖页必须自带 `BackHandler { onBack() }`。
**禁止**：引入 Navigation-Compose 或任何路由表。

### 3.4 设置项只能加在 `SettingsRepository`
`data/prefs/SettingsRepository.kt`（SP 名 `yunx_settings`）是唯一入口：`var` + 自定义 getter/setter，值域用 `coerceIn` 兜住，默认值放 `companion object` 常量。
需要「改了立刻生效」的开关，另在 `ui/theme/AppThemeManager.kt` 建对应 `mutableStateOf` + setter（这是主题与显示开关的唯一内存态）。

### 3.5 下载引擎：参数用 Provider 闭包注入
`DownloadManager` 不持有 `SettingsRepository`，只接收 lambda：`threadProvider` / `concurrencyProvider` / `speedLimitProvider`。
**禁止**：构造时取设置快照（会导致改设置不即时生效）。

### 3.6 凭证存储：明文落库
账号 Dao 一律经 `SecureAccountDaos.xxx(rawDao, cipher)` 装饰后使用；`CredentialCipher.encrypt` 直通返回原文（2026-10 起不再加密，理由见 §3.36）。
下载任务的请求头（含 Cookie）同样明文落库，读取经 `CredentialCipher.decrypt(json, "download.requestHeaders")` 兼容旧密文。
**禁止**：直接用 `rawXxxAccountDao()`；日志里出现 URL / Cookie / token 时必须过 `LogRedactor.url(...)`。

### 3.7 Room 迁移
`data/db/AppDatabase.kt` 现 **version = 24**。加表/字段五步：① `entities` 追加 → ② `version` +1 → ③ 新增 `abstract fun xxxDao()` → ④ 写 `MIGRATION_N_N+1`（新表用 `CREATE TABLE IF NOT EXISTS`，不改旧表）→ ⑤ 注册到 `.addMigrations(...)`。
**禁止**：破坏性迁移。`fallbackToDestructiveMigrationFrom(dropAllTables = false, startVersions = intArrayOf(1..8))` 只覆盖 1..8，**v9 起必须保留用户凭证与下载任务**。
已知降级崩溃：高库版本包之后再装低版本包会抛 `IllegalStateException: A migration from N to M was required but not found`（崩点在首次开库处，之后每次启动都崩）。要修就加 `.fallbackToDestructiveMigrationOnDowngrade()` + `onDestructiveMigration()` 提示，升级路径不受影响。

### 3.8 平台标识用 `DownloadPlatform` 常量
`data/download/DownloadPlatform.kt`：`QUARK` / `UC` / `XUNLEI` / `BAIDU` / `C139` / `PAN123` / `PAN115` / `GUANGYA` / `ILANZOU` / `LANZOU` / `MAGNET` / `GITHUB` / `GENERIC` / `VIDEO`。**禁止**写裸字符串。

### 3.9 新增平台支持的完整清单
成对创建 `{X}Api.kt` / `{X}Constants.kt` / `{X}AccountRepository.kt` / `{X}ResolveRepository.kt`（实现 `ShareResolveRepository`）/ `{X}Account{Entity,Dao}.kt` / `{X}AccountSheet.kt` / `{X}CloudScreen.kt` / `{X}LoginScreen.kt` / `{X}AccountViewModel.kt` / `{X}CloudViewModel.kt`，并在 `ShareLinkParser`、`DownloadPlatform`、`AppDatabase`、`ui/items/PlatformLogos.kt` 登记。

### 3.10 material3 显式钉 alpha（Material 3 Expressive，勿顺手改回）
`gradle/libs.versions.toml` 里的 `material3Expressive` 刻意覆盖 BOM 给的 1.4.0：ButtonGroup / SplitButton / ToggleButton / LoadingIndicator / MaterialShapes / FloatingToolbar 等**只存在于 1.5.0-alpha**。
升 BOM 或该 alpha 前**逐条重核四条门槛**：① Kotlin metadata 可读（禁 `-Xskip-metadata-version-check` 伪造）；② `material3.aar` 内 `META-INF/.../aar-metadata.properties` 的 `minCompileSdk` ≤ compileSdk、`minAndroidGradlePluginVersion` ≤ AGP；③ manifest minSdk ≤ 24；④ pom 依赖的 `ui`/`foundation`/`runtime` 与 BOM 同线（跨线就是混版）。
**数值的唯一真源**是 `libs.versions.toml` 中 `material3Expressive` 上方的注释，本节只写规则、不抄版本号。
`MaterialTheme.motionScheme` 是 `@Composable` 属性：要把它给非 composable 的 lambda（`transitionSpec` / `remember` / 点击回调）用时，必须先在 composable 里取局部变量再捕获，否则报 `@Composable invocations can only happen from the context of a @Composable function`。

### 3.11 minSdk 钉 24（勿降回 23）
两道约束：miuix 0.9.4 与 `material3:1.5.0-alpha26` 的 AAR 清单都写 `minSdkVersion=24`；且 minSdk < 24 时 D8 把接口 `$default` 桥搬进合成 `Xxx$-CC` 类却不改写第三方 AAR 的调用点 ⇒ 运行时 `NoSuchMethodError: No static method weight$default(...)`（只有未混淆的 debug/CI 包暴露，release 的 R8 会先内联掉）。
降回 23 的前提：① 依赖 AAR 的 minSdk 都先降；② 确认 AGP 已修该 D8 缺陷（**AGP 9.1.1 下尚未复验**，复验办法是用 debug 包打开带表格的 README 实测不崩）。

### 3.12 文件名展示一律用 `FileNameText`
`ui/components/FileNameText.kt`（受 `SettingsRepository.fileNameMultiLine` 控制）：默认单行 + 跑马灯，可折行最多 3 行；需要多行/居中就传 `maxLines` / `textAlign`。
**禁止**：调用处再写 `maxLines` / `overflow` / `basicMarquee`。面包屑、对话框标题、收藏页分享标题**不是**文件名，不走它。

### 3.13 文件操作弹窗：解析页与网盘页同一套
操作统一收进底部弹窗，行内不放操作图标（`ShareFileRow` 只剩 `onClick` / `onMore` / `onLongClick`）。共享组件 `FileActionSheet` / `FileSheetHeader` / `ActionItem` 都在 `ui/screens/CloudFileSheets.kt`，改样式只改这些。
转存是**同一弹窗内的二级步骤**（`ActionStep{MENU,SAVE}` + `AnimatedContent`），外壳用 `SaveStepScaffold(title, subtitle, onBack, content)`；**禁止**另写 `ModalBottomSheet` + 自绘标题行或另抄一份菜单行。
转存成功判定：`ResolveViewModel.saveTarget` 由非空变 `null`（失败/未登录保留原值让用户重试）；`saving` 期间禁止下滑关闭与返回菜单。
只有「下载 / 下载文件夹」这类要另开弹窗的动作才先 `sheetState.hide()` 播完退场再执行；同弹窗内的步骤切换不关弹窗。

### 3.14 主页快捷方式：状态存 Room，不存设置
`BookmarkEntity.homePinned`（`MIGRATION_13_14`）与 `homeLabel`（`MIGRATION_14_15`）经 `BookmarkDao.observeHomePinned()` / `updateHomePinned` / `updateHomeLabel` 读写。**禁止**用 `SettingsRepository` 存 ID 列表（会出现两份状态、删除残留、顺序不同步）。
UI 在 `ui/screens/ResolveScreen.kt` 的 `HomeShortcutsSection`：**禁用 `LazyVerticalGrid`**（外层已纵向滚动，同向嵌套会崩），用 `chunked(HOME_SHORTCUT_COLUMNS = 4)` 手写行网格。
瓦片文字规则唯一实现 `homeTileLabel(bookmark)`：自定义 `homeLabel` > `title` 前 `HOME_LABEL_MAX_LENGTH = 4` 字 > `platformShortLabel`。GitHub 收藏（`platform = "GITHUB"`）不得被网盘解析吞掉。

### 3.15 叠加页：容器变换（Container Transform）机制已整体移除
`SharedTransitionLayout` / `overlayRoute` / `OverlayPage` / `OVERLAY_KEY_*` **零命中**，不要再按旧描述找源侧/目标侧 modifier。
现状：主界面之上有 4 个叠加页（关于云析 / 支持开发 / 主题与外观 / 收藏），各自 `AnimatedVisibility(visible = showXxx) + Modifier.fillMaxSize()` 叠在最外层 Box 内，与主界面是**叠加不是互斥**；引导页与 11 个登录页走 `if (showXxx) { XxxScreen(...); return }` 提前返回。
共享元素现在只活在 `ui/screens/AnnouncementScreen.kt` 内部（列表项 ↔ 详情 ↔ 全屏看图）。叠加页仍须自带 `BackHandler`。

### 3.16 深色模式黑字：全屏页必须有 `Surface`
`LocalContentColor` 默认是 `Color.Black`，只有 `Surface` / `Scaffold` 才会设成 `contentColorFor(底色)`。新增全屏覆盖页/手动布局页用 `Surface(Modifier.fillMaxSize(), color = colorScheme.surface)` 铺底；确需 `Modifier.background` 时每个 `Text` 都要显式给 `color`。
雷区：`ui/MainScreen.kt` 的 `BoxWithConstraints` → `Row` → `Box` 既无 `Surface` 也无 `background`（内容区靠 Scaffold 兜底，但 **Scaffold 之外的 Snackbar 宿主与其后新增的裸 `Text` 都要自己保证颜色**）。
自查：`grep -rn "Color(0x|Color.White|Color.Black" app/src/main/kotlin/com/yunx/app`，正常只应命中 `ui/theme/Color.kt`。

### 3.17 权限申请统一收口在引导页第 3 页
**唯一状态入口**是 `util/PermissionState.kt`——`Build.VERSION.SDK_INT` 的分支只允许写在这个文件里，调用处不要自己判版本。
三张卡片：通知权限（13+ 可申请，否则跳系统设置）、后台运行（忽略电池优化白名单）、存储权限（**仅 Android 9 及以下需要，且不可跳过**；10+ 走媒体库无需授权）。
存储权限未授权时 `OnboardingBottomBar(finishEnabled = false)` 把「开始使用」置灰，**文案与图标恒为「开始使用」+ Check，不随状态改字**；`PermissionCard` 内部只要 `granted == true` 就不渲染按钮。
**禁止**往业务页面加「每次启动都弹」的检查（`MainActivity` 的通知权限弹窗、`MainScreen` 的电池优化 AlertDialog 已移除，勿恢复）。保留的兜底只有真正用到功能且权限缺失时的提示（保存前、手动添加任务、保存图片、设置页）。

### 3.18 发布签名：本地环境变量自签名
`app/build.gradle.kts` 读 `YUNX_KEYSTORE_FILE` / `YUNX_KEYSTORE_PASSWORD` / `YUNX_KEY_ALIAS` / `YUNX_KEY_PASSWORD`，四项齐备且证书存在才注册 release 签名，否则产物是 `app-release-unsigned.apk`。
**禁止**把证书提交进仓库（`.gitignore` 挡 `*.jks` / `*.keystore` / `keystore.properties`，`!debug.keystore` 例外）。换签名后与 debug 包互不兼容，必须卸载重装，否则 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

### 3.19 游客模式：列目录不要求登录
10 家网盘的分享**列表**接口都允许匿名访问；**夸克 / UC 的分享直链也不校验登录态**（夸克约 50MB 以内，UC 实测 4GB）。
闸门在 `ui/viewmodel/ResolveViewModel.kt`：`startResolve` / `openFolder` / `goBack` 空凭据照常下传并置 `isGuest`；仍要求登录的是 `saveToCloud` / `batchSaveToCloud` / `requestSave`；仅夸克/UC 放行 `fetchDownloadLink` / `downloadFiles` / `startDownload`（判据 `supportsGuestDownload()`）。
游客下载的命门是 `__pugs`：服务端随取链响应下发（3 小时有效），**必须用同一次响应里的值**，写进 `DownloadLink.guestCookie`；夸克缺它 CDN 412、UC 缺它 403 `require login [auth not found]`。115 复用同一字段装的是 900 秒 CDN Cookie，非游客态。
**禁止**：往列目录加登录拦截；迅雷游客请求带失效 `Bearer`（会被判 `unauthenticated`，空 token 时不要写 `Authorization` 头）。

### 3.20 分享有效期：UI 中性码必须经 `ShareExpire` 转换
中性码只有一套（`1` 永久 / `2` 一天 / `3` 七天 / `4` 三十天），定义在 `data/network/model/ShareExpire.kt`；**必须在 ViewModel 层转换后再调 `api.createShare(...)`**，各 API 的 KDoc 都写明「不是 UI 中性码」。
- 139：`period` = 天数，**永久 = 完全不传该字段** → `daysOrNull()`；
- 百度：`period` = `0/1/7/30`（`0` = 永久）→ `baiduPeriod()`；
- 123：`expiration` = 绝对 ISO 时间串（永久 = 2099 哨兵）→ `daysOrNull()` 后拼串；
- 迅雷：`expiration_days` = **字符串** `-1/1/7/30` → `xunleiDays()`；
- 夸克 / UC：`expired_type` 取值恰好等于中性码，原值直传；
- 115：`share_duration` = 字符串 `-1/1/3/7/15`，**且只在 `/share/updateshare` 里给**（创建接口没有该字段）→ `pan115Duration()`。

**禁止**：中性码直传接口；`else -> 永久 / 30 天 / "-1"` 兜底（`daysOrNull()` 对未知码**抛异常**，fail-loud）；把 115 的五档并回中性码（另开 `PAN115_FOREVER=101`…`PAN115_FIFTEEN_DAYS=105` 与 `ShareExpire.PAN115_OPTIONS`，否则「3 天」会静默变「7 天」）。
选择器两个坑（`CloudFileSheets.kt` 的 `ShareStep`）：默认选中项必须取 `expireOptions.firstOrNull()?.second`（写死 `FOREVER` 会让 115 五档全不选中）；档位行必须 `FlowRow`（`Row` 会把「15 天」压扁）。认不出的回填值统一 `ShareExpire.UNKNOWN = 0`（显示「未知」），百度字段缺失时回退用户所选、**不要用 0 兜底**（0 是永久）。

### 3.21 「网盘更新」按钮：能自动化就直接下载
`ResolveViewModel.startUpdateDownload()`：已登录 → 自动「建会话 → 列目录（`collectShareFolder`，最多 12 层）→ 取体积最大的 `.apk` → 转存/取直链 → 入队」；未登录仅夸克/UC 匿名取链（夸克超 `QUARK_GUEST_MAX_BYTES` = 50MB 回落），其余回落 `fallbackToResolve()` 并切回「解析」Tab。
入口是 `ui/MainScreen.kt` 的 `onNetdiskUpdate`。已登录夸克/UC 先 `getFreshCookie()`，会话/列目录/取链/下载四处必须同一份 cookie。分享里没有 `.apk` 就回落，不顺手下别的文件。

### 3.22 「自动识别剪贴板」开关
持久化 `SettingsRepository.clipboardSuggestEnabled`（键 `clipboard_suggest_enabled`，默认 `true`），内存态在 `AppThemeManager.clipboardSuggestEnabled`。
全应用**唯一**读取点是 `ui/screens/ResolveScreen.kt` 的 `readClipboardSafely()`（只被 `maybeSuggestClipboard()` 调用）。**新增任何剪贴板读取点前必须先判这个开关**；写入剪贴板（复制直链/Cookie/群号）不受它约束。

### 3.23 「接受预发布版更新」开关
`SettingsRepository.acceptPrereleaseUpdate`（键 `accept_prerelease_update`，默认 `false`）+ `AppThemeManager.acceptPrereleaseUpdate`；统一入口 `UpdateChecker.fetchLatestRelease(includePrerelease)`：正式版走 `RELEASES_LATEST_URL`，预发布走 `RELEASES_LIST_URL`（取首条 `draft == false` 且 `tag_name` 非空）。
**`compareVersions` 别改回去**：先比数字前缀；数字段相同时只有**两边都带后缀**才继续比后缀，**只有一边带后缀视为相等**（保住 fork `1.2.6-gh1` 的既有约定）⇒ 预发布包的 `versionName` 必须带同样后缀，否则 beta1→beta2 会被判「已是最新」。
`cleanReleaseNotes` 只在 `parseRelease()` 里清洗一次，且**只认两类**：capsule-render 波浪头图 `<img>` 与 QQ 群徽章；两条 QQ 正则**顺序不能换**（带外链的先跑）。用例见 `app/src/test/kotlin/com/yunx/app/data/update/UpdateCheckerNotesTest.kt`。

### 3.24 对外联系方式（QQ 群 / 仓库地址）只在 `AppLinks`
`util/AppLinks.kt`：`QQ_GROUP = 635207650`、`GITHUB_REPO`、`GITHUB_REPO_DISPLAY`。界面里引用常量，**禁止写死**。
QQ 入口 = 先跳 QQ、失败再复制：`openQQGroup(context)` 用 `mqqapi://card/show_pslcard?...&uin=<群号>&card_type=group`，返回 false 才 `copyToClipboard` + 提示「未安装 QQ，群号已复制」。**不要用 `resolveActivity()` 预检**（Android 11+ 软件包可见性会误判成「没装」），靠 `runCatching` 捕获 `ActivityNotFoundException`。

### 3.25 115 网盘：七条与其它平台不同的硬规则
契约：host `https://webapi.115.com`；桌面 UA + `X-Requested-With: XMLHttpRequest`（带 Cookie 时不能省）+ `Referer: https://115.com/`；成功判定顶层 `state == true`，错误码在 `errno` / `errNo` / `error`。
① **分享者 UID 不是自己的 UID**，只出现在 `share/snap` 的 `data.userinfo.user_id`；它与提取码一起编码进 `stoken`（`encodeShareToken(receiveCode, userId) = "<receive_code>|<user_id>"`）。**禁止往 `ShareSession` 加字段**——那是 10 个平台共用的契约。
② **目录判定是反的**：文件夹项没有 `fid`、自身 ID 在 `cid` 里；分享列表用 `fc == 0` 判目录。`parsePersonalFiles` / `parseShareFiles` 已把 `ShareFile.fid` 归一成可直接下发的 ID，**不要再按平台分叉判一次目录**。
③ **网页端直链要双 Cookie**：`files/download` 与 `share/downurl` 会 `Set-Cookie` 下发 **900 秒、path 绑定该文件**的 CDN Cookie，必须与登录 Cookie 合并成 `Cookie: <登录 Cookie>; <CDN Cookie>`，缺了 CDN 403 `no cookie value`。它经 `DownloadLink.guestCookie` 传到 `ResolveViewModel.enqueueDownload` 的 `mergeCookies`，并补 `User-Agent` 与 `Referer`。取链与下载必须紧邻（Cookie 会过期）。
④ **创建分享的有效期不在 `/share/send` 里**：建完必须再调 `/share/updateshare {share_duration}`，否则选「长期」也落成默认 15 天；首次创建前过 `/user/allow_protocol {action: pubshare}`（进程内 `@Volatile protocolAllowed` 缓存，失败也放行）。
⑤ **大文件必须走「电脑端」加密接口**：网页端报 `50028 文件大小超出限制，请使用115电脑端下载` / 分享报 `50029 当前版本过低`，**改 UA 无效**。真协议是 `POST https://proapi.115.com/app/chrome/downurl?t=<unix秒>`，表单 `data = Pan115Crypto.encode({"pickcode":…}, key)`，响应用同一 key `decode`（算法已与两份参考实现逐字节核对一致）。
⑥ **客户端 UA 版本号必须动态取**：`Pan115Constants.CLIENT_UA` 是 `@Volatile var`，由 `Pan115Api.refreshClientVersion()` 从 `https://appversion.115.com/1/web/1.0/api/chrome` 取 `data.win.version_code` 后刷新，三处取链前都调，失败用 `CLIENT_UA_VERSION_FALLBACK`。**禁止写死**。
⑦ **分享大文件 = 先转存再取电脑端直链**：`getShareDownloadLink()` 先试 `share/downurl`，失败且已登录时走 `downloadViaTempTransfer()`（建 `YunX临时转存/tr_<nanoTime>` → `share/receive` → 轮询列目录认领新 `fid` → `getAppDownloadLink()`），临时目录 cid 放进 `DownloadLink.cleanupDirFid`，由 `ResolveViewModel` 调 `cleanupTempDir()` 删除。**这条清理链路不能断**（会在用户网盘里留垃圾）。
其它已收口的坑：`/rb/delete` 返回 `800007` 需二次确认（补 `ignore_warn=1` 重试）、`990009` 表示上个任务未完成；访问码服务端生成不可自定义（UI 用 `PasscodeMode.SERVER_GENERATED`）；`share/receive` 不回传新文件 id，`transferFile` 靠重新列目录按文件名认领。

### 3.26 网盘页「+」新建菜单（个人盘）
入口唯一：十家个人盘页顶栏搜索图标旁的「+」→ 新建文件夹 / 上传文件，多选态自动隐藏；**分享浏览页不挂**。共享组件 `CloudAddMenu` / `CreateFolderDialog` 与名称校验 `cloudNameError` 都在 `ui/screens/CloudFileSheets.kt`（校验：非空、非 `.` / `..`、不含 `/` `\` 与控制字符、≤255 字符）。
根目录取值各不相同：夸克/UC `"0"`、迅雷 `""`、百度 `"/"`（绝对路径，别拼成 `//名字`）、115 `Pan115Constants.ROOT_CID`、139 `"/"`、123 `"0"`。
**禁止**：把 `onUploadFile` 传成空 lambda（默认 `null` 时就置灰标「开发中」）；自行兜底建目录错误（十家都没有错误码表，按「服务端报错就提示」处理）。
`LaunchedEffect(目录) { listState.scrollToItem(...) }` 不能无条件执行，用 `rememberSaveable` 的 `appliedDirKey` 标记「已应用过一次」；`DriveScreen` 的 `LazyListState` 必须提升到 `AnimatedContent` 之外。

### 3.27 内置 Gopeed 引擎（一半编译期、一半运行时）
`app/libs/gopeed-classes.jar`（12KB）作编译依赖；`libgojni.so`（56MB）**不进仓库不进 APK**，由用户导入 AAR 后运行时解出。
★**加载顺序硬约束**：先 `System.load(绝对路径)` 成功，再触碰任何 `go.*` / `com.gopeed.*` 类；`GopeedEngine.loadLibrary()` 只在它失败时才回退 `System.loadLibrary("gojni")`，两条原始错误都要保留并 `Log.e`。
**禁止**：DexClassLoader / 运行时 dex；删 proguard 里 `-keep class go.** {*;}` 与 `-keep class com.gopeed.** {*;}`（`.so` 用 `FindClass` 反查类名，混淆即崩）。

### 3.28 `libgojni.so` 缺 `DT_SONAME`（不要把补丁回退掉）
AAR 里的 `libgojni.so` 没有 `DT_SONAME`，而 gomobile 的 `go.Seq.<clinit>` 写死 `System.loadLibrary("gojni")` ⇒ linker 无 soname 可匹配 ⇒ `UnsatisfiedLinkError`，且**类初始化失败会被 JVM 永久记住**（必须杀进程重开）。修法：把 `<clinit>` 里 `ldc "gojni"` + `invokestatic System.loadLibrary` 共 5 字节原地写成 `0x00`。
脚本 `tools/patch-gopeed-classes.py` 要求**恰好命中 1 处**、改完反汇编复核残留为 0；**换新 AAR 时必须重跑并提交新 jar**。
**不要再试**已否决方案：重写 `go/Seq.java`、自定义 `PathClassLoader(librarySearchPath)`（会把 56MB Go runtime 再加载一份）、给 `.so` 补 `DT_SONAME`、把 `.so` 打进 `jniLibs`。

### 3.29 落盘与权限（引擎走 B 路线）+ 两套下载器并存
用户已拍板：落盘申请 `MANAGE_EXTERNAL_STORAGE` 直写公共目录；内核由用户手动导入 AAR（应用内云端获取见 §3.34）；两套下载器并存、应用内可切换。
权限三档（判据只在 `util/PermissionState.kt`）：Android 9- 运行时 `WRITE_EXTERNAL_STORAGE`；10 = 同一运行时权限 + manifest `requestLegacyExternalStorage="true"`；11+ = `MANAGE_EXTERNAL_STORAGE`（只能用户去系统设置开）。
`GopeedEngine.resolveDownloadDir()` 顺序 = `SettingsRepository.engineDownloadDir` → `StorageDirs.defaultDownloadDir()` → `Android/data/<包名>/files/gopeed`；可写性用 `.yunx_write_probe` 试写探针，**不按系统版本推断**（各 ROM 不一致）。
**禁止**：改 `PermissionState.storageGranted()`（10+ 恒 true，是给媒体库/SAF 用的）；用 MediaStore 直写绕权限（只支持顺序流，引擎是多连接随机写）。

### 3.30 引擎开关 + 任务映射（DB v17 起的 `engineTaskId`）
`SettingsRepository.downloadEngine`（`ENGINE_BUILTIN` 默认 / `ENGINE_GOPEED`，非法值按内置）。**分流点只有一处**：`DownloadManager.enqueue()` 末尾 `start(id, headers)` 之前 `if (shouldUseEngine(platform)) startViaEngine(...) else start(id, headers)`；`shouldUseEngine()` = 平台不是 GitHub 且设置选了引擎 且 `GopeedEngine.isInstalled()`。
引擎建任务失败**按失败任务落库**、`errorMsg` 写引擎原文，**不要静默回退内置下载器**。进度由 `startEngineSync` 每 700ms 拉回写（done→`dao.complete` 并触发 `taskCallbacks` / error→失败 / pause→已暂停 / 其余 `updateProgress`）；`pause` / `remove` 不提前 return，要转发给引擎。

### 3.31 引擎任务的前台保活（引用计数）
引擎跑在进程内，保活只需让「有引擎任务在跑」算作「有下载在跑」，复用 `DownloadService`。★起停一律走 `DownloadService.acquire(context, title)` / `release(context)`，**新增调用方必须成对**，**禁止直接 `stop()`**（会关掉内核包下载等其它来源的保活）。
配对靠 `engineKeepAliveIds`（`ConcurrentHashMap.newKeySet<Long>()`）：`add` / `remove` 返回 `true` 才动作；**新增任何终结路径都必须补 `if (engineKeepAliveIds.remove(id)) onTaskFinished()`**。`notifyProgress` 必须写在 `_stats.update` 之后。

### 3.32 下载引擎页
设置 →「下载引擎」→ `ui/screens/DownloadEngineScreen.kt`：一张卡片上下两段（中缝 `outlineVariant` 细线），字号只允许 `titleMedium` / `bodySmall` / `labelLarge` / `labelSmall`，动效规格一律取自 `ui/theme/Motion.kt`。
三条交互口径：① 无存储权限不给切 Gopeed（`chooseEngine(ENGINE_GOPEED)` 里硬拦截 + notice）；② 不提供启动/停止，只有「重启引擎」= `stop()` 后 `start()`（顺序不能反），切回内置**不停引擎**；③ `GopeedEngine.syncInstalledState(context)` 在 `YunXApp.autoStartGopeedIfSelected` 开头（必须在「选没选 Gopeed」的 return 之前）与页面 `LaunchedEffect(Unit)` 各调一次。
删内核必须自己先 `stop()` 再 `uninstall()`，并顺手写回 `ENGINE_BUILTIN`。**禁止**在 `chooseEngine(ENGINE_BUILTIN)` 里加 `stop()`（加过又删了）；`autoStartGopeedIfSelected` 任何失败只 `Log.e`，不能影响应用启动。

### 3.33 下载目录口径
默认目录 = `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`，**唯一来源是 `util/StorageDirs.kt`**，别再写死 `YunX` 目录名。
引擎另存 `SettingsRepository.engineDownloadDir`（空 = 默认目录），因为 `opts.path` 只认真实路径；`realPathFromTreeUri` 只认 authority `com.android.externalstorage.documents`，第三方 provider 一律返回 null 退手输。`prepareDownloadDir(path)` 必须是以 `/` 开头的绝对路径 + `mkdirs()` + `canWrite()` 探针，失败抛 `IllegalArgumentException`（文案直接给用户看）。
**禁止**：把 `content://` 或 docId 当路径交给引擎；拿 `DownloadSaver.safDirDisplay` 的显示串当路径。「下载保存目录」行圆角固定 `ListGroupPos.MIDDLE`；`Spacer(ListGroupGap)` 必须放在 `AnimatedVisibility` 外面（折叠会连内部 Spacer 一起收）。

### 3.34 内核云端获取（GitHub / 网盘自动下载 → 自动导入）
全链路在 `data/gopeed/KernelProvisioner.kt` 一个对象里（取包 → 下载 → 校验 → 导入 → 清理），界面只渲染 `phase: StateFlow<Phase>`。**刻意不复用 `DownloadManager.enqueue`**（内核包落应用私有目录、不进下载页不落库、需要关不掉的进度弹窗），只复用 `ChunkDownloader` / `UpdateChecker` / `QuarkResolveRepository` / `GopeedEngine.installFromAar`。
分片：网盘通道目标片数固定 `PAN_CHUNKS = 64`（不看设置页档位），GitHub 通道按设置页线程数；`MIN_CHUNK_BYTES = 256KB`；探不到大小或服务器忽略 Range 就退回单流 `downloadFull`，**绝不按分片写整文件**。
**禁止**：Release/分享缺本机架构的包时静默换架构（必须直接报错）；GitHub 通道 sha256 校验不过还导入；`acquire`/`release` 不配对；用 `lastError` 承载「需重启」状态。转存临时目录清理必须放 `finally` 且包 `NonCancellable`。
导入前必须先 `GopeedEngine.stop()`，导入后若原本在跑立刻 `start()`；新 `.so` 只有全新进程能 dlopen ⇒ 状态用 `GopeedEngine.pendingRestartForNewKernel`。

### 3.35 应用内公告
只用 `GET /api/v1/announcements?page=&pageSize=` 与 `{id}` 详情，管理端不碰；**判成功读 body 的 `success` 而非 HTTP 码**。列表接口计「客户端启动数」⇒ **禁止轮询**（调用点只有启动检查一次 + 手动刷新/翻页），`pageSize` 固定 100、翻页按 id 去重。
已读纯客户端维护：`data/announcement/AnnouncementReadStore.kt` 存 SharedPreferences JSON 数组（上限 500），打开详情或关掉启动弹窗都算已读。详情按 id 缓存（同会话只请求一次，`viewCount` 只 +1），刷新必须让缓存作废（列表刷新先 `detailCache.clear()`，详情页走 `reloadCurrentDetail()` 而不是复用 `openDetail`）。弹窗候选 `pickPopupCandidate`：未读置顶 → 按 `publishAt`（null 退 `createdAt`）取最大 → 全读则不弹；启动检查失败静默。
**禁止**：全局放开 `network_security_config.xml` 的 `base-config` 明文（只给 `yunx.cyqawa.os.kg` 开 `domain-config`；上 HTTPS 后同时删例外并改 `AnnouncementApi.BASE_URL`）；用 `BadgedBox`；把角标画在 `IconButton` 内（内部 `.clip(CircleShape)` 会切成水滴形，须套 48dp 外层 Box）；用 `produceState` 加载会变的 URL；正文换 WebView；再引图片库（唯一例外是 §3.43 的 Coil）。
不用 `java.time`（minSdk 24 无脱糖），走 `AnnouncementTime.kt` 的 `SimpleDateFormat`（`Z` 是字面量，须配 `timeZone = UTC`）。共享元素形变：`resizeMode = RemeasureToBounds`、退出动画 ≥300ms、`visible` 只用 `detailId` 真值（`shownDetailId` 永不清空，否则详情页关不掉）。

### 3.36 凭证不再加密（明文落库）
账号 Cookie/JWT、GitHub Token、下载任务请求头**一律明文落库**。`data/security/CredentialCipher.kt` 只有 `PlaintextCredentialCipher` 一个实现：`encrypt` 原样返回、`isEncrypted` 判前缀 `yunx:v1:`、`decrypt` 只对历史密文只读解一次；`PlaintextCredentialCipher.shared` 全进程唯一，DAO / `GitHubTokenStore` / `DownloadManager` 共用。
旧密文解密用 Keystore 别名 `yunx.account.credentials.v1`（AES-GCM），**该路径绝不 `generateKey()` / `deleteEntry()`**；解密成功后用 `if (cipher.isEncrypted(stored))` 明文回写原行（条件与旧版相反：是密文才写回）。
任何解密失败**只返回 null / 空表，绝不删数据**；`observeAccount()` 逐条兜异常，**不要让异常进 `Flow.catch`**（会终止长驻订阅，界面整个会话停在未登录）。
**禁止**：再引入任何加密；直接用 `rawXxxAccountDao()`；找回已删除的 `CredentialStore` / `CredentialKeyException` / `AndroidKeystoreCredentialCipher` / `installRecovery` / `isKeyLost` 与「本机密钥失效」弹窗。测试见 `app/src/test/kotlin/com/yunx/app/data/db/SecureAccountDaosTest.kt`。

### 3.37 迅雷 / 123 登录
迅雷三个入口：密码（`xluser.core.login/v3/login`，新设备/异地必返回 `review_panel(1007)`）、短信（`sendsms` → `smslogin`，重发冷却 `SMS_RESEND_COOLDOWN_MS = 60s`，**服务端确认发出后才计时**）、网页（`pan.xunlei.com`）。进登录页与切换登录方式都必须调 `resetLoginStep()`。
`Uri.getPort()` 无端口返回 `-1`（白名单只能写 `port != -1 && port != 443` 才拒绝）；剥 `Bearer ` 先 `trim` 再匹配；`captcha/init` 的 `meta.user_id` 必须传本次登录的 `XunleiLoginStep.userID`（`initCaptcha(..., userId = step.userID)`），`currentUserId` 仅兜底。
App 与网页 token 属**不同 OAuth 客户端**：App = `Xp6vsxz_7IYVw2BB` + secret（表单刷新 + `X-Device-Id`）；网页 = `Xqp0kJBXWhwaTpB6` 无 secret（JSON 刷新 + `X-Client-Id` + Origin/Referer + 桌面 UA），落库记 `authType = "webToken"`（`MIGRATION_18_19`）且认证备份要带。网页凭据读 localStorage `credentials_Xqp0kJBXWhwaTpB6` 只是文本，须 `GET /drive/v1/about` 验证通过才落库。
123 双路：网页读 localStorage `authorToken`；账号密码 `POST https://user.123pan.cn/api/user/sign_in`，成功判定 `code == 200`（**不是 0**），密码不 trim、账号 trim，头需 `platform: web` / `app-version: 132` / `loginuuid`（`Pan123DeviceId`）/ Origin / Referer；失败文案统一走 `Pan123LoginSupport.describeFailure`，**绝不复述服务端原文**（会回显账号甚至密码）。
登录判定逻辑必须放在**不碰 JSON 的纯函数**里（`fieldsFrom` / `parseRawToken` / `isValidToken`），因为 JVM 单测里 `org.json` 是空壳（见 §4）。

### 3.38 磁力（BT）下载
内核零改动（现 AAR 已含 BT/磁力/ed2k），`schema == "MAGNET"` 走 `internal/protocol/bt`，任务用 `POST /api/v1/tasks` 建。建任务立即返回 ⇒ **无 peer 时状态 `running` 但 `total = 0`**；落盘由引擎定（`<下载目录>/<种子名>/...`，`opts.name` 不参与），完成后必须读 `GET /api/v1/tasks/{id}` 的 `meta.res.name` 才是真路径。
做种必须显式关：`seedRatio = 0 && seedTime = 0 && seedKeep = false` 反而永不退出，能立刻停的是 `seedTime = 1`。启动配置的 `downloadConfig` 只在空库首次生效 ⇒ 必须 `GET /api/v1/config` → 改 `protocolConfig.bt` / 顶层 `maxRunning` → `PUT /api/v1/config`，**整份原样带回**（别冲掉 `downloadDir` / `trackers` / `listenPort`）。
**拦截不降级**：`DownloadManager.magnetBlockReason()` 在 `enqueue` 就拦下并落带原因的失败任务（「未导入内核」/「未切 Gopeed」两种文案分开）。`MagnetLink.kt` 是纯字符串解析、**不用 `android.net.Uri`**；`DownloadSaver.delete` 必须支持目录（多文件种子是目录）。

### 3.39 最大同时下载任务数
并发由引擎自带，只需把用户值下发进 `maxRunning`（在 `DownloaderStoreConfig` **顶层**，不在 `downloadConfig` 里），**绝不能下发 0**（`MaxRunning == 0` 时 `Init()` 给 5、`Merge()` 回退旧值）。
调大上限不会自动放行排队任务（`notifyRunning` 只挂在任务结束）⇒ `GopeedEngine.applyMaxRunning()` 下发后自己补位：`GET /api/v1/tasks` 数出 running/wait，按空位数逐个 `PUT /api/v1/tasks/{id}/continue`，**严格按空位数，一个都不能多**（多传会让 `Continue` 暂停正在跑的任务让路）。
`status = "wait"` 必须回写成 `STATUS_PENDING`，否则落 `else` 显示 0%「下载中」像卡死。设置项选项 1/2/3/5/8、默认 3。已知边界（取舍非 bug）：调小不打断在跑任务；GitHub 固定走内置下载器 ⇒ 可能「3 引擎 + 1 内置」比设置多一个。

### 3.40 诊断模式与日志导出
`util/LogExporter.kt` 就是 `query(listOf("logcat", "-d", "-v", "time"))`，**不加任何过滤**（logd 按 uid 隔离，本来就只读到自己 uid 且含所有进程与闪退那次）；文件名 `yunx_log_<yyyyMMdd_HHmmss>.txt`；`writeLogLines()` 要丢掉 NUL。
诊断模式入口：设置 → 关于云析长按 → 开发调试，持久化 `SettingsRepository.diagnosticMode`（键 `diagnostic_mode`，默认关）；`DiagnosticLog.setEnabled` 立刻起/停写线程（关闭前先 flush），落盘 `getExternalFilesDir("diagnostic_logs")`（拿不到回退 `filesDir/diagnostic_logs`），按模块分 `db/crypto/download/webview/network/operation.log`。
六条硬约束：关闭时 `log()` 首行 `if (!enabled) return`；绝不阻塞调用方（有界队列 2000 + 守护线程 `yunx-diagnostic-log`）；两道限流（丢队列 + 全局 300 行/秒）；轮转单文件 2MB / 每模块 5 个 / 目录 10MB，**按 `lastModified` 从旧到新删**（不能按文件名排序）；时间戳在写线程格式化；`dbOp{}` 记表名/操作/耗时/行数并原样抛异常。
`body` 只在非 200/206 或抛异常时记、各截 1.5KB；请求体用 `TeeRequestBody` 只复制声明长度 ≤64KB；crypto 只记用途与长度、**绝不记明文/密文**；`DiagnosticWebViewClient` 只对未被子类覆写的回调生效 ⇒ 各登录页覆写体里要再插一行 `DiagnosticLog.webview(...)`。
**禁止**：给 logcat 加 `--pid` / `--uid` / 任何白名单（`--pid` 会滤掉用户复现闪退那次；`--uid` 在 vivo V2065A / Android 10 报 `Unrecognized Option` 并把 Usage 灌满文件）。

### 3.41 底部导航栏（Miuix 悬浮底栏）
`Scaffold` 只用 `topBar` 槽，`bottomBar` / `floatingToolbar` / `floatingToolbarPosition` 三槽**一律不用**；底栏是内容 Box 的兄弟节点，调用处只给 `AppBottomBar(..., Modifier.align(Alignment.BottomCenter))`，外壳（inset、左右 24dp、上限 540dp、贴底居中）由 `AppFloatingBottomBar` 自己负责。**`Scaffold` 必须保留**（`BlurredBar` 折叠靠它算 inset；Overlay 弹窗需要它的 Popup 图层，无 Scaffold 祖先会崩）。
Rail 与底栏**严格互斥**：`isWideWindow(w) = w >= 600`、`shouldShowNavigationRail = isWide && !useFloating`、`shouldShowBottomBar = !isWide || useFloating`，两条必须互补（`WindowLayoutTest` 钉住）；判据用 `BoxWithConstraints.maxWidth`，**不用 `LocalConfiguration.screenWidthDp`**。
内容层必须先于底栏布局（折射采样方向「内容 → 底栏」）；宽度唯一来源是 `FloatingBottomBar` 内部的 `Modifier.width(IntrinsicSize.Min)`，槽位最小宽 `FLOATING_BAR_ITEM_MIN_WIDTH_DP = 72`、上限 `FLOATING_BAR_MAX_WIDTH_DP = 540`。
避让用常量 `BOTTOM_BAR_CONTENT_PADDING_DP = 100`：`LocalScaffoldPadding` bottom = `showBottomBar ? 100.dp + bottomInset : bottomInset`，`bottomInset` 只算一次、内容与 Snackbar 共用；Snackbar 判据是「有没有底栏」而非「哪一档」。`AppBottomBar` 门面只收 `style`、不收独立 `isFloating`。

### 3.42 视频解析
平台标识单一真源 = `data/network/video/VideoPlatform.kt` 枚举 + `VideoParsers` 注册表（账号页顺序由 `supportedPlatforms` 的 `sortedBy needsLogin` 驱动）；人类可读名只有 `SharePlatform.displayName` / `VideoPlatform.displayName` 一处。`VideoLinkParser` **只认链接、故意不支持裸数字 ID**（会吃掉提取码/迅雷口令里的数字）；短链由 `VideoParseRepository` 展开重定向后再识别。
失败必须抛 `VideoParseException(kind)`，文案由 `userMessage()` 给（403/418/429 → 平台风控），**不许静默失败**；**不许把登录墙报成「内容已删除」**——按 `RawResponse.finalUrl` 识别 302 到登录/验证码页（范例 `XhsParser.fetchPage`）。媒体直链必须过 `withSecureMediaUrls` → `VideoHttp.secureUrl` 归一 https。
小红书图集换域名取去水印原图 `https://sns-img-bd.xhscdn.com/<图片ID>`（ID = 去掉 host 后前两段路径之后的整段、截掉 `!` 规格），逐个 HEAD 探测，404/410 回退原图并写 `VideoParseResult.notice`。动图合成 Motion Photo：静图 JPEG 插两段 APP1（XMP 在前 + OPPO Exif `UserComment` 标记串 `oplus_8388608`），插入点 = `4 + APP0 长度字段值`。
登录 WebView 一律桌面 UA（`VideoHttp.UA_CHROME`），**不给平台换移动 UA**；`loginUrl` 直指登录页；表单被裁的平台用 `VideoPlatform.loginViewportWidth`（小红书 1280）。登录态一律**行为探测**（`validateCookie` + `followRedirects = false` 只看 3xx Location），探测失败一律放行；`VideoLoginScreen` 取 2.5s 游客基线、Cookie 变了才自动保存，并 `setAcceptThirdPartyCookies(true)`。
下载只加 `DownloadPlatform.VIDEO` 一个常量，m3u8 自动走 HLS；简介/正文必须落 `VideoParseResult.text`；「仅下载音频」有 `audioUrl` 就直下，否则 `audioOnly = true` 下整段再抽轨（`AudioExtractor` 不重编码，流量同视频）。`runTask` 里 `audioOnly` 必须排在 `mergeAudioUrl` **之前**且强制走内置下载器（`needMerge`，落库 DB v24）；`DownloadSaver.mimeOf` 要把 `m4a/m4s` 映射成 `audio/mp4`；批量下载给文件名加「作品标题/」前缀。
**禁止**：把 drawable 资源 id 塞进 `VideoPlatform`；**按 Cookie 名判登录态**（游客 `web_session` / `PHPSESSID` 会让登录页刚打开就被自动保存关闭）；加载作品封面与作者头像；依赖 yt-dlp / gallery-dl / curl_cffi / ffmpeg。

### 3.43 在线预览与播放（含 B 站 DASH 合轨）
入口统一走全局启动器 `ui/preview/PreviewLauncher.kt`（发请求 → `MainScreen` 挂载），`PreviewTypes.kindOf(fileName)` 是**唯一**「能否预览」判据；云盘页可见性统一由 `ui/preview/CloudPreview.kt` 的 `canPreview` 判断。**禁止**在十个云盘页各抄一份扩展名判断。
`PreviewMedia.headers` 必带，云盘预览必须复用下载那份 `downloadHeaders`（**别另建第二个取链/请求头来源**）；取链成功后先清 `actionFile` 再 `PreviewLauncher`；`DownloadLink.isHls` 一路带到 `CloudPreview.open(..., isHls =)`。
起播参数（别压低缓冲，调参只改 `previewLoadControl()`）：`minBuffer 15s / maxBuffer 50s / 起播 1.2s / 重缓冲后 3s` + `setPrioritizeTimeOverSizeThresholds(true)`。图片 = Coil 3 `AsyncImage` + `HorizontalPager`；视频/音频 = Media3 ExoPlayer + `DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)`，m3u8 显式 `HlsMediaSource`，`DisposableEffect` 里 `player.release()`；文本 = OkHttp `Range: bytes=0-524287`（仅 512KB，UTF-8 → GBK → ISO-8859-1）。
缩略图字段各家不一：百度 `thumbs.url3 > url2 > url1`、139 `thumbnailUrls[]`（style=Large 优先）、迅雷 `thumbnail_link`（列表请求必须带 `thumbnail_size=SIZE_SMALL`）、光鸭 `thumbnail|thumbnailUrl|cover`、123 只有原图直链；夸克/UC 需账号 Cookie。**列表缩略图开关关闭时不许发任何取图请求**（不是加载后隐藏）。
B 站 DASH 合轨：`playurl?fnval=4048&qn=120`（仅已登录；未登录 `fnval=1`）→ `ResolveViewModel.downloadVideoMedia` 传 `mergeAudioUrl` / `mergeAudioHeaders` → DB v22 两列（明文落库）→ `runTask` 的 `dashMergeDownload` → `DashMuxer.mux`（逐样本重封装，不重编码）。带合轨任务**强制内置下载器**；合轨失败**不做降级**，以可见错误结束。预览侧用 `MergingMediaSource` 流式合并。

### 3.44 支持开发页：赞助码循环轮播堆叠卡片
两张码（`weixin` / `sponsor_tidain_weixin`），当前牌在最前，另一张缩放 `0.93`、露出 `38dp`、倾斜 `4°`。唯一驱动量是带符号像素 `travel`（`t ≤ W → x = dir*t`；`t > W → x = dir*(t - 2W)`；终点 `t = 2W - P`），用普通 state + 松手后 `animate(...)` + `animJob`（重抓时 `animJob.cancel()` 且 `fingerX = travel`）。
**禁止**：`backSide` 初值改成 -1/0（必须 `+1`）；把 `travel` 换成 `Animatable`（快滑时 `snapTo` 排在松手后会把换牌动画打断）；引入第二个进度量；用 `if (isFront)` 硬切底牌样式（换牌帧会闪）；换成 `HorizontalPager`；把 `deckShiftPx` 写死往左（必须跟 `backSide` 符号）。换牌判定别再调严：距离 `min(88dp, 牌宽 × 18%)`、甩速阈值 `800px/s`。

### 3.45 视频平台登录：UA 对齐内核；Pixiv 手动填 Cookie
登录页 UA 的 Chrome 大版本**必须等于 WebView 内核真实版本**：`engineChromeMajor(context)` 从 `WebSettings.getDefaultUserAgent()` 抽，再用 `alignClientHintsWith(ua)` 一并调齐 `Sec-CH-UA` 与 `navigator.userAgentData`。**新增平台不要再手写固定 UA 版本号**，也**不要只改 UA 不改 client hints**。
Pixiv 走 `VideoPlatform.manualCookieOnly`：`needsLogin = loginUrl.isNotBlank() || manualCookieOnly`，`VideoLoginScreen` 见到它就转交手动 Cookie 页（不建 WebView），校验仍走 `VideoAccountViewModel.save` → `validateCookie`，`Cookie:` 前缀用 `normalizePastedCookie()` 去掉。**别再给 Pixiv 加回网页登录**（二段验证服务端在验码前就拒，实测 `common:error.general.pleaseReloadAndRetry`）。

### 3.46 文件上传（十家网盘 · 个人盘当前目录）
三层结构：① 通用引擎 `app/src/main/kotlin/com/yunx/app/data/upload/`（`UploadSource` SAF 区间流 / `UploadIo` 定长体 + `ossMultipart` + `s3Multipart` / `UploadSigning` 纯函数 / `UploadRunner` 串行状态机，进度节流 120ms）；② 每家 `XxxApi.upload(io, file, 目录标识, 凭据…)`（夸克/UC 共用 `data/network/QuarkUpload.kt`）；③ 页面 `rememberUploadPicker` + `UploadProgressDialog`。入口只在个人盘页的「+」菜单第二项，**分享浏览页不挂**。
**禁止**：控制类请求（初始化/合并/中止）在 `start == null` 时不写 `body = ""`（`FileRequestBody` 会把整个文件当请求体再发一遍）；用 `String.toRequestBody(MediaType)` 发文本体（OkHttp 无 charset 时会补 `; charset=utf-8`，OSS V1/S3 按原样 Content-Type 签名 ⇒ 403 `SignatureDoesNotMatch`）；每读 64KiB 就 `withContext(Main)`；在上传过程中读 `dirStack`；点击时取一次凭据用到底；用 `GetContent`（要用 `OpenMultipleDocuments`，否则多文件串行中途读不到）；123 有 `UploadId` 时绕过预签名分片。
上传完成后统一 `reloadCurrent()`（**蓝奏云优享版除外**，它必须轮询结果接口到 `status = 1`）。取消 = 标志位 + `Call.cancel()` + 读循环 `alive()`，`IOException` 且已取消时转 `CancellationException`。

### 3.47 百度个人盘列表：App 通道优先 + 网页通道兜底
`BaiduApi.listCloudFiles` 固定两条通道（`listDir` 也走它）：App 通道 = `pan.baidu.com/api/list` + `clienttype=1` + `version=13.15.4` + **`channel=`（键在值空）** + `bdstoken = MD5(BDUSS)` + `devuid/cuid/apn_id/freeisp/queryfree` + `netdisk` UA + `start/limit`；网页兜底 = `channel=chunlei&clienttype=0&web=1` + 网页 UA + `Referer: pan.baidu.com/disk/main` + `page/num`。
**禁止**：参数与 UA 跨通道混用（`UA_APP` 版本号必须与 `APP_VERSION` 一致，百度会交叉校验）；两套 `bdstoken` 互换（App 用 `MD5(BDUSS)`，网页用 `gettemplatevariable` 的 token；上传与文件管理仍走网页通道）；失败 `return` 空列表（必须抛「百度文件列表加载失败（errno=…）」，否则界面既无错误也无文件）；从中途页切换通道（会漏文件）；采集硬件标识做 `devuid/cuid`（用 BDUSS 派生）。
兜底成功写 `event=baidu_list_web_fallback`，双失败写 `event=baidu_list_failed`；翻页用 `seen` 去重 + 满页终止 + 封顶 100 页。

### 3.48 账号页排序与「隐藏未登录的网盘」
按钮画在全局顶栏（`ui/MainScreen.kt` 的 `actions`，条件 `currentTab == MainTab.Drive && !driveSubPageOpen`），列表与状态在 `ui/screens/DriveScreen.kt`，三处连改：`MainScreen` 两个 `rememberSaveable`（`showDriveSort` / `driveSubPageOpen`）、`DriveScreen` 三参数（`showSortDialog` / `onDismissSortDialog` / `onSubPageChange`，`drivePage` 经 `LaunchedEffect` 回传）、`DriveSortDialog`。
排序按 `SettingsRepository.driveAccountOrder` 的 id 名次做**稳定** `sortedBy`（加新网盘不需迁移设置）；上移/下移 `onMove(from, to)` → `removeAt` + `add` → 即时落盘。**弹窗必须传 `orderedDriveItems`（含被隐藏项）**，传过滤后的列表会让隐藏项无法恢复。**别用拖拽**（只有十来行，用上移/下移箭头 + `contentDescription`）。纯计算在 `ui/screens/DriveOrder.kt`（`rank` / `sort` / `visible` / `hiddenCount` / `move`），规则由 `DriveOrderTest` 钉住 —— 改排序或过滤规则改那里，别在 `DriveScreen` 里重写内联逻辑。
隐藏只藏「需要登录且未登录」（GitHub 未配置 Token 算未登录），**免登录平台永不隐藏**；尾部提示「已隐藏 N 个未登录的网盘、M 个未登录的视频平台」。额度必须在 `LazyColumn` **外** `collectAsState`（九个平台各一个 `*Quota`，蓝奏无额度接口 ⇒ `quota = null`），否则每帧重订阅会掉帧。

---

## 4. 交付前验证

**先跑三个本地脚本**（`~/.yunx-bal/` 下，能挡住大部分 CI 往返）：

1. `python3 ~/.yunx-bal/ktcheck.py <改动的 .kt ...>`：词法检查（注释嵌套 / 字符串闭合 / 括号配对）；
2. `python3 ~/.yunx-bal/impcheck.py`：**必须整项目跑（无参数）**，找「用了但没 import」——它靠「别处 import 过这个名字」当证据，只扫单文件会漏报；
3. `python3 ~/.yunx-bal/vischeck.py`：**必须整项目跑**，找跨文件可见性冲突（`Cannot access ...`），`impcheck` 查不到这类错。

再自查：import 齐全 / 实验性 API 注解 / 符号一致性（改签名后 grep 旧调用点）/ Room 一致性（`version` +1 且 Migration 已注册）/ 命名风格与同目录一致。
提交前跑 `./gradlew.bat :app:compileDebugKotlin` 与 `:app:testDebugUnitTest`（本机是 Windows + pwsh：要写 `.\gradlew.bat`，不需要设 `JAVA_HOME`）。

### 常见编译坑

| 坑 | 处理 |
| --- | --- |
| `FlowRow` / `FilterChip` | 需 `@OptIn(ExperimentalLayoutApi::class)` / `ExperimentalMaterial3Api` |
| `combinedClickable` | 需 `@OptIn(ExperimentalFoundationApi::class)` |
| `Unresolved reference: pointerInput` + 同 lambda 里 `detectTransformGestures` / `size` / `detectTapGestures` 全报 `Cannot infer type` | **一个 import 缺失、级联一片**。`pointerInput` 在 `androidx.compose.ui.input.pointer`，手势检测器在 `androidx.compose.foundation.gestures`（两个包容易记混）。用 `impcheck.py` 定位真正缺的那一个 |
| `Cannot access companion object Companion: it is private in Foo` | `private companion object` 会连带锁住它自己的成员，别处一律访问不到。用 `vischeck.py` 查 |
| `rememberSaveable` 报 `Unresolved reference` | 包名是 `androidx.compose.runtime.saveable`（不是 `runtime`），写错会级联出一片假报错 |
| `animateColorAsState` 报 `Unresolved reference` | 在 `androidx.compose.animation`（不是 `.animation.core`）：`.animation` 放进出场/内容切换，`.animation.core` 放时间曲线与动画值 |
| 单测里 `JSONObject` 解析永远是 null / CI 红 | 工程开了 `unitTests.isReturnDefaultValues = true`，JVM 单测里 `org.json` 与 `android.net.Uri` 都是**空壳**。把判定逻辑抽成不碰 JSON / Uri 的纯函数（收 `Map` / `String`）再测 |
| `Unclosed comment` + 一串行号错位的语法错 | 注释正文里出现了 `/*`（Kotlin 块注释可嵌套，会一路吞到文件尾）。典型来源：注释里写 `/api/v1/admin/**` 这类路径 |

---

## 5. 下载引擎重点笔记（改 `DownloadManager.kt` 前必读）

以下机制都是为修真实线上问题而存在，**不要随意「简化」**。**动手前先读**
`docs/engineering-notes/download-engine.md`（本节只放最容易被改错的常量与结论）。

```text
分片规划：chunkCount = chunkCountFor(total, threads)
          主池 = chunkCount × 0.7 → part_0 … part_{n-1}（等分区间）
          弹性区 = 剩余 30% 字节 → seg_{start}_{end}.part（按字节顺序领 4MB 块）
在飞上限 = inflightLimiter（全进程共享 Semaphore，绝不手动 release）
MAX_INFLIGHT_CHUNKS = clamp(maxHeap / 8 / 64KB, 8, 512)   // BUFFER_SIZE = 64KB
actualWorkers = min(effectiveWorkers, MAX_INFLIGHT_CHUNKS)
chunkCountFor：minChunkBytes=256KB；bySize <5MB→1 / <50MB→8 / <500MB→32 / else 64
               want = maxOf(bySize, threads×8)；return minOf(want, total/256KB, 512)
抢占：PREEMPT_MIN_BPS=12KB/s · MIN_AGE=15s · COOLDOWN=10s · MIN_REMAIN=128KB
      MAX=3 · PER_TICK=2 · ENDGAME_INFLIGHT=3 · ENDGAME_MIN_AGE=3s
CDN：RANGE_WORKERS_CAP=8 · RANGE_IGNORED_TOLERANCE=3 · STAGGER_CAP=8 · STAGGER_MS=25
```

- **禁止**改回「每任务一个信号量」与「中点劈分」；分片 IO 必须走 `chunkIoDispatcher`（`core = max`，**不能退回 `Dispatchers.IO`**）。
- `HttpClients.buildDownload()` 协议固定 `HTTP_1_1`（去掉每流 16MB 窗口）；`buildApi()` 不设 protocols。
- 主池 / 弹性区 / 失败重试三条路径都必须走 `inflightLimiter.withPermit`；`threadCount` 原样传 `chunkCountFor`（`plan.txt` 签名不可变，否则全体用户断点失效）。
- 进度落盘按时间节流 **500ms**（不要改回按字节，历史 ANR）；合并阶段用 `mergeReportIntervalMs = 300L` + `DownloadStats.mergePercent` 上报，**不要**加 `STATUS_MERGING`、**不要**用 `downloadedSize == totalSize` 判合并。
- 并发安全：`activeJobs` 全在 `jobsLock` 内、`finally` 只移除自己注册的 deferred、`taskLocks` 不在 finally 清理、暂停以磁盘 part/seg 长度回写进度。

---

## 6. 代理工作规范

### 6.1 动手前

1. **先读同类文件**再写新代码（加页面先读 `BookmarkScreen.kt` / `AboutScreen.kt`）。
2. 涉及 **下载引擎 / Room 迁移 / 凭证存储**（`data/security`、`SecureAccountDaos`）的改动，**先说明方案并等用户确认**再改。
3. **不引入新依赖、新架构**（DI 框架、Navigation-Compose、其他网络库）。确需引入时先说明必要性并取得同意。

### 6.2 改动中

- 注释用中文，解释**为什么**这样写（尤其是绕过平台限制的 workaround）。
- 按「数据层 → ViewModel → UI → 入口接线」的顺序推进；改完 grep 旧签名残留。
- 不做超出任务范围的顺手重构。

### 6.3 交付时

- 数据库版本变更、分片规划变更、并发上限变更，**必须在总结里显式标注兼容性影响**。
- 性能类改动要给**可验证方法**（如看 `分片规划:` 日志里的 `threads` / `effectiveWorkers`），不要只声称变快。
- 业务逻辑 / 数据处理 / 修 Bug 的改动**先写测试**（红 → 绿 → 重构）。红灯必须是业务断言失败（不能拿编译错冒充），禁止 `@Ignore` / 注释掉用例伪造绿灯，断言要验业务输出而不是「不抛异常」，计算与规则逻辑至少两组差异化数据。豁免：纯 UI 样式/布局/动效（其底层状态与数据计算仍需测）、配置与依赖升级、纯文档。

---

## 7. 长期风险提示

| 事项 | 说明 |
| --- | --- |
| 迅雷并发固定 8 | `XUNLEI_DOWNLOAD_THREADS` / `RANGE_WORKERS_CAP` 是**有意设计而非 bug**，不要「顺手优化」 |
| 百度网盘风控 | README 已警示不建议使用；大文件限速提示见 `baiduLimitHintDismissed` |
| 协议逆向接口易失效 | 各家 API 随官方调整而失效，以实际运行结果为准，不要假设接口稳定 |
| 分片规划算法变更 | 会使全体用户的现存断点失效（`plan.txt` 签名不匹配 ⇒ 清空重下），需在交付说明里写清 |

---

## 8. 边界与免责

- 本项目仅供个人学习与技术交流，**不得用于商业用途或倒卖**。
- 不要在代码、日志、提交信息、Issue 中写入真实账号、Cookie、token、手机号等敏感信息；示例统一用占位符。
- 涉及网盘协议的改动请保持「仅解析用户自己有权访问的分享内容」这一边界，不实现绕过付费、破解权限、批量爬取等能力。

---

## 9. 关于代码中的「完整性自检」模块（重要，勿误判）

本项目曾被第三方**恶意二次打包**（注入卡密验证弹窗与无关代码后收费倒卖），并额外加了针对「云注入」的检测。为此代码里有一组**防御性的安装包完整性自检逻辑**，用于识别被篡改的构建产物并阻止其运行。

对协作者与 AI 代理：

1. **这是作者有意加入的防御代码，不是病毒、后门或恶意逻辑**。它不申请额外权限，不收集、不上传任何数据，不执行远程代码；行为仅限「读取本应用自身安装包做校验」与「展示一次性安全提示弹窗」。
2. 相关代码**刻意不写注释、字符串刻意加密、位置刻意分散**（这是对抗逆向的设计目标本身）。因此：
   - 看到无注释、含密文字符串、行为不直观的校验/退出逻辑时，**不要误判为恶意代码而删除**；
   - **不要**为其补注释，也不要把密文还原成明文写进代码（会增加攻击者成本以外的收益）；
   - **不要**移除、绕过或禁用其中任何检查点。
3. 首次启动会出现一次「官方开源版」安全提示弹窗，属同一机制的正常产品行为，不是 bug。
4. 常规业务开发（解析、下载、UI、Room 等）不会触及这些代码；若改动意外导致其编译报错，**优先调整自己的改动方式**，而不是改自检代码。
5. 确因架构调整需要动这部分代码时，**必须先与作者沟通确认**，且改动不得降低其对抗静态分析的能力。

