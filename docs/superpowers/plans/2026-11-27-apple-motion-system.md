# Apple Liquid Glass Motion System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver v3.0.0-test9 with coherent Apple-style motion, continuous navigation, consistent control/state feedback, semantic glass hierarchy, and no new small-node Backdrop crash path.

**Architecture:** Extend the existing Screen/overlay model rather than introducing a navigation framework. Add small MotionTokens/GlassLevel policy files, use Compose shared-element and draggable-sheet APIs for the two highest-value flows, and keep Haze+Canvas on small controls. Every task is independently testable and committed.

**Tech Stack:** Kotlin 2.4.21, Jetpack Compose 1.12.1, Material3, Haze 2.1, Kyant0 Backdrop 1.0.6, Robolectric, JUnit4, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-27-apple-motion-system-design.md`

## Global Constraints

- No small-size Backdrop/AGSL nodes; Backdrop requires short side ≥96dp.
- No sensor effects, particles, new navigation framework, or Macrobenchmark module in test9.
- Reduce motion means no infinite loops, no SizeTransform spring, no flash, no drag inertia.
- Every tappable target ≥48dp with complete selected/toggle semantics.
- Existing conversion, MediaStore, delete/restore semantics remain unchanged.
- Version target: `versionCode = 143`, `versionName = 3.0.0-test9`.
- Simulator validates function/visual only; real-device performance remains a user gate.

## Review Focus

- Reduce-motion + busy grids must not create infinite transitions or hang Compose idle.
- Snackbar/small cards below 96dp must never enter LensGlassCard/drawBackdrop.
- Viewer/AlbumSheet/repair overlays must consume back before the underlying page.
- Rapid viewer open/close and drag cancellation must not leave a stuck shared element or sheet.
- 2× font and photo-ambient backgrounds must preserve text contrast and 48dp targets.

---

### Task 1: MotionTokens and system motion policy

**Files:**
- Create: `app/src/main/java/com/vliveconvert/app/ui/motion/MotionTokens.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/MainActivity.kt:1883-1889`
- Test: `app/src/test/java/com/vliveconvert/app/MotionTokensTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/ReduceMotionTest.kt`

**Interfaces:**
- Produces: `internal object MotionTokens` with `Snappy`, `Standard`, `Gentle` (`SpringSpec<Float>`), `FastMs=120`, `BaseMs=180`, `SlowMs=240`, `CompletionMs=340`, and `fun stateSizeTransform(reduceMotion: Boolean): SizeTransform`.

- [ ] **Step 1: Write failing token/policy tests**

Assert exact spring constants and durations; set `Settings.Global.ANIMATOR_DURATION_SCALE=0`, call `MotionPrefs.refreshSystem(context)`, assert `MotionPrefs.effective` is true; restore scale to 1 and assert false.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.MotionTokensTest --tests com.vliveconvert.app.ReduceMotionTest`
Expected: FAIL because MotionTokens does not exist and onResume policy is not yet covered.

- [ ] **Step 3: Implement MotionTokens and onResume refresh**

Create the token object. `stateSizeTransform(true)` returns `SizeTransform { _, _ -> snap() }`; false returns `SizeTransform(clip = false)`. Call `MotionPrefs.refreshSystem(this)` in `MainActivity.onResume`.

- [ ] **Step 4: Run tests to verify pass**

Run the same command. Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/vliveconvert/app/ui/motion/MotionTokens.kt app/src/main/java/com/vliveconvert/app/MainActivity.kt app/src/test/java/com/vliveconvert/app/MotionTokensTest.kt app/src/test/java/com/vliveconvert/app/ReduceMotionTest.kt`

### Task 2: GlassLevel and Backdrop minimum-size guard

**Files:**
- Create: `app/src/main/java/com/vliveconvert/app/ui/GlassSafety.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassDesign.kt:382-465`
- Test: `app/src/test/java/com/vliveconvert/app/GlassSafetyTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassOpticsTest.kt`

**Interfaces:**
- Produces: `internal enum class GlassLevel { Thin, Regular, Thick }`, `internal const val MinBackdropShortSideDp = 96f`, `internal fun canUseBackdrop(shortSideDp: Float): Boolean`.
- Modifies: `HazeGlassCard(..., level: GlassLevel? = null, ...)`; null preserves existing dense/liquid mapping.

- [ ] **Step 1: Write failing guard tests**

Assert `canUseBackdrop(95.9f)==false`, `96f==true`, and a 44dp Snackbar-sized card resolves to non-Backdrop rendering.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.GlassSafetyTest`
Expected: FAIL because guard/API do not exist.

- [ ] **Step 3: Implement guard and level resolution**

Add pure guard. Wrap HazeGlassCard measurement in `BoxWithConstraints`; only select LensGlassCard when backdrop exists, Haze is available, and `min(maxWidth.value, maxHeight.value) >= 96f`. Resolve null level as `dense -> Thick`, otherwise `Regular`; `Thin` always forces Haze/membrane.

- [ ] **Step 4: Run tests to verify pass**

Run GlassSafetyTest and GlassOpticsTest. Expected: PASS.

- [ ] **Step 5: Commit**

Commit GlassSafety.kt, GlassDesign.kt and tests.

### Task 3: Reduce-motion sweep for existing animations

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/Components.kt:154-254`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/MainScreen.kt:384,541,1216`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/OriginalViewer.kt:131,392`
- Test: `app/src/test/java/com/vliveconvert/app/ReduceMotionTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/MainScreenGridModeTest.kt`

**Interfaces:**
- Consumes: `MotionTokens.stateSizeTransform`.
- Produces: no infinite transition or scale/position animation when `LocalReduceMotion.current` is true.

- [ ] **Step 1: Add failing reduce-motion UI test**

Render MainScreen grid in busy state with `LocalReduceMotion provides true`; advance the clock and assert idle returns, busy semantics exist, and no completion flash modifier is present.

- [ ] **Step 2: Run test to verify current gap**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.MainScreenGridModeTest`
Expected: FAIL or timeout on the new reduce-motion assertion.

- [ ] **Step 3: Gate animations**

MediaThumbnail uses snap/fade-only transitions in reduce motion; MainScreen AnimatedContent uses `MotionTokens.stateSizeTransform`; OriginalViewer uses direct visibility/alpha with no spring; completion flash remains zero.

- [ ] **Step 4: Run tests to verify pass**

Run MainScreenGridModeTest and ReduceMotionTest. Expected: PASS.

- [ ] **Step 5: Commit**

Commit the reduce-motion sweep.

### Task 4: Overlay back priority and draggable AlbumSheet

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/MainActivity.kt:536-554,1109-1118,1547-1569`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt:724-857`
- Test: `app/src/test/java/com/vliveconvert/app/BackNavigationTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerAlbumSheetTest.kt`

**Interfaces:**
- Produces: `private fun dismissTopDialog()` handles BrokenRepair/LocationRecovery/AlbumSheet before page pop; `AlbumSheet` supports vertical drag dismissal with threshold 96dp and snap-back.

- [ ] **Step 1: Write failing back/drag tests**

Assert back closes AlbumSheet before Picker; assert repair overlay back does not pop Settings; swipe AlbumSheet down >96dp and assert `onAlbumSheetChange(false)`.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.BackNavigationTest --tests com.vliveconvert.app.PickerAlbumSheetTest`
Expected: FAIL on overlay priority/drag.

- [ ] **Step 3: Implement overlay priority and sheet drag**

Extend top-overlay dismissal order. Add `draggable(Orientation.Vertical)` plus offset state to AlbumSheet; on drag stop animate to 0 or dismiss past threshold. Reduce motion uses snap, no inertia.

- [ ] **Step 4: Run tests to verify pass**

Run the same tests. Expected: PASS.

- [ ] **Step 5: Commit**

Commit navigation/sheet changes.

### Task 5: Picker-to-Viewer shared-element transition

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt:171-770,1094-1260`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/OriginalViewer.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerOriginalViewerTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerScreenshotTest.kt`

**Interfaces:**
- Produces: Picker root `SharedTransitionLayout`; GridCell accepts `sharedState: SharedTransitionScope.SharedContentState?` and `sharedVisible: Boolean`; OriginalViewer accepts `sharedState` and `AnimatedVisibilityScope`.
- Uses: `Modifier.sharedElementWithCallerManagedVisibility` on the source cell and `Modifier.sharedElement` on the viewer image.

- [ ] **Step 1: Write failing viewer transition test**

Open preview from a cell, assert viewer appears; close it, assert the source cell is visible again; rapid open/close must not leave two visible images or a stuck transition.

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.PickerOriginalViewerTest`
Expected: FAIL because no shared transition exists.

- [ ] **Step 3: Implement shared transition**

Wrap Picker content in SharedTransitionLayout. Use item.key as shared key. Source cell uses caller-managed visibility false while its viewer is active. Viewer is wrapped in AnimatedVisibility and uses sharedElement with MotionTokens.Gentle; reduce motion uses snap/fade only.

- [ ] **Step 4: Run tests to verify pass**

Run PickerOriginalViewerTest and PickerScreenshotTest. Expected: PASS.

- [ ] **Step 5: Commit**

Commit shared-element transition.

### Task 6: Control feedback contracts

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt:1040-1105`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassDesign.kt:629-668`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/Components.kt:256-410`
- Test: `app/src/test/java/com/vliveconvert/app/PickerAlbumSheetTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassDialogExitTest.kt`

**Interfaces:**
- Produces: AlbumChip has `Role.Tab`, `selected` semantics, ≥48dp target, press spring and selected morph; dialog buttons use no-ripple press feedback and one haptic per action.

- [ ] **Step 1: Write failing semantics/press tests**

Assert AlbumChip exposes selected state and ≥48dp bounds; click emits one selection; dialog confirm emits one haptic and closes once.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.PickerAlbumSheetTest --tests com.vliveconvert.app.GlassDialogExitTest`
Expected: FAIL on selected semantics/press contract.

- [ ] **Step 3: Implement control contract**

Add interaction source, press scale, animated selected background/scale, semantics and minimum touch size. Replace dialog ripple with `clickableNoRipple` + MotionTokens.Snappy + CONFIRM haptic.

- [ ] **Step 4: Run tests to verify pass**

Run the same tests. Expected: PASS.

- [ ] **Step 5: Commit**

Commit control feedback changes.

### Task 7: Shared state choreography

**Files:**
- Create: `app/src/main/java/com/vliveconvert/app/ui/StateUi.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/picker/PickerScreen.kt:350-430`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/FixTimeScreen.kt:260-330`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/LocationRecoveryScreen.kt:370-430`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/AboutScreen.kt:55-90`
- Test: `app/src/test/java/com/vliveconvert/app/PickerEmptyStateTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/StateUiTest.kt`

**Interfaces:**
- Produces: `StateCrossfade`, `GlassLoadingSkeleton`, `GlassEmptyErrorState`; all accept `reduceMotion` explicitly or read LocalReduceMotion once.

- [ ] **Step 1: Write failing state tests**

Assert loading→empty, empty→error, error→success transitions keep one stable state visible and reduce motion snaps without SizeTransform animation.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.StateUiTest --tests com.vliveconvert.app.PickerEmptyStateTest`
Expected: FAIL because helpers do not exist.

- [ ] **Step 3: Implement and wire helpers**

Add the three helpers. Picker uses them for scanner states; FixTime/LocationRecovery/About replace direct `when` jumps only at loading/empty/error boundaries. Keep data and retry callbacks unchanged.

- [ ] **Step 4: Run tests to verify pass**

Run the same tests. Expected: PASS.

- [ ] **Step 5: Commit**

Commit state choreography.

### Task 8: Material hierarchy, shared shimmer, and visual matrix

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/ui/GlassDesign.kt:184-220,382-567,671-705`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/Components.kt:154-254`
- Modify: `app/src/main/java/com/vliveconvert/app/ui/theme/DesignTokens.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassOpticsTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/GlassPressScreenshotTest.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PickerScreenshotTest.kt`

**Interfaces:**
- Consumes: `GlassLevel`, `MotionTokens`.
- Produces: Thin/Regular/Thick parameter mapping, one shimmer phase per visible skeleton container, static thumbnail pulse while scrolling/reduced.

- [ ] **Step 1: Add failing material/shimmer tests**

Assert Thin never selects Backdrop, Thick raises opacity/dimming, and reduce/scroll state contains no infinite shimmer transition.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.vliveconvert.app.GlassOpticsTest`
Expected: FAIL on level mapping.

- [ ] **Step 3: Implement material and shimmer wiring**

Map GlassLevel to existing opacity/blur/refraction parameters. Use one remembered infinite phase inside GlassLoadingSkeleton. MediaThumbnail uses LocalGlassScrollPaused/LocalReduceMotion to render a static placeholder.

- [ ] **Step 4: Run visual matrix**

Run GlassPressScreenshotTest and PickerScreenshotTest; inspect light/dark, 2× font, reduce motion, compatibility and photo-ambient frames.

- [ ] **Step 5: Commit**

Commit material/performance polish.

### Task 9: Full validation and v3.0.0-test9 release

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `docs/releases/v3.0.0-test9.md`
- Modify: `README.md`

**Interfaces:**
- Produces: versionCode 143, versionName 3.0.0-test9, Pre-release with Chinese notes.

- [ ] **Step 1: Bump version and write release notes**

Document MotionTokens, navigation continuity, control/state choreography, glass hierarchy, safety guard and measured validation.

- [ ] **Step 2: Run full local gate**

`./gradlew :app:testDebugUnitTest :app:lintVitalRelease :app:rotateSignRelease`
Expected: all tests pass, 0 failures, lint clean, signed APK produced.

- [ ] **Step 3: Run host GPU emulator matrix**

Verify Picker→Viewer→back, AlbumSheet drag/cancel, repair overlay back, grid conversion, settings, light/dark, 2× font, reduce motion and compatibility mode.

- [ ] **Step 4: Record Jank/PSS baseline**

Capture `dumpsys gfxinfo` and `meminfo` for key flows; do not claim real-device performance from emulator data.

- [ ] **Step 5: Commit, tag and push**

Commit release metadata, tag `v3.0.0-test9`, push main and tag.

- [ ] **Step 6: Verify CI and artifact**

Confirm main/tag CI green; verify versionCode, v3 certificate, embedded revision and SHA-256 against Release digest.

## Rollback Points

- After Task 2: revert GlassDesign guard only if layout constraints regress; safety guard remains.
- After Task 4: overlay back changes can be reverted independently of sheet drag.
- After Task 5: shared transition is isolated to Picker/OriginalViewer; fallback is the existing direct viewer.
- After Task 7: state helpers can be disabled per page without touching data flow.
- Before release: if host GPU shows any native crash, disable shared/glass effects via GlassCompatibility and ship test9 as a stability build rather than forcing visuals.
