import React from 'react';
import './monaco-environment';
import * as monaco from 'monaco-editor';
import { BRANCHLINE_LANGUAGE_ID, ensureBranchlineLanguage } from './branchline-language';
import {
  CATALOG_AI_SUBSET_OPTIONS,
  catalogAiSubsetLabel,
  catalogAiSubsetValue,
  catalogCategoryLabel,
  catalogCategoryOptions,
  catalogCategoryValue,
  filterCatalogExamples,
  visibleCatalogTags,
  type CatalogAiSubset,
  type CatalogAiSubsetFilter,
  type CatalogCategoryFilter
} from './playground-catalog';
import {
  cancelInspectRequest,
  createInitialPlaygroundState,
  receiveInspectResult,
  startInspectRequest,
  type PlaygroundDiagnostic,
  type PlaygroundInspectPayload
} from './playground-state';
import './playground.css';

const DEFAULT_PROGRAM = `LET fullName = input.first_name + " " + input.last_name;
LET loyalty = input.loyalty_tier ?? "standard";

OUTPUT {
    id: input.id,
    full_name: fullName,
    loyalty_tier: loyalty,
    shipping_city: input.address.city
}`;

const DEFAULT_INPUT = `{
  "id": 42,
  "first_name": "Ada",
  "last_name": "Lovelace",
  "loyalty_tier": null,
  "address": {
    "city": "London"
  }
}`;

const PLAYGROUND_HOSTED_URL = 'https://ehlyzov.github.io/branchline/playground/';

type InputFormat = 'json' | 'xml';
type ContractMode = 'off' | 'warn' | 'strict';
type OutputFormat = 'json' | 'json-compact' | 'json-canonical' | 'xml' | 'xml-compact';

type RawExample = {
  title: string;
  id?: string;
  category?: string;
  tags?: string[];
  aiSubset?: string;
  description?: string;
  program: string | string[];
  input: unknown;
  inputFormat?: InputFormat;
  outputFormat?: OutputFormat;
  trace?: boolean;
  showContracts?: boolean;
  shared?: SharedStorageSpec[];
};

type ExampleModule = {
  default: RawExample;
};

type PlaygroundExample = {
  id: string;
  title: string;
  category?: string;
  tags: string[];
  aiSubset: CatalogAiSubset;
  description?: string;
  program: string;
  input: string;
  inputFormat: InputFormat;
  outputFormat: OutputFormat;
  enableTracing: boolean;
  enableContracts: boolean;
  shared: SharedStorageSpec[];
};

type SharedStorageSpec = {
  name: string;
  kind: 'SINGLE' | 'MANY';
};

const exampleModules = import.meta.glob<ExampleModule>('../examples/*.json', {
  eager: true
});

function normalizeExample(id: string, raw: RawExample): PlaygroundExample {
  const program = Array.isArray(raw.program) ? raw.program.join('\n') : raw.program ?? DEFAULT_PROGRAM;
  let input = '';
  const inputFormat = raw.inputFormat ?? 'json';
  const outputFormat = raw.outputFormat ?? 'json';

  if (typeof raw.input === 'string') {
    input = raw.input;
  } else {
    input = JSON.stringify(raw.input, null, 2);
  }

  return {
    id,
    title: raw.title,
    category: raw.category,
    tags: Array.isArray(raw.tags) ? raw.tags.filter((tag): tag is string => typeof tag === 'string') : [],
    aiSubset: catalogAiSubsetValue(raw.aiSubset),
    description: raw.description,
    program,
    input: input || DEFAULT_INPUT,
    inputFormat,
    outputFormat,
    enableTracing: Boolean(raw.trace),
    enableContracts: Boolean(raw.showContracts),
    shared: raw.shared ?? []
  };
}

type WorkerResult = {
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

type BranchlinePlaygroundProps = {
  defaultExampleId?: string;
};

type PlaygroundDraft = {
  program: string;
  input: string;
  inputFormat: InputFormat;
  outputFormat: OutputFormat;
  tracing: boolean;
  contracts: boolean;
  contractsMode: ContractMode;
  contractsDebug: boolean;
};

function originalDraft(example: PlaygroundExample): PlaygroundDraft {
  return {
    program: example.program, input: example.input,
    inputFormat: example.inputFormat, outputFormat: example.outputFormat,
    tracing: example.enableTracing, contracts: example.enableContracts,
    contractsMode: 'off', contractsDebug: false
  };
}

type InspectTab = 'normalized' | 'diagnostics' | 'blockers';

type InspectTabDefinition = {
  id: InspectTab;
  label: string;
};

const INSPECT_TABS: InspectTabDefinition[] = [
  { id: 'normalized', label: 'Normalized source' },
  { id: 'diagnostics', label: 'Diagnostics' },
  { id: 'blockers', label: 'Subset blockers' }
];

function readExampleFromLocation(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get('example');
  if (fromQuery) {
    return fromQuery;
  }
  const hash = window.location.hash?.replace(/^#/, '');
  return hash || null;
}

function resolveDefaultExample(examples: PlaygroundExample[], preferred?: string | null): string {
  const requested = readExampleFromLocation() ?? preferred;
  if (requested) {
    const match = examples.find((item) => item.id === requested);
    if (match) {
      return match.id;
    }
  }
  return examples[0]?.id ?? '';
}

function buildHostedPlaygroundUrl(exampleId: string): string {
  if (!exampleId) {
    return PLAYGROUND_HOSTED_URL;
  }
  return `${PLAYGROUND_HOSTED_URL}?example=${encodeURIComponent(exampleId)}`;
}

function isSubsetBlocker(diagnostic: PlaygroundDiagnostic): boolean {
  return (
    diagnostic.category === 'unsupported-subset' ||
    diagnostic.code === 'unsupported_in_ai_subset' ||
    diagnostic.payload?.operation === 'ai-subset-check'
  );
}

function formatSpan(diagnostic: PlaygroundDiagnostic): string | null {
  const span = diagnostic.span;
  if (span == null) {
    return null;
  }
  const samePosition = span.startLine === span.endLine && span.startColumn === span.endColumn;
  const end = samePosition ? '' : `-${span.endLine}:${span.endColumn}`;
  return `line ${span.startLine}:${span.startColumn}${end}`;
}

function formatDiagnosticPayload(diagnostic: PlaygroundDiagnostic): string | null {
  const payload = diagnostic.payload;
  if (payload == null) {
    return null;
  }
  const entries = [
    payload.operation ? `operation: ${payload.operation}` : null,
    payload.targetPath ? `path: ${payload.targetPath}` : null,
    payload.expectedKind ? `expected: ${payload.expectedKind}` : null,
    payload.actualKind ? `actual: ${payload.actualKind}` : null,
    payload.hint ? `hint: ${payload.hint}` : null
  ].filter((item): item is string => item != null);
  return entries.length > 0 ? entries.join(' | ') : null;
}

function diagnosticsForInspect(result: PlaygroundInspectPayload | null): PlaygroundDiagnostic[] {
  if (result == null) {
    return [];
  }
  return [...result.diagnostics, ...result.warnings];
}

function subsetBlockersForInspect(result: PlaygroundInspectPayload | null): PlaygroundDiagnostic[] {
  return diagnosticsForInspect(result).filter(isSubsetBlocker);
}

function compatibilityLabel(result: PlaygroundInspectPayload | null): string {
  if (result == null) {
    return 'Not inspected';
  }
  if (result.subsetCompatibility === 'COMPATIBLE') {
    return 'Compatible';
  }
  if (result.subsetCompatibility === 'INCOMPATIBLE') {
    return 'Incompatible';
  }
  return 'Unknown';
}

function compatibilityClass(result: PlaygroundInspectPayload | null): string {
  return result?.subsetCompatibility.toLowerCase() ?? 'not-inspected';
}

export function BranchlinePlayground({ defaultExampleId }: BranchlinePlaygroundProps) {
  const programContainerRef = React.useRef<HTMLDivElement | null>(null);
  const inputContainerRef = React.useRef<HTMLDivElement | null>(null);
  const programEditorRef = React.useRef<monaco.editor.IStandaloneCodeEditor>();
  const inputEditorRef = React.useRef<monaco.editor.IStandaloneCodeEditor>();
  const workerRef = React.useRef<Worker>();
  const restartWorkerRef = React.useRef<() => void>(() => {});
  const failWorkerRef = React.useRef<(message: string) => void>(() => {});
  const runningRef = React.useRef(false);
  const [lifecycleMessage, setLifecycleMessage] = React.useState<string | null>(null);
  const requestIdRef = React.useRef(0);
  const runRef = React.useRef<() => void>(() => {});
  const draftsRef = React.useRef(new Map<string, PlaygroundDraft>());
  const revisionRef = React.useRef(0);
  const submittedRevisionRef = React.useRef(0);
  const [revision, setRevision] = React.useState(0);
  const [resultRevision, setResultRevision] = React.useState<number | null>(null);
  const instanceId = React.useId();
  const inspectTabRefs = React.useRef<Array<HTMLButtonElement | null>>([]);
  const markEdited = React.useCallback(() => {
    revisionRef.current += 1;
    setRevision(revisionRef.current);
    const model = programEditorRef.current?.getModel();
    if (model) monaco.editor.setModelMarkers(model, 'branchline', []);
  }, []);
  const [playgroundState, setPlaygroundState] = React.useState(() => createInitialPlaygroundState());
  const [catalogCategory, setCatalogCategory] = React.useState<CatalogCategoryFilter>('all');
  const [catalogAiSubset, setCatalogAiSubset] = React.useState<CatalogAiSubsetFilter>('all');
  const [inputFormat, setInputFormat] = React.useState<InputFormat>('json');
  const [outputFormat, setOutputFormat] = React.useState<OutputFormat>('json');

  const [isRunning, setIsRunning] = React.useState(false);
  const [activeInspectTab, setActiveInspectTab] = React.useState<InspectTab>('normalized');
  const [error, setError] = React.useState<string | null>(null);
  const [output, setOutput] = React.useState('');
  const [traceHuman, setTraceHuman] = React.useState<string | null>(null);
  const [traceJson, setTraceJson] = React.useState<string | null>(null);
  const [inputContract, setInputContract] = React.useState<string | null>(null);
  const [outputContract, setOutputContract] = React.useState<string | null>(null);
  const [contractSource, setContractSource] = React.useState<string | null>(null);
  const [contractWarnings, setContractWarnings] = React.useState<string | null>(null);
  const examples = React.useMemo(() => {
    const items: PlaygroundExample[] = Object.entries(exampleModules).map(([path, module]) => {
      const filename = path.split('/').pop() ?? 'example';
      const id = filename.replace(/\.json$/i, '');
      return normalizeExample(id, module.default);
    });

    if (items.length === 0) {
      items.push(
        normalizeExample('starter', {
          title: 'Starter playground example',
          description: 'Begin with a minimal Branchline transform body and an accompanying JSON message.',
          program: DEFAULT_PROGRAM,
          input: JSON.parse(DEFAULT_INPUT)
        })
      );
    }

    return items.sort((a, b) => a.title.localeCompare(b.title));
  }, []);
  const categoryOptions = React.useMemo(() => catalogCategoryOptions(examples), [examples]);
  const filteredExamples = React.useMemo(
    () => filterCatalogExamples(examples, { category: catalogCategory, aiSubset: catalogAiSubset }),
    [catalogAiSubset, catalogCategory, examples]
  );
  const [selectedExampleId, setSelectedExampleId] = React.useState(() =>
    resolveDefaultExample(examples, defaultExampleId)
  );
  const selectedExample = React.useMemo(
    () => examples.find((item) => item.id === selectedExampleId) ?? null,
    [examples, selectedExampleId]
  );
  const openInNewTabUrl = React.useMemo(
    () => buildHostedPlaygroundUrl(selectedExampleId),
    [selectedExampleId]
  );
  const selectedExampleInFiltered = filteredExamples.some((example) => example.id === selectedExampleId);
  const visibleSelectedExample = selectedExample;
  const selectedTags = visibleSelectedExample ? visibleCatalogTags(visibleSelectedExample.tags) : [];
  const hiddenTagCount = visibleSelectedExample ? Math.max(0, visibleSelectedExample.tags.length - selectedTags.length) : 0;
  const [isTracingEnabled, setIsTracingEnabled] = React.useState<boolean>(() => selectedExample?.enableTracing ?? false);
  const [isContractsEnabled, setIsContractsEnabled] = React.useState<boolean>(() => selectedExample?.enableContracts ?? false);
  const [contractsMode, setContractsMode] = React.useState<ContractMode>('off');
  const [contractsDebug, setContractsDebug] = React.useState(false);
  const currentDraft = React.useCallback((): PlaygroundDraft => ({
    program: programEditorRef.current?.getValue() ?? selectedExample?.program ?? '',
    input: inputEditorRef.current?.getValue() ?? selectedExample?.input ?? '',
    inputFormat, outputFormat, tracing: isTracingEnabled, contracts: isContractsEnabled,
    contractsMode, contractsDebug
  }), [selectedExample, inputFormat, outputFormat, isTracingEnabled, isContractsEnabled, contractsMode, contractsDebug]);

  const selectExample = (id: string) => {
    if (id === selectedExampleId) return;
    draftsRef.current.set(selectedExampleId, currentDraft());
    setSelectedExampleId(id);
  };

  const run = React.useCallback(() => {
    if (runningRef.current) return;
    if (!workerRef.current) restartWorkerRef.current();
    if (!workerRef.current) return;
    setLifecycleMessage(null);
    submittedRevisionRef.current = revisionRef.current;
    const program = programEditorRef.current?.getValue() ?? '';
    const input = inputEditorRef.current?.getValue() ?? '';
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setPlaygroundState((state) => startInspectRequest(state, requestId));
    runningRef.current = true;
    setIsRunning(true);
    setError(null);
    setTraceHuman(null);
    setTraceJson(null);
    setInputContract(null);
    setOutputContract(null);
    setContractSource(null);
    setContractWarnings(null);
    try {
      workerRef.current.postMessage({
        requestId,
        code: program,
        input,
        trace: isTracingEnabled,
        inspect: true,
        inputFormat,
        outputFormat,
        includeContracts: isContractsEnabled,
        contractsMode,
        contractsDebug,
        shared: selectedExample?.shared ?? []
      });
    } catch {
      failWorkerRef.current('The playground could not start this run. Run again to retry.');
    }
  }, [contractsDebug, contractsMode, inputFormat, isContractsEnabled, isTracingEnabled, outputFormat, selectedExample]);
  runRef.current = run;

  const loadExample = React.useCallback((draft?: PlaygroundDraft) => {
    if (!selectedExample) {
      return;
    }
    const next = draft ?? originalDraft(selectedExample);
    const { program, input, inputFormat: format, outputFormat: outputFmt } = next;

    if (programEditorRef.current) {
      programEditorRef.current.setValue(program);
    }
    if (inputEditorRef.current) {
      inputEditorRef.current.setValue(input);
    }

    setInputFormat(format);
    setOutputFormat(outputFmt);

    setLifecycleMessage(null);
    setResultRevision(null);
    setError(null);
    runningRef.current = false;
    setIsRunning(false);
    setOutput('');
    setTraceHuman(null);
    setTraceJson(null);
    setInputContract(null);
    setOutputContract(null);
    setContractSource(null);
    setContractWarnings(null);
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setPlaygroundState((state) => cancelInspectRequest(state, requestId));

    setIsTracingEnabled(next.tracing);
    setIsContractsEnabled(next.contracts);
    setContractsMode(next.contractsMode);
    setContractsDebug(next.contractsDebug);
    restartWorkerRef.current();
  }, [selectedExample]);

  const resetExample = () => {
    if (!selectedExample) return;
    if (JSON.stringify(currentDraft()) !== JSON.stringify(originalDraft(selectedExample)) &&
        !window.confirm('Discard edits and restore this example?')) return;
    draftsRef.current.delete(selectedExampleId);
    loadExample();
  };

  const stopRun = () => {
    requestIdRef.current += 1;
    setPlaygroundState((state) => cancelInspectRequest(state, requestIdRef.current));
    runningRef.current = false;
    setIsRunning(false);
    setLifecycleMessage('Run stopped. Edit or Run again when ready.');
    restartWorkerRef.current();
  };

  React.useEffect(() => {
    ensureBranchlineLanguage();

    if (programContainerRef.current && !programEditorRef.current) {
      programEditorRef.current = monaco.editor.create(programContainerRef.current, {
        value: selectedExample?.program ?? DEFAULT_PROGRAM,
        language: BRANCHLINE_LANGUAGE_ID,
        automaticLayout: true,
        minimap: { enabled: false },
        fontSize: 14,
        ariaLabel: 'Branchline program editor'
      });
      programEditorRef.current.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current());
      programEditorRef.current.onDidChangeModelContent(markEdited);
    }

    if (inputContainerRef.current && !inputEditorRef.current) {
      inputEditorRef.current = monaco.editor.create(inputContainerRef.current, {
        value: selectedExample?.input ?? DEFAULT_INPUT,
        language: 'json',
        automaticLayout: true,
        minimap: { enabled: false },
        fontSize: 14,
        ariaLabel: 'Input data editor'
      });
      inputEditorRef.current.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current());
      inputEditorRef.current.onDidChangeModelContent(markEdited);
    }

    const receiveResult = (event: MessageEvent<WorkerResult>) => {
      const result = event.data;
      setPlaygroundState((state) =>
        receiveInspectResult(state, result.requestId, result.inspectResult, result.inspectError).state
      );

      if (result.requestId !== requestIdRef.current) {
        return;
      }

      runningRef.current = false;
      setIsRunning(false);
      setResultRevision(submittedRevisionRef.current);

      if (programEditorRef.current && submittedRevisionRef.current === revisionRef.current) {
        const model = programEditorRef.current.getModel();
        if (model) {
          if (result.success || result.line == null || result.column == null) {
            monaco.editor.setModelMarkers(model, 'branchline', []);
          } else {
            const line = result.line;
            const column = result.column;
            monaco.editor.setModelMarkers(model, 'branchline', [
              {
                startLineNumber: line,
                startColumn: column,
                endLineNumber: line,
                endColumn: column + 1,
                message: result.errorMessage ?? 'Error',
                severity: monaco.MarkerSeverity.Error
              }
            ]);
          }
        }
      }

      if (result.success) {
        setError(null);
        setOutput(result.outputJson ?? 'null');
        setTraceHuman(result.explainHuman ?? null);
        setTraceJson(result.explainJson ?? null);
        setInputContract(result.inputContractJson ?? null);
        setOutputContract(result.outputContractJson ?? null);
        setContractSource(result.contractSource ?? null);
        setContractWarnings(result.contractWarnings ?? null);
      } else {
        const location =
          result.line != null && result.column != null
            ? ` (line ${result.line}, column ${result.column})`
            : '';
        const message = result.errorMessage ?? 'Unexpected error';
        const formatted = `${message}${location}`;
        setError(formatted);
        setOutput('');
        setTraceHuman(null);
        setTraceJson(null);
        setInputContract(null);
        setOutputContract(null);
        setContractSource(null);
        setContractWarnings(null);
      }
    };

    const failWorker = (message: string) => {
      requestIdRef.current += 1;
      setPlaygroundState((state) => cancelInspectRequest(state, requestIdRef.current, message));
      runningRef.current = false;
      setIsRunning(false);
      setLifecycleMessage(null);
      setError(message);
      setOutput('');
      setTraceHuman(null);
      setTraceJson(null);
      setInputContract(null);
      setOutputContract(null);
      setContractSource(null);
      setContractWarnings(null);
      setResultRevision(submittedRevisionRef.current);
      workerRef.current?.terminate();
      workerRef.current = undefined;
    };
    failWorkerRef.current = failWorker;
    const restartWorker = () => {
      workerRef.current?.terminate();
      workerRef.current = undefined;
      try {
        const worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
        workerRef.current = worker;
        worker.onmessage = receiveResult;
        worker.onerror = (event) => {
          event.preventDefault();
          if (workerRef.current === worker) failWorker('The playground worker failed. Run again to retry.');
        };
        worker.onmessageerror = () => {
          if (workerRef.current === worker) failWorker('The playground could not read the worker result. Run again to retry.');
        };
      } catch {
        failWorker('The playground worker could not start. Run again to retry.');
      }
    };
    restartWorkerRef.current = restartWorker;
    restartWorker();

    return () => {
      if (programContainerRef.current && programWheelGuardRef.current) {
        programContainerRef.current.removeEventListener('wheel', programWheelGuardRef.current, { capture: true } as EventListenerOptions);
      }
      if (inputContainerRef.current && inputWheelGuardRef.current) {
        inputContainerRef.current.removeEventListener('wheel', inputWheelGuardRef.current, { capture: true } as EventListenerOptions);
      }
      workerRef.current?.terminate();
      programEditorRef.current?.dispose();
      inputEditorRef.current?.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  React.useEffect(() => {
    loadExample(draftsRef.current.get(selectedExampleId));
  }, [loadExample, selectedExampleId]);

  React.useEffect(() => {
    const model = inputEditorRef.current?.getModel();
    if (!model) {
      return;
    }
    const language = inputFormat === 'xml' ? 'xml' : 'json';
    monaco.editor.setModelLanguage(model, language);
  }, [inputFormat]);

  React.useEffect(() => {
    markEdited();
  }, [inputFormat, outputFormat, isTracingEnabled, isContractsEnabled, contractsMode, contractsDebug, markEdited]);

  React.useEffect(() => {
    if (!isContractsEnabled) {
      setInputContract(null);
      setOutputContract(null);
      setContractSource(null);
      setContractWarnings(null);
      setContractsDebug(false);
    }
  }, [isContractsEnabled]);

  React.useEffect(() => {
    if (contractsMode === 'off') {
      setContractWarnings(null);
    }
  }, [contractsMode]);

  const resultsAreStale = resultRevision !== null && resultRevision !== revision;
  const hasTrace = Boolean(traceHuman || traceJson);
  const inspectState = playgroundState.inspect;
  const inspectDiagnostics = diagnosticsForInspect(inspectState.result);
  const inspectBlockers = subsetBlockersForInspect(inspectState.result);
  const inspectCompatibility = compatibilityLabel(inspectState.result);

  const programWheelGuardRef = React.useRef<(event: WheelEvent) => void>();
  const inputWheelGuardRef = React.useRef<(event: WheelEvent) => void>();

  React.useEffect(() => {
    const programContainer = programContainerRef.current;
    const inputContainer = inputContainerRef.current;
    const programEditor = programEditorRef.current;
    const inputEditor = inputEditorRef.current;

    if (programContainer && programEditor && !programWheelGuardRef.current) {
      const handler = (event: WheelEvent) => {
        if (!programEditor.hasTextFocus()) {
          // Let page scroll instead of editor when not focused.
          event.stopImmediatePropagation();
        }
      };
      programContainer.addEventListener('wheel', handler, { capture: true });
      programWheelGuardRef.current = handler;
    }

    if (inputContainer && inputEditor && !inputWheelGuardRef.current) {
      const handler = (event: WheelEvent) => {
        if (!inputEditor.hasTextFocus()) {
          event.stopImmediatePropagation();
        }
      };
      inputContainer.addEventListener('wheel', handler, { capture: true });
      inputWheelGuardRef.current = handler;
    }

    return () => {
      if (programContainer && programWheelGuardRef.current) {
        programContainer.removeEventListener('wheel', programWheelGuardRef.current, { capture: true } as EventListenerOptions);
        programWheelGuardRef.current = undefined;
      }
      if (inputContainer && inputWheelGuardRef.current) {
        inputContainer.removeEventListener('wheel', inputWheelGuardRef.current, { capture: true } as EventListenerOptions);
        inputWheelGuardRef.current = undefined;
      }
    };
  }, []);

  return (
    <div className="branchline-playground">
      <header className="playground-header">
        <div className="playground-title">
          <h2>Branchline Playground</h2>
          <p>
            Pick an example, edit the program and input, then Run.
          </p>
        </div>
        <div className="playground-controls">
          <label className="playground-select">
            <span>Example</span>
            <select
              data-playground-example-select
              value={selectedExampleId}
              onChange={(event) => selectExample(event.target.value)}
            >
              {!selectedExampleInFiltered && selectedExample ? (
                <option value={selectedExampleId}>{selectedExample.title} (current; outside filters)</option>
              ) : null}
              {filteredExamples.map((example) => (
                <option key={example.id} value={example.id}>
                  {example.title}
                </option>
              ))}
            </select>
          </label>
          <button className="playground-button" onClick={run} disabled={isRunning}>
            {isRunning ? 'Running…' : 'Run ▶'}
          </button>
          {isRunning ? <button className="playground-button playground-button--ghost" onClick={stopRun}>Stop</button> : null}
          <button className="playground-button playground-button--ghost" onClick={resetExample}>
            Reset example
          </button>
          <a className="playground-button playground-button--ghost" href={openInNewTabUrl} target="_blank" rel="noreferrer" title="Opens the original example; edits are kept only in this tab.">
            Open original example ↗
          </a>
        </div>
        <p className="playground-session-notice">Drafts stay in this tab until reload.</p>
        <details className="playground-advanced">
          <summary>Filters &amp; run settings</summary>
          <div className="playground-controls playground-controls--advanced">
            <label className="playground-select playground-select--compact">
              <span>Category</span>
              <select
                value={catalogCategory}
                onChange={(event) => setCatalogCategory(event.target.value as CatalogCategoryFilter)}
              >
                {categoryOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="playground-select playground-select--compact">
              <span>AI subset</span>
              <select
                value={catalogAiSubset}
                onChange={(event) => setCatalogAiSubset(event.target.value as CatalogAiSubsetFilter)}
              >
                {CATALOG_AI_SUBSET_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <span className="playground-catalog-count" aria-live="polite">
              {filteredExamples.length} of {examples.length} examples
            </span>
            <label className="playground-select">
              <span>Input format</span>
              <select value={inputFormat} onChange={(event) => setInputFormat(event.target.value as InputFormat)}>
                <option value="json">JSON</option>
                <option value="xml">XML</option>
              </select>
            </label>
            <label className="playground-select">
              <span>Output format</span>
              <select value={outputFormat} onChange={(event) => setOutputFormat(event.target.value as OutputFormat)}>
                <option value="json">JSON (pretty)</option>
                <option value="json-compact">JSON (compact)</option>
                <option value="json-canonical">JSON (canonical)</option>
                <option value="xml">XML</option>
                <option value="xml-compact">XML (compact)</option>
              </select>
            </label>
            <label className="playground-toggle">
              <input
                type="checkbox"
                checked={isTracingEnabled}
                onChange={(event) => setIsTracingEnabled(event.target.checked)}
              />
              <span>Enable tracing (EXPLAIN)</span>
            </label>
            <label className="playground-toggle">
              <input
                type="checkbox"
                checked={isContractsEnabled}
                onChange={(event) => setIsContractsEnabled(event.target.checked)}
              />
              <span>Show input/output contracts</span>
            </label>
            <label className="playground-select">
              <span>Contract checks</span>
              <select value={contractsMode} onChange={(event) => setContractsMode(event.target.value as ContractMode)}>
                <option value="off">Off</option>
                <option value="warn">Warn</option>
                <option value="strict">Strict</option>
              </select>
            </label>
            {isContractsEnabled ? (
              <label className="playground-toggle">
                <input
                  type="checkbox"
                  checked={contractsDebug}
                  onChange={(event) => setContractsDebug(event.target.checked)}
                />
                <span>Contract debug (include spans)</span>
              </label>
            ) : null}
          </div>
          <p className="playground-session-hint">Filters narrow the list without replacing your current draft. Clear filters to browse every example.</p>
        </details>
      </header>

      {visibleSelectedExample ? (
        <details className="example-description">
          <summary>Example details</summary>
          {visibleSelectedExample.description ? <p>{visibleSelectedExample.description}</p> : null}
            <div className="example-metadata" aria-label="Selected example metadata">
              <span className="example-metadata__badge example-metadata__badge--category">
                {catalogCategoryLabel(catalogCategoryValue(visibleSelectedExample))}
              </span>
              <span className={`example-metadata__badge example-metadata__badge--ai-${visibleSelectedExample.aiSubset}`}>
                {catalogAiSubsetLabel(visibleSelectedExample.aiSubset)}
              </span>
              {selectedTags.map((tag) => (
                <span key={tag} className="example-metadata__badge example-metadata__badge--tag">
                  {tag}
                </span>
              ))}
              {hiddenTagCount > 0 ? (
                <span className="example-metadata__badge example-metadata__badge--tag">
                  +{hiddenTagCount}
                </span>
              ) : null}
            </div>
        </details>
      ) : null}

      <div className="playground-note">
        Read the incoming object with <code>input</code>. ⌘/Ctrl + Enter runs from either editor.
      </div>

      <main className="playground-main">
        <section className="panel panel--editor playground-main__program">
          <header className="panel-header">
            <div>
              <h3>Branchline Program</h3>
              <p>A transform body or full TRANSFORM program.</p>
            </div>
          </header>
          <div ref={programContainerRef} className="editor-surface" />
        </section>

        <section className="panel panel--editor playground-main__input">
          <header className="panel-header">
            <div>
              <h3>Input {inputFormat === 'xml' ? 'XML' : 'JSON'}</h3>
              <p>
                {inputFormat === 'xml' ? (
                  <>
                    XML is mapped to an object under its root element, available as <code>input</code>.
                  </>
                ) : (
                  <>
                    Provide a JSON object, available as <code>input</code>.
                  </>
                )}
              </p>
            </div>
          </header>
          <div ref={inputContainerRef} className="editor-surface" />
        </section>

        <section className="panel playground-main__output" aria-busy={isRunning}>
          <header className="panel-header">
            <div>
              <h3>Output &amp; Trace</h3>
              <p>The result of executing your Branchline program.</p>
            </div>
          </header>
          <div className={`playground-run-status${resultsAreStale ? ' playground-run-status--stale' : ''}`} role="status" aria-live="polite">
            {isRunning ? 'Running submitted program…' : lifecycleMessage ?? (resultsAreStale
              ? 'Results are out of date. Run again to update output and Inspect.'
              : resultRevision === null ? 'Ready to run.' : error ? 'Run failed.' : 'Run complete.')}
          </div>
          {error ? (
            <div className="panel-error" role="alert">{resultsAreStale ? 'Previous run: ' : ''}{error}</div>
          ) : (
            <>
              {contractWarnings ? (
                <div className="panel-warning">
                  <div className="panel-subheader">Contract warnings</div>
                  <pre className="panel-output panel-output--warning">{contractWarnings}</pre>
                </div>
              ) : null}
              <div className={`results-grid${hasTrace ? ' results-grid--with-trace' : ''}`}>
                <div className="results-pane">
                  <div className="panel-subheader">Program output</div>
                  <pre className="panel-output">
                    {output || 'Run the playground to view output.'}
                  </pre>
                </div>
                {hasTrace ? (
                  <div className="results-pane results-pane--trace">
                    <div className="panel-subheader">Trace explanations</div>
                    {traceHuman ? (
                      <pre className="panel-output panel-output--trace">{traceHuman}</pre>
                    ) : null}
                    {traceJson ? (
                      <details className="panel-trace-structured">
                        <summary>View structured provenance JSON</summary>
                        <pre className="panel-output panel-output--trace">{traceJson}</pre>
                      </details>
                    ) : null}
                  </div>
                ) : null}
              </div>
              {isContractsEnabled ? (
                <div className="contracts-panel">
                  <div className="contracts-header">
                    <div className="panel-subheader">Input/output contracts</div>
                    <span className="contracts-source">
                      {contractSource ? `Source: ${contractSource}` : 'Run the playground to infer contracts.'}
                    </span>
                  </div>
                  <div className="contracts-grid">
                    <div className="contracts-pane">
                      <div className="panel-subheader">Input contract</div>
                      <pre className="panel-output panel-output--contract">
                        {inputContract ?? 'Run the playground to view the inferred input contract.'}
                      </pre>
                    </div>
                    <div className="contracts-pane">
                      <div className="panel-subheader">Output contract</div>
                      <pre className="panel-output panel-output--contract">
                        {outputContract ?? 'Run the playground to view the inferred output contract.'}
                      </pre>
                    </div>
                  </div>
                </div>
              ) : null}
            </>
          )}
        </section>

        <section className="panel playground-main__inspect inspect-panel">
          <header className="panel-header inspect-panel__header">
            <div>
              <h3>Inspect</h3>
              <p>Normalized source, diagnostics, and AI subset compatibility.</p>
            </div>
            <span
              className={`inspect-compatibility inspect-compatibility--${compatibilityClass(resultsAreStale ? null : inspectState.result)}`}
            >
              {resultsAreStale ? 'Out of date' : inspectCompatibility}
            </span>
          </header>
          {resultsAreStale ? <p className="playground-run-status playground-run-status--stale">Inspect describes the previous run. Run again after your edits.</p> : null}
          <div className="inspect-tabs" role="tablist" aria-label="Inspect panes">
            {INSPECT_TABS.map((tab, index) => (
              <button
                key={tab.id}
                type="button"
                className={`inspect-tab${activeInspectTab === tab.id ? ' inspect-tab--active' : ''}`}
                role="tab"
                id={`${instanceId}-tab-${tab.id}`}
                aria-controls={`${instanceId}-inspect-panel`}
                tabIndex={activeInspectTab === tab.id ? 0 : -1}
                ref={(element) => { inspectTabRefs.current[index] = element; }}
                onKeyDown={(event) => {
                  const next = event.key === 'ArrowRight' ? (index + 1) % INSPECT_TABS.length
                    : event.key === 'ArrowLeft' ? (index + INSPECT_TABS.length - 1) % INSPECT_TABS.length
                    : event.key === 'Home' ? 0 : event.key === 'End' ? INSPECT_TABS.length - 1 : null;
                  if (next === null) return;
                  event.preventDefault();
                  setActiveInspectTab(INSPECT_TABS[next].id);
                  inspectTabRefs.current[next]?.focus();
                }}
                aria-selected={activeInspectTab === tab.id}
                onClick={() => setActiveInspectTab(tab.id)}
              >
                {tab.label}
                {tab.id === 'diagnostics' && inspectDiagnostics.length > 0 ? (
                  <span className="inspect-tab__count">{inspectDiagnostics.length}</span>
                ) : null}
                {tab.id === 'blockers' && inspectBlockers.length > 0 ? (
                  <span className="inspect-tab__count">{inspectBlockers.length}</span>
                ) : null}
              </button>
            ))}
          </div>
          <div className="inspect-content" role="tabpanel" id={`${instanceId}-inspect-panel`}
            aria-labelledby={`${instanceId}-tab-${activeInspectTab}`} tabIndex={0}>
            {inspectState.isLoading ? (
              <div className="inspect-state">Inspecting submitted program…</div>
            ) : inspectState.error ? (
              <div className="inspect-state inspect-state--error" role="alert">{inspectState.error}</div>
            ) : activeInspectTab === 'normalized' ? (
              inspectState.result?.normalizedSource ? (
                <pre className="inspect-code">{inspectState.result.normalizedSource}</pre>
              ) : (
                <div className="inspect-state">
                  {inspectState.result
                    ? 'No normalized source is available for this program.'
                    : 'Run the playground to view normalized source.'}
                </div>
              )
            ) : activeInspectTab === 'diagnostics' ? (
              inspectDiagnostics.length > 0 ? (
                <div className="diagnostic-list">
                  {inspectDiagnostics.map((diagnostic, index) => {
                    const span = formatSpan(diagnostic);
                    const payload = formatDiagnosticPayload(diagnostic);
                    return (
                      <div
                        key={`${diagnostic.code}-${index}`}
                        className={`diagnostic-item diagnostic-item--${diagnostic.severity.toLowerCase()}`}
                      >
                        <div className="diagnostic-item__meta">
                          <span>{diagnostic.severity}</span>
                          <span>{diagnostic.category}</span>
                          <span>{diagnostic.code}</span>
                          {span ? <span>{span}</span> : null}
                        </div>
                        <div className="diagnostic-item__message">{diagnostic.message}</div>
                        {payload ? (
                          <div className="diagnostic-item__payload">{payload}</div>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="inspect-state">
                  {inspectState.result ? 'No diagnostics reported.' : 'Run the playground to view diagnostics.'}
                </div>
              )
            ) : inspectBlockers.length > 0 ? (
              <div className="diagnostic-list">
                {inspectBlockers.map((diagnostic, index) => {
                  const span = formatSpan(diagnostic);
                  return (
                    <div key={`${diagnostic.code}-${index}`} className="diagnostic-item diagnostic-item--blocker">
                      <div className="diagnostic-item__meta">
                        <span>{diagnostic.payload?.actualKind ?? diagnostic.code}</span>
                        {span ? <span>{span}</span> : null}
                      </div>
                      <div className="diagnostic-item__message">{diagnostic.message}</div>
                      {diagnostic.payload?.hint ? (
                        <div className="diagnostic-item__payload">{diagnostic.payload.hint}</div>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="inspect-state">
                {inspectState.result
                  ? 'No AI subset blockers reported.'
                  : 'Run the playground to view subset blockers.'}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
