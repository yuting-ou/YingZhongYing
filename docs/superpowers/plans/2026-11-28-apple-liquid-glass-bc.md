# Apple Liquid Glass B+C Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 v3.0.0-test9 基础上交付 v3.0.0-test10（versionCode 144），把液态玻璃材质与动效优先级提升到展示型水准，同时保持真机安全、减少动效和数据语义零变化。

**Architecture:** 扩展现有 Compose/Haze/Backdrop 组件，不引入导航框架或新渲染引擎；先锁定安全与动画预算，再逐层增强材质、信息层级、导航和状态动效，最后用截图矩阵、全量测试、模拟器和签名 Release 验证。

**Tech Stack:** Kotlin 2.4.21、Compose 1.12.1、Haze 2.1、Kyant0 Backdrop 1.0.6、Robolectric、Gradle。

**Spec:** `docs/superpowers/specs/2026-11-28-apple-liquid-glass-bc-design.md`

## Global Constraints

- versionName `3.0.0-test10`，versionCode `144`。
- 不升级 Kotlin/Compose，不替换 Haze/Backdrop，不引入新 Navigation 框架。
- 小尺寸节点禁止 Backdrop/AGSL；Backdrop 最小短边 `96dp`，Thin 永不进入 Backdrop。
- 减少动效时移除位移、形变、无限循环和闪白，只保留状态与触觉。
- 每个可见容器最多一个无限动画；滚动、离屏、兼容模式下降级。
- 转换、MediaStore、删除/恢复、分享导入和数据格式语义零变化。
- 不加入传感器高光、粒子、持续彩虹辉光或 Macrobenchmark 前置依赖。
- 模拟器数据只作基线，真机性能与视觉仍需用户确认；正式 3.0 不自动发布。

## File Structure

- `ui/motion/MotionTokens.kt`：统一弹簧、时长和动画预算判据。
- `ui/GlassDesign.kt`、`ui/GlassSafety.kt`：Thin/Regular/Thick 材质、边缘色散、Backdrop 安全门禁。
- `ui/AppleSegmentedControl.kt`：列表/网格分段控件与语义合同。
- `ui/theme/DesignTokens.kt`：字阶、间距、标题收缩参数。
- `ui/MainScreen.kt`：主页信息层级、Large Title、Dock、转换动效与动画预算。
- `ui/SettingsScreen.kt`：扁平分组、开关/分段/滑块反馈。
- `MainActivity.kt`、`picker/PickerScreen.kt`、`picker/OriginalViewer.kt`：页面转场、预测返回、Sheet 速度回弹、照片英雄转场。
- `ui/Components.kt`、`ui/StateUi.kt`：按压、触觉、骨架和状态编排。
- `app/src/test/java/com/vliveconvert/app/`：纯逻辑、Compose UI、截图和返回回归。
- `app/build.gradle.kts`、`README.md`、`docs/releases/v3.0.0-test10.md`：发布元数据。

## Review Focus

- **小尺寸 Backdrop 节点**：Thin 或短边 <96dp 时必须回退，不能触发既有 RenderThread 崩溃路径；由 `GlassSafetyTest` 钉死。
- **退场期间快速第二次返回**：Viewer/Sheet 关闭动画不能吞掉上一层返回；由 `PickerOriginalViewerTest` 与 `BackNavigationTest` 钉死。
- **2× 字号与 360dp 窄屏**：分段控件、标题、Dock 不得截断或溢出；由 `AppleSegmentedControlTest` 和截图矩阵钉死。
- **大批量转换并发**： busy 卡片数量增长时不得每张都跑无限动画；由 `MotionBudgetTest` 与 `MainScreenGridModeTest` 钉死。
- **减少动效/滚动暂停**：shimmer、缩略图 pulse、共享元素尺寸动画必须停止；由 `ReduceMotionTest` 与 `StateUiTest` 钉死。

---

### Task 1: 锁定动效与安全不变量

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/motion/MotionTokens.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassSafety.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/Components.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/StateUi.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MotionTokensTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassSafetyTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/ReduceMotionTest.kt`

**Interfaces:**
- Produces: `MotionTokens.Press: SpringSpec<Float>`、`MotionTokens.Hero: SpringSpec<Float>`、`motionBudgetAllowsInfinite(reduceMotion: Boolean, scrollPaused: Boolean, compatibility: Boolean): Boolean`、`maxConcurrentStatusAnimations(busyCount: Int): Int`。
- Consumes: 现有 `GlassSafety.allowsBackdrop`。

- [ ] **Step 1: Write failing tests**
  - `motionBudgetAllowsInfinite(false, false, false) == true`；任一 true 时为 false。
  - `maxConcurrentStatusAnimations(1) == 1`、`(6) == 2`、`(24) == 1`。
  - Thin + 240dp + hardware 可用仍禁止 Backdrop。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.MotionTokensTest --tests com.vliveconvert.app.GlassSafetyTest --tests com.vliveconvert.app.ReduceMotionTest`
  - Expected: 新接口/断言失败。
- [ ] **Step 3: Implement invariants**
  - 在 `MotionTokens.kt` 增加 Press/Hero 与两个纯预算函数；`Components.kt`/`StateUi.kt` 统一调用预算判据。
- [ ] **Step 4: Run tests to verify pass**
  - 同 Step 2，Expected: PASS。
- [ ] **Step 5: Commit**
  - `git add app/src/main/java/com/vliveconvert/app/ui/motion/MotionTokens.kt app/src/main/java/com/vliveconvert/app/ui/GlassSafety.kt app/src/main/java/com/vliveconvert/app/ui/Components.kt app/src/main/java/com/vliveconvert/app/ui/StateUi.kt app/src/test/java/com/vliveconvert/app/MotionTokensTest.kt app/src/test/java/com/vliveconvert/app/GlassSafetyTest.kt app/src/test/java/com/vliveconvert/app/ReduceMotionTest.kt`
  - Commit message: `feat: 锁定 B+C 动效预算与玻璃安全不变量`

**Rollback:** 仅回退 MotionTokens/预算函数；Backdrop guard 必须保留。

### Task 2: 增强 Thin / Regular / Thick 玻璃材质

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassDesign.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassOpticsTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassPressScreenshotTest.kt`

**Interfaces:**
- Produces: `GlassLevelOptics.edgeDispersion: Float`、`Modifier.glassEdgeDispersion(level: GlassLevel): Modifier`。
- Consumes: Task 1 的 `motionBudgetAllowsInfinite`。

- [ ] **Step 1: Write failing material tests**
  - `glassFill` 的 top/bottom/dimming 满足 Thin < Regular < Thick。
  - `glassLevelOptics(Thick).blurDp > Regular`，`edgeDispersion` 满足 Thin=0、Regular∈0.25..0.45、Thick∈0.55..0.75。
  - 新增截图 `glass_material_bc_light` 与 `glass_material_bc_dark`。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.GlassOpticsTest --tests com.vliveconvert.app.GlassPressScreenshotTest`
  - Expected: 新字段/截图断言失败。
- [ ] **Step 3: Implement material mapping**
  - 扩展 `GlassLevelOptics`；Thin 降白膜/色散，Regular 保持平衡，Thick 提高压暗、模糊与边缘色散。
  - 用 Canvas 绘制上青蓝、下玫橙细边缘；Haze/Lens 两条路径都接入，兼容模式只保留静态膜。
- [ ] **Step 4: Run tests and inspect frames**
  - 同 Step 2；检查浅/深材质图无明显白板、彩虹辉光或文字不可读。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 增强 B+C 液态玻璃材质与边缘色散`

**Rollback:** 回退 GlassDesign 的视觉参数；保留 GlassSafety 与预算判据。

### Task 3: Apple 式分段控件与信息层级

**Files:**
- Create: `app/src/main/java/com/vliveconvert/app/ui/AppleSegmentedControl.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/theme/DesignTokens.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/MainScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/SettingsScreen.kt`
- Test: `app/src/test/java/com/vliveconvert/app/AppleSegmentedControlTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MainScreenViewModeScreenshotTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/SettingsPerfScreenshotTest.kt`

**Interfaces:**
- Produces: `AppleSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`。
- Produces: `DesignTokens.BodyLarge/Body/Caption/Metric` 字阶 token。

- [ ] **Step 1: Write failing semantics/layout tests**
  - 两个 option 均为 Role.Tab，selected 正确，点击只回调一次。
  - 360dp + 2× 字号下高度 ≥48dp、文字不截断。
  - 主页截图存在 `main_view_segmented` 语义节点。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.AppleSegmentedControlTest --tests com.vliveconvert.app.MainScreenViewModeScreenshotTest --tests com.vliveconvert.app.SettingsPerfScreenshotTest`
  - Expected: 组件不存在/语义失败。
- [ ] **Step 3: Implement component and hierarchy**
  - 新增分段控件；MainScreen 用它替换“列表/网格”文字按钮。
  - SettingsSection 减少嵌套卡片、统一字阶/分隔线/开关行；保留所有回调与设置键。
- [ ] **Step 4: Run tests and inspect screenshots**
  - 同 Step 2；检查 360dp/2×、浅/深、空/非空队列。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 加入 Apple 分段控件并收紧信息层级`

**Rollback:** 可单独回退 SettingsSection 视觉；分段控件语义合同保留。

### Task 4: 主页 Hero 与照片氛围强化

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/MainScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/MainActivity.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassAmbient.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MainScreenViewModeScreenshotTest.kt`
- Create: `app/src/test/java/com/vliveconvert/app/AppleMotionScreenshotTest.kt`

**Interfaces:**
- Produces: `largeTitleCollapse(firstVisibleItem: Int, offsetPx: Int, reduceMotion: Boolean): Float`。
- Consumes: `GlassSamplingBackdrop`、`LocalGlassPhotoAmbient`、`GlassLevel.Thick`。

- [ ] **Step 1: Write failing collapse/ambient tests**
  - `largeTitleCollapse(0, 0, false)==1f`；滚动超过阈值后 <1f；reduceMotion 时只允许 0/1。
  - 照片氛围关闭/兼容模式/无 URI 时不加载缩略图。
  - 截图覆盖 `photoAmbient=true/false` 与深色主页。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.MainScreenViewModeScreenshotTest --tests com.vliveconvert.app.AppleMotionScreenshotTest`
  - Expected: 新纯函数/截图夹具失败。
- [ ] **Step 3: Implement hero behavior**
  - 主页 Large Title 随滚动收缩；顶部按钮使用 Thin，Dock 使用 Thick。
  - 氛围图只作为 Haze 采样源，压缩到 640x640；关闭、兼容、无照片时立即回退壁纸。
- [ ] **Step 4: Run tests and inspect frames**
  - 同 Step 2；确认可见背景不变、无上传/权限变化、深色文字可读。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 强化主页 Large Title 与照片氛围玻璃`

**Rollback:** 回退 Large Title 收缩和氛围亮度；照片采样安全条件保留。

### Task 5: 导航与照片英雄转场

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/MainActivity.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/OriginalViewer.kt`
- Test: `app/src/test/java/com/vliveconvert/app/BackNavigationTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerOriginalViewerTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerAlbumSheetTest.kt`

**Interfaces:**
- Produces: `shouldDismissSheet(offsetPx: Float, thresholdPx: Float, velocityPxSec: Float): Boolean`、`shouldDismissViewer(offsetPx: Float, heightPx: Float, velocityPxSec: Float): Boolean`。
- Consumes: Task 1 `MotionTokens.Hero`。

- [ ] **Step 1: Write failing motion tests**
  - Sheet 位移过阈值或快速下滑关闭，慢速未过阈值回弹。
  - Viewer 下拉超过高度 18% 或速度阈值关闭，取消回到原位。
  - 退场 48ms 内第二次返回到达父层；忙碌修复仍消费返回。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.BackNavigationTest --tests com.vliveconvert.app.PickerOriginalViewerTest --tests com.vliveconvert.app.PickerAlbumSheetTest`
  - Expected: 新纯函数/手势断言失败。
- [ ] **Step 3: Implement navigation motion**
  - 子页转场按来源/目标使用 scale+fade 或横向共享轴；预测返回续接不变。
  - Viewer 增加下拉关闭与取消回弹；AlbumSheet 使用速度阈值；保持共享元素与返回优先级。
- [ ] **Step 4: Run tests**
  - 同 Step 2；Expected: PASS。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 完善预测返回、照片英雄转场与 Sheet 速度回弹`

**Rollback:** 可回退下拉关闭和横向轴；共享元素、返回优先级、忙碌保护不可回退。

### Task 6: 控件手感与触觉收口

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/Components.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassDesign.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/SettingsScreen.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassPressFeedbackTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassDialogExitTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/AppleSegmentedControlTest.kt`

**Interfaces:**
- Produces: `rememberPressScale(interactionSource, pressedScale, spec: FiniteAnimationSpec<Float> = MotionTokens.Press)`。
- Consumes: Task 1 `MotionTokens.Press`。

- [ ] **Step 1: Write failing press/haptic tests**
  - 按下/松开/取消分别进入 pressed、回弹、回稳；reduceMotion 使用 snap。
  - Dialog confirm 快速连点只执行一次动作和一次 CONFIRM。
  - 分段控件选中态使用 Press 弹簧且无系统涟漪。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.GlassPressFeedbackTest --tests com.vliveconvert.app.GlassDialogExitTest --tests com.vliveconvert.app.AppleSegmentedControlTest`
  - Expected: 默认 spec/新断言失败。
- [ ] **Step 3: Implement control feel**
  - 统一按钮、Chip、相册行、Dialog、Switch 行使用 Press 弹簧；Haze 只增强折射/光照，不重复 scale。
  - 所有关键主动作保留单次触觉，取消/失败不触发确认触觉。
- [ ] **Step 4: Run tests**
  - 同 Step 2；Expected: PASS。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 统一 B+C 控件弹簧、取消反馈与触觉节奏`

**Rollback:** 可回退单个控件 scale；语义、48dp 与单次触觉保留。

### Task 7: 网格转换动效与批量预算

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/MainScreen.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MainScreenGridModeTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MotionTokensTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/AppleMotionScreenshotTest.kt`

**Interfaces:**
- Produces: `statusAnimationSlot(index: Int, busyCount: Int): Int`，只有 `slot < maxConcurrentStatusAnimations(busyCount)` 的卡片跑无限 spinner。
- Consumes: Task 1 `maxConcurrentStatusAnimations`。

- [ ] **Step 1: Write failing batch tests**
  - busyCount=24 时同时无限 spinner ≤1；busyCount=6 时 ≤2。
  - 状态文字即时切“转换中…”；完成闪白/徽标/失败抖动仍存在。
  - reduceMotion 下无闪白、无 spinner、无位移动画。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.MainScreenGridModeTest --tests com.vliveconvert.app.MotionTokensTest --tests com.vliveconvert.app.AppleMotionScreenshotTest`
  - Expected: 预算接口/截图断言失败。
- [ ] **Step 3: Implement conversion choreography**
  - 按可见 busy 数量分配 spinner 名额；其余卡片保留暗幕和静态状态文字。
  - 完成/失败动画只在可见卡片执行；Dock 进度与触觉保持单实例。
- [ ] **Step 4: Run tests and inspect busy/done frames**
  - 同 Step 2；确认批量状态不抢镜、单张反馈仍完整。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 完善网格转换动效并限制批量动画预算`

**Rollback:** 可回退批量降级策略；单张状态反馈与 reduceMotion 门控保留。

### Task 8: 状态、无障碍与兼容模式收口

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/StateUi.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/FixTimeScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/LocationRecoveryScreen.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/AboutScreen.kt`
- Test: `app/src/test/java/com/vliveconvert/app/StateUiTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerEmptyStateTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/AppleMotionScreenshotTest.kt`

**Interfaces:**
- Produces: `GlassEmptyErrorState` 的图标/标题/说明/主动作统一语义；`GlassLoadingSkeleton` 使用 Indeterminate。
- Consumes: Task 1 动画预算、Task 2 材质层级。

- [ ] **Step 1: Write failing accessibility tests**
  - 骨架 progress 为 Indeterminate；空态/错误态标题可读；重试按钮 ≥48dp。
  - 兼容模式/减少动效下无无限 shimmer，状态切换仍可读。
  - 截图覆盖 Loading/Empty/Error/Success 四态。
- [ ] **Step 2: Run tests to verify failure**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.StateUiTest --tests com.vliveconvert.app.PickerEmptyStateTest --tests com.vliveconvert.app.AppleMotionScreenshotTest`
  - Expected: 新截图/语义断言失败。
- [ ] **Step 3: Implement state polish**
  - 统一图标尺度、标题/说明字阶、主动作按压；保留所有数据与重试回调。
  - 非硬件/兼容模式使用静态材质膜，不创建无限动画。
- [ ] **Step 4: Run tests**
  - 同 Step 2；Expected: PASS。
- [ ] **Step 5: Commit**
  - Commit message: `feat: 收口 B+C 状态动效、无障碍与兼容模式`

**Rollback:** 可回退图标/字阶视觉；Indeterminate、48dp、兼容门控保留。

### Task 9: 视觉矩阵、性能与对抗审查

**Files:**
- Modify: `app/src/test/java/com/vliveconvert/app/AppleMotionScreenshotTest.kt`
- Modify: `app/src/test/java/com/vliveconvert/app/GlassPressScreenshotTest.kt`
- Modify: `app/src/test/java/com/vliveconvert/app/PickerScreenshotTest.kt`
- Modify: `app/src/test/java/com/vliveconvert/app/MainScreenViewModeScreenshotTest.kt`
- Modify: `app/src/test/java/com/vliveconvert/app/SettingsPerfScreenshotTest.kt`

**Interfaces:**
- Produces: `build/ui-shots/apple_bc_*.png` 视觉矩阵与 `/tmp` 帧/内存基线。

- [ ] **Step 1: Add matrix tests**
  - 浅/深、Thin/Regular/Thick、2× 字号、减少动效、兼容模式、照片氛围、Loading/Empty/Error/Success。
- [ ] **Step 2: Run targeted matrix**
  - `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.AppleMotionScreenshotTest --tests com.vliveconvert.app.GlassPressScreenshotTest --tests com.vliveconvert.app.PickerScreenshotTest --tests com.vliveconvert.app.MainScreenViewModeScreenshotTest --tests com.vliveconvert.app.SettingsPerfScreenshotTest`
  - Expected: 0 失败，截图可读、无白板/彩虹辉光/截断。
- [ ] **Step 3: Run emulator matrix and record metrics**
  - 运行 `./gradlew :app:assembleDebug`，启动 host GPU 模拟器并安装 Debug，跑主页、Picker→Viewer→返回、Sheet、设置、修复页、网格转换。
  - 记录 `dumpsys gfxinfo` 与 `meminfo`，不宣称真机性能。
- [ ] **Step 4: Independent review**
  - 审查小 Backdrop、返回吞键、批量无限动画、2× 字号、数据语义；修复后再跑相关测试。
- [ ] **Step 5: Commit**
  - Commit message: `test: 建立 B+C 视觉矩阵与性能基线`

**Rollback:** 截图测试可单独关闭；安全与性能门禁不可关闭。

### Task 10: 发布 v3.0.0-test10

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `docs/releases/v3.0.0-test10.md`
- Modify: `README.md`

**Interfaces:**
- Produces: versionCode `144`、versionName `3.0.0-test10`、GitHub Pre-release。

- [ ] **Step 1: Bump version and write Chinese notes**
  - 记录材质、导航、控件、转换、状态、性能与验证结果；正式 3.0 仍待真机批准。
- [ ] **Step 2: Run full local gate**
  - `./gradlew :app:testDebugUnitTest :app:lintVitalRelease :app:rotateSignRelease`
  - Expected: 0 失败，lint clean，签名 APK 生成。
- [ ] **Step 3: Verify local artifact**
  - `aapt2 dump badging`、`apksigner verify --print-certs`、SHA-256、dex 新增字符串。
- [ ] **Step 4: Commit, tag and push**
  - 提交发布元数据，tag `v3.0.0-test10`，推送 main 与 tag。
- [ ] **Step 5: Verify CI and Release**
  - CI 全绿；下载附件核验 versionCode/versionName、v3 证书、SHA-256 与 `META-INF/version-control-info.textproto` revision。

**Rollback:** 若 CI/真机门禁失败，保留 main 但不发正式版；必要时回退 tag 对应提交并发布稳定性修订包。
