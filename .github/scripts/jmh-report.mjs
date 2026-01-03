import fs from 'node:fs/promises';
import path from 'node:path';

const [outputDir, ...resultPaths] = process.argv.slice(2);
if (!outputDir || resultPaths.length === 0) {
  console.error('Usage: node jmh-report.mjs <output-dir> <result.json> [result2.json ...]');
  process.exit(2);
}

const results = [];
for (const resultPath of resultPaths) {
  const resolved = path.resolve(resultPath);
  const jsonPath = await resolveJsonPath(resolved);
  const data = JSON.parse(await fs.readFile(jsonPath, 'utf8'));
  if (!Array.isArray(data)) {
    console.error(`Expected JMH JSON array in ${jsonPath}`);
    process.exit(2);
  }
  results.push(...data);
}

const rows = results.map((entry) => {
  const primary = entry.primaryMetric ?? {};
  const percentiles = primary.scorePercentiles ?? {};
  const unit = primary.scoreUnit ?? 'ns/op';
  const meanUs = toUs(Number(primary.score), unit);
  const p95Raw = percentiles['0.95'] ?? percentiles['95.0'] ?? primary.score;
  const p95Us = toUs(Number(p95Raw), unit);
  const alloc = resolveAllocBytes(entry);
  const params = entry.params ?? {};
  const dataset = params.dataset ?? '';
  const paramsText = Object.entries(params)
    .filter(([key]) => key !== 'dataset')
    .map(([key, value]) => `${key}=${value}`)
    .join(', ');

  const benchmark = entry.benchmark ?? 'unknown';
  const method = benchmark.split('.').pop();
  const suite = resolveSuite(benchmark);

  return {
    suite,
    method,
    dataset,
    paramsText,
    meanUs,
    p95Us,
    alloc,
    benchmark,
  };
});

rows.sort((a, b) => {
  const suiteOrder = rankSuite(a.suite) - rankSuite(b.suite);
  if (suiteOrder !== 0) return suiteOrder;
  const benchOrder = rankBenchmark(a.method) - rankBenchmark(b.method);
  if (benchOrder !== 0) return benchOrder;
  const datasetOrder = rankDataset(a.dataset) - rankDataset(b.dataset);
  if (datasetOrder !== 0) return datasetOrder;
  return a.method.localeCompare(b.method) || a.dataset.localeCompare(b.dataset);
});

const outputPath = path.resolve(outputDir);
await fs.mkdir(outputPath, { recursive: true });

const summaryMarkdown = buildMarkdown(rows);
const summaryCsv = buildCsv(rows);

await fs.writeFile(path.join(outputPath, 'jmh-summary.md'), summaryMarkdown, 'utf8');
await fs.writeFile(path.join(outputPath, 'jmh-summary.csv'), summaryCsv, 'utf8');

console.log(`Wrote ${rows.length} rows to ${outputPath}`);

function resolveSuite(benchmark) {
  if (benchmark.includes('.InterpreterTransformBenchmark')) return 'Interpreter';
  if (benchmark.includes('.VMTransformBenchmark')) return 'VM';
  return 'Other';
}

function resolveAllocBytes(entry) {
  const secondary = entry.secondaryMetrics ?? {};
  const metric =
    secondary['gc.alloc.rate.norm'] ??
    secondary['\u00b7gc.alloc.rate.norm'] ??
    secondary['.gc.alloc.rate.norm'];
  if (!metric || metric.score === undefined || metric.score === null) {
    return null;
  }
  const value = Number(metric.score);
  return Number.isNaN(value) ? null : value;
}

function toUs(value, unit) {
  if (Number.isNaN(value)) return null;
  switch (unit) {
    case 'ns/op':
      return value / 1_000;
    case 'us/op':
    case '\u00b5s/op':
      return value;
    case 'ms/op':
      return value * 1_000;
    case 's/op':
      return value * 1_000_000;
    default:
      return null;
  }
}

function formatNumber(value, digits = 3) {
  if (value === null || value === undefined) return 'n/a';
  return Number(value).toFixed(digits);
}

function formatAlloc(value) {
  if (value === null || value === undefined) return 'n/a';
  return Number(value).toFixed(1);
}

function titleCase(value) {
  if (!value) return 'n/a';
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function buildMarkdown(items) {
  const lines = [];
  lines.push(buildRuntimeSection(items));

  const comparison = buildComparison(items);
  if (comparison.length > 0) {
    lines.push('');
    lines.push(buildComparisonSection(comparison));
  }

  return `${lines.join('\n')}\n`;
}

function buildRuntimeSection(items) {
  const lines = [];
  lines.push('## Runtime and allocation (mean, p95)');
  lines.push('');
  lines.push('| Runtime | Dataset | Mean (us/op) | p95 (us/op) | Alloc (B/op) | Params |');
  lines.push('| --- | --- | ---: | ---: | ---: | --- |');
  for (const row of items) {
    const runtime = `${row.suite} ${row.method}`;
    const dataset = titleCase(row.dataset);
    const mean = formatNumber(row.meanUs);
    const p95 = formatNumber(row.p95Us);
    const alloc = formatAlloc(row.alloc);
    const params = row.paramsText || 'n/a';
    lines.push(`| ${runtime} | ${dataset} | ${mean} | ${p95} | ${alloc} | ${params} |`);
  }
  return lines.join('\n');
}

function buildComparisonSection(comparison) {
  const lines = [];
  lines.push('## Interpreter vs VM ratio (mean)');
  lines.push('');
  lines.push('| Benchmark | Dataset | Interpreter (us/op) | VM (us/op) | Ratio (VM/Interp) |');
  lines.push('| --- | --- | ---: | ---: | ---: |');
  for (const row of comparison) {
    lines.push(
      `| ${row.method} | ${titleCase(row.dataset)} | ${formatNumber(row.interpreter)} | ${formatNumber(row.vm)} | ${row.ratio.toFixed(2)}x |`,
    );
  }
  return lines.join('\n');
}

function buildCsv(items) {
  const header = [
    'suite',
    'benchmark',
    'dataset',
    'params',
    'mean_us_op',
    'p95_us_op',
    'alloc_b_op',
  ];
  const lines = [header.join(',')];
  for (const row of items) {
    const params = row.paramsText ? `"${row.paramsText}"` : '';
    lines.push(
      [
        row.suite,
        row.method,
        row.dataset,
        params,
        formatNumber(row.meanUs),
        formatNumber(row.p95Us),
        formatAlloc(row.alloc),
      ].join(','),
    );
  }
  return `${lines.join('\n')}\n`;
}

function buildComparison(items) {
  const map = new Map();
  for (const row of items) {
    if (row.suite !== 'Interpreter' && row.suite !== 'VM') continue;
    const key = `${row.method}::${row.dataset}`;
    const entry = map.get(key) ?? { method: row.method, dataset: row.dataset };
    entry[row.suite.toLowerCase()] = row.meanUs;
    map.set(key, entry);
  }

  const output = [];
  for (const entry of map.values()) {
    if (entry.interpreter === undefined || entry.vm === undefined) continue;
    output.push({
      method: entry.method,
      dataset: entry.dataset,
      interpreter: entry.interpreter,
      vm: entry.vm,
      ratio: entry.vm / entry.interpreter,
    });
  }

  output.sort((a, b) => {
    const benchOrder = rankBenchmark(a.method) - rankBenchmark(b.method);
    if (benchOrder !== 0) return benchOrder;
    const datasetOrder = rankDataset(a.dataset) - rankDataset(b.dataset);
    if (datasetOrder !== 0) return datasetOrder;
    return a.method.localeCompare(b.method) || a.dataset.localeCompare(b.dataset);
  });

  return output;
}

function rankSuite(suite) {
  if (suite === 'Interpreter') return 0;
  if (suite === 'VM') return 1;
  return 2;
}

function rankBenchmark(method) {
  const order = {
    pathExpressions: 0,
    arrayComprehensions: 1,
    typicalTransform: 2,
  };
  return order[method] ?? 99;
}

function rankDataset(dataset) {
  const order = {
    small: 0,
    medium: 1,
    large: 2,
  };
  return order[dataset] ?? 99;
}

async function resolveJsonPath(targetPath) {
  const stat = await fs.stat(targetPath);
  if (stat.isFile()) {
    return targetPath;
  }
  const entries = await fs.readdir(targetPath, { withFileTypes: true });
  const jsonFiles = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.json'))
    .map((entry) => path.join(targetPath, entry.name));
  if (jsonFiles.length === 0) {
    throw new Error(`No JSON results found in ${targetPath}`);
  }
  const withTimes = await Promise.all(
    jsonFiles.map(async (file) => ({ file, mtimeMs: (await fs.stat(file)).mtimeMs })),
  );
  withTimes.sort((a, b) => b.mtimeMs - a.mtimeMs);
  return withTimes[0].file;
}
