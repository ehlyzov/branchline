#!/usr/bin/env node
import { promises as fs } from 'node:fs';
import { join } from 'node:path';

async function main() {
  const [, , modulePath = ''] = process.argv;
  if (!modulePath) {
    console.error('Usage: node junit-summary.mjs <module-path>');
    process.exit(1);
  }

  const reportsRoot = join(process.cwd(), modulePath, 'build', 'test-results');

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

  let totalTests = 0;
  let totalFailures = 0;
  let totalErrors = 0;
  let totalSkipped = 0;

  for (const file of xmlFiles) {
    try {
      const content = await fs.readFile(file, 'utf8');
      const suites = [...content.matchAll(/<testsuite\b[^>]*>/g)];
      for (const suiteMatch of suites) {
        const attrs = Object.fromEntries(
          [...suiteMatch[0].matchAll(/(\w+)="([^"]*)"/g)].map(([ , key, value ]) => [key, value]),
        );
        totalTests += parseInt(attrs.tests ?? '0', 10);
        totalFailures += parseInt(attrs.failures ?? '0', 10);
        totalErrors += parseInt(attrs.errors ?? '0', 10);
        totalSkipped += parseInt(attrs.skipped ?? '0', 10);
      }
    } catch (error) {
      console.error(`Failed to parse JUnit report ${file}:`, error.message);
    }
  }

  const failedCount = totalFailures + totalErrors;
  const status = failedCount === 0 && totalTests > 0 ? 'passing' : failedCount > 0 ? 'failing' : 'unknown';
  const color = colorFor(status);

  const details = formatDetails({ totalTests, failedCount, totalSkipped });
  await setOutputs({ status, tests: details, color });
  console.log(`Summary for ${modulePath}: ${status} (${details})`);
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

await main();
