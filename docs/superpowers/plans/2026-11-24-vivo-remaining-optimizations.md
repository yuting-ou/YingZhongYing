# Vivo 剩余优化实施计划

> **For agentic workers:** 本轮由主代理直接执行；每一步先写测试或验证探针，再实现，最后运行对应回归。用户未授权提交或发版，计划中不包含 Git commit/push/release。

**Goal:** 完成真机冒烟装置、pending 删除/落地状态持久化和大素材测量，并用证据决定是否实施 T9。

**Architecture:** P1 复用现有 Python/adb 工具链，新增主机可测试的 UI 冒烟脚本；pending 状态采用现有队列持久化之外的原子 JSON 快照，在批次收尾前落盘、每完成一个落地项后 checkpoint；T9 先做按需大夹具分配测量与设备 profile 装置，没有明确收益不改无损字节管线。

**Tech Stack:** Kotlin/Compose、Robolectric、Python 3 标准库、adb/uiautomator、Gradle/JUnit。

**Spec:** 2026-11-24 对话中已确认的四切片设计；P0 已完成，本计划覆盖 P1/P2/P3/最终验证。

## 执行结果（2026-11-24）

- P0：三处 O(n²) 路径已改为 HashSet/批量操作；2 万项遍历测试通过。
- P1：真机冒烟脚本主机侧 13 条测试、语法检查、profile dry-run 通过；无头模拟器 Debug 与 v3 签名 Release 均完整通过（队列恢复、权限门禁、转换、EXIF 读回），真实手机未执行。
- P2：pending 原子快照、恢复分派、删除结果跨重建、落地幂等与 checkpoint 已落地；相关回归通过。
- P3：32MiB JPEG + 128MiB MP4 实测累计分配 485MiB、耗时 1380ms；无真机 PSS/吞吐对照，T9 暂不实施。
- 最终验证：JUnit 452 条（5 跳过，0 失败）、Debug 构建、v3 签名 Release、Release vital lint、Python 脚本 13 条均通过；Release 模拟器冒烟通过。
- 未提交 Git、未推送 GitHub、未创建 Release。

## Global Constraints

- 删除原图必须继续由用户确认；脚本默认不开启删原图，破坏性模式需显式环境变量。
- 不改变无损转换格式、XMP 命名空间、签名或版本号。
- 真机操作必须显式 `ANDROID_SERIAL`，每次点击前重新 dump；禁止盲用旧坐标。
- 无设备时只完成主机侧测试，真机执行结果必须标记为“未执行”，不得冒充通过。
- 不提交 Git、不推送 GitHub、不创建 Release。

## Review Focus

- 模拟器/真机MediaStore 与 Robolectric 差异：脚本必须读真实 UI/文件字节，不信模拟结果。
- 进程死在删除确认或原名落地中途：恢复只能续办未完成项，不得重复删除或重复移动。
- URI 已失效或旧产物已移走：按已处理项剔除，不能把“已经成功”误报成失败。
- 大相册/大队列：所有新增合并与恢复路径不得重新引入 O(n²)。
- 大照片/视频：测量必须区分累计分配、峰值 PSS 与耗时，不能把单一指标当结论。

---

### Task 1: 真机冒烟脚本

**Files:**
- Create: `tools/device_smoke.py`
- Create: `tools/smoke-device.sh`
- Create: `tools/test_device_smoke.py`
- Reuse: `tools/make_location_fixtures.py`、`tools/android-probe/ExifProbe.java`

**Interfaces:**
- `parse_ui_xml(xml: str) -> list[dict]`：提取可点击节点及 bounds。
- `find_node(nodes, text=None, content_desc=None) -> dict | None`：按当前 dump 精确查找。
- `tap_center(bounds: str) -> tuple[int, int]`：解析 `[x1,y1][x2,y2]`。
- `delete_original_is_off(ui_text: str) -> bool`：只有明确读到“删原图 关”才放行。
- `main(argv=None) -> int`：参数门禁、设备检查、UI 流程、EXIF 读回、可选 profile。

- [x] 先写 `tools/test_device_smoke.py`，覆盖 bounds 解析、节点查找、删原图安全门禁、缺序列号/APK/设备时非零退出。
- [x] 运行 `python3 -m unittest tools/test_device_smoke.py`，确认旧代码因模块缺失而失败。
- [x] 实现 `tools/device_smoke.py` 与 `tools/smoke-device.sh`；默认只做非破坏性转换、权限收回、进程杀死恢复。
- [x] 实现 `--profile`：记录转换墙钟耗时与 `dumpsys meminfo` 前后快照；无设备时允许 `--dry-run` 只验证命令计划。
- [x] 运行主机单测、`bash -n tools/smoke-device.sh`、`--dry-run`；记录真机未执行原因。

### Task 2: pending 原子快照

**Files:**
- Create: `app/src/main/java/com/vliveconvert/app/service/PendingOperations.kt`
- Create: `app/src/test/java/com/vliveconvert/app/PendingOperationsTest.kt`
- Create: `app/src/test/java/com/vliveconvert/app/PendingOperationsStoreTest.kt`

**Interfaces:**
- `data class PendingFinalizeEntry(val uri: String, val originalName: String, val itemKey: String)`
- `data class PendingSnapshot(val trashUris: List<String>, val finalize: List<PendingFinalizeEntry>)`
- `PendingOperationsJson.toJson(snapshot): String`
- `PendingOperationsJson.fromJson(text): PendingSnapshot?`
- `PendingOperationsStore.load/save/clear`：临时文件 + 同目录 rename + 写锁。

- [x] 先写 JSON 往返、损坏输入、旧文件缺失、原子写失败保留旧快照测试，运行确认失败。
- [x] 实现纯 JSON 与 Store，保持 Uri 字符串往返无损。
- [x] 运行新增测试转绿。

### Task 3: 接入 pending 恢复与 checkpoint

**Files:**
- Modify: `app/src/main/java/com/vliveconvert/app/service/ConvertCenter.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/service/ConvertService.kt`
- Modify: `app/src/main/java/com/vliveconvert/app/MainActivity.kt`
- Test: `app/src/test/java/com/vliveconvert/app/PendingOperationsStoreTest.kt`

**Interfaces:**
- `ConvertCenter.persistPendingOperations(context)` / `restorePendingOperations(context)` / `clearPendingOperations(context)`
- `ConvertCenter.checkpointPendingFinalize(context, itemKey)`：移除已处理落地项并原子保存。
- 服务批次进入删除收尾前保存快照；Activity 无 saved-state 时从快照恢复；确认/取消/失败后按路径清理。

- [x] 先写“保存→清内存→恢复不重复”“每处理一个落地项后快照只剩剩余项”“损坏快照安全降级”测试，运行确认失败。
- [x] 在服务批次收尾前持久化；直接删除和系统确认路径都在结束时清理。
- [x] Activity `onStart` 仅在删除弹窗上下文不存在时恢复；两条 finalize 路径每项完成后 checkpoint。
- [x] 运行 pending、队列、删除/取消、冷启动相关测试，再跑全量回归。

### Task 4: 大素材测量与 T9 决策

**Files:**
- Create: `app/src/test/java/com/vliveconvert/app/LargeAssetProfileTest.kt`
- Reuse: Task 1 的 `--profile` 设备模式

**Interfaces:**
- `LargeAssetProfileTest` 默认由 `Assume` 跳过；`VLC_PROFILE_LARGE=1` 时用 32MiB JPEG + 128MiB MP4 夹具测量线程累计分配与耗时。
- 设备 profile 输出转换耗时和 `dumpsys meminfo` 快照。

- [x] 先写按需大夹具测试，正常运行确认 skipped，profile 模式确认能产出 samples/median。
- [x] 运行 `VLC_PROFILE_LARGE=1 ./gradlew :app:testDebugUnitTest --tests '*LargeAssetProfileTest'`。
- [x] 无真机 PSS/吞吐数据时不作 T9 实现；记录“条件未满足，保留现有零复制实现”的结论与后续测量命令。

### Task 5: 全量对抗验证与交付

**Files:**
- Modify only if verification finds a real defect.

- [x] `git diff --check`，审查所有变更范围与敏感文件。
- [x] 运行 `./gradlew :app:testDebugUnitTest`；若 XML 保留首次 attempt 失败，按退出码 + 单跑复核。
- [x] 运行 `./gradlew :app:assembleDebug :app:lintVitalRelease`。
- [x] 复核删除安全、pending 幂等、O(n²) 未回流、设备未执行项。
- [x] 汇总：本机验证、对抗审查、GitHub 同步（未提交/未推送）。
