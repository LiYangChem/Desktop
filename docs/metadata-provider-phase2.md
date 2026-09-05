# Phase 2 Implementation Record — DOIProvider

> Status: **DONE, verified** (2026-09-02)
> Scope: 实现 DOIProvider（doi.org content negotiation）；Google Scholar 链路继续零修改。
> Design basis: `docs/metadata-provider-architecture.md`（§Provider 层 DOIProvider、§R7 去重风险）

## 1. What Was Implemented

### 1.1 New code in `docear_metadata` (5 classes, all Java 1.6 syntax)

| Package | Class | Responsibility |
|---|---|---|
| `org.docear.metadata.providers` | `DOIProvider` | doi.org 内容协商：`GET https://doi.org/{doi}` + `Accept: application/vnd.citationstyles.csl+json` → CSL-JSON → `BibliographicMetadata`。**能解析任意注册机构（Crossref/DataCite/mEDRA…）的 DOI**——这是它相对 CrossrefProvider 的差异化价值（Zenodo 数据集、DataCite 预印本等）。`supports()` 仅在 query 含 DOI 时为 true；`resolveByTitle/Authors/TitleAndYear` 设计上返回空（精确解析器，非检索器）。5xx/429 单次重试 |
| `org.docear.metadata.io` | `CslJsonMapper` | **共享**的 CSL-JSON → `BibliographicMetadata` 映射器（从 CrossrefProvider 抽取）。接受 string/array 两种 title 形式；作者支持 family/given 与 DataCite 的 `literal`（机构作者，输出 `{Org Name}` 保护括号）|
| `org.docear.metadata.adapter` | `DoiSource` | MetaDataSource 枚举单例 DOI（列表来源标签）|
| | `DoiSearchEngine` | extends SearchEngine 桥接（与 CrossrefSearchEngine 同构：合并 options 成新 map、默认 timeout 10s）|
| | `DoiExtractor` | extends HtmlDataExtractor：query 是裸 DOI/doi.org URL/`doi:` 前缀时精确解析；**非 DOI 输入零网络调用**、仍恰好发 1 次 FetchedResultsEvent（空结果），满足向导 requestCount 协议 |

### 1.2 Refactors of phase-1 code (behaviour-preserving)

| Class | Change |
|---|---|
| `CrossrefProvider` | 私有 JSON→model 方法（~80 行）删除，委托 `CslJsonMapper.fromWorkItem()`。Crossref work item 本就是 CSL-JSON 超集，一份映射器服务两个 Provider——以后 PubMed/OpenAlex 若走 CSL 也能直接复用 |
| `Metadata2BibtexConverter` | `toEntryType` 同时接受两套类型词汇表：Crossref API（`journal-article`/`book-chapter`/`proceedings-article`…）与 CSL（`article-journal`/`chapter`/`paper-conference`/`thesis`…）。实测 doi.org 返回 CSL 名、api.crossref.org 返回 Crossref 名，必须双支持。新增 public `mapEntryType()` 测试钩子 |

### 1.3 Modified files (plugin layer, minimal intrusion)

| File | Change |
|---|---|
| `MetaDataExtractorPage.java` | ① import 3 类；② `preparePage()` 注册 `DoiSearchEngine`（GS/Crossref 注册保留）；③ `setupSources()` 按选项勾选 `DoiSearchEngine.class`；④ 渲染器增加 DoiSource 标签分支；⑤ **新增 `filterDuplicateDois()` 跨引擎 DOI 去重**（见 §3）|
| `MetaDataOptionsPage.java` | `DOCEAR_METADATA_SEARCH_DOI` 常量 + DOI 勾选框（布局 `4, 4`，与 Crossref 同一 scroll 面板）+ 属性读写 |
| `defaults.properties` | `docear_metadata_searchDOI = true`（默认开启）|
| `Resources_en.properties` | `docear.metadata.extraction.sources.doi=DOI (doi.org)` |

**Google Scholar 相关代码：依旧零修改。**

### 1.4 Binary artifact update

`docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar` 再次 `jar uf` 合并（新增 5 类 + 重编译的 CrossrefProvider/Metadata2BibtexConverter）。构建方式与 Phase 1 相同（见 `docs/metadata-provider-phase1.md` §2，源列表 `tools/new-sources.txt` 已更新）。

## 2. Design Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | DOIProvider = 精确解析器，不实现标题检索 | doi.org 没有 fuzzy search 端点；标题检索职责归 CrossrefProvider。架构文档 §resolveByDOI 如此规划 |
| D2 | 复用 `CrossrefProvider.extractDoi()` 识别 DOI 输入 | 同一正则（`10.\d{4,9}/...`、`doi:` 前缀、doi.org URL），两个 Provider 行为一致；避免正则漂移 |
| D3 | 抽取 `CslJsonMapper` 共享映射器 | Phase 1 在 CrossrefProvider 内私有一份；Phase 2 需要第二份相同逻辑。宁可小重构（有 TestCrossref 全量回归兜底）也不复制 80 行 |
| D4 | 非 DOI 输入在 DoiExtractor 里短路（零网络） | 用户默认勾选 3 个引擎；标题检索时 DOI 引擎必须"安静地空手而归"，否则每次搜索都白等一个请求 |
| D5 | 页面级 `filterDuplicateDois()`（按归一化 DOI 去重） | DOI 与 Crossref **都默认开启**且都会精确解析 DOI——不去重则粘贴 DOI 时同一论文出现两条（架构文档风险 R7 提前兑现）。聚合器的完整去重（标题+年份）仍留给后续阶段；此处只做 DOI 精确去重，~30 行，风险可控 |

## 3. Cross-engine DOI Dedup (minimal R7 mitigation)

`MetaDataExtractorPage.onFinishedRequest()` 在 `addEntries` 前过滤：新条目的 `doi` 字段（经 `TextNormalizer.normalizeDoi` 归一化）若已存在于结果列表则丢弃。引擎完成顺序不定，先到者保留——两条内容等价（同一 DOI），保留哪条都无损。无 DOI 字段的条目（如 GS 结果）原样通过。

## 4. Verification Results

| Test | Result |
|---|---|
| `tools/TestDoi.java`（类型映射/supports/Crossref DOI 三种输入形式/DataCite DOI/非 DOI 短路/引擎桥接/Crossref 回归，共 31 项断言） | **ALL PASSED** |
| 真实 doi.org 内容协商 | Crossref DOI（`10.1038/nature02431`→Nature 2004 论文）与 DataCite DOI（`10.5281/zenodo.3734853`）均正确解析出 title/authors/year/type/doi |
| 非 DOI 查询短路 | 0 ms、恰好 1 次空事件、无网络请求 |
| `tools/TestCrossref.java`（Phase 1 全量回归） | **ALL TESTS PASSED**（CslJsonMapper 重构无行为变化）|
| `tools/TestCrossrefEngine.java` | **PASSED**（引擎桥接协议不变）|
| Ant full build | **BUILD SUCCESSFUL**（43s）|
| GUI 启动 | 干净启动，11 插件全部 Installed+Started，无新增异常 |

## 5. Known Limitations / Next Steps (Phase 3 Candidates)

- DOI 发现仅覆盖**用户在搜索框粘贴 DOI**；XMP 元数据中的 DOI 尚未自动注入查询（架构文档规划的来源①，需向导层再改一处——留待聚合器阶段一并做）。
- `filterDuplicateDois` 只按 DOI 去重；GS 与 Crossref 对同一论文（无 DOI 字段时）仍可能双条——完整方案是架构文档中的 `MetadataCandidateAggregator`。
- DataCite 记录的 `container-title` 常为空/无意义（如 Zenodo 填 "Zenodo"），BibTeX journal 字段质量受限——数据源本身如此。
- doi.org 直连可达（未走系统代理）；与 Crossref 相同的代理注意事项见 Phase 1 文档。
- PubMed / OpenAlex Provider 按架构文档留待下一阶段。
