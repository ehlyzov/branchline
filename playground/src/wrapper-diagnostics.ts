import type { PlaygroundDiagnostic, PlaygroundInspectPayload } from './playground-state';

export type WrapperAdjustment = {
  lineOffset: number;
  firstContent: number;
  lastContent: number;
  firstColumnOffset: number;
  originalLines: string[];
};

export function computeWrapperAdjustment(
  code: string,
  sharedOffset: number,
  isBodyOnly: boolean
): WrapperAdjustment | null {
  if (!isBodyOnly) return null;
  const originalLines = code.split(/\r\n|\r|\n/);
  let firstContent = 0;
  while (firstContent < originalLines.length && originalLines[firstContent].trim() === '') firstContent += 1;
  let lastContent = originalLines.length - 1;
  while (lastContent >= firstContent && originalLines[lastContent].trim() === '') lastContent -= 1;
  const firstLine = originalLines[firstContent] ?? '';
  return {
    // PlaygroundFacade adds one TRANSFORM header. trimIndent removes its four
    // inserted body spaces, while body.trim() removes the first line's margin.
    lineOffset: 1 + sharedOffset,
    firstContent,
    lastContent,
    firstColumnOffset: firstLine.length - firstLine.trimStart().length,
    originalLines
  };
}

function adjustPosition(
  rawLine: number,
  rawColumn: number,
  adjustment: WrapperAdjustment
): { line: number; column: number } {
  const sourceIndex = rawLine - adjustment.lineOffset - 1 + adjustment.firstContent;
  if (sourceIndex < adjustment.firstContent) return { line: rawLine, column: rawColumn };
  if (sourceIndex <= adjustment.lastContent) {
    return {
      line: sourceIndex + 1,
      column: rawColumn + (sourceIndex === adjustment.firstContent ? adjustment.firstColumnOffset : 0)
    };
  }
  const lastLine = Math.max(0, adjustment.lastContent);
  return { line: lastLine + 1, column: adjustment.originalLines[lastLine].length + 1 };
}

function unwrapMessage(message: string | null): string | null {
  if (message == null) return null;
  // ParseException embeds the generated wrapper and its coordinates. The UI
  // already reports the corrected source position, so retain only its summary.
  const firstLine = message.split(/\r?\n/, 1)[0];
  return /^\[\d+:\d+\]\s/.test(firstLine)
    ? firstLine.replace(/^\[\d+:\d+\]\s*/, '')
    : message;
}

type LocatedResult = {
  success: boolean;
  line: number | null;
  column: number | null;
  errorMessage: string | null;
};

export function adjustResultForWrapper<T extends LocatedResult>(
  result: T,
  adjustment: WrapperAdjustment | null
): T {
  if (!adjustment || result.success || result.line == null || result.column == null) return result;
  return {
    ...result,
    ...adjustPosition(result.line, result.column, adjustment),
    errorMessage: unwrapMessage(result.errorMessage)
  };
}

function adjustDiagnostic(diagnostic: PlaygroundDiagnostic, adjustment: WrapperAdjustment): PlaygroundDiagnostic {
  if (diagnostic.span == null) return diagnostic;
  const start = adjustPosition(diagnostic.span.startLine, diagnostic.span.startColumn, adjustment);
  const end = adjustPosition(diagnostic.span.endLine, diagnostic.span.endColumn, adjustment);
  return {
    ...diagnostic,
    message: unwrapMessage(diagnostic.message) ?? diagnostic.message,
    span: { startLine: start.line, startColumn: start.column, endLine: end.line, endColumn: end.column }
  };
}

export function adjustInspectForWrapper(
  result: PlaygroundInspectPayload,
  adjustment: WrapperAdjustment | null
): PlaygroundInspectPayload {
  if (!adjustment) return result;
  return {
    ...result,
    diagnostics: result.diagnostics.map((diagnostic) => adjustDiagnostic(diagnostic, adjustment)),
    warnings: result.warnings.map((diagnostic) => adjustDiagnostic(diagnostic, adjustment))
  };
}
