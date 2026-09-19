# 更新日志

## [未发布]

### 重构
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
