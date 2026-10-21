export const UNCATEGORIZED_CATEGORY = '__uncategorized__';

export type CatalogAiSubset = 'compatible' | 'incompatible' | 'unknown';
export type CatalogAiSubsetFilter = 'all' | CatalogAiSubset;
export type CatalogCategoryFilter = 'all' | string;

export type CatalogFilters = {
  category: CatalogCategoryFilter;
  aiSubset: CatalogAiSubsetFilter;
};

export type CatalogExample = {
  id: string;
  title: string;
  category?: string;
  tags: string[];
  aiSubset: CatalogAiSubset;
};

export type CatalogOption = {
  value: string;
  label: string;
};

// This order is shared by the task filter and the grouped example picker.
export const CATALOG_TASKS = [
  { value: 'reshape-data', label: 'Reshape data', advanced: false },
  { value: 'summarize-data', label: 'Summarize data', advanced: false },
  { value: 'validate-data', label: 'Validate data', advanced: false },
  { value: 'text-and-encoding', label: 'Format text and encode payloads', advanced: true },
  { value: 'xml-data', label: 'Read and write XML', advanced: true },
  { value: 'numeric-data', label: 'Work with numbers and JSON output', advanced: true },
  { value: 'inspect-data', label: 'Inspect paths and derived values', advanced: true },
  { value: 'contracts', label: 'Inspect input and output contracts', advanced: true },
  { value: 'state-and-time', label: 'Work with shared state and time', advanced: true },
  { value: 'configure-transforms', label: 'Configure transforms and identifiers', advanced: true }
] as const;

export const CATALOG_AI_SUBSET_OPTIONS: CatalogOption[] = [
  { value: 'all', label: 'All subset statuses' },
  { value: 'compatible', label: 'AI compatible' },
  { value: 'incompatible', label: 'AI incompatible' },
  { value: 'unknown', label: 'Unknown or unmarked' }
];

export function catalogAiSubsetValue(value?: string): CatalogAiSubset {
  if (value === 'compatible' || value === 'incompatible' || value === 'unknown') {
    return value;
  }
  return 'unknown';
}

export function catalogAiSubsetLabel(value: CatalogAiSubsetFilter): string {
  return CATALOG_AI_SUBSET_OPTIONS.find((option) => option.value === value)?.label ?? 'Unknown or unmarked';
}

export function catalogCategoryValue(example: { category?: string }): string {
  return example.category?.trim() || UNCATEGORIZED_CATEGORY;
}

export function catalogCategoryLabel(value: string): string {
  if (value === 'all') {
    return 'All tasks';
  }
  if (value === UNCATEGORIZED_CATEGORY) {
    return 'Uncategorized';
  }
  const task = CATALOG_TASKS.find((task) => task.value === value);
  if (task) {
    return task.label;
  }
  return value
    .split('-')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function catalogCategoryOptions(examples: CatalogExample[]): CatalogOption[] {
  const categories = new Set<string>();
  let hasUncategorized = false;

  for (const example of examples) {
    const category = catalogCategoryValue(example);
    if (category === UNCATEGORIZED_CATEGORY) {
      hasUncategorized = true;
    } else {
      categories.add(category);
    }
  }

  const ordered = CATALOG_TASKS.map((task) => task.value as string);
  const options = [...categories].sort((a, b) => {
    const aIndex = ordered.indexOf(a);
    const bIndex = ordered.indexOf(b);
    return (aIndex < 0 ? ordered.length : aIndex) - (bIndex < 0 ? ordered.length : bIndex) ||
      compareText(a, b);
  }).map((category) => ({
    value: category,
    label: catalogCategoryLabel(category)
  }));

  if (hasUncategorized) {
    options.push({
      value: UNCATEGORIZED_CATEGORY,
      label: catalogCategoryLabel(UNCATEGORIZED_CATEGORY)
    });
  }

  return [{ value: 'all', label: 'All tasks' }, ...options];
}

function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

export function catalogCategoryGroups(examples: CatalogExample[]): { label: string; options: CatalogOption[] }[] {
  const options = catalogCategoryOptions(examples);
  const primary = new Set<string>(CATALOG_TASKS.filter((task) => !task.advanced).map((task) => task.value));
  return [
    { label: 'Common tasks', options: options.filter((option) => option.value === 'all' || primary.has(option.value)) },
    { label: 'Advanced tasks', options: options.filter((option) => option.value !== 'all' && !primary.has(option.value)) }
  ].filter((group) => group.options.length > 0);
}

export function catalogExampleGroups<T extends CatalogExample>(examples: T[]): { value: string; label: string; examples: T[] }[] {
  return catalogCategoryOptions(examples)
    .filter((option) => option.value !== 'all')
    .map((option) => ({
      ...option,
      examples: examples.filter((example) => catalogCategoryValue(example) === option.value)
        .sort((a, b) => compareText(a.title.toLowerCase(), b.title.toLowerCase()) || compareText(a.id, b.id))
    }));
}

export function filterCatalogExamples<T extends CatalogExample>(examples: T[], filters: CatalogFilters): T[] {
  return examples.filter((example) => {
    const matchesCategory =
      filters.category === 'all' || catalogCategoryValue(example) === filters.category;
    const matchesSubset =
      filters.aiSubset === 'all' || catalogAiSubsetValue(example.aiSubset) === filters.aiSubset;
    return matchesCategory && matchesSubset;
  });
}

export function visibleCatalogTags(tags: string[]): string[] {
  return tags.slice(0, 3);
}
