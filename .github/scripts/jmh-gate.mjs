import fs from 'node:fs/promises';
import path from 'node:path';

const [configPath, ...resultPaths] = process.argv.slice(2);
if (!configPath || resultPaths.length === 0) {
  console.error('Usage: node jmh-gate.mjs <config.json> <result.json> [result2.json ...]');
  process.exit(2);
}

const config = JSON.parse(await fs.readFile(configPath, 'utf8'));
const benchmarks = config.benchmarks ?? [];
if (!Array.isArray(benchmarks) || benchmarks.length === 0) {
  console.error('No benchmarks configured in perf gates.');
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

const failures = [];
for (const gate of benchmarks) {
  const match = results.find((entry) => {
    if (entry.benchmark !== gate.name) return false;
    const params = gate.params ?? {};
    const entryParams = entry.params ?? {};
    return Object.entries(params).every(([key, value]) => `${entryParams[key]}` === `${value}`);
  });

  if (!match) {
    failures.push(`Missing benchmark result: ${gate.name} ${JSON.stringify(gate.params ?? {})}`);
    continue;
  }

  const p95 = resolveP95Ns(match);
  const alloc = resolveAllocBytes(match);

  if (p95 > gate.p95Ns) {
    failures.push(
      `${gate.name} p95 ${formatNs(p95)} > gate ${formatNs(gate.p95Ns)} (${JSON.stringify(gate.params ?? {})})`,
    );
  }
  if (alloc > gate.allocBytes) {
    failures.push(
      `${gate.name} alloc ${alloc.toFixed(2)} B/op > gate ${gate.allocBytes} (${JSON.stringify(gate.params ?? {})})`,
    );
  }
}

if (failures.length > 0) {
  console.error('Performance gates failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('Performance gates passed.');

function resolveP95Ns(entry) {
  const metric = entry.primaryMetric;
  if (!metric) {
    throw new Error(`Missing primaryMetric for ${entry.benchmark}`);
  }
  const scoreUnit = metric.scoreUnit ?? 'ns/op';
  const percentiles = metric.scorePercentiles ?? {};
  const p95Raw = percentiles['0.95'] ?? percentiles['95.0'] ?? metric.score;
  if (p95Raw === undefined || p95Raw === null) {
    throw new Error(`Missing p95 score for ${entry.benchmark}`);
  }
  return toNs(Number(p95Raw), scoreUnit);
}

function resolveAllocBytes(entry) {
  const secondary = entry.secondaryMetrics ?? {};
  const allocMetric = secondary['gc.alloc.rate.norm'];
  if (!allocMetric) {
    throw new Error(`Missing gc.alloc.rate.norm for ${entry.benchmark}`);
  }
  return Number(allocMetric.score);
}

function toNs(value, unit) {
  if (Number.isNaN(value)) {
    throw new Error(`Invalid value for ${unit}`);
  }
  switch (unit) {
    case 'ns/op':
      return value;
    case 'us/op':
    case 'µs/op':
      return value * 1_000;
    case 'ms/op':
      return value * 1_000_000;
    case 's/op':
      return value * 1_000_000_000;
    default:
      throw new Error(`Unsupported time unit: ${unit}`);
  }
}

function formatNs(value) {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(2)} ms/op`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(2)} µs/op`;
  }
  return `${value.toFixed(2)} ns/op`;
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
