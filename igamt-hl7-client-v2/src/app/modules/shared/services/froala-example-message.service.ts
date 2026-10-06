import { Injectable, NgZone } from '@angular/core';
import { MatDialog } from '@angular/material';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, take } from 'rxjs/operators';
import { IAvailableMessage, IgExampleMessages } from '../../example-messages/domain/example-messages.model';
import { ExampleMessagesService } from '../../example-messages/services/example-messages.service';
import {
  ISelectMessageSnippetDialogResult,
  SelectMessageSnippetDialogComponent,
} from '../../ig/components/select-message-snippet-dialog/select-message-snippet-dialog.component';
import { ISnippetPart, ISnippetRenderResult } from '../../example-messages/services/example-messages.service';
import {
  buildExampleMessageWidgetHtml,
  ensureEditableAroundExampleMessages,
  findExampleMessageWidgets,
  IExampleMessageHighlightRange,
  setExampleMessageWidgetBody,
  widgetViewMode,
} from '../froala/example-message.plugin';

@Injectable({
  providedIn: 'root',
})
export class FroalaExampleMessageService {

  constructor(
    private dialog: MatDialog,
    private exampleMessagesService: ExampleMessagesService,
    private zone: NgZone,
  ) { }

  pickAndInsert(editor: any, igId: string) {
    if (!editor || !igId) {
      return;
    }
    this.zone.run(() => {
      if (editor.selection && editor.selection.save) {
        editor.selection.save();
      }
      this.exampleMessagesService.getIgExampleMessages(igId).pipe(
        take(1),
        map((data) => this.toAvailableMessages(data)),
        catchError(() => of([] as IAvailableMessage[])),
        switchMap((availableMessages) => {
          return this.dialog.open(SelectMessageSnippetDialogComponent, {
            data: {
              availableMessages,
              confirmLabel: 'Insert',
            },
          }).afterClosed();
        }),
        take(1),
      ).subscribe((result: ISelectMessageSnippetDialogResult) => {
        if (editor.selection && editor.selection.restore) {
          editor.selection.restore();
        }
        if (!result || !result.messageId) {
          return;
        }
        const html = buildExampleMessageWidgetHtml(
          igId,
          result.messageId,
          result.snippetId,
          result.label,
        );
        if (editor.exampleMessage && editor.exampleMessage.insertWidget) {
          editor.exampleMessage.insertWidget(html);
        } else {
          editor.html.insert(html, true);
          this.renderEditor(editor, igId, true);
        }
      });
    });
  }

  renderEditor(editor: any, igId: string, persist?: boolean) {
    if (!editor || !editor.$el || !igId) {
      return;
    }
    this.zone.run(() => {
      const widgets = findExampleMessageWidgets(editor.$el) as HTMLElement[];
      const jobs = widgets.map((widget) => this.renderWidget(widget, igId));
      const done$ = jobs.length ? forkJoin(jobs) : of(null);
      done$.pipe(take(1)).subscribe(() => {
        ensureEditableAroundExampleMessages(editor.$el);
        if (persist && editor.undo && editor.undo.saveStep) {
          editor.undo.saveStep();
        }
      });
    });
  }

  toAvailableMessages(igExampleMessages: IgExampleMessages): IAvailableMessage[] {
    const available: IAvailableMessage[] = [];
    if (!igExampleMessages || !igExampleMessages.profileExampleMessages) {
      return available;
    }
    for (const profileMessages of igExampleMessages.profileExampleMessages) {
      const profileLabel = profileMessages.profile
        ? (profileMessages.profile.fixedName || '') +
          (profileMessages.profile.variableName ? '#' + profileMessages.profile.variableName : '')
        : 'Unknown Profile';
      for (const msg of profileMessages.exampleMessages) {
        available.push({
          id: msg.id,
          name: msg.name,
          profileName: profileLabel,
          snippets: msg.snippets || [],
        });
      }
    }
    return available;
  }

  private renderWidget(widget: HTMLElement, igId: string): Observable<boolean> {
    const messageId = widget.getAttribute('data-message-id');
    const snippetId = widget.getAttribute('data-snippet-id');
    if (!messageId) {
      return of(false);
    }
    const apply = (body: string, missing?: boolean, highlights?: IExampleMessageHighlightRange[]) => {
      setExampleMessageWidgetBody(widget, body, missing, highlights);
      return true;
    };
    if (snippetId) {
      return this.exampleMessagesService.renderSnippet(igId, messageId, snippetId).pipe(
        take(1),
        map((result) => {
          if (!result) {
            return apply('Example message could not be loaded.', true);
          }
          if (result.snippetNotFound) {
            return apply(result.fullMessage || 'The attached snippet could not be found.', true);
          }
          const useFullMessage = widgetViewMode(widget) === 'full' || (!result.renderedContent && !!result.fullMessage);
          const body = useFullMessage
            ? (result.fullMessage || result.renderedContent || '')
            : (result.renderedContent || result.fullMessage || '');
          return apply(body, false, snippetHighlights(result, useFullMessage));
        }),
        catchError(() => of(apply('Example message could not be loaded.', true))),
      );
    }
    return this.exampleMessagesService.getExampleMessage(igId, messageId).pipe(
      take(1),
      map((msg) => {
        if (!msg) {
          return apply('The attached example message could not be found.', true);
        }
        return apply(msg.message || '');
      }),
      catchError(() => of(apply('The attached example message could not be found.', true))),
    );
  }
}

function snippetHighlights(result: ISnippetRenderResult, useFullMessage: boolean): IExampleMessageHighlightRange[] {
  const parts = (result && result.parts) || [];
  const ranges = parts.map((part) => toHighlightRange(part, useFullMessage)).filter((range) => !!range);
  if (ranges.length) {
    return ranges;
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
