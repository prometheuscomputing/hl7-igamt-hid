import { ISnippetPart, ISnippetRenderResult } from '../../example-messages/services/example-messages.service';
import { IExampleMessageHighlightRange } from '../froala/example-message.plugin';

/** Where the snippet's own elements are in the text it is rendered as. */
export function snippetHighlights(result: ISnippetRenderResult, useFullMessage: boolean): IExampleMessageHighlightRange[] {
  const parts = (result && result.parts) || [];
  const ranges = parts.map((part) => toHighlightRange(part, useFullMessage)).filter((range) => !!range);
  if (ranges.length) {
    return ranges;
  }
  // The snippet could not be found in the message as it is now: nothing is marked, since yellow means "this is the snippet".
  if (result && result.warnings && result.warnings.length) {
    return [];
  }
  const body = useFullMessage ? result.fullMessage : (result.renderedContent || result.fullMessage || '');
  const lines = String(body || '').split(/\r\n|\n|\r/);
  if (!lines.length) {
    return [];
  }
  return [{
    startLine: 0,
    startCol: 0,
    endLine: lines.length - 1,
    endCol: (lines[lines.length - 1] || '').length,
  }];
}

function toHighlightRange(part: ISnippetPart, useFullMessage: boolean): IExampleMessageHighlightRange {
  if (!part || !part.highlightStart || !part.highlightEnd) {
    return null;
  }
  const startLine = useFullMessage
    ? (part.startLine || part.highlightStart.line) - 1
    : part.highlightStart.line - 1;
  const endLine = useFullMessage
    ? (part.endLine || part.highlightEnd.line) - 1
    : part.highlightEnd.line - 1;
  return {
    startLine,
    startCol: part.highlightStart.column - 1,
    endLine,
    endCol: part.highlightEnd.column - 1,
  };
}
