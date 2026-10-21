import assert from 'node:assert/strict';
import { mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ts from 'typescript';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const sourcePath = join(root, 'src', 'playground-catalog.ts');
const tempDir = join(root, '.tmp-catalog-tests');
const tempModule = join(tempDir, 'playground-catalog.mjs');

await mkdir(tempDir, { recursive: true });

try {
  const source = await readFile(sourcePath, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ES2022,
      target: ts.ScriptTarget.ES2022,
      strict: true
    },
    fileName: sourcePath
  });
  await writeFile(tempModule, output.outputText);

  const catalog = await import(`${pathToFileURL(tempModule).href}?v=${Date.now()}`);
  const examples = [
    {
      id: 'hello-transform',
      title: 'Hello',
      category: 'reshape-data',
      tags: ['minimal', 'ai-canonical'],
      aiSubset: 'compatible'
    },
    {
      id: 'shared-memory-read',
      title: 'Shared Memory',
      category: 'state-and-time',
      tags: ['shared-memory'],
      aiSubset: 'incompatible'
    },
    {
      id: 'legacy',
      title: 'Legacy descriptor',
      tags: []
    }
  ];

  assert.deepEqual(
    catalog.catalogCategoryOptions(examples).map((option) => option.value),
    ['all', 'reshape-data', 'state-and-time', '__uncategorized__']
  );
  assert.deepEqual(
    catalog.filterCatalogExamples(examples, { category: 'state-and-time', aiSubset: 'all' }).map((item) => item.id),
    ['shared-memory-read']
  );
  assert.deepEqual(
    catalog.filterCatalogExamples(examples, { category: 'all', aiSubset: 'compatible' }).map((item) => item.id),
    ['hello-transform']
  );
  assert.deepEqual(
    catalog.filterCatalogExamples(examples, { category: '__uncategorized__', aiSubset: 'unknown' }).map((item) => item.id),
    ['legacy']
  );
  assert.equal(catalog.catalogCategoryLabel('__uncategorized__'), 'Uncategorized');
  assert.equal(catalog.catalogCategoryLabel('all'), 'All tasks');
  assert.equal(catalog.catalogCategoryLabel('reshape-data'), 'Reshape data');
  assert.equal(catalog.catalogAiSubsetLabel('incompatible'), 'AI incompatible');
  assert.deepEqual(catalog.visibleCatalogTags(['a', 'b', 'c', 'd']), ['a', 'b', 'c']);

  const fixtures = [];
  const ids = new Set();
  const taskValues = new Set(catalog.CATALOG_TASKS.map((task) => task.value));
  const filenames = (await readdir(join(root, 'examples'))).filter((name) => name.endsWith('.json')).sort();
  assert.ok(filenames.length > 0, 'The example catalog must not be empty');
  for (const filename of filenames) {
    const fixture = JSON.parse(await readFile(join(root, 'examples', filename), 'utf8'));
    const id = filename.slice(0, -5);
    assert.equal(fixture.id, id, `${filename}: explicit ID must preserve filename deep links`);
    assert.ok(!ids.has(fixture.id), `${filename}: duplicate ID`);
    ids.add(fixture.id);
    for (const field of ['title', 'description', 'category']) {
      assert.equal(typeof fixture[field], 'string', `${filename}: missing ${field}`);
      assert.ok(fixture[field].trim(), `${filename}: empty ${field}`);
    }
    assert.ok(taskValues.has(fixture.category), `${filename}: unknown task category ${fixture.category}`);
    assert.ok(Array.isArray(fixture.tags) && fixture.tags.length > 0, `${filename}: missing tags`);
    assert.ok(fixture.tags.every((tag) => typeof tag === 'string' && tag.trim()), `${filename}: invalid tags`);
    assert.equal(new Set(fixture.tags).size, fixture.tags.length, `${filename}: duplicate tags`);
    assert.ok(['compatible', 'incompatible', 'unknown'].includes(fixture.aiSubset), `${filename}: missing AI subset status`);
    fixtures.push(fixture);
  }

  const options = catalog.catalogCategoryOptions(fixtures);
  assert.deepEqual(options.slice(0, 4).map((option) => option.value),
    ['all', 'reshape-data', 'summarize-data', 'validate-data']);
  assert.ok(!options.some((option) => option.value === catalog.UNCATEGORIZED_CATEGORY));
  assert.deepEqual(catalog.catalogCategoryOptions([...fixtures].reverse()), options,
    'Task order must not depend on fixture discovery order');
  const taskGroups = catalog.catalogCategoryGroups(fixtures);
  assert.deepEqual(taskGroups.map((group) => group.label), ['Common tasks', 'Advanced tasks']);
  assert.deepEqual(taskGroups[0].options.map((option) => option.value),
    ['all', 'reshape-data', 'summarize-data', 'validate-data']);
  assert.deepEqual(taskGroups.flatMap((group) => group.options), options);

  const groups = catalog.catalogExampleGroups(fixtures);
  assert.deepEqual(groups.map((group) => group.value), options.slice(1).map((option) => option.value));
  assert.deepEqual(groups.flatMap((group) => group.examples.map((example) => example.id)).sort(), [...ids].sort(),
    'Grouping must include every example exactly once');
  assert.deepEqual(catalog.catalogExampleGroups([...fixtures].reverse()), groups,
    'Example order must not depend on fixture discovery order');
  for (const option of options.slice(1)) {
    for (const aiSubset of ['all', 'compatible', 'incompatible', 'unknown']) {
      const expected = fixtures.filter((fixture) => fixture.category === option.value &&
        (aiSubset === 'all' || fixture.aiSubset === aiSubset));
      assert.deepEqual(catalog.filterCatalogExamples(fixtures, { category: option.value, aiSubset }), expected);
    }
  }
  assert.deepEqual(catalog.filterCatalogExamples(fixtures, { category: 'missing-task', aiSubset: 'all' }), []);
  assert.deepEqual(catalog.catalogExampleGroups([]), []);
  const catalogGuide = await readFile(join(root, '..', 'docs', 'guides', 'examples.md'), 'utf8');
  const linkedIds = new Set([
    ...[...catalogGuide.matchAll(/\?example=([a-z0-9-]+)/g)].map((match) => match[1]),
    ...[...catalogGuide.matchAll(/\.\.\/recipes\/([a-z0-9-]+)\.md/g)].map((match) => match[1])
  ]);
  assert.deepEqual([...linkedIds].sort(), [...ids].sort(),
    'The documentation catalog must link every example, with no stale or orphaned IDs');
  console.log(`Checked metadata, task ordering, grouping, filters and documentation links for ${fixtures.length} examples.`);
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
