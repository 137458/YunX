# 下载引擎重点笔记（改 `DownloadManager.kt` 前必读）

> 本文件是 `AGENTS.md` §5 的完整版，用于**动手改下载引擎时**查阅。
> 日常开发只需读 `AGENTS.md` §5 的摘要；两者冲突时以源码与 `AGENTS.md` 为准。

以下机制都是为修真实线上问题而存在，**不要随意「简化」**。

### 5.1 任务池 + 弹性区

```text
分片规划：chunkCount = chunkCountFor(total, threads)
          主池 = chunkCount × 0.7  → part_0 … part_{n-1}（等分区间）
          弹性区 = 剩余 30% 字节  → seg_{start}_{end}.part（按字节顺序领 4MB 块）
actualWorkers = min(effectiveWorkers, MAX_INFLIGHT_CHUNKS)
```

worker **循环领片**（慢片不阻塞其他线程，根治尾部并发塌缩）。**禁止**改回早期的「中点劈分」（主池耗尽瞬间全部线程涌入、区间跨度翻倍、连接复用率崩塌 ⇒ 中后段掉速）。

### 5.1.1 全进程在飞上限（不要改回「每任务一个信号量」）

```kotlin
MAX_INFLIGHT_CHUNKS = inflightChunksFor(maxMemory) = clamp(maxHeap / 8 / BUFFER_SIZE, 8, 512)   // BUFFER_SIZE = 64KB
inflightLimiter：★ 全进程共享的 Semaphore，跨任务生效，绝不手动 release
chunkIoDispatcher：ThreadPoolExecutor(core = max = MAX_INFLIGHT_CHUNKS, 30s, LinkedBlockingQueue)
                   + allowCoreThreadTimeOut(true)，线程名 yunx-chunk-io、daemon
```

- 旧实现每任务一个 `Semaphore(effectiveWorkers)`（容量恰等于 worker 数 ⇒ 永不阻塞 = 从不限流），**别改回去**。
- 分片 IO **不能退回 `Dispatchers.IO`**（并行度钉在 `max(64, 核数)`，设 512 线程也只跑得出 64 路）。`chunkIoDispatcher` 必须 `core = max`（`core = 0` + 无界队列只会养出 1 个 worker）。
- `HttpClients.buildDownload()` 协议固定 `listOf(Protocol.HTTP_1_1)`：OOM 的直接机制是客户端声明的每流 16MB 应用层接收窗口（`OKHTTP_CLIENT_WINDOW_SIZE`），几十路 × 16MB 远超 256MB 堆；HTTP/1.1 没有流窗口，堆占用只剩每路 64KB。`buildApi()` **不设** protocols。
- 单路读缓冲 64KB；`MAX_QUEUED_CALLS = 64`；空闲连接池 8 条 / 1 分钟；`Application.onTrimMemory` 里调 `HttpClients.evictIdleConnections()`。
- 主池、弹性区、**失败重试**三条路径都必须走 `inflightLimiter.withPermit`（少一条三路并发就会叠加）。
- `threadCount` 仍**原样传给 `chunkCountFor`**：`plan.txt` 签名不能变，否则全体用户断点续传失效。钳的是 worker 数，不是分片数。
- 边界由 `InflightChunkBudgetTest` 守住：小堆保底 8 路、大堆封顶 512 路、总缓冲不超过最大堆的 1/8。

### 5.2 `chunkCountFor` 的真实语义（易被误读）

```kotlin
val minChunkBytes = 256 * 1024L            // 「单片最小 256KB」= 分片数上限阀，不是「每片就是 256KB」
val bySize = when { total < 5MB -> 1; total < 50MB -> 8; total < 500MB -> 32; else -> 64 }
val want = maxOf(bySize, threads * 8)      // 每线程平均 8 片盈余
return minOf(want, (total / minChunkBytes).toInt(), 512)   // 512 为硬封顶
```

**实际单片 = `ceil(total / chunkCount)`**；只有弹性区（后 30%）才用 `ElasticAllocator` 按实测速度动态定块（`clamp(单路速度 × 5s, 256KB, 4MB)`，尾部收缩到 `remaining / workers`、下限 64KB）。小文件的分片数（⇒ 实际并发路数）可能低于用户设置的线程数，这是为避免碎片化的取舍 —— 排查「线程数没生效」先看这一层。

### 5.3 CDN 并发限制（硬编码上限的由来）

`RANGE_WORKERS_CAP = 8`、`RANGE_IGNORED_TOLERANCE = 3`、`STAGGER_CAP = 8` / `STAGGER_MS = 25L`。迅雷并发超过约 8 会被降级为 `200` 整文件响应（忽略 Range）⇒ 整任务回退单流、速度暴跌，故 `SettingsRepository.XUNLEI_DOWNLOAD_THREADS = 8` **固定不可改**。**提高任何平台的并发上限前必须实测是否触发 200 降级**。

### 5.3.1 慢连接抢占（治「收尾塌到 KB 级」，**不要删**）

```kotlin
PREEMPT_MIN_BPS = 12 * 1024L        PREEMPT_MIN_AGE_MS = 15_000L   PREEMPT_COOLDOWN_MS = 10_000L
PREEMPT_MIN_REMAIN = 128 * 1024L    PREEMPT_MAX = 3                PREEMPT_PER_TICK = 2
PREEMPT_ENDGAME_INFLIGHT = 3        PREEMPT_ENDGAME_MIN_AGE_MS = 3_000L
判定阈值 = max(PREEMPT_MIN_BPS, 本任务平均单连接速度 / 2)
```

- 看门狗每 5s 采样（`sampleInflightChunks` → `preemptSlowChunks`），命中只把 `InflightChunk.preempt` 置位；`ChunkDownloader.downloadChunk(preempt = …)` 读到即 `throw PreemptedException`，**已写字节全部保留**、从 `partFile.length()` 续传、**不退避不计失败**。
- 收尾放宽：在飞 ≤ 3 时不再要求跑满 15s / 剩余 ≥128KB（重连握手约 0.5s，比继续等便宜）。
- 抢占**永远不丢数据、不产生空洞**。`preempt` / `preemptCount` / `lastPreemptAtMs`、`sampleInflightChunks` 与「抢占慢连接」日志都是**永久逻辑**，**别因为诊断日志删干净了就把它们一起删掉**。

### 5.4 断点续传与分片计划签名

`plan.txt` 形如 `chunks=37 total=39536652 main=25`；签名不一致时**整目录清空重下**。缓存目录 `context.externalCacheDir/download_tmp/{taskId}/`。改动分片规划算法会让**所有用户的现存断点失效**，必须在交付说明里写清。

### 5.5 进度落盘必须节流（ANR 历史）

DB 进度**按时间节流 500ms**（`progressPersistIntervalMs`），UI 走内存 `_stats`（`StateFlow<Map<Long, DownloadStats>>`）高频展示。`dao.updateProgress` 会触发全表 Flow 重发 ⇒ 主线程全列表重组，早期按字节（256KB）节流导致高速下载每秒写库几十次 ⇒ **ANR**。**不要把落盘改回按字节触发。**

### 5.6 并发安全要点

- `activeJobs` 的注册/移除全程在 `jobsLock` 内（防 start/pause/remove 的 TOCTOU 竞态）；
- `finally` 只移除**自己注册的** deferred（`if (activeJobs[id] === deferred)`），否则「暂停后立即恢复」会误删新任务注册；
- `taskLocks` **不在 finally 清理**（会误删新任务的锁导致并发写分片）；
- 暂停时以**磁盘 part/seg 真实长度**回写进度（避免恢复时进度回跳）；进度累加一律 `minOf(..., total)`。

### 5.7 合并阶段进度（别让界面停在 100%）

`ChunkDownloader.mergeChunksToStream(chunkFiles, out, onProgress)` 每写完一片回调「已合并字节数」；`DownloadManager.finishDownload` 用 `mergeReportIntervalMs = 300L` 节流上报 `_stats.update { … DownloadStats(mergePercent = percent) }` 与 `DownloadService.update(…, DownloadService.MERGE_TEXT, …)`。UI 唯一判据是 `DownloadStats.mergePercent`（默认 `-1` = 不在合并）。
**禁止**：在 DB 加 `STATUS_MERGING`（进程被杀合并本就中断，只会换来「重启后永远卡在合并中」）；让 UI 用 `downloadedSize == totalSize` 判合并（暂停/失败时同样成立）；把 `MERGE_TEXT` 当速度拼成「下载速度 合并中」。合并期间「暂停」照旧有效。
