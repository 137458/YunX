# 更新日志

## [未发布]

### 修复
- 修复 Toast 提示被底部悬浮底栏遮挡的问题，在悬浮底栏模式下精确注入 88dp 底部避让间距
- 修复网盘页面及云盘文件列表下拉刷新指示器位置偏顶被顶栏遮挡的问题，补齐 contentPadding 与 topAppBarScrollBehavior 联动
- 修复设置页选项被悬浮底栏遮挡的问题，在全局 LocalScaffoldPadding 中计算悬浮工具栏避让高度，确保末尾选项完整露显
- 修复下载管理页面右下角悬浮添加按钮 (FAB) 被底栏遮挡的问题，使其准确悬浮于底栏之上
- 修复设置页通知栏下载进度开关因取反赋值导致的状态反跳问题，直接绑定新状态
- 修复下载管理页批量操作栏在外层定位导致被 TopAppBar 遮挡的问题，统一内嵌至列表顶部并对齐内边距

### 新增
- 接入 MIUIX 沉浸式顶部渐变毛玻璃组件 `BlurredBar`，主页、关于、书签、主题、支持开发各页面顶栏支持随滚动平滑淡入及 DstIn 渐变边缘过渡
- 设置页补充「通用直链 / 手动添加」并发线程配置项，完善网盘并发下载配置覆盖面

### 优化
- 升级全局 Toast 为现代 Miuix / HyperOS 风格半透明超椭圆悬浮胶囊 (`AppToast` 与 `AppSnackbarHost`)，自适应内容居中排版并增加柔和投影与微光描边，全面替换老旧直角 Material 3 Snackbar
- 优化各二级页面（书签、主题、关于、支持开发）内边距消费与滚动穿透顺序，适配沉浸式渐变顶栏


### 重构
- 重构液态玻璃底栏渲染管线：离屏高亮层补齐玻璃折射通道（修复滑块采到原始页面内容、双重折射层次丢失），滑块改用原生 innerShadow 离屏层实现凹陷感，外壳改用 dropShadow 柔和投影
- 底栏交互对齐生产实现：补充 selectableGroup 单选语义、遥控器/外键 Enter 与空格激活、触觉反馈、拖拽速度形变下沉到 Draw 阶段读取（消除拖拽期逐帧重组）、原地轻点当前 Tab 也可触发选中
- 触摸聚光点改为跟随滑块几何中心并绘制在内容之下，抬手回落到手势起点
- 接入 miuix-blur-android 官方模块，构建原生 Backdrop 背景模糊采样与 AGSL SDF 圆角矩形透镜折射、色散彩虹晕管线
- 重构液态玻璃底栏为双重背景采样架构 (CombinedBackdrop)，实现离屏高亮 Tab 透镜折射穿透与三态平滑降级
- 优化传感器生命周期管理，在应用后台自动注销重力监听；消除 Composition 阶段状态解包引发的高频重组风暴
- 修复交互聚光点物理触控坐标映射与 Tab 无障碍语义适配
- 重构悬浮底栏为生产级物理液态玻璃 (Liquid Glass Floating Bottom Bar)，集成弹簧阻尼拖拽手感、流体速度形变、陀螺仪 3° 量化重力高光、触摸聚光点与三态平滑降级
- 全面迁移 UI 组件体系至 Miuix (HyperOS Compose)，彻底清退废弃 Material 3 组件
- 悬浮底栏切换为 Miuix 官方 FloatingNavigationBar 与 FloatingNavigationBarItem，清理手写伪三态逻辑
- 抽离通用云盘文件浏览模板骨架 CloudDriveScaffold 与 CloudFileSheets，统一 6 大网盘列表与操作抽屉
- 收敛双主题系统，MD3 ColorScheme 直接由 MiuixTheme 映射，消除冗余 SchemeTonalSpot 计算与符号混淆
- 彻底清理 top.yukonga.miuix.kmp 伪命名空间垫片代码，规范迁移自研组件至 com.yunx.app.ui.components
- 主屏幕集成 Miuix 官方悬浮胶囊导航栏与自适应 NavigationRail，接入 Miuix 官方顶栏滚动折叠联动
- 重构设置页为 Miuix 卡片化列表，接入 SwitchPreference、ArrowPreference 与 SliderPreference
- 迁移各网盘文件管理页面为 Miuix 物理阻尼 PullToRefresh 下拉刷新与 BreadcrumbBar 面包屑路径条
- 迁移多选操作栏为 Miuix 胶囊悬浮工具栏 FloatingToolbar
- 统一重构所有网盘登录页面、解析模块、下载模块及业务弹窗为 Miuix WindowDialog 与 WindowBottomSheet 规范组件
- 清理 miuix-blur 与 miuix-nav 冗余依赖，移除 uses-sdk overrideLibrary 冲突规避配置与 AAR 禁用选项
