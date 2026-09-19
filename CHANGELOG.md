# 更新日志

## [未发布]

### 修复
- 修复液态玻璃底栏 L2 离屏层双重背景采样与底色冗余叠加致暗问题，移除多余 drawBackdrop 恢复清澈通透度
- 修复手势拖拽中频繁创建协程与 animateTo 追赶带来的跟手迟滞，改用 snapTo 瞬时跟手与 UNDISPATCHED 调度
- 修复宿主采样源 rememberLayerBackdrop 缺少底色垫底引发的 Skia 预乘透明黑边伪影，注入 surfaceColor 垫底
- 修复 AGSL 透镜折射着色器在滑块中心像素处除以零产生 NaN 的潜在 GPU 崩溃风险，引入 safeNormalize 保护
- 修复 Tab 点击与滑块手势释放时的双重触觉震动冲突，统一收敛由底栏内部调度

### 新增
- 「主题与外观」页新增「底部导航栏」分组，支持液态玻璃 / 毛玻璃 / 标准三档切换并持久化；Android 13 以下自动降级为毛玻璃

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
