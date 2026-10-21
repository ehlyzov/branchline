export const FIRST_SUCCESS_EXAMPLE_ID = 'prepare-order';

// Only these recipes are complete, standalone JSON transforms. Other catalog
// entries may be bodies, use shared storage, or consume XML.
export const RECIPE_EXAMPLE_IDS = [
  'prepare-order',
  'recipe-customer',
  'recipe-sales-summary',
  'recipe-incomplete-data',
  'recipe-test-report',
  'recipe-pipeline-guard'
] as const;

export type JourneyExercise = {
  instruction: string;
  patch?: { path: (string | number)[]; value: unknown };
  expectedOutput?: unknown;
  expectedError?: string;
};

export function journeyGuideUrl(exampleId: string): string | null {
  return RECIPE_EXAMPLE_IDS.some((id) => id === exampleId) ? `../recipes/${exampleId}/` : null;
}

export function journeyInstruction(exampleId: string, exercise?: JourneyExercise): string | null {
  return journeyGuideUrl(exampleId) ? exercise?.instruction ?? null : null;
}

export function currentJourneyFiles(exampleId: string, program: string, input: string, inputFormat: string) {
  if (!journeyGuideUrl(exampleId) || inputFormat !== 'json') return null;
  try { JSON.parse(input); } catch { return null; }
  return [
    { name: 'program.bl', content: program, type: 'text/plain;charset=utf-8' },
    { name: 'input.json', content: input, type: 'application/json;charset=utf-8' }
  ];
}
