# 苹果 Liquid Glass 动效系统设计（A 路线）

- 日期：2026-11-27
- 基线：v3.0.0-test8（versionCode 142）
- 目标交付：v3.0.0-test9（versionCode 143）
- 状态：待用户审阅
- 范围：全应用设计语言、动效、导航连续性、玻璃层级、无障碍与性能门禁

## 1. 目标

把“影中影”从已经成立的液态玻璃视觉，提升为一套可维护、可测量、可回退的苹果式动效系统：

- 每个操作都有短暂、精确、符合空间关系的反馈。
- 页面、缩略图、Sheet、Dialog 的进出有连续性，不硬跳。
- 玻璃材质有明确层级，不再靠 dense/liquid 布尔值隐式表达。
- 动效丰富但不堆叠；减少动效、系统动画开关、高对比与读屏都有完整分支。
- 不重新引入小尺寸 Backdrop/AGSL 崩溃路径。
- 关键路径有 Jank/PSS/帧时间门禁，真机验收前不宣称性能完美。

## 2. 非目标

- 不重写转换管线、MediaStore 数据语义或删除/恢复逻辑。
- 不默认加入传感器高光、粒子、持续彩虹辉光等不可控特效。
- 不把内容层全部改成 Liquid Glass；玻璃只用于功能层与 transient controls。
- 不在本轮替换 Haze/Backdrop 引擎，不做工具链再次升级。
- 不在真机验证前发布正式 3.0。

## 3. 现状与证据

### 3.1 已具备

- 预测返回跟手、取消回弹、提交续接；页面转场已有减少动效分支：`app/src/main/java/com/vliveconvert/app/MainActivity.kt:253,1115,1517`。
- 主界面摘要、空态、Dock 三态、对话框对称进出已有体系：`MainScreen.kt:284,381,538`、`GlassDesign.kt:593`、`PermissionScreen.kt:57`。
- 滑块 1:1 拖动、弹簧、速度形变、触点高光与色散：`GlassSlider.kt:117`、`GlassPresetSelector.kt:88`、`LiquidPress.kt:32`。
- 连续圆角、CJK 字阶、减少动效持久化、硬件/模拟器回退与原生崩溃自动降级：`ContinuousShape.kt:17`、`Type.kt:24`、`MotionPrefs.kt:18`、`GlassCrashSafety.kt:12`。

### 3.2 系统性缺口

- BrokenRepair/LocationRecovery 是覆盖层而非 Screen，Viewer 无统一进出转场：`MainActivity.kt:1547`、`PickerScreen.kt:759`。
- 加载/空/错/结果 `when` 直切：`PickerScreen.kt:374`、`FixTimeScreen.kt:271`、`LocationRecoveryScreen.kt:379`、`AboutScreen.kt:64`。
- 列表/网格直接更换 Lazy 容器；部分减少动效分支仍保留 SizeTransform 默认弹簧：`MainScreen.kt:444,384,541`。
- Picker Chip 缺按压/选中动效与 selected 语义，热区偏小；底部面板不可拖：`PickerScreen.kt:981,726`。
- 缩略图入场、Viewer 缩放、Crossfade、M3 spinner、完成闪光未全量门控：`Components.kt:182`、`OriginalViewer.kt:131,392`、`PickerScreen.kt:972`、`GlassDesign.kt:788`。
- 设计 token 未完全接线；dense/liquid 不等于语义材质层级；弹簧与曲线缺统一 token：`DesignTokens.kt:11`、`GlassDesign.kt:359`。
- onResume 未刷新系统 animator scale；部分完成态缺同帧触觉；每个加载缩略图各自运行无限 pulse：`MainActivity.kt:1883,2325`、`Components.kt:212`。

### 3.3 高风险

- 全局预测返回仍看旧页面标志，覆盖层可能误弹底层设置：`MainActivity.kt:1115,1548`。
- HazeGlassCard 可在任意尺寸进入 Lens/drawBackdrop；约 44dp Snackbar 也走该路径，缺最小尺寸硬约束：`GlassDesign.kt:382,393,524`、`Components.kt:443,449`。

## 4. 设计原则

依据 Apple HIG Motion / Materials：

1. 动效必须有目的、简短、精确，不能遮挡内容或让用户等待。
2. 直接操作的反馈要跟手；取消必须可回弹；转场要保留空间关系。
3. Liquid Glass 是功能层材料，不是内容层装饰；只用于重要交互与导航。
4. regular 材质优先保证可读；clear 只用于照片/视频等丰富背景，并配合 dimming。
5. 动效必须可关闭；重要反馈同时提供颜色、文字、图标、触觉等非动画通道。
6. 同一语义只保留一个主动画来源，避免重复 spring、无限 shimmer 与状态滞后。

参考：
- https://developer.apple.com/design/human-interface-guidelines/motion
- https://developer.apple.com/design/human-interface-guidelines/materials

## 5. 架构

### 5.1 MotionTokens

新增 `ui/motion/MotionTokens.kt`：

- `Spring.Snappy`：按压、小控件、徽标进入。
- `Spring.Standard`：页面内容、状态切换、Sheet 回弹。
- `Spring.Gentle`：大卡片、共享元素、预测返回。
- `Duration.Fast / Base / Slow / CompletionBeat`：120 / 180 / 240 / 340–450ms。
- 统一 `EaseOutStrong` 与线性 progress easing 的适用边界。
- 所有 `AnimatedContent` 必须显式声明 `SizeTransform`；减少动效时使用 `snap()`，不允许默认弹簧漏网。

### 5.2 MotionPolicy

- `LocalReduceMotion` 保持唯一动画判据。
- `MainActivity.onResume` 调用 `MotionPrefs.refreshSystem()`。
- 新增 `LocalMotionBudget`（Normal / Reduced / Paused）：
  - Normal：完整动效。
  - Reduced：短淡入/颜色反馈，无无限循环、无闪白、无尺寸弹簧。
  - Paused：静态材质与进度语义，停止 shimmer/spinner。
- 离屏 composable 不启动无限动画；可见性变化即取消/恢复。

### 5.3 导航模型

新增统一 `AppRoute` / `OverlayRoute` 描述：

- Screen：Permission / Main / Picker / FixTime / Settings / About / BrokenRepair / LocationRecovery。
- Overlay：OriginalViewer / AlbumSheet / Dialog / Trash / StatusDetail。
- NavigationHost 统一处理前进、返回、覆盖层优先级与预测返回。
- Viewer 使用共享元素转场：Picker cell bounds → 全屏图片 bounds；取消返回跟手。
- AlbumSheet 使用 draggable bottom sheet，支持拖拽关闭、速率吸附与减少动效直切。
- 覆盖层忙碌时禁止底层返回，避免误弹设置。

### 5.4 控件合同

所有可点组件遵循同一合同：

- `PressFeedback`：按下即响应，松开可取消；弹簧参数来自 MotionTokens。
- 触控区域 ≥48dp；视觉尺寸与热区分离。
- `Role.Button / Checkbox / Switch / Tab` 与 selected/toggleable 语义完整。
- Haptic：选择 CONFIRM、危险操作 REJECT、数值步进 CLOCK_TICK；同一动作只触发一次。
- `GlassButton` / `GlassIconButton` / `GlassChip` / `GlassSlider` / `GlassDialog` / `GlassSheet` / `Snackbar` 使用统一进出与按压反馈。
- 预设 Chip 增加选中液滴/形态过渡；不再只做底色交叉。

### 5.5 状态编排

新增统一状态容器：

- Loading：共享相位 skeleton shimmer；不在每个卡片各自创建 infinite transition。
- Empty：柔和玻璃插画 + 单一主动作，fade/scale 进入。
- Error：语义色图标 + 可重试动作，brief shake/haptic；不整页跳变。
- Success：短闪白/徽标弹入/状态文案过渡；340ms 内收敛。
- 所有状态切换使用 Crossfade + 显式 SizeTransform；减少动效直接切换但保留文字/图标/触觉。

### 5.6 玻璃材质层级

新增语义材质 token：

- `GlassThin`：图标、Chip、Slider thumb、Snackbar；Haze+Canvas，禁止 Backdrop。
- `GlassRegular`：内容卡片、设置分组、Picker/Dock；Haze 优先，Backdrop 仅大尺寸。
- `GlassThick`：Dialog、全屏 Sheet、高文本密度浮层；更高不透明度与 dimming，保证可读。
- Backdrop 最小短边阈值：小于 96dp 直接走 Haze/膜；调用处增加 `requireGlassBackdropSize()` 断言与测试。
- 小尺寸节点继续使用 `LiquidPress` Canvas 色散，不新增 RuntimeShader。

### 5.7 性能预算

- 共享 shimmer phase：一个可见容器最多一个无限相位。
- 每个页面同时运行的无限动画数量有上限；spinner/shimmer 离屏即停止。
- 关键路径加入 JankStats/FrameMetrics 采样；Macrobenchmark 覆盖启动、列表滚动、Picker 进入、Viewer 开关、网格转换。
- 真机门禁：关键交互 P95 帧时间 ≤ 33.4ms，Jank 比例 ≤ 5%，PSS 相对 test8 不增加超过 5%。
- 模拟器只用于功能/视觉，不作为真机性能结论。

## 6. 数据流

- RouteState → NavigationHost → 页面转场/预测返回。
- InteractionSource → PressFeedback → MotionTokens → 视觉/触觉反馈。
- ConvertCenter / Scanner / Repository 状态 → UI State Model → Loading/Empty/Error/Success 编排。
- MaterialLevel + BackgroundLuminance + ReduceMotion + GlassQuality → Glass token → Haze/Backdrop/Canvas 选择。
- 任一渲染层不可用 → 自动回退下一层，不改变业务数据与语义。

## 7. 错误处理与安全

- Backdrop 尺寸不足、硬件不可用、模拟器/Robolectric：直接回退 Haze 或膜。
- RuntimeShader/AGSL 失败：不重试危险路径；保留 Canvas 色散与静态材质。
- 原生崩溃：`GlassCrashSafety` 自动开启兼容模式；用户可手动恢复。
- 动画取消/中断：不保留半透明浮层、不锁死进度、不重复触发 haptic。
- 数据删除、移动、恢复逻辑完全不变。

## 8. 无障碍

- 系统“移除动画”与应用内减少动效均即时生效。
- 所有状态不仅靠动画表达；颜色之外有图标、文字、语义与触觉。
- 高对比/减少透明度偏好下增加不透明 scrim。
- TalkBack：48dp 热区、selected/toggle、ProgressBarRangeInfo、状态描述完整。
- 大字号 2× 与长文案不溢出；Chip/Sheet/Dialog 使用 maxLines/ellipsis/自适应高度。

## 9. 测试计划

- 单元：MotionTokens 数值合同、MaterialLevel 选择、Backdrop 最小尺寸、ReduceMotion policy。
- Compose UI：按压/释放/取消、SetProgress、selected/toggle、Sheet 拖拽、预测返回、状态切换。
- 截图矩阵：浅/深、标准/流畅、2× 字号、减少动效、兼容模式、照片氛围。
- Macrobenchmark：启动、主列表滚动、Picker、Viewer、网格转换。
- 模拟器 host GPU：真路径视觉与交互；无窗口软件 GPU 不用于性能结论。
- 真机：用户设备完成最终视觉、触感、帧率、PSS 与崩溃验收。

## 10. 分阶段实施

### P0 设计冻结

- 完成 MotionTokens、MotionPolicy、GlassLevel 合同与 spec。
- 输出关键帧截图矩阵。
- 用户确认后进入实现。

### P1 导航连续性

- 统一 AppRoute/OverlayRoute 与预测返回优先级。
- Viewer 共享元素转场。
- AlbumSheet 拖拽/速率吸附。
- 修复覆盖层返回栈污染。

### P2 控件与状态

- 统一按钮/Chip/Slider/Dialog/Snackbar 合同。
- Loading skeleton、Empty/Error/Success 编排。
- Haptic 与 selected/toggle 语义扫尾。
- 减少动效全量门控。

### P3 材质与性能

- thin/regular/thick 材质 token。
- Backdrop 最小尺寸硬约束。
- 共享 shimmer phase、离屏停止动画。
- JankStats/Macrobenchmark 基线。

### P4 验收发布

- 全量 JVM、lint、签名 Release。
- 模拟器 host GPU 矩阵。
- 真机门禁（设备在线时）。
- 发布 v3.0.0-test9 Pre-release；正式 3.0 仍需用户视觉确认。

## 11. 验收标准

- 小尺寸节点无 Backdrop/AGSL；Backdrop 尺寸约束有自动化测试。
- 减少动效下无无限循环、无 SizeTransform 弹簧、无闪白、无拖拽惯性。
- 所有可点元素 ≥48dp，语义与触觉完整。
- 页面/Viewer/Sheet/Dialog 转场连续，返回不误触底层。
- Loading/Empty/Error/Success 不硬切，状态不滞后。
- 全量 JVM、lint、签名构建通过；关键路径性能不回归。
- 用户过目关键页面与动效后，才可发布正式版。

## 12. 风险与缓解

- 动效过多导致分心：每页只保留一个主动画层级，其他用短反馈。
- GPU 成本上升：共享相位、离屏停止、Performance 档、Backdrop 尺寸门禁。
- 原生崩溃：小节点禁 Backdrop、兼容模式、自动降级。
- 大改导航导致回归：分阶段提交、预测返回 UI 测试、真机路径复核。
- 模拟器性能误判：真机门禁为准，模拟器仅功能/视觉。

## 13. 效果预览

- 主流程故事板：`docs/design-previews/a-route-main-flow.png`
- 材质与按压细节：`docs/design-previews/a-route-materials.png`
- 导航与状态连续性：`docs/design-previews/a-route-navigation-states.png`
