import { Component, OnInit, ViewChild } from '@angular/core';
import { Actions } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { EMPTY, Observable, of } from 'rxjs';
import { catchError, distinctUntilChanged, flatMap, map, mergeMap, take } from 'rxjs/operators';
import * as fromDam from 'src/app/modules/dam-framework/store/index';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { IDisplayElement } from '../../../shared/models/display-element.interface';
import { EditorID } from '../../../shared/models/editor.enum';
import { Type } from '../../../shared/constants/type.enum';
import { withUpdatedSnippetName } from '../../domain/example-messages.model';
import { DamAbstractEditorComponent } from 'src/app/modules/dam-framework/services/dam-editor.component';
import { FroalaService } from 'src/app/modules/shared/services/froala.service';
import { MessageService } from 'src/app/modules/dam-framework/services/message.service';
import { ExampleMessagesService, ISnippetPart, ISnippetRenderResult } from '../../services/example-messages.service';
import { CodemirrorComponent } from '@ctrl/ngx-codemirror';
import * as CodeMirror from 'codemirror';

@Component({
  selector: 'app-snippet-editor',
  templateUrl: './snippet-editor.component.html',
  styleUrls: ['./snippet-editor.component.scss'],
})
export class SnippetEditorComponent extends DamAbstractEditorComponent implements OnInit {

  renderResult: ISnippetRenderResult = null;
  igId: string = null;
  messageId: string = null;
  snippetId: string = null;
  viewMode: 'reduced' | 'full' = 'reduced';
  displayMessage: string = null;
  snippetName: string = '';
  snippetNarrative: string = '';
  snippetDescription: string = '';
  messageName: string = '';
  parts: ISnippetPart[] = [];
  selectionMarkers: CodeMirror.TextMarker[] = [];
  activeTab: number = 0;
  froalaConfig$: Observable<any>;
  snippetNotFound = false;
  snippetWarnings: string[] = [];

  @ViewChild('codemirror') private codeEditor: CodemirrorComponent;

  messageEditorOptions = {
    mode: 'hl7v2',
    lineNumbers: true,
    foldGutter: true,
    readOnly: true,
    cursorBlinkRate: -1,
    styleActiveLine: false,
    gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
    matchBrackets: true,
    lineWrapping: false,
  };

  constructor(
    actions$: Actions,
    store: Store<any>,
    private froalaService: FroalaService,
    private exampleMessagesService: ExampleMessagesService,
    private messageService: MessageService,
  ) {
    super({
      id: EditorID.EXAMPLE_SNIPPET,
      title: 'Snippet',
    }, actions$, store);
    this.froalaConfig$ = this.froalaService.getConfig();
  }

  ngOnInit() {
    this.currentSynchronized$.pipe(
      map((data) => {
        if (data) {
          if (this.snippetId && data.snippetId && this.snippetId !== data.snippetId) {
            this.viewMode = 'reduced';
          }
          this.renderResult = data as ISnippetRenderResult;
          this.igId = (data as any).igId;
          this.messageId = data.messageId || null;
          this.snippetId = data.snippetId || null;
          this.snippetName = data.snippetName || '';
          this.snippetDescription = (data as any).snippetDescription || '';
          this.messageName = data.messageName || '';
          this.parts = data.parts || [];
          this.snippetNotFound = !!(data as any).snippetNotFound;
          this.snippetWarnings = (data as any).warnings || [];
          this.displayMessage = this.viewMode === 'full' ? data.fullMessage : data.renderedContent;
          setTimeout(() => this.applyHighlights(), 300);
        }
      }),
    ).subscribe();
  }

  setViewMode(mode: 'reduced' | 'full') {
    this.viewMode = mode;
    if (this.renderResult) {
      this.displayMessage = mode === 'full'
        ? this.renderResult.fullMessage
        : this.renderResult.renderedContent;
      setTimeout(() => this.applyHighlights(), 300);
    }
  }

  applyHighlights() {
    this.clearMarkers();
    if (!this.codeEditor || !this.codeEditor.codeMirror || !this.parts || this.parts.length === 0) {
      return;
    }
    const editor = this.codeEditor.codeMirror;
    const doc = editor.getDoc();

    for (const part of this.parts) {
      if (!part.highlightStart || !part.highlightEnd) {
        continue;
      }
      let startLine: number;
      let startCol: number;
      let endLine: number;
      let endCol: number;

      if (this.viewMode === 'full') {
        // In full mode, use original line numbers from the message
        startLine = part.startLine - 1;
        startCol = part.highlightStart.column - 1;
        endLine = part.endLine - 1;
        endCol = part.highlightEnd.column - 1;
      } else {
        // In reduced mode, use the adjusted highlight coordinates
        startLine = part.highlightStart.line - 1;
        startCol = part.highlightStart.column - 1;
        endLine = part.highlightEnd.line - 1;
        endCol = part.highlightEnd.column - 1;
      }

      const from = CodeMirror.Pos(startLine, startCol);
      const to = CodeMirror.Pos(endLine, endCol);
      const marker = doc.markText(from, to, { className: 'cm-highlight' });
      this.selectionMarkers.push(marker);
    }

    // Scroll to first highlight
    if (this.parts.length > 0 && this.parts[0].highlightStart) {
      const firstLine = this.viewMode === 'full'
        ? this.parts[0].startLine - 1
        : this.parts[0].highlightStart.line - 1;
      editor.scrollIntoView(CodeMirror.Pos(firstLine, 0), 50);
    }
  }

  clearMarkers() {
    for (const marker of this.selectionMarkers) {
      marker.clear();
    }
    this.selectionMarkers = [];
  }

  editorDisplayNode(): Observable<IDisplayElement> {
    return this.current$.pipe(
      map((current) => this.toDisplay((current && current.data && current.data.snippetName) || this.snippetName)),
      distinctUntilChanged((a, b) => a.id === b.id && a.variableName === b.variableName),
    );
  }

  private toDisplay(name: string): IDisplayElement {
    return {
      id: this.snippetId,
      variableName: name || '',
      type: Type.EXAMPLEMESSAGESNIPPET,
    } as IDisplayElement;
  }

  onNameChange(name: string) {
    this.snippetName = name;
    this.current$.pipe(
      take(1),
      map((current) => {
        this.editorChange({
          ...current.data,
          snippetName: name,
        }, true);
      }),
    ).subscribe();
  }

  onDescriptionChange(description: string) {
    this.snippetDescription = description;
    this.current$.pipe(
      take(1),
      map((current) => {
        this.editorChange({
          ...current.data,
          snippetDescription: description,
        }, true);
      }),
    ).subscribe();
  }

  onEditorSave(action: fromDam.EditorSave): Observable<Action> {
    return this.current$.pipe(
      take(1),
      flatMap((current) => {
        if (!this.igId || !this.messageId || !this.snippetId) {
          return EMPTY;
        }
        return this.exampleMessagesService.saveSnippet(this.igId, this.messageId, this.snippetId, {
          name: current.data.snippetName || this.snippetName,
          description: current.data.snippetDescription || this.snippetDescription,
        }).pipe(
          flatMap((response) => this.store.select(selectIgExampleMessages).pipe(
            take(1),
            mergeMap((ig) => {
              const name = current.data.snippetName || this.snippetName;
              return [
                this.messageService.messageToAction(response),
                new fromDam.EditorUpdate({ value: current.data, updateDate: false }),
                new fromDam.SetValue({ selected: current.data }),
                new fromDam.LoadPayloadData(withUpdatedSnippetName(ig, this.messageId, this.snippetId, name)),
              ];
            }),
          )),
          catchError((error) => {
            return of(this.messageService.actionFromError(error));
          }),
        );
      }),
    );
  }

  onDeactivate(): void {
  }
}

