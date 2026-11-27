# 苹果 Liquid Glass 动效系统设计（A 路线 · 第一性原理优化版）

- 日期：2026-11-27
- 基线：v3.0.0-test8（versionCode 142）
- 目标交付：v3.0.0-test9（versionCode 143）
- 状态：已批准执行
- 范围：动效连续性、交互反馈、玻璃层级、无障碍与性能安全

## 1. 第一性原理

用户感知到的“高级动效”不是动画数量，而是四件事：

1. **因果**：画面变化必须由用户动作或数据变化解释。
2. **连续**：同一个对象在页面/状态之间移动时，位置、尺寸、层级不能断。
3. **克制**：同一时刻只有一个主动画层级，其他元素保持安静。
4. **可逆**：手势取消、返回、失败都能回到稳定状态，不锁死、不闪退。

由此得到硬约束：

- 每个动效只解决一个语义问题，不重复表达。
- 小尺寸节点不新增 Backdrop/AGSL/RenderEffect 渲染层。
- 减少动效不是“更快”，而是去掉位移、形变、无限循环与闪白。
- 没有真机数据，不宣称性能完美；模拟器只验证功能与视觉。

## 2. 目标

- 主流程、设置、选择器、预览、修复页、弹窗/Sheet 的转场连续。
- 所有可点元素反馈一致、可取消、有语义与触觉。
- Loading/Empty/Error/Success 不硬切，状态不滞后。
- 玻璃有 thin/regular/thick 层级，Backdrop 有尺寸安全边界。
- 关键路径动画有性能预算，test9 可安装、可回退、可验收。

## 3. 非目标与主动砍掉

- 不重写转换管线、MediaStore、删除/恢复语义。
- 不做全新导航框架；扩展现有 Screen/覆盖层逻辑。
- 不默认加入传感器高光、粒子、持续彩虹辉光。
- 不在 test9 引入 Macrobenchmark 模块；先用 JankStats/FrameMetrics 与现有测试取证，真机再决定是否建基准。
- 不把所有页面重画成新视觉；只改影响连续性、反馈与层级的节点。
- 不再次升级 Kotlin/Compose，不替换 Haze/Backdrop。

## 4. 审计结论

### 已具备

- 预测返回跟手/取消回弹、页面转场与减少动效分支：`MainActivity.kt:253,1115,1517`。
- 滑块/预设/Canvas 色散、连续圆角、CJK 字阶、崩溃自动降级：`GlassSlider.kt:117`、`GlassPresetSelector.kt:88`、`LiquidPress.kt:32`、`GlassCrashSafety.kt:12`。
- 网格转换忙碌/完成/失败反馈已成体系：`MainScreen.kt:1099`。

### 必须修

- Viewer/AlbumSheet/覆盖层未纳入统一返回优先级，可能误触底层：`MainActivity.kt:1115,1547`、`PickerScreen.kt:726,759`。
- Loading/Empty/Error/Success 多处 `when` 直切：`PickerScreen.kt:374`、`FixTimeScreen.kt:271`、`LocationRecoveryScreen.kt:379`、`AboutScreen.kt:64`。
- Picker Chip 缺选中/按压合同与 selected 语义：`PickerScreen.kt:981`。
- 部分减少动效分支仍允许 SizeTransform 弹簧/缩略图动画：`MainScreen.kt:384,541`、`Components.kt:182`、`OriginalViewer.kt:131,392`。
- onResume 未刷新系统 animator scale：`MainActivity.kt:1883`。
- HazeGlassCard 缺 Backdrop 最小尺寸约束：`GlassDesign.kt:382,524`。

## 5. 设计方案

### 5.1 MotionTokens：只统一，不抽象框架

新增 `ui/motion/MotionTokens.kt`，仅存放常量：

- `Snappy`：按压、徽标、Chip。
- `Standard`：状态切换、Sheet 回弹。
- `Gentle`：共享元素、预测返回。
- `Fast/Base/Slow/Completion`：120/180/240/340–450ms。
- `StateSizeTransform(reduceMotion)`：所有 AnimatedContent 显式使用；减少动效直接 snap。

不新增通用动画管理器；现有 composable 直接引用 token。

### 5.2 MotionPolicy

- `LocalReduceMotion` 仍是唯一判据。
- `MainActivity.onResume` 刷新 `MotionPrefs.refreshSystem()`。
- 新增 `LocalMotionPaused`（滚动/不可见时暂停非关键无限动画）。
- Loading shimmer 只在可见容器运行；spinner 不可见即不创建。

### 5.3 导航连续性：扩展现有模型

- 保留现有 Screen enum，新增“当前最上层 Overlay”状态，统一预测返回优先级。
- Viewer：Picker cell → 全屏图片使用 bounds 插值，取消返回跟手。
- AlbumSheet：draggable bottom sheet，拖拽关闭、速率吸附、减少动效直切。
- 修复页/找回位置忙碌时禁止底层返回。
- 不引入新的 Navigation 库或路由框架。

### 5.4 控件合同

- 所有可点元素：按下即响应、松开可取消、≥48dp、语义完整。
- Picker Chip：按压 spring + selected morph + selected 语义。
- Dialog/Snackbar/按钮：统一进出与 haptic，不与涟漪叠加重复反馈。
- Slider/Preset 保持现有 Haze+Canvas 路径，不新增渲染层。

### 5.5 状态编排

只提供三个轻量 helper：

- `StateCrossfade`：显式 fade/SizeTransform，减少动效 snap。
- `GlassLoadingSkeleton`：共享相位 skeleton。
- `GlassEmptyErrorState`：图标 + 文案 + 单一主动作。

应用到关键页面：Picker、FixTime、LocationRecovery、About。Main/Settings 已有状态动效，只补缺口，不重做。

### 5.6 玻璃层级

- `GlassLevel.Thin / Regular / Thick` 只映射现有材质参数，不新建渲染引擎。
- Thin：小控件、Chip、Slider thumb、Snackbar；禁止 Backdrop。
- Regular：内容卡片、Picker/Dock。
- Thick：Dialog、全屏 Sheet、高文本密度浮层。
- Backdrop 最小短边 96dp；不足直接 Haze/膜，并有自动化测试。

### 5.7 性能与无障碍

- 每个可见容器最多一个无限 shimmer；缩略图 pulse 在 reduce motion 下静态。
- Viewer/Sheet 关闭后立即停止动画与触摸。
- 完成/失败 haptic 同帧且只触发一次。
- 2× 字号、深色、浅色、照片氛围、兼容模式、减少动效全部进入截图矩阵。
- JankStats 只采集关键页面，不做全量常驻监控。

## 6. 工作包与顺序

### WP0 不变量与安全门禁（先做）

- MotionTokens、MotionPolicy、系统 animator scale 刷新。
- Backdrop 最小尺寸 guard 与测试。
- 减少动效审计：SizeTransform、缩略图、Viewer、spinner、闪白。

验收：现有行为零回归，安全约束可自动测试。

### WP1 导航连续性（用户感知最大）

- 覆盖层返回优先级。
- Viewer 共享元素转场。
- AlbumSheet 拖拽与预测返回。

验收：Picker→Viewer→返回、Sheet 拖拽/取消、修复页返回不误触底层。

### WP2 控件与状态反馈

- Picker Chip 合同。
- Dialog/Snackbar/按钮反馈统一。
- 关键页面 Loading/Empty/Error/Success helper 接入。
- Haptic 扫尾。

验收：所有关键状态不硬切，所有可点元素反馈一致。

### WP3 材质与视觉收口

- thin/regular/thick token 接线。
- 字阶/间距/圆角/状态色统一。
- 选中态液滴形态、玻璃边缘色散收敛。
- 共享 shimmer 与离屏停止。

验收：浅/深、2× 字号、减少动效、兼容模式截图矩阵通过。

### WP4 验证与发布

- 全量 JVM、Compose UI、截图、lint、签名 Release。
- host GPU 模拟器关键路径。
- Jank/PSS 基线记录；真机门禁留给用户设备。
- 发布 v3.0.0-test9 Pre-release。

## 7. 测试计划

- 单元：MotionTokens 数值、GlassLevel 选择、Backdrop 尺寸 guard、ReduceMotion policy。
- Compose UI：按压/释放/取消、selected/toggle、Sheet 拖拽、预测返回、状态切换。
- 截图：浅/深、标准/流畅、2× 字号、减少动效、兼容模式、照片氛围。
- 模拟器 host GPU：Viewer/Sheet/Picker/设置/网格转换关键路径。
- 真机：帧率、PSS、触感、崩溃最终验收；不在真机缺席时宣称性能结论。

## 8. 验收标准

- 小节点无 Backdrop；尺寸 guard 有测试。
- 减少动效下无无限循环、无 SizeTransform 弹簧、无闪白。
- 可点元素 ≥48dp，语义与触觉完整。
- Viewer/Sheet/覆盖层返回连续，不误触底层。
- 关键状态不硬切、不滞后。
- 全量 JVM、lint、签名构建通过；关键路径无新增原生崩溃。
- 用户过目关键动效后才发布正式版。

## 9. 风险与缓解

- 动效过多：同一时刻只保留一个主动画层级。
- GPU 成本：小节点禁 Backdrop、共享 shimmer、离屏停止、Performance 档。
- 导航回归：先修返回优先级，再做共享元素；每步单独验证。
- 范围膨胀：严格按 WP0→WP4，非目标项不做。
- 模拟器误判：性能结论以真机为准。

## 10. 效果预览

- `docs/design-previews/a-route-main-flow.png`
- `docs/design-previews/a-route-materials.png`
- `docs/design-previews/a-route-navigation-states.png`
