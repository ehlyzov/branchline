export const FIRST_SUCCESS_EXAMPLE_ID = 'prepare-order';

export type JourneyExercise = { instruction: string };

export function journeyInstruction(exampleId: string, exercise?: JourneyExercise): string | null {
  return exampleId === FIRST_SUCCESS_EXAMPLE_ID ? exercise?.instruction ?? null : null;
}

export function currentJourneyFiles(exampleId: string, program: string, input: string, inputFormat: string) {
  // The starter is a complete TRANSFORM. Other catalog entries can be bodies,
  // use shared storage, or consume XML; do not present those as this CLI journey.
  if (exampleId !== FIRST_SUCCESS_EXAMPLE_ID || inputFormat !== 'json') return null;
  try { JSON.parse(input); } catch { return null; }
  return [
    { name: 'program.bl', content: program, type: 'text/plain;charset=utf-8' },
    { name: 'input.json', content: input, type: 'application/json;charset=utf-8' }
  ];
}
