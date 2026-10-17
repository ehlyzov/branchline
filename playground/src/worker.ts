type InputFormat = 'json' | 'xml';
type ContractMode = 'off' | 'warn' | 'strict';
type OutputFormat = 'json' | 'json-compact' | 'json-canonical' | 'xml' | 'xml-compact';

type SharedStorageSpec = {
  name: string;
  kind: 'SINGLE' | 'MANY';
};

type WorkerRequest = {
  requestId: number;
  code: string;
  input: string;
  trace: boolean;
  inspect: boolean;
  inputFormat: InputFormat;
  outputFormat: OutputFormat;
  includeContracts: boolean;
  contractsMode: ContractMode;
  contractsDebug: boolean;
  shared: SharedStorageSpec[];
};

type WorkerResponse = {
  requestId: number;
  success: boolean;
  outputJson: string | null;
  errorMessage: string | null;
  line: number | null;
  column: number | null;
  explainJson: string | null;
  explainHuman: string | null;
  inputContractJson: string | null;
  outputContractJson: string | null;
  contractSource: string | null;
  contractWarnings: string | null;
  inspectResult: PlaygroundInspectPayload | null;
  inspectError: string | null;
};

import type {
  PlaygroundDiagnostic,
  PlaygroundInspectPayload,
  PlaygroundSubsetCompatibility
} from './playground-state';
import kotlinStdlibUrl from '../../interpreter/build/dist/js/productionLibrary/kotlin-kotlin-stdlib.js?url';
import immutableCollectionsUrl from '../../interpreter/build/dist/js/productionLibrary/Kotlin-Immutable-Collections-kotlinx-collections-immutable.js?url';
import atomicfuUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-atomicfu.js?url';
import coroutinesCoreUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-coroutines-core.js?url';
import serializationCoreUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-serialization-kotlinx-serialization-core.js?url';
import serializationJsonUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-serialization-kotlinx-serialization-json.js?url';
import interpreterUrl from '../../interpreter/build/dist/js/productionLibrary/branchline-interpreter.js?url';
import { prepareXmlInput } from './xml-input';
import { computeWrapperAdjustment, adjustResultForWrapper, adjustInspectForWrapper, type WrapperAdjustment } from './wrapper-diagnostics';

type PlaygroundFacade = {
  run(program: string, inputJson: string, enableTracing: boolean, includeContracts: boolean): WorkerResponse;
  isBodyOnlyProgram?(program: string): boolean;
  inspect?(program: string, sharedJsonConfig: string | null): RawPlaygroundInspectResult;
  runWithShared?(
    program: string,
    inputJson: string,
    enableTracing: boolean,
    includeContracts: boolean,
    sharedJsonConfig: string | null
  ): WorkerResponse;
  runWithContracts?(
    program: string,
    inputJson: string,
    enableTracing: boolean,
    includeContracts: boolean,
    contractsMode: ContractMode,
    contractsDebug: boolean,
    sharedJsonConfig: string | null,
    outputFormat: OutputFormat
  ): WorkerResponse;
};

type RawPlaygroundInspectResult = {
  success: boolean;
  normalizedSource: string | null;
  subsetCompatibility: string;
  diagnosticsJson: string;
  warningsJson: string;
  featureUsageJson: string;
  transformsJson: string;
  inspectJson: string | null;
  errorMessage: string | null;
};

const INTERPRETER_GLOBAL = 'io.github.ehlyzov.branchline:interpreter';

let facadePromise: Promise<PlaygroundFacade> | null = null;
const loadedScripts = new Set<string>();

async function loadScript(url: string): Promise<void> {
  if (loadedScripts.has(url)) {
    return;
  }

  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load interpreter bundle: ${url}`);
  }

  const source = await response.text();
  (0, eval)(source);
  loadedScripts.add(url);
}

function loadFacade(): Promise<PlaygroundFacade> {
  if (!facadePromise) {
    facadePromise = (async () => {
      const globalScope = self as typeof self & Record<string, unknown>;

      if (!globalScope[INTERPRETER_GLOBAL]) {
        await loadScript(kotlinStdlibUrl);
        await loadScript(immutableCollectionsUrl);
        await loadScript(atomicfuUrl);
        await loadScript(coroutinesCoreUrl);
        await loadScript(serializationCoreUrl);
        await loadScript(serializationJsonUrl);
        await loadScript(interpreterUrl);
      }

      const moduleRoot = globalScope[INTERPRETER_GLOBAL] as Record<string, unknown> | undefined;
      if (!moduleRoot) {
        throw new Error('Branchline interpreter module failed to initialize.');
      }

      const playgroundNamespace = (moduleRoot['playground'] ?? moduleRoot) as Record<string, unknown>;
      const facadeEntry = playgroundNamespace['PlaygroundFacade'];

      if (facadeEntry && typeof facadeEntry === 'object' && typeof (facadeEntry as PlaygroundFacade).run === 'function') {
        return facadeEntry as PlaygroundFacade;
      }

      if (typeof facadeEntry === 'function') {
        try {
          const maybeInstance = (facadeEntry as () => PlaygroundFacade)();
          if (maybeInstance && typeof maybeInstance.run === 'function') {
            return maybeInstance;
          }
        } catch (_error) {
          // ignore and fall back to getter resolution
        }
      }

      const getter = playgroundNamespace['PlaygroundFacade_getInstance'];
      if (typeof getter === 'function') {
        const instance = (getter as () => PlaygroundFacade)();
        if (instance && typeof instance.run === 'function') {
          return instance;
        }
      }

      throw new Error('Unable to resolve Branchline playground facade.');
    })().catch((error) => {
      facadePromise = null;
      throw error;
    });
  }
  return facadePromise;
}

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  const {
    requestId,
    code,
    input,
    trace,
    inspect,
    inputFormat,
    outputFormat = 'json',
    includeContracts,
    contractsMode,
    contractsDebug,
    shared
  } = event.data;
  const sharedOffset = shared.length ? shared.length + 1 : 0;
  let inspectResult: PlaygroundInspectPayload | null = null;
  let inspectError: string | null = null;
  try {
    const runner = await loadFacade();
    const isBodyOnly = runner.isBodyOnlyProgram?.(code) ?? !/\bTRANSFORM\b/i.test(code);
    const wrapperAdjustment = computeWrapperAdjustment(code, sharedOffset, isBodyOnly);
    const sharedJson = shared.length ? JSON.stringify(shared) : null;
    if (inspect) {
      try {
        inspectResult = runInspect(runner, code, sharedJson, wrapperAdjustment);
      } catch (error) {
        inspectError = error instanceof Error ? error.message : String(error);
      }
    }
    const payload = prepareInput(input, inputFormat);
    const result = runner.runWithContracts
      ? runner.runWithContracts(code, payload, trace, includeContracts, contractsMode, contractsDebug, sharedJson, outputFormat)
      : runner.runWithShared
        ? runner.runWithShared(code, payload, trace, includeContracts, sharedJson)
        : runner.run(code, payload, trace, includeContracts);
    const adjusted: WorkerResponse = {
      ...adjustResultForWrapper(result, wrapperAdjustment),
      requestId,
      inspectResult,
      inspectError
    };
    self.postMessage(adjusted satisfies WorkerResponse);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    const fallback: WorkerResponse = {
      requestId,
      success: false,
      outputJson: null,
      errorMessage: message,
      line: null,
      column: null,
      explainJson: null,
      explainHuman: null,
      inputContractJson: null,
      outputContractJson: null,
      contractSource: null,
      contractWarnings: null,
      inspectResult,
      inspectError
    };
    self.postMessage(fallback);
  }
};

export {};

function runInspect(
  runner: PlaygroundFacade,
  code: string,
  sharedJson: string | null,
  wrapperAdjustment: WrapperAdjustment | null
): PlaygroundInspectPayload {
  if (typeof runner.inspect !== 'function') {
    throw new Error('Branchline playground facade does not expose inspect.');
  }

  const raw = runner.inspect(code, sharedJson);
  return adjustInspectForWrapper(decodeInspectResult(raw), wrapperAdjustment);
}

function decodeInspectResult(raw: RawPlaygroundInspectResult): PlaygroundInspectPayload {
  return {
    success: raw.success,
    normalizedSource: raw.normalizedSource ?? null,
    subsetCompatibility: normalizeSubsetCompatibility(raw.subsetCompatibility),
    diagnostics: parseJsonArray<PlaygroundDiagnostic>(raw.diagnosticsJson),
    warnings: parseJsonArray<PlaygroundDiagnostic>(raw.warningsJson),
    featureUsage: parseFeatureUsage(raw.featureUsageJson),
    transforms: parseJsonArray<unknown>(raw.transformsJson),
    rawJson: raw.inspectJson
  };
}

function normalizeSubsetCompatibility(value: string): PlaygroundSubsetCompatibility {
  if (value === 'COMPATIBLE' || value === 'INCOMPATIBLE' || value === 'UNKNOWN') {
    return value;
  }
  return 'UNKNOWN';
}

function parseFeatureUsage(raw: string): { features: string[] } {
  const value = parseJsonValue(raw);
  if (value == null || typeof value !== 'object' || Array.isArray(value)) {
    return { features: [] };
  }
  const features = (value as { features?: unknown }).features;
  return {
    features: Array.isArray(features) ? features.filter((item): item is string => typeof item === 'string') : []
  };
}

function parseJsonArray<T>(raw: string): T[] {
  const value = parseJsonValue(raw);
  return Array.isArray(value) ? (value as T[]) : [];
}

function parseJsonValue(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch (_error) {
    return null;
  }
}

function prepareInput(raw: string, format: InputFormat): string {
  return format === 'xml' ? prepareXmlInput(raw) : raw;
}
