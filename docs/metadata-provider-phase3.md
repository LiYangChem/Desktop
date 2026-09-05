# 阶段三实施记录：GoogleScholarProvider 薄适配器 + MetadataCandidateAggregator 聚合器

> 架构文档 D7 决策的落地。Google Scholar 的检索路径（GoogleScholarSearchEngine /
> GoogleScholarExtractor：验证码、cookie、403/503 重试）**一行未改**；本阶段只做结果侧的
> 结构化适配与跨引擎统一聚合。

## 一、新增类（5 个）

### docear_metadata 库（2 个）

| 类 | 路径 | 职责 |
|---|---|---|
| `BibtexFieldParser` | `docear_metadata/src/main/java/org/docear/metadata/io/BibtexFieldParser.java` | 最小只读 BibTeX 字段抽取：`getEntryType` / `getCitationKey` / `parseFields`。支持嵌套花括号、`"..."` 引号值、`#` 拼接、末字段无逗号、`(...)` 定界符。外层定界符剥离、内层花括号保留（match 包的归一化会处理）。约 260 行纯 Java 1.6 |
| `GoogleScholarProvider` | `docear_metadata/src/main/java/org/docear/metadata/providers/GoogleScholarProvider.java` | 薄适配器：`adapt(ScholarMetaData/String)` 把 GS 产出的 BibTeX 转成 `BibliographicMetadata`（title/author 按 " and " 拆分/journal 或 booktitle/year 四位数字/volume/number→issue/pages/publisher/doi——缺失时从 doi.org URL 恢复）。实现 `MetadataProvider` 接口（`supports()`：有标题且无 DOI；resolve* 全部返回空——**适配器永不发起检索**），为决策点 B 的未来收敛保留位置 |

### docear_plugin_bibtex 插件（1 个）

| 类 | 职责 |
|---|---|
| `MetadataCandidateAggregator` | `docear_plugin_bibtex/src/org/docear/plugin/bibtex/dialogs/MetadataCandidateAggregator.java`。聚合统一出口：`aggregate(results, query)` 把每个引擎事件里的 `ScholarMetaData` 归一为候选——`BibMetaData`（Crossref/DOI）直接取 `getStructured()`，纯 GS 结果经 `GoogleScholarProvider.adapt()` 结构化，`BibtexParser` 解析展示条目；`CandidateScorer` 统一打分。`getSortedEntryPairs()` **双键去重 + finalScore 降序**。方法级 synchronized（hub 线程池回调可能并发） |

### 测试（2 个，tools/）

- `TestGoogleScholar.java` — 62 项断言（离线）：字段解析、引号/嵌套值、adapt 各分支、URL 恢复 DOI、能力协商、打分集成、BibTeX round-trip
- `TestAggregator.java` — 15 项断言（离线，JabRef jar classpath）：三引擎乱序到达的排序、跨引擎 DOI 去重、**有 DOI 记录 vs 无 DOI 同标题记录的互去重**、title+year 去重、空/垃圾输入容错

## 二、修改类（2 个）

### `Metadata2BibtexConverter.java`（库）
`toEntryType` 增加 BibTeX 词汇表同义词（`article`/`inproceedings`/`incollection`/`techreport`/`unpublished`/`phdthesis`/`mastersthesis` 恒等映射）。原因：GS 适配器把原始 BibTeX entry type 放进 `publicationType`，round-trip 时不能退化成 `misc`。

### `MetaDataExtractorPage.java`（插件）
1. `searchMetadata()`：搜索开始时 `candidateAggregator.clear()` + `buildQuery(searchValue)` 构建 `MetadataQuery`（**粘贴 DOI → DOI 期望**（复用 `CrossrefProvider.extractDoi`），否则 → title 期望）
2. `onFinishedRequest()`：整段重写。不再逐引擎追加条目，而是把匹配当前查询的 `ScholarMetaData` 结果交给聚合器，然后**整体重刷**列表（clear + addEntries(getSortedEntryPairs())）——跨引擎排序与去重只有重发累积集才稳定。计数协议（requestCount/Finished x of N）不变
3. 删除了阶段二的 `filterDuplicateDois`/`containsDoi`/`normalizedDoi` 临时方案（被聚合器取代）
4. 移除未用的 `BibtexParser`/`ParserResult`/`TextNormalizer` 导入，新增 `Collections`/`MetadataQuery`/`CrossrefProvider` 导入

## 三、行为变化（用户可见）

| 之前 | 现在 |
|---|---|
| 各引擎结果按**到达顺序**追加 | 所有结果按 **finalScore 降序**统一排序（权重 title 0.5/author 0.2/year 0.15/doi 0.15，不可计算项重归一化） |
| 粘贴 DOI 时 DOI+Crossref 双引擎出双条目（阶段二已按 DOI 去重） | **双键去重**：DOI 键 + 标题+年份键，任一命中即折叠为得分最高的那条——连"GS 导出带 DOI 的记录" vs "Crossref 无 DOI 同标题记录"这种跨形態重复也覆盖 |
| GS 结果无结构化视图，无法打分 | GS 结果经适配后与 Crossref/DOI 同一打分体系 |

不变的：GS 检索路径与验证码流程、来源标签渲染、用户操作流程、`BibtexParser → BibtexEntry → BibtexDatabase` 下游链路、GS/Crossref/DOI 三个勾选框语义。

## 四、验证结果

| 测试 | 结果 |
|---|---|
| TestGoogleScholar（62 项，离线） | 全部通过 |
| TestAggregator（15 项，离线） | 全部通过 |
| TestCrossref / TestDoi / TestCrossrefEngine（回归） | 全部通过 |
| Ant 全量构建 | BUILD SUCCESSFUL（46s） |
| GUI 启动 | 11 插件全部加载，无新增异常 |

## 五、实施中发现并修复的问题

1. **双键去重缺口**：初版只按"有 DOI 用 DOI 键，无 DOI 用标题+年份键"，导致同一篇论文的"带 DOI 版本"与"无 DOI 版本"互不去重（TestAggregator 捕获）。修复为双键并存、任一命中即折叠。
2. **排序稳定性**：`Collections.sort` 稳定排序保证同分候选保持聚合顺序（GS rank 序），去重时"先到且同分者胜"可复现。

## 六、已知限制与下一步

- `MetadataQuery` 仍只从单一搜索框构建（DOI 或 title）；XMP 中已有的作者/年份/DOI **尚未注入**——这是提高打分区分度的最大剩余收益，留在聚合器增强阶段
- 去重键不含作者；极端情况下同名同年不同作者的论文会被误折叠（GS/Crossref 均按相关度返回，实际风险低）
- 决策点 B（GS 收敛到统一 Provider 注册）仍未实施，`GoogleScholarProvider.resolve*` 为空实现占位
- 下一步候选：XMP 注入 MetadataQuery → PubMed/OpenAlex Provider → 候选列表显示 finalScore（决策点 D 选项 2）
