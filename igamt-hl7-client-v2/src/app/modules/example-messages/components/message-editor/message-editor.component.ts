import { Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatDialog } from '@angular/material';
import { ActivatedRoute, Router } from '@angular/router';
import { saveAs } from 'file-saver';
import { Actions } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import * as _ from 'lodash';
import { combineLatest, EMPTY, from, Observable, of, throwError } from 'rxjs';
import * as fromDam from 'src/app/modules/dam-framework/store/index';
import { ExpandSideBar } from 'src/app/modules/dam-framework/store/data/dam.actions';
import { Notify } from 'src/app/modules/dam-framework/store/messages/messages.actions';
import { MessageType, UserMessage } from 'src/app/modules/dam-framework/models/messages/message.class';
import { IDisplayElement } from '../../../shared/models/display-element.interface';
import { EditorID } from '../../../shared/models/editor.enum';
import { DamAbstractEditorComponent } from 'src/app/modules/dam-framework/services/dam-editor.component';
import { FroalaService } from 'src/app/modules/shared/services/froala.service';
import { catchError, concatMap, distinctUntilChanged, finalize, flatMap, map, mergeMap, take } from 'rxjs/operators';
import { ExampleMessagesService } from '../../services/example-messages.service';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { MessageService } from 'src/app/modules/dam-framework/services/message.service';
import { TreeComponent, TreeNode } from 'angular-tree-component';
import { CodemirrorComponent } from '@ctrl/ngx-codemirror';
import * as CodeMirror from 'codemirror';
import { ContextMenuComponent, ContextMenuService } from 'ngx-contextmenu';
import { Type } from '../../../shared/constants/type.enum';
import { exampleNameError, exampleNameValidator, IExampleMessageLocation, IExampleMessageLocationContext, IExampleMessageSnippet, IExampleMessageValidationEntry, IExampleMessageValidationResult, MessageElement, siblingMessageNames, withUpdatedMessageName } from '../../domain/example-messages.model';
import { CreateDialogComponent } from '../create-dialog/create-dialog.component';
import { LocateContextDialogComponent } from '../locate-context-dialog/locate-context-dialog.component';
import { VALIDATION_REPORT_CSS, VALIDATION_REPORT_SCRIPT } from './validation-report.css';

@Component({
  selector: 'app-message-editor',
  templateUrl: './message-editor.component.html',
  styleUrls: ['./message-editor.component.scss'],
})
export class MessageEditorComponent extends DamAbstractEditorComponent implements OnInit, OnDestroy {

  message: string;
  narrative: string = '';
  messageId: string;
  igId$: Observable<string>;
  froalaConfig$: Observable<any>;
  viewOnly$: Observable<boolean>;
  viewOnly = false;
  nameControl = new FormControl('');
  parsed: any[];
  loading = false;
  options = {
    allowDrag: (node: TreeNode) => {
      return false;
    },
    actionMapping: {
      mouse: {
        click: () => { },
      },
    },
    animateExpand: false,
    scrollOnActivate: true,
  };
  staleMessageTree = false;
  parseError = '';
  highlighted: CodeMirror.TextMarker | null = null;
  selected: MessageElement | null = null;
  selectedElements: MessageElement[] = [];
  selectionMarkers: CodeMirror.TextMarker[] = [];
  messageHash: string | null = null;
  snippetId: string;
  snippetToHighlightPaths: string[] = [];
  activeTab: number = 0;
  messageName: string = '';
  messageDescription: string = '';
  snippetValidationWarnings: { snippetName: string, brokenPaths: string[] }[] = [];
  validating = false;
  validationResult: IExampleMessageValidationResult = null;
  validationError: string = '';
  validationTabIndex = 0;
  showQuietDetections = false;
  treeFilter = '';
  validationSheetHeight = 300;
  sheetDragging = false;
  activeValidationEntry: IExampleMessageValidationEntry = null;
  private sheetDragStart: { y: number, height: number } = null;
  /** The element under the pointer in the message text, its marker, and the tip that names it. */
  private hovered: MessageElement | null = null;
  private hoverMarker: CodeMirror.TextMarker | null = null;
  private hoverTip: HTMLElement | null = null;
  private hoverFrame: number | null = null;
  private textMenuOpen = false;
  private detachTextListeners: () => void = null;

  @ViewChild('codemirror') private codeEditor!: CodemirrorComponent;
  @ViewChild('treeroot') private parsedTree: TreeComponent;
  @ViewChild('reportHost') private reportHost: ElementRef;
  @ViewChild('contentStage') private contentStage: ElementRef;
  @ViewChild('textContextMenu') private textContextMenu: ContextMenuComponent;

  constructor(
    actions$: Actions,
    store: Store<any>,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    private froalaService: FroalaService,
    private messageService: MessageService,
    private exampleMessagesService: ExampleMessagesService,
    private sanitizer: DomSanitizer,
    private router: Router,
    private zone: NgZone,
    private contextMenuService: ContextMenuService,
  ) {
    super({
      id: EditorID.EXAMPLE_MESSAGE,
      title: '',
    }, actions$, store);
    this.froalaConfig$ = this.froalaService.getConfig();
    this.igId$ = store.select(selectIgExampleMessages).pipe(
      map((igExampleMessages) => {
        return igExampleMessages.id
      })
    );
    this.viewOnly$ = store.select(selectIgExampleMessages).pipe(
      map((igExampleMessages) => !!(igExampleMessages && igExampleMessages.viewOnly)),
    );
    this.viewOnly$.subscribe((viewOnly) => {
      this.viewOnly = viewOnly;
      this.messageEditorOptions = {
        ...this.messageEditorOptions,
        readOnly: viewOnly ? 'nocursor' : false,
      };
    });
    this.nameControl.valueChanges.subscribe((name) => {
      this.messageName = name;
      if (this.nameControl.dirty) {
        this.change();
      }
    });
    this.currentSynchronized$.pipe(
      map(async (current) => {
        const nextId = current && current.id;
        if (nextId !== this.messageId) {
          this.resetEditorState();
        }
        this.message = (current && current.message) || '';
        this.narrative = (current && current.narrativeHTML) || '';
        this.messageId = nextId;
        this.messageName = (current && current.name) || '';
        this.messageDescription = (current && current.description) || '';
        this.bindName(this.messageName);
        this.resolveSnippetPath();
        if (this.message) {
          await this.parseMessage();
        } else {
          this.parsed = [];
          this.parseError = '';
          this.staleMessageTree = false;
          this.messageHash = null;
        }
      })
    ).subscribe();

    this.route.queryParamMap.pipe(
      map((params) => params.get('snippetId'))
    ).subscribe((snippetId) => {
      this.snippetId = snippetId;
      this.resolveSnippetPath();
      this.highlightSnippetIfAvailable();
      // Expand the sidebar when navigating with a snippetId
      if (snippetId) {
        this.store.dispatch(new ExpandSideBar());
      }
    });
  }

  messageEditorOptions: {
    mode: string;
    lineNumbers: boolean;
    foldGutter: boolean;
    styleActiveLine: boolean;
    autoCloseTags: boolean;
    gutters: string[];
    matchBrackets: boolean;
    extraKeys: { 'Alt-F': string };
    lineWrapping: boolean;
    placeholder: string;
    readOnly: boolean | 'nocursor';
  } = {
    mode: 'hl7v2',
    lineNumbers: true,
    foldGutter: true,
    styleActiveLine: true,
    autoCloseTags: true,
    gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter', 'CodeMirror-lint-markers'],
    matchBrackets: true,
    extraKeys: { 'Alt-F': 'findPersistent' },
    lineWrapping: false,
    placeholder: '',
    readOnly: false,
  };

  async getMessageHash() {
    const msgBuffer = new TextEncoder().encode(this.message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  async messageChange() {
    if (this.viewOnly) {
      return;
    }
    await this.updateStaleMessageState();
    if (this.staleMessageTree) {
      this.clearAllSelections();
    }
    this.change();
  }

  change() {
    this.editorChange(
      {
        message: this.message,
        narrativeHTML: this.narrative,
        name: this.messageName,
        description: this.messageDescription,
      },
      this.nameControl.valid,
    );
  }

  nameError(): string {
    return exampleNameError(this.nameControl);
  }

  private bindName(name: string) {
    this.store.select(selectIgExampleMessages).pipe(take(1)).subscribe((ig) => {
      this.nameControl.setValidators(exampleNameValidator(siblingMessageNames(ig, this.messageId)));
      this.nameControl.setValue(name || '', { emitEvent: false });
      this.nameControl.updateValueAndValidity({ emitEvent: false });
    });
  }

  onMessageDescriptionChange(description: string) {
    this.messageDescription = description;
    this.change();
  }

  editorDisplayNode(): Observable<IDisplayElement> {
    return this.current$.pipe(
      map((current) => this.toDisplay((current && current.data && current.data.name) || this.messageName)),
      distinctUntilChanged((a, b) => a.id === b.id && a.variableName === b.variableName),
    );
  }

  private toDisplay(name: string): IDisplayElement {
    return {
      id: this.messageId,
      variableName: name || '',
      type: Type.EXAMPLEMESSAGE,
    } as IDisplayElement;
  }

  onEditorSave(action: fromDam.EditorSave): Observable<Action> {
    return combineLatest(this.igId$, this.current$).pipe(
      take(1),
      concatMap(([id, current]) => {
        return this.exampleMessagesService.saveExampleMessage(id, this.messageId, {
          message: current.data.message,
          narrative: current.data.narrativeHTML,
          name: current.data.name,
          description: current.data.description,
        }).pipe(
          flatMap((response) => {
            // Check for snippet validation warnings in the response data
            this.snippetValidationWarnings = [];
            if (response.data && response.data.snippetValidations) {
              const broken = response.data.snippetValidations.filter((v: any) => v.brokenPaths && v.brokenPaths.length > 0);
              this.snippetValidationWarnings = broken.map((v: any) => ({
                snippetName: v.snippetName || v.snippetId,
                brokenPaths: v.brokenPaths,
              }));
              // Dispatch a warning toast for each broken snippet
              if (broken.length > 0) {
                const names = broken.map((v: any) => v.snippetName || v.snippetId).join(', ');
                const warnMsg = `Warning: ${broken.length} snippet(s) have broken references after message change: ${names}`;
                this.store.dispatch(new Notify(new UserMessage(MessageType.WARNING, warnMsg, null, { closable: true, timeout: 8000 })));
              }
            }
            return from(this.parseMessage()).pipe(
              flatMap(() => this.store.select(selectIgExampleMessages).pipe(take(1))),
              mergeMap((ig) => [
                this.messageService.messageToAction(response),
                new fromDam.EditorUpdate({ value: current.data, updateDate: false }),
                new fromDam.SetValue({ selected: current.data }),
                new fromDam.LoadPayloadData(withUpdatedMessageName(ig, this.messageId, current.data.name)),
              ])
            );
          }),
          catchError((error) => throwError(this.messageService.actionFromError(error))),
        );
      }),
    );
  }

  highlight(element: MessageElement, event?: MouseEvent) {
    if (event && (event.ctrlKey || event.metaKey)) {
      // Multi-select: toggle this element
      this.toggleSelect(element);
    } else {
      // Single select: clear previous and select only this one
      this.clearAllSelections();
      this.selected = element;
      this.selectedElements = [element];
      if (element && element.start && element.end) {
        this.markElement(element, 'cm-highlight');
      }
    }
  }

  toggleSelect(element: MessageElement) {
    const idx = this.selectedElements.findIndex(
      (e) => e.positionalPath === element.positionalPath
    );
    if (idx >= 0) {
      // Deselect
      this.selectedElements.splice(idx, 1);
    } else {
      // Add to selection
      this.selectedElements.push(element);
    }
    // Update the primary selected to the last one
    this.selected = this.selectedElements.length > 0
      ? this.selectedElements[this.selectedElements.length - 1]
      : null;
    // Re-render all highlights
    this.refreshSelectionMarkers();
  }

  isElementSelected(element: MessageElement): boolean {
    return this.selectedElements.some((e) => e.positionalPath === element.positionalPath);
  }

  clearAllSelections() {
    this.selected = null;
    this.selectedElements = [];
    this.clearAllMarkers();
  }

  clearAllMarkers() {
    if (this.highlighted) {
      this.highlighted.clear();
      this.highlighted = null;
    }
    for (const marker of this.selectionMarkers) {
      marker.clear();
    }
    this.selectionMarkers = [];
  }

  refreshSelectionMarkers() {
    this.clearAllMarkers();
    for (const element of this.selectedElements) {
      if (element.start && element.end) {
        this.markElement(element, 'cm-highlight');
      }
    }
  }

  markElement(element: MessageElement, className: string) {
    if (!element.start || !element.end || !this.codeEditor || !this.codeEditor.codeMirror) {
      return;
    }
    const editor = this.codeEditor.codeMirror;
    const doc = editor.getDoc();
    const start = CodeMirror.Pos(element.start.line - 1, element.start.column - 1);
    const end = CodeMirror.Pos(element.end.line - 1, element.end.column - 1);
    const marker = doc.markText(start, end, { className });
    this.selectionMarkers.push(marker);
    editor.scrollIntoView(start, 10);
  }

  createSnippet(element: MessageElement) {
    // Single element right-click: add it to selection if not already, then create
    if (!this.isElementSelected(element)) {
      this.clearAllSelections();
      this.selectedElements = [element];
      this.selected = element;
      this.refreshSelectionMarkers();
    }
    this.createSnippetFromSelection();
  }

  createSnippetFromSelection() {
    if (this.viewOnly || this.selectedElements.length === 0) {
      return;
    }
    const references = this.selectedElements
      .filter((e) => e.positionalPath)
      .map((e) => e.positionalPath);
    if (references.length === 0) {
      return;
    }

    const selectionSummary = this.selectedElements.map((e) => e.hl7Path || e.name).join(', ');

    this.dialog.open(CreateDialogComponent, {
      data: {
        title: 'Create Snippet',
        kind: 'snippet',
        description: this.selectedElements.length > 1
          ? `Creating snippet from ${this.selectedElements.length} selected elements: ${selectionSummary}`
          : `Creating snippet from: ${selectionSummary}`,
        existingNames: this.currentSnippetNames(),
      },
    }).afterClosed().pipe(
      take(1),
      mergeMap((data) => {
        if (!data || !data.name) {
          return EMPTY;
        }
        return this.igId$.pipe(
          take(1),
          mergeMap((igId) => {
            return this.exampleMessagesService.createExampleMessageSnippet(igId, this.messageId, {
              name: data.name,
              messageReferences: references,
            }).pipe(
              map((message) => {
                const actions = [];
                actions.push(this.messageService.messageToAction(message));
                if (message.data) {
                  actions.push(new fromDam.LoadPayloadData(message.data));
                }
                actions.forEach((action) => {
                  this.store.dispatch(action);
                });
              }),
              catchError((error) => {
                this.store.dispatch(this.messageService.actionFromError(error));
                return EMPTY;
              }),
            );
          }),
        );
      }),
    ).subscribe();
  }

  select(from: { line: number, column: number }, to: { line: number, column: number }) {
    if (!from || !to || !this.codeEditor || !this.codeEditor.codeMirror) {
      return;
    }
    const editor = this.codeEditor.codeMirror;
    const doc = editor.getDoc();

    const start = CodeMirror.Pos(from.line - 1, from.column - 1);
    const end = CodeMirror.Pos(to.line - 1, to.column - 1);

    if (this.highlighted) {
      this.highlighted.clear();
    }

    // Apply the highlight using markText
    const marker = doc.markText(start, end, {
      className: 'cm-highlight',
    });

    editor.scrollIntoView(start, 10);
    this.highlighted = marker;
  }

  async parseMessage() {
    this.loading = true;
    this.parseError = '';
    this.messageHash = await this.getMessageHash();
    try {
      const igId = await this.igId$.pipe(take(1)).toPromise();
      const parsed = await this.exampleMessagesService.parseExampleMessage(igId, this.messageId).toPromise();
      this.parsed = parsed;
      this.parseError = '';
      await this.updateStaleMessageState();
      this.highlightSnippetIfAvailable();
      setTimeout(() => {
        if (this.snippetToHighlightPaths.length === 0) {
          this.expandToSegments();
        }
        this.applyTreeFilter();
      }, 0);
    } catch (error) {
      this.parsed = null;
      this.parseError = this.extractParseError(error);
      this.store.dispatch(this.messageService.actionFromError(error));
    } finally {
      this.loading = false;
    }
  }

  onTreeFilterChange(value: string) {
    this.treeFilter = value || '';
    this.applyTreeFilter();
  }

  clearTreeFilter() {
    this.treeFilter = '';
    this.applyTreeFilter();
  }

  applyTreeFilter() {
    if (!this.parsedTree || !this.parsedTree.treeModel) {
      return;
    }
    const query = (this.treeFilter || '').trim().toLowerCase();
    if (!query) {
      this.parsedTree.treeModel.clearFilter();
      return;
    }
    this.parsedTree.treeModel.filterNodes((node: TreeNode) => {
      const data = node && node.data;
      if (!data) {
        return false;
      }
      return this.nodeMatchesFilter(data, query);
    });
  }

  private nodeMatchesFilter(data: any, query: string): boolean {
    const fields = [data.hl7Path, data.name, data.profilePath, data.positionalPath, data.type];
    return fields.some((field) => field && String(field).toLowerCase().indexOf(query) >= 0);
  }

  expandAllTree() {
    if (this.parsedTree && this.parsedTree.treeModel) {
      this.parsedTree.treeModel.expandAll();
    }
  }

  collapseAllTree() {
    if (this.parsedTree && this.parsedTree.treeModel) {
      this.parsedTree.treeModel.collapseAll();
    }
  }

  expandToSegments() {
    if (!this.parsedTree || !this.parsedTree.treeModel) {
      return;
    }
    this.parsedTree.treeModel.collapseAll();
    const roots = this.parsedTree.treeModel.roots || [];
    for (const root of roots) {
      this.expandAncestorsOfSegments(root);
    }
  }

  private expandAncestorsOfSegments(node: any): boolean {
    if (!node) {
      return false;
    }
    const type = node.data && node.data.type;
    if (type === 'SEGMENT' || type === 'SEGMENTREF') {
      return true;
    }
    if (type === 'FIELD' || type === 'COMPONENT' || type === 'SUBCOMPONENT') {
      return false;
    }
    let hasSegment = false;
    const children = node.children || [];
    for (const child of children) {
      if (this.expandAncestorsOfSegments(child)) {
        hasSegment = true;
      }
    }
    if (hasSegment || type === 'GROUP' || !type) {
      if (node.expand) {
        node.expand();
      }
    }
    return hasSegment || type === 'GROUP';
  }

  isStructuralNode(data: any): boolean {
    const type = data && data.type;
    return type === 'GROUP' || type === 'SEGMENT' || type === 'SEGMENTREF';
  }

  treeNodeTitle(data: any): string {
    if (!data) {
      return '';
    }
    const parts = [data.hl7Path, data.name, data.profilePath].filter((part) => !!part);
    return parts.join(' — ');
  }

  startSheetResize(event: MouseEvent) {
    if (!event) {
      return;
    }
    event.preventDefault();
    this.sheetDragging = true;
    this.sheetDragStart = { y: event.clientY, height: this.validationSheetHeight };
  }

  @HostListener('document:mousemove', ['$event'])
  onSheetResize(event: MouseEvent) {
    if (!this.sheetDragging || !this.sheetDragStart || !event) {
      return;
    }
    const next = this.sheetDragStart.height + (this.sheetDragStart.y - event.clientY);
    this.validationSheetHeight = this.clampSheetHeight(next);
  }

  @HostListener('document:mouseup')
  stopSheetResize() {
    if (!this.sheetDragging) {
      return;
    }
    this.sheetDragging = false;
    this.sheetDragStart = null;
    this.refreshCodeMirror();
  }

  validationSheetCollapsed(): boolean {
    return this.validationSheetHeight <= 72;
  }

  toggleValidationSheet() {
    if (this.validationSheetCollapsed()) {
      this.validationSheetHeight = this.clampSheetHeight(Math.round(this.stageHeight() * 0.46));
    } else {
      this.validationSheetHeight = 46;
    }
    setTimeout(() => this.refreshCodeMirror(), 50);
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent) {
    if (!event || this.activeTab !== 0) {
      return;
    }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }
    const editorFocused = this.codeEditor && this.codeEditor.codeMirror && this.codeEditor.codeMirror.hasFocus();
    const target = event.target as HTMLElement;
    const typing = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA');
    if ((editorFocused || typing) && !event.altKey) {
      return;
    }
    if (!this.validationResult) {
      return;
    }
    event.preventDefault();
    this.stepValidation(event.key === 'ArrowDown' ? 1 : -1);
  }

  private stageHeight(): number {
    const stage = this.contentStage && this.contentStage.nativeElement;
    return stage && stage.clientHeight ? stage.clientHeight : 640;
  }

  private clampSheetHeight(height: number): number {
    return Math.max(46, Math.min(this.stageHeight() - 72, Math.round(height)));
  }

  currentValidationKind(): 'errors' | 'alerts' | 'warnings' | 'affirmatives' | 'informationals' | 'report' {
    const kinds: Array<'errors' | 'alerts' | 'warnings' | 'affirmatives' | 'informationals' | 'report'> = this.showQuietDetections
      ? ['errors', 'alerts', 'warnings', 'affirmatives', 'informationals', 'report']
      : ['errors', 'alerts', 'warnings', 'report'];
    return kinds[this.validationTabIndex] || 'errors';
  }

  validationEntries(): IExampleMessageValidationEntry[] {
    const kind = this.currentValidationKind();
    if (kind === 'report') {
      return [];
    }
    return this.entriesFor(kind);
  }

  validationPositionLabel(): string {
    const kind = this.currentValidationKind();
    if (kind === 'report') {
      return 'Report';
    }
    const entries = this.validationEntries();
    if (!entries.length) {
      return 'No ' + kind;
    }
    const index = this.activeValidationIndex(entries);
    return (index >= 0 ? index + 1 : 0) + ' / ' + entries.length + ' ' + kind;
  }

  canStepValidation(delta: number): boolean {
    const entries = this.validationEntries();
    if (!entries.length) {
      return false;
    }
    const index = this.activeValidationIndex(entries);
    if (index < 0) {
      return true;
    }
    const next = index + delta;
    return next >= 0 && next < entries.length;
  }

  stepValidation(delta: number) {
    const entries = this.validationEntries();
    if (!entries.length) {
      return;
    }
    const index = this.activeValidationIndex(entries);
    const start = index < 0 ? (delta > 0 ? -1 : 0) : index;
    const next = Math.max(0, Math.min(entries.length - 1, start + delta));
    this.selectValidationEntry(entries[next]);
  }

  selectValidationEntry(entry: IExampleMessageValidationEntry, revealMessage: boolean = true) {
    if (!entry) {
      return;
    }
    this.activeValidationEntry = entry;
    const parsed = this.findParsedForValidation(entry);
    if (parsed) {
      this.highlight(parsed);
      this.revealInTree(parsed);
    }
    this.jumpToEntry(entry);
    if (revealMessage) {
      const stage = this.stageHeight();
      if (this.validationSheetHeight > stage * 0.62) {
        this.validationSheetHeight = this.clampSheetHeight(stage * 0.42);
        setTimeout(() => this.refreshCodeMirror(), 50);
      } else if (this.validationSheetCollapsed()) {
        this.validationSheetHeight = this.clampSheetHeight(280);
      }
    }
    setTimeout(() => this.scrollActiveValidationRow(), 0);
  }

  isActiveValidationEntry(entry: IExampleMessageValidationEntry): boolean {
    return !!entry && entry === this.activeValidationEntry;
  }

  private selectFirstValidationEntry() {
    const entries = this.validationEntries();
    if (!entries.length) {
      return;
    }
    if (this.activeValidationIndex(entries) >= 0) {
      this.selectValidationEntry(this.activeValidationEntry, false);
      return;
    }
    this.selectValidationEntry(entries[0], false);
  }

  private activeValidationIndex(entries: IExampleMessageValidationEntry[]): number {
    return entries.indexOf(this.activeValidationEntry);
  }

  private scrollActiveValidationRow() {
    const row = document.querySelector('.validation-table tr.active-entry') as HTMLElement;
    if (row && row.scrollIntoView) {
      row.scrollIntoView({ block: 'nearest' });
    }
  }

  private refreshCodeMirror() {
    if (this.codeEditor && this.codeEditor.codeMirror) {
      this.codeEditor.codeMirror.refresh();
    }
  }

  private extractParseError(error: any): string {
    if (error && error.error) {
      if (typeof error.error === 'string' && error.error.trim()) {
        return error.error;
      }
      if (error.error.text) {
        return error.error.text;
      }
      if (error.error.message) {
        return error.error.message;
      }
    }
    if (error && error.message) {
      return error.message;
    }
    return 'Failed to parse the example message.';
  }

  validateMessage() {
    if (!this.message || !this.messageId) {
      return;
    }
    this.activeTab = 0;
    if (this.validationSheetHeight < 220) {
      this.validationSheetHeight = this.clampSheetHeight(Math.round(this.stageHeight() * 0.5));
    }
    this.validating = true;
    this.validationError = '';
    this.igId$.pipe(
      take(1),
      mergeMap((igId) => this.exampleMessagesService.validateExampleMessage(igId, this.messageId, this.message)),
      finalize(() => {
        this.validating = false;
        setTimeout(() => this.refreshCodeMirror(), 50);
      }),
    ).subscribe(
      (result) => {
        this.validationResult = result;
        this.validationError = result && result.error ? result.error : '';
        this.showQuietDetections = false;
        this.activeValidationEntry = null;
        if (!this.validationError) {
          if (result.errors > 0) {
            this.validationTabIndex = 0;
          } else if (result.alerts > 0) {
            this.validationTabIndex = 1;
          } else if (result.warnings > 0) {
            this.validationTabIndex = 2;
          } else {
            this.validationTabIndex = 3;
          }
          setTimeout(() => this.selectFirstValidationEntry(), 0);
        }
      },
      (error) => {
        this.validationResult = null;
        this.validationError = (error && error.error && (error.error.message || error.error.error)) || 'Validation failed';
      },
    );
  }

  entriesFor(kind: 'errors' | 'alerts' | 'warnings' | 'affirmatives' | 'informationals'): IExampleMessageValidationEntry[] {
    if (!this.validationResult || !this.validationResult.entries) {
      return [];
    }
    return this.validationResult.entries.filter((entry) => {
      const classification = (entry.classification || '').toLowerCase();
      if (kind === 'errors') {
        return classification.indexOf('error') >= 0 && classification.indexOf('spec') < 0;
      }
      if (kind === 'alerts') {
        return classification.indexOf('alert') >= 0;
      }
      if (kind === 'warnings') {
        return classification.indexOf('warning') >= 0;
      }
      if (kind === 'affirmatives') {
        return classification.indexOf('affirmative') >= 0;
      }
      return classification.indexOf('error') < 0
        && classification.indexOf('alert') < 0
        && classification.indexOf('warning') < 0
        && classification.indexOf('affirmative') < 0;
    });
  }

  openElementInIg(element: MessageElement) {
    if (!element) {
      return;
    }
    this.locateAndOpen(element.positionalPath, element.hl7Path);
  }

  validationEr7Path(entry: IExampleMessageValidationEntry): string {
    return (entry && (entry.er7Path || entry.path)) || '';
  }

  validationIgPath(entry: IExampleMessageValidationEntry): string {
    if (!entry) {
      return '';
    }
    if (entry.igPath) {
      return entry.igPath;
    }
    return this.normalizeHl7Path(this.validationEr7Path(entry));
  }

  openValidationInMessage(event: Event, entry: IExampleMessageValidationEntry) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    if (!entry) {
      return;
    }
    const parsed = this.findParsedForValidation(entry);
    if (parsed) {
      this.highlight(parsed);
      this.revealInTree(parsed);
      return;
    }
    this.jumpToEntry(entry);
  }

  openValidationInIg(event: Event, entry: IExampleMessageValidationEntry) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    if (!entry) {
      return;
    }
    const parsed = this.findParsedForValidation(entry);
    this.locateAndOpen(
      (parsed && parsed.positionalPath) || entry.positionalPath,
      this.validationEr7Path(entry) || this.validationIgPath(entry),
    );
  }

  private locateAndOpen(positionalPath: string, hl7Path: string) {
    this.igId$.pipe(
      take(1),
      mergeMap((igId) => this.exampleMessagesService.locateExampleMessageElement(igId, this.messageId, positionalPath, hl7Path)),
    ).subscribe(
      (location) => this.navigateToIgLocation(location),
      (error) => {
        this.store.dispatch(this.messageService.actionFromError(error));
      },
    );
  }

  private navigateToIgLocation(location: IExampleMessageLocation) {
    if (!location || location.error) {
      this.store.dispatch(new Notify(new UserMessage(
        MessageType.FAILED,
        (location && location.error) || 'Could not locate this element in the IG.',
      )));
      return;
    }
    const contexts = location.contexts || [];
    if (contexts.length > 1) {
      this.dialog.open(LocateContextDialogComponent, {
        data: location,
        width: '540px',
        maxWidth: '94vw',
        autoFocus: false,
      }).afterClosed().pipe(take(1)).subscribe((context: IExampleMessageLocationContext) => {
        if (context) {
          this.goToIgResource(location.igId, context);
        }
      });
      return;
    }
    const only = contexts.length === 1 ? contexts[0] : location;
    this.goToIgResource(location.igId, only);
  }

  private goToIgResource(igId: string, target: { routeType: string; resourceId: string; location?: string }) {
    if (!target || !target.routeType || !target.resourceId) {
      this.store.dispatch(new Notify(new UserMessage(
        MessageType.FAILED,
        'Could not locate this element in the IG.',
      )));
      return;
    }
    this.router.navigate(
      ['/ig', igId, target.routeType, target.resourceId, 'structure'],
      { queryParams: target.location ? { location: target.location } : {} },
    );
  }

  private findParsedForValidation(entry: IExampleMessageValidationEntry): MessageElement | null {
    if (!entry) {
      return null;
    }
    if (entry.positionalPath) {
      const byPosition = this.findParsedMatchPath(entry.positionalPath, 'positionalPath', false);
      if (byPosition) {
        return byPosition;
      }
    }
    const er7 = this.validationEr7Path(entry);
    if (er7) {
      const exact = this.findParsedMatchPath(er7, 'hl7Path', true);
      if (exact) {
        return exact;
      }
    }
    const ig = this.validationIgPath(entry);
    if (ig) {
      return this.findParsedMatchPath(ig, 'hl7Path', true) || this.findParsedMatchPath(ig, 'profilePath', true);
    }
    return null;
  }

  private findParsedByHl7Path(hl7Path: string): MessageElement | null {
    return this.findParsedMatchPath(hl7Path, 'hl7Path', true);
  }

  private findParsedMatchPath(path: string, field: 'hl7Path' | 'positionalPath' | 'profilePath', allowSuffix: boolean): MessageElement | null {
    if (!path || !this.parsed) {
      return null;
    }
    const visit = (node: any): MessageElement | null => {
      if (!node) {
        return null;
      }
      if (this.pathMatches(node[field], path, allowSuffix)) {
        return node;
      }
      const children = node.children || [];
      for (let i = 0; i < children.length; i++) {
        const found = visit(children[i]);
        if (found) {
          return found;
        }
      }
      return null;
    };
    if ((this.parsed as any).children) {
      return visit(this.parsed);
    }
    const roots = this.parsed as any[];
    for (let i = 0; i < roots.length; i++) {
      const found = visit(roots[i]);
      if (found) {
        return found;
      }
    }
    return null;
  }

  private pathMatches(candidate: string, target: string, allowSuffix: boolean): boolean {
    if (!candidate || !target) {
      return false;
    }
    if (candidate === target) {
      return true;
    }
    const left = this.normalizeHl7Path(candidate);
    const right = this.normalizeHl7Path(target);
    if (left === right) {
      return true;
    }
    if (!allowSuffix) {
      return false;
    }
    return this.pathEndsWith(candidate, target)
      || this.pathEndsWith(target, candidate)
      || this.pathEndsWith(left, right)
      || this.pathEndsWith(right, left);
  }

  private pathEndsWith(full: string, suffix: string): boolean {
    if (!full || !suffix || full.length <= suffix.length) {
      return false;
    }
    if (full.substring(full.length - suffix.length) !== suffix) {
      return false;
    }
    const boundary = full.charAt(full.length - suffix.length - 1);
    return boundary === '.' || boundary === '-';
  }

  private normalizeHl7Path(path: string): string {
    return (path || '').replace(/\[\d+]/g, '').trim();
  }

  jumpToEntry(entry: IExampleMessageValidationEntry) {
    if (!entry || !entry.line || !this.codeEditor || !this.codeEditor.codeMirror) {
      return;
    }
    const editor = this.codeEditor.codeMirror;
    const doc = editor.getDoc();
    const line = Math.max(entry.line - 1, 0);
    const column = Math.max((entry.column || 1) - 1, 0);
    const start = CodeMirror.Pos(line, column);
    editor.focus();
    doc.setCursor(start);
    editor.scrollIntoView(start, 40);
  }

  private revealInTree(element: MessageElement) {
    if (!element || !this.parsedTree || !this.parsedTree.treeModel) {
      return;
    }
    const node = this.parsedTree.treeModel.getNodeBy((candidate) => {
      const data = candidate && candidate.data;
      if (!data) {
        return false;
      }
      if (element.positionalPath && data.positionalPath) {
        return data.positionalPath === element.positionalPath;
      }
      return data.hl7Path === element.hl7Path;
    });
    if (node && node.ensureVisible) {
      node.ensureVisible();
    }
  }

  reportHtml(): SafeHtml {
    const body = this.reportBodyHtml();
    if (!body) {
      return '';
    }
    return this.sanitizer.bypassSecurityTrustHtml(body);
  }

  downloadReport() {
    const body = this.reportBodyHtml();
    if (!body) {
      return;
    }
    const title = (this.messageName || 'example-message').replace(/[^\w.-]+/g, '_');
    const html = '<!DOCTYPE html><html><head><meta charset="utf-8">'
      + '<title>Message Validation Report</title>'
      + '<style>' + VALIDATION_REPORT_CSS + '</style>'
      + '<script>' + VALIDATION_REPORT_SCRIPT + '</script>'
      + '</head><body>' + body + '</body></html>';
    saveAs(new Blob([html], { type: 'text/html;charset=utf-8' }), title + '-validation-report.html');
  }

  onReportClick(event: Event) {
    const host = this.reportHost && this.reportHost.nativeElement;
    if (!host) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target) {
      return;
    }
    if (target.tagName === 'INPUT') {
      const input = target as HTMLInputElement;
      const handler = input.getAttribute('onclick') || '';
      const vis = handler.match(/toggle_visibility\('([^']+)'/);
      if (vis) {
        this.toggleById(host, vis[1], input.checked);
        return;
      }
      const visC = handler.match(/toggle_visibilityC\('([^']+)'/);
      if (visC) {
        this.toggleByClass(host, visC[1], input.checked);
      }
      return;
    }
    if (target.tagName === 'BUTTON') {
      const handler = target.getAttribute('onclick') || '';
      const fi = handler.match(/fi\('([^']+)'/);
      if (fi) {
        this.toggleFailuresInterpretation(host, fi[1], target);
        return;
      }
      const show = handler.match(/ShowSep\('([^']+)'/);
      if (show) {
        this.showMessageTable(host, 'msgC' + show[1], 'msgS' + show[1]);
        return;
      }
      const hide = handler.match(/HideSep\('([^']+)'/);
      if (hide) {
        this.showMessageTable(host, 'msgS' + hide[1], 'msgC' + hide[1]);
      }
    }
  }

  private reportBodyHtml(): string {
    if (!this.validationResult || !this.validationResult.html) {
      return '';
    }
    const html = this.validationResult.html;
    const body = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
    return body ? body[1] : html;
  }

  private toggleById(host: HTMLElement, id: string, visible: boolean) {
    const el = host.querySelector('#' + id) as HTMLElement;
    if (el) {
      el.style.display = visible ? '' : 'none';
    }
  }

  private toggleByClass(host: HTMLElement, cls: string, visible: boolean) {
    const nodes = host.getElementsByClassName(cls);
    for (let i = 0; i < nodes.length; i++) {
      (nodes[i] as HTMLElement).style.display = visible ? '' : 'none';
    }
  }

  private toggleFailuresInterpretation(host: HTMLElement, id: string, button: HTMLElement) {
    const div = host.querySelector('#' + id) as HTMLElement;
    if (!div) {
      return;
    }
    const hidden = div.style.display === 'none';
    div.style.display = hidden ? '' : 'none';
    if (button.childNodes[0]) {
      button.childNodes[0].nodeValue = hidden ? ' Hide ' : ' View ';
    }
  }

  private showMessageTable(host: HTMLElement, hideId: string, showId: string) {
    const hide = host.querySelector('#' + hideId) as HTMLElement;
    const show = host.querySelector('#' + showId) as HTMLElement;
    if (hide) {
      hide.style.display = 'none';
    }
    if (show) {
      show.style.display = '';
    }
  }

  async updateStaleMessageState() {
    const hash = await this.getMessageHash();
    this.staleMessageTree = hash !== this.messageHash;
  }

  private resetEditorState() {
    this.clearHover();
    this.clearAllSelections();
    this.parsed = [];
    this.parseError = '';
    this.staleMessageTree = false;
    this.messageHash = null;
    this.validating = false;
    this.validationResult = null;
    this.validationError = '';
    this.validationTabIndex = 0;
    this.showQuietDetections = false;
    this.activeValidationEntry = null;
    this.snippetValidationWarnings = [];
    this.snippetToHighlightPaths = [];
    this.selected = null;
    this.treeFilter = '';
  }

  private currentSnippetNames(): string[] {
    let names: string[] = [];
    this.store.select(selectIgExampleMessages).pipe(
      take(1),
      map((igExampleMessages) => {
        const allMessages = (igExampleMessages.profileExampleMessages || [])
          .reduce((acc, profile) => [...acc, ...profile.exampleMessages], []);
        const message = allMessages.find((exampleMessage) => exampleMessage.id === this.messageId);
        names = ((message && message.snippets) || []).map((snippet) => snippet.name).filter((name) => !!name);
      }),
    ).subscribe();
    return names;
  }

  private resolveSnippetPath() {
    if (!this.snippetId || !this.messageId) {
      this.snippetToHighlightPaths = [];
      return;
    }
    this.store.select(selectIgExampleMessages).pipe(
      take(1),
      map((igExampleMessages) => {
        const allMessages = igExampleMessages.profileExampleMessages
          .reduce((acc, profile) => [...acc, ...profile.exampleMessages], []);
        const message = allMessages.find((exampleMessage) => exampleMessage.id === this.messageId);
        if (!message || !message.snippets) {
          this.snippetToHighlightPaths = [];
          return;
        }
        const snippet = message.snippets.find((entry: IExampleMessageSnippet) => entry.id === this.snippetId);
        this.snippetToHighlightPaths = snippet && snippet.messageReferences && snippet.messageReferences.length > 0
          ? [...snippet.messageReferences]
          : [];
      }),
    ).subscribe();
  }

  private highlightSnippetIfAvailable() {
    if (!this.parsed || this.snippetToHighlightPaths.length === 0) {
      return;
    }
    this.clearAllSelections();
    for (const path of this.snippetToHighlightPaths) {
      const target = this.findByPositionalPath(this.parsed, path);
      if (target && target.start && target.end) {
        this.selectedElements.push(target);
      }
    }
    if (this.selectedElements.length > 0) {
      this.selected = this.selectedElements[this.selectedElements.length - 1];
      this.refreshSelectionMarkers();
      // Expand tree to show all selected paths
      setTimeout(() => {
        for (const path of this.snippetToHighlightPaths) {
          this.expandTreeToPath(path);
        }
      }, 200);
    }
  }

  /**
   * Walk the angular-tree-component tree and expand parents
   * so the node matching the given positionalPath is visible.
   */
  private expandTreeToPath(positionalPath: string) {
    if (!this.parsedTree || !this.parsedTree.treeModel) {
      return;
    }
    const roots = this.parsedTree.treeModel.roots || [];
    for (const root of roots) {
      if (this.expandNodeToPath(root, positionalPath)) {
        break;
      }
    }
  }

  private expandNodeToPath(treeNode: any, positionalPath: string): boolean {
    if (treeNode.data && treeNode.data.positionalPath === positionalPath) {
      treeNode.ensureVisible();
      return true;
    }
    if (treeNode.children) {
      for (const child of treeNode.children) {
        if (this.expandNodeToPath(child, positionalPath)) {
          return true;
        }
      }
    }
    return false;
  }

  private findByPositionalPath(node: any, positionalPath: string): MessageElement | null {
    if (!node) {
      return null;
    }
    if (node.positionalPath === positionalPath) {
      return node as MessageElement;
    }
    if (!node.children || node.children.length === 0) {
      return null;
    }
    for (const child of node.children) {
      const found = this.findByPositionalPath(child, positionalPath);
      if (found) {
        return found;
      }
    }
    return null;
  }

  onDeactivate(): void {
  }

  ngOnInit() {
    // Wait for CodeMirror to be ready, then add mousedown handler for multi-select
    setTimeout(() => this.setupEditorClickHandler(), 500);
  }

  ngOnDestroy() {
    if (this.detachTextListeners) {
      this.detachTextListeners();
    }
    this.clearHover();
  }

  setupEditorClickHandler() {
    if (!this.codeEditor || !this.codeEditor.codeMirror) {
      return;
    }
    const editor = this.codeEditor.codeMirror;
    editor.on('mousedown', (cm: any, event: MouseEvent) => {
      if (!this.parsed || this.staleMessageTree) {
        return;
      }
      if (event.ctrlKey || event.metaKey) {
        event.preventDefault();
        const pos = cm.coordsChar({ left: event.clientX, top: event.clientY });
        // pos.line is 0-based, pos.ch is 0-based
        const line1 = pos.line + 1;
        const col1 = pos.ch + 1;
        const element = this.findElementAtPosition(this.parsed, line1, col1);
        if (element) {
          this.toggleSelect(element);
        }
      }
    });
    this.setupTextHover(editor);
  }

  /**
   * Pointing at a value in the message text names the element it belongs to, and a right-click
   * on it offers the same actions as the message tree. Pointer moves stay outside Angular: the
   * tip is plain DOM, so nothing on the page has to be checked again for each move.
   */
  private setupTextHover(editor: CodeMirror.Editor) {
    const wrapper = editor.getWrapperElement();
    const onMove = (event: MouseEvent) => {
      if (this.textMenuOpen || this.hoverFrame !== null) {
        return;
      }
      this.hoverFrame = requestAnimationFrame(() => {
        this.hoverFrame = null;
        this.showHover(editor, event);
      });
    };
    const onLeave = () => {
      if (!this.textMenuOpen) {
        this.clearHover();
      }
    };
    const onMenu = (event: MouseEvent) => {
      const element = this.elementUnderPointer(editor, event);
      if (!element) {
        // Nothing the profile knows here: leave the browser's own menu.
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      this.zone.run(() => {
        this.setHovered(editor, element);
        this.hideHoverTip();
        this.textMenuOpen = true;
        this.contextMenuService.show.next({
          contextMenu: this.textContextMenu,
          event,
          item: element,
        });
      });
    };
    const onScroll = () => {
      if (!this.textMenuOpen) {
        this.clearHover();
      }
    };
    this.zone.runOutsideAngular(() => {
      wrapper.addEventListener('mousemove', onMove);
      wrapper.addEventListener('mouseleave', onLeave);
      wrapper.addEventListener('contextmenu', onMenu);
      editor.on('scroll', onScroll);
      editor.on('change', onScroll);
    });
    this.detachTextListeners = () => {
      wrapper.removeEventListener('mousemove', onMove);
      wrapper.removeEventListener('mouseleave', onLeave);
      wrapper.removeEventListener('contextmenu', onMenu);
      editor.off('scroll', onScroll);
      editor.off('change', onScroll);
      if (this.hoverFrame !== null) {
        cancelAnimationFrame(this.hoverFrame);
        this.hoverFrame = null;
      }
    };
  }

  /** The deepest element whose text is under the pointer, or null (stale tree, blank area). */
  private elementUnderPointer(editor: CodeMirror.Editor, event: MouseEvent): MessageElement | null {
    if (!this.parsed || this.staleMessageTree || this.loading) {
      return null;
    }
    const pos: any = editor.coordsChar({ left: event.clientX, top: event.clientY }, 'window');
    if (!pos || pos.outside) {
      return null;
    }
    // coordsChar answers with the nearest gap between two characters; xRel says on which side
    // of that gap the pointer is, which gives the character it is really over.
    const index = pos.xRel < 0 ? pos.ch - 1 : pos.ch;
    if (index < 0 || index >= editor.getDoc().getLine(pos.line).length) {
      return null;
    }
    return this.findElementAtPosition(this.parsed, pos.line + 1, index + 1, true);
  }

  private showHover(editor: CodeMirror.Editor, event: MouseEvent) {
    const element = this.elementUnderPointer(editor, event);
    if (!element) {
      this.clearHover();
      return;
    }
    if (element !== this.hovered) {
      this.setHovered(editor, element);
    }
    this.placeHoverTip(editor, element, event);
  }

  private setHovered(editor: CodeMirror.Editor, element: MessageElement) {
    if (this.hoverMarker) {
      this.hoverMarker.clear();
    }
    this.hovered = element;
    this.hoverMarker = editor.getDoc().markText(
      CodeMirror.Pos(element.start.line - 1, element.start.column - 1),
      CodeMirror.Pos(element.end.line - 1, element.end.column - 1),
      { className: 'cm-er7-hover' },
    );
  }

  private placeHoverTip(editor: CodeMirror.Editor, element: MessageElement, event: MouseEvent) {
    if (!this.hoverTip) {
      this.hoverTip = document.createElement('div');
      this.hoverTip.className = 'er7-hover-tip';
      document.body.appendChild(this.hoverTip);
    }
    const tip = this.hoverTip;
    if (tip.getAttribute('data-path') !== element.positionalPath) {
      tip.setAttribute('data-path', element.positionalPath || '');
      tip.innerHTML = '';
      const head = tip.appendChild(document.createElement('div'));
      head.className = 'er7-hover-head';
      const path = head.appendChild(document.createElement('span'));
      path.className = 'er7-hover-path';
      path.textContent = element.hl7Path || '';
      const name = head.appendChild(document.createElement('span'));
      name.className = 'er7-hover-name';
      name.textContent = element.name || '';
      const foot = tip.appendChild(document.createElement('div'));
      foot.className = 'er7-hover-foot';
      foot.textContent = this.elementKindLabel(element) + ' · right-click for options';
    }
    tip.style.display = 'block';
    const type = (element as any).type;
    const start = editor.charCoords(CodeMirror.Pos(element.start.line - 1, element.start.column - 1), 'window');
    const box = editor.getWrapperElement().getBoundingClientRect();
    // A segment or a group runs the whole line, so its tip follows the pointer instead.
    const wide = type === 'SEGMENT' || type === 'GROUP' || start.left < box.left;
    const width = tip.offsetWidth;
    const height = tip.offsetHeight;
    let left = wide ? event.clientX - 14 : start.left;
    left = Math.max(8, Math.min(left, window.innerWidth - width - 8));
    let top = start.top - height - 6;
    if (top < 8) {
      top = start.bottom + 6;
    }
    tip.style.left = left + 'px';
    tip.style.top = top + 'px';
  }

  elementKindLabel(element: MessageElement): string {
    switch (element && (element as any).type) {
      case 'GROUP': return 'Group';
      case 'SEGMENT':
      case 'SEGMENTREF': return 'Segment';
      case 'FIELD': return 'Field';
      case 'COMPONENT': return 'Component';
      case 'SUBCOMPONENT': return 'Sub-component';
      default: return 'Element';
    }
  }

  private hideHoverTip() {
    if (this.hoverTip) {
      this.hoverTip.style.display = 'none';
    }
  }

  private clearHover() {
    this.hovered = null;
    if (this.hoverMarker) {
      this.hoverMarker.clear();
      this.hoverMarker = null;
    }
    if (this.hoverTip && this.hoverTip.parentNode) {
      this.hoverTip.parentNode.removeChild(this.hoverTip);
    }
    this.hoverTip = null;
  }

  onTextMenuClosed() {
    this.textMenuOpen = false;
    this.clearHover();
  }

  showElementInTree(element: MessageElement) {
    if (!element) {
      return;
    }
    this.highlight(element);
    this.revealInTree(element);
  }

  /**
   * Find the deepest element in the parsed tree that contains the given position.
   */
  findElementAtPosition(node: any, line: number, column: number, exact: boolean = false): MessageElement | null {
    if (!node) {
      return null;
    }
    const children = node.children || [];
    // Search children first (deepest match wins)
    for (const child of children) {
      const found = this.findElementAtPosition(child, line, column, exact);
      if (found) {
        return found;
      }
    }
    // Check if this node contains the position
    if (node.start && node.end) {
      const afterStart = (line > node.start.line) || (line === node.start.line && column >= node.start.column);
      // An element's end column is the one after its last character: a click there still counts,
      // a pointer over that character (exact) does not.
      const beforeEnd = (line < node.end.line)
        || (line === node.end.line && (exact ? column < node.end.column : column <= node.end.column));
      if (afterStart && beforeEnd) {
        return node as MessageElement;
      }
    }
    return null;
  }

}
