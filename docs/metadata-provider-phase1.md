# Phase 1 Implementation Record — CrossrefProvider

> Status: **DONE, verified** (2026-09-02)
> Scope: 只实现 CrossrefProvider；Google Scholar 链路零修改。
> Design basis: `docs/metadata-provider-architecture.md`

## 1. What Was Implemented

### 1.1 New code in `docear_metadata` (15 classes, all Java 1.6 syntax)

| Package | Class | Responsibility |
|---|---|---|
| `org.docear.metadata.model` | `BibliographicMetadata` | 统一元数据对象：title/authors/journal/year/volume/issue/pages/doi/url/publisher/abstract/publicationType |
| | `MetadataQuery` | 统一查询对象（title/authors/year/doi），`hasXxx()` 判断可用分量 |
| | `MetadataCandidate` | 候选结果：metadata + provider + titleSimilarity/authorSimilarity/yearMatch/doiMatch/finalScore，实现 Comparable 按 finalScore 降序 |
| `org.docear.metadata.match` | `TextNormalizer` | 标题/姓名/DOI 归一化（小写、标点折叠、首冠词剥离、连字符姓名不拆散、doi.org 前缀剥离） |
| | `SimilarityCalculator` | 标题相似度（0.6×token-Jaccard + 0.4×Levenshtein）；作者相似度（姓氏集合 Jaccard，空列表返回 -1=未知） |
| | `CandidateScorer` | 打分：title 0.5 / author 0.2 / year 0.15 / doi 0.15，未知分量剔除后权重再归一化 |
| `org.docear.metadata.io` | `JsonReader` | 零依赖最小 JSON 解析器（约 200 行，支持 Unicode 转义/数字/布尔/null/嵌套） |
| | `Metadata2BibtexConverter` | BibliographicMetadata → BibTeX 字符串；Crossref type→entry type 映射；citation key 生成（family+year，标题回退跳过首冠词）；页码 `-`→`--`；花括号转义 |
| `org.docear.metadata.providers` | `MetadataProvider` | 统一 Provider 接口：resolveByDOI/resolveByTitle/resolveByAuthors/resolveByTitleAndYear |
| | `CrossrefProvider` | Crossref REST API (`https://api.crossref.org/works`) 实现；DOI 精确解析 + `query.bibliographic` 模糊检索；JATS XML 摘要清洗；**429/5xx 退避重试**（1s/3s 两次）；可选 polite pool（`-Dorg.docear.metadata.crossref.mailto=…`） |
| `org.docear.metadata.adapter` | `CrossrefSource` | MetaDataSource 枚举单例 CROSSREF（列表来源标签） |
| | `BibMetaData` | **extends ScholarMetaData** —— 结构化元数据 + 自动生成的 BibTeX 字符串，通过下游 `instanceof ScholarMetaData` 检查 |
| | `CrossrefSearchEngine` | extends SearchEngine 桥接：把 hub 的 options 合并成 queryConfig（不改共享 map），默认 timeout 提升到 10s |
| | `CrossrefExtractor` | extends HtmlDataExtractor：检索→打分→排序→包装成 BibMetaData；输入是 DOI 时走精确解析；任何 IOException 都保证发出一次 FetchedResultsEvent（空结果），与向导的 requestCount 协议一致 |

### 1.2 Modified files (plugin layer, minimal intrusion)

| File | Change |
|---|---|
| `docear_plugin_bibtex/.../dialogs/MetaDataExtractorPage.java` | ① import 2 类；② `preparePage()` 注册 `CrossrefSearchEngine`（GS 注册保留）；③ `setupSources()` 按选项勾选 `CrossrefSearchEngine.class`；④ `onFinishedRequest()` 的 `source instanceof ScholarSource` 放宽为 **`result instanceof ScholarMetaData`**（关键单点，否则新结果被静默丢弃）；⑤ 状态条计数从“已注册引擎数”改为“**已选引擎数**”（修复勾选状态不同时进度卡住）；⑥ 预览渲染器增加 CrossrefSource 标签分支 |
| `docear_plugin_bibtex/.../dialogs/MetaDataOptionsPage.java` | 新增 `DOCEAR_METADATA_SEARCH_CROSSREF` 常量 + Crossref 勾选框（读/写属性） |
| `docear_plugin_bibtex/src/.../defaults.properties` | `docear_metadata_searchCrossref = true`（默认开启；GS 开关原样保留默认 true） |
| `docear_plugin_bibtex/resources/translations/Resources_en.properties` | `docear.metadata.extraction.sources.crossref=Crossref` |

**Google Scholar 相关代码：零修改。** 三个入口 Action、Wizard、JabRefCommons、MetaDataSearchHub、GS engine/extractor、JabRef fork、Freeplane、构建脚本全部未动。

### 1.3 Binary artifact update

`docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar`（fat jar）通过 `jar uf` 合并了 15 个新类的 class 文件。原 jar 备份在 `tools/docear-metadata-lib-backup-orig.jar`。Ant 主构建照常复制该 jar 到 `docear_framework/build/plugins/org.docear.plugin.bibtex/lib/`。

## 2. How to Rebuild After Changing `docear_metadata` Sources

```bash
# 1. compile the metadata lib classes (JDK8, target 1.6)
cd docear-desktop
<path-to-jdk8>/bin/javac.exe -source 1.6 -target 1.6 -encoding UTF-8 -nowarn \
  -cp docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar \
  -d ../tools/meta-classes \
  @<file containing the .java paths of the 5 new packages>

# 2. merge into the bundled jar
<path-to-jdk8>/bin/jar.exe uf docear_plugin_bibtex/lib/docear-metadata-lib-0.0.1.jar \
  -C ../tools/meta-classes .

# 3. rebuild the app (compiles plugin layer, copies jar)
bash ../tools/docear-build.sh build
```

Convenience: the source list is kept in `tools/new-sources.txt`.

## 3. Verification Results

| Test | Result |
|---|---|
| `tools/TestCrossref.java`（JsonReader/Normalizer/Similarity/Scorer/Provider live/Extractor 全链路） | **ALL PASSED**（含真实 Crossref API：标题检索返回 5 条、DOI 精确解析 1 条、BibTeX 生成正确） |
| `tools/TestCrossrefEngine.java`（引擎桥接，按 MetaDataExtractorPage 完全相同的 options 键） | **PASSED**：恰好 1 次 FetchedResultsEvent、结果数一致、全部 ScholarMetaData 兼容、无 captcha |
| Ant full build | **BUILD SUCCESSFUL**（48s 增量） |
| GUI 启动 | 干净启动：插件全部加载、项目与 .bib 打开、MindMap 模式就绪、无异常 |

## 4. Bugs Found & Fixed During Implementation

1. **连字符姓氏拆散**（`TextNormalizer.normalizeNamePart`）：`Berners-Lee` 被折成 `berners lee`，"Given Family" 形式取末 token 只剩 `lee`，作者匹配全灭。修复：`-` 和 `'` 直接删除不加空格 → `bernerslee`。
2. **429 限流**（`CrossrefProvider`）：测试连续请求触发 Crossref 限流。修复：429/5xx 退避重试（1s/3s），并支持 polite pool。
3. **首冠词**：`The Semantic Web` vs `Semantic Web` 只得 0.69。修复：归一化时剥离标题首个 a/an/the → 1.0。
4. **citation key 回退成 "the"**：无作者无年份时 key 取标题首词。修复：跳过首冠词。
5. **进度统计基数**（插件层）：requestCount 对比“已注册引擎数”，勾选状态不同时状态条永久停在 "Finished 1 of 2"。修复：改用已选 sources.size()。

## 5. Known Limitations / Next Steps (Phase 2 Candidates)

- Crossref `query.bibliographic` 对纯图书章节类标题的首条结果相关度一般——已有打分排序缓解，用户仍可手选。
- 不走系统代理设置（jsoup 直连）；api.crossref.org 国内一般可直连，Clash 全局模式可能反而变慢。
- PDF 标题提取仍走原 `PdfDataExtractor`（二进制 jar，字号启发式），标题提取质量不变。
- DOIProvider（doi.org content negotiation）与 PubMed/OpenAlex 按架构文档留待后续阶段。
