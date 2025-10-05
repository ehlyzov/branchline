#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { promises as fs } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const repoRoot = resolve(__dirname, '..', '..');
const fileSummaryScript = join(repoRoot, 'cli', 'scripts', 'junit-file-summary.bl');
const summaryScript = join(repoRoot, 'cli', 'scripts', 'junit-summary.bl');

async function main() {
  const [, , modulePath = ''] = process.argv;
  if (!modulePath) {
    console.error('Usage: node junit-summary.mjs <module-path>');
    process.exit(1);
  }

  const cliBin = await resolveCliBin();
  await ensureCliDependencies(cliBin);
  const moduleRoot = join(repoRoot, modulePath);
  const reportsRoot = join(moduleRoot, 'build', 'test-results');
  const reportsDir = join(moduleRoot, 'build', 'reports');
  const resultsPath = join(reportsDir, 'branchline-junit-results.jsonl');
  const summaryInputPath = join(reportsDir, 'branchline-junit-summary-input.json');
  const summaryOutputPath = join(reportsDir, 'branchline-junit-summary.json');

  let xmlFiles;
  try {
    xmlFiles = await collectXmlFiles(reportsRoot);
  } catch (error) {
    await setOutputs({ status: 'error', tests: 'no test reports directory', color: colorFor('error') });
    console.error(`Failed to read reports from ${reportsRoot}:`, error.message);
    return;
  }

  if (xmlFiles.length === 0) {
    await setOutputs({ status: 'error', tests: 'no test reports found', color: colorFor('error') });
    console.warn(`No test reports found under ${reportsRoot}`);
    return;
  }

  await fs.mkdir(reportsDir, { recursive: true });
  await fs.writeFile(resultsPath, '', 'utf8');

  const collected = [];
  for (const file of xmlFiles) {
    try {
      const result = await runBranchline(cliBin, fileSummaryScript, {
        inputPath: file,
        inputFormat: 'xml',
      });
      const entry = buildFileSummary(relative(repoRoot, file), result);
      collected.push(entry);
      await fs.appendFile(resultsPath, `${JSON.stringify(entry)}\n`);
    } catch (error) {
      console.error(`Branchline failed to process ${file}:`, error.message);
    }
  }

  if (collected.length === 0) {
    await setOutputs({ status: 'error', tests: 'no reports parsed', color: colorFor('error') });
    return;
  }

  const summaryInput = { reports: collected };
  await fs.writeFile(summaryInputPath, JSON.stringify(summaryInput), 'utf8');

  let summary;
  try {
    summary = await runBranchline(cliBin, summaryScript, { inputPath: summaryInputPath });
  } catch (error) {
    console.error('Failed to summarize Branchline results:', error.message);
    await setOutputs({ status: 'error', tests: 'summary evaluation failed', color: colorFor('error') });
    return;
  }

  await fs.writeFile(summaryOutputPath, JSON.stringify(summary, null, 2), 'utf8');

  const { status, tests, color } = summary;
  await setOutputs({ status, tests, color });
  console.log(`Summary for ${modulePath}: ${status} (${tests})`);
}

async function collectXmlFiles(root) {
  const entries = await safeReadDir(root);
  const files = [];
  for (const entry of entries) {
    const fullPath = join(root, entry.name);
    if (entry.isDirectory()) {
      const nested = await collectXmlFiles(fullPath);
      files.push(...nested);
    } else if (entry.isFile() && entry.name.endsWith('.xml')) {
      files.push(fullPath);
    }
  }
  return files;
}

async function safeReadDir(path) {
  try {
    return await fs.readdir(path, { withFileTypes: true });
  } catch (error) {
    if (error.code === 'ENOENT') {
      throw new Error('test reports directory not found');
    }
    throw error;
  }
}

function runBranchline(cliBin, scriptPath, { inputPath, inputFormat } = {}) {
  const cliArgs = [scriptPath];
  if (inputPath) {
    cliArgs.push('--input', inputPath);
    if (inputFormat) {
      cliArgs.push('--input-format', inputFormat);
    }
  }

  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [cliBin, ...cliArgs], {
      cwd: repoRoot,
      env: process.env,
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`Branchline CLI exited with ${code}: ${stderr || stdout}`));
        return;
      }
      try {
        const jsonText = extractJson(stdout);
        resolve(JSON.parse(jsonText));
      } catch (error) {
        reject(new Error(`Failed to parse Branchline output: ${error.message}\n${stdout}`));
      }
    });
  });
}

function extractJson(output) {
  const trimmed = output.trim();
  const start = trimmed.indexOf('{');
  const end = trimmed.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) {
    throw new Error('Branchline CLI did not produce JSON output');
  }
  return trimmed.slice(start, end + 1);
}

function buildFileSummary(path, result) {
  const suites = Array.isArray(result?.suites) ? result.suites : [];
  let tests = 0;
  let failures = 0;
  let errors = 0;
  let skipped = 0;

  for (const suite of suites) {
    tests += toCount(suite?.tests);
    failures += toCount(suite?.failures);
    errors += toCount(suite?.errors);
    skipped += toCount(suite?.skipped);
  }

  const failed = failures + errors;
  const status = statusForTotals(tests, failed);
  const summary = formatDetails({ totalTests: tests, failedCount: failed, totalSkipped: skipped });
  const color = colorFor(status);

  return {
    path,
    suites: suites.length,
    tests,
    failures,
    errors,
    skipped,
    failed,
    status,
    summary,
    color,
  };
}

function toCount(value) {
  if (value === null || value === undefined) {
    return 0;
  }
  const parsed = Number.parseInt(String(value), 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function statusForTotals(totalTests, failed) {
  if (totalTests === 0) {
    return 'error';
  }
  if (failed === 0) {
    return 'passing';
  }
  return 'failing';
}

async function resolveCliBin() {
  const override = process.env.BRANCHLINE_CLI_BIN;
  if (override) {
    return override;
  }

  const packagedBin = join(repoRoot, 'cli', 'build', 'cliJsPackage', 'bin', 'bl.cjs');
  if (await pathExists(packagedBin)) {
    return packagedBin;
  }

  throw new Error(
    'Branchline JS CLI not found. Run `./gradlew :cli:prepareJsCliPackage` (or set BRANCHLINE_CLI_BIN) before executing this script.',
  );
}

async function pathExists(path) {
  try {
    await fs.access(path);
    return true;
  } catch {
    return false;
  }
}

async function ensureCliDependencies(cliBin) {
  if (process.env.BRANCHLINE_SKIP_DEP_CHECK === '1') {
    return;
  }
  try {
    const requireFromCli = createRequire(cliBin);
    requireFromCli.resolve('fast-xml-parser');
  } catch (error) {
    throw new Error(
      'Branchline JS CLI is missing the "fast-xml-parser" dependency. Install it (e.g. `npm install --prefix cli/build/cliJsPackage fast-xml-parser`) or point BRANCHLINE_CLI_BIN to a build that bundles the module.',
    );
  }
}

function colorFor(status) {
  switch (status) {
    case 'passing':
      return '44cc11';
    case 'failing':
      return 'e05d44';
    case 'error':
      return 'f39c12';
    default:
      return '9f9f9f';
  }
}

function formatDetails({ totalTests, failedCount, totalSkipped }) {
  if (totalTests === 0) {
    return '0 tests';
  }
  const parts = [`${totalTests} tests`];
  if (failedCount > 0) {
    parts.push(`${failedCount} failed`);
  }
  if (totalSkipped > 0) {
    parts.push(`${totalSkipped} skipped`);
  }
  return parts.join(', ');
}

async function setOutputs(outputs) {
  const lines = Object.entries(outputs)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${key}=${String(value).replace(/%/g, '%25').replace(/\n/g, '%0A').replace(/\r/g, '%0D')}`);

  const githubOutput = process.env.GITHUB_OUTPUT;
  if (!githubOutput) {
    lines.forEach((line) => console.log(line));
    return;
  }
  await fs.appendFile(githubOutput, `${lines.join('\n')}\n`);
}

try {
  await main();
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(message);
  process.exit(1);
}
