import { Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { IAvailableMessage, IExampleMessageSnippet } from '../../../example-messages/domain/example-messages.model';
import { ExampleMessagesService } from '../../../example-messages/services/example-messages.service';

export interface ISelectMessageSnippetDialogData {
  availableMessages: IAvailableMessage[];
  /** The verb of the confirm button: "Insert", "Attach". The dialog adds "message" or "snippet". */
  confirmLabel?: string;
  title?: string;
  /** With the guide's id the dialog can show the text of a snippet and link to the Example Messages area. */
  igId?: string;
  /** What is attached now, marked in the list and selected when the dialog opens. */
  selectedMessageId?: string;
  selectedSnippetId?: string;
  /** The list could not be loaded, which is not the same as a guide without examples. */
  loadFailed?: boolean;
  /** Also ask for a name, for a caller that creates something to hold the example. The result then carries `name`. */
  askName?: boolean;
  nameLabel?: string;
  name?: string;
  /** With `askName`: the label of a second button that confirms the name alone, without an example. */
  withoutExampleLabel?: string;
}

export interface ISelectMessageSnippetDialogResult {
  messageId: string;
  snippetId: string;  // null means whole message
  label: string;      // human-readable label of selection
  name?: string;      // only when the dialog asked for one
}

export interface IPickRow {
  /** Unique in the list: a snippet's id alone is only unique inside its message. */
  key: string;
  type: 'message' | 'snippet';
  message: IAvailableMessage;
  snippet?: IExampleMessageSnippet;
  label: string;
  detail: string;
  current: boolean;
}

export interface IPickMessage {
  row: IPickRow;
  snippets: IPickRow[];
  total: number;
  open: boolean;
}

export interface IPickGroup {
  label: string;
  messages: IPickMessage[];
}

interface IPreview {
  text: string;
  loading: boolean;
  note: string;
}

/** Opens every message's snippets when the whole list still fits in the dialog without much scrolling. */
const OPEN_ALL_UP_TO = 14;

@Component({
  selector: 'app-select-message-snippet-dialog',
  templateUrl: './select-message-snippet-dialog.component.html',
  styleUrls: ['./select-message-snippet-dialog.component.scss'],
})
export class SelectMessageSnippetDialogComponent implements OnInit, OnDestroy {

  @ViewChild('list') list: ElementRef;

  term = '';
  groups: IPickGroup[] = [];
  /** The rows on screen, top to bottom: what the arrow keys walk through. */
  visible: IPickRow[] = [];
  selected: IPickRow = null;
  preview: IPreview = { text: '', loading: false, note: '' };
  total = 0;
  name = '';
  /** Until the name is typed by hand it follows the selection. */
  private nameTyped = false;

  private closed: { [messageId: string]: boolean } = {};
  private snippetText: { [key: string]: IPreview } = {};
  private rendering: Subscription;

  constructor(
    public dialogRef: MatDialogRef<SelectMessageSnippetDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ISelectMessageSnippetDialogData,
    private exampleMessagesService: ExampleMessagesService,
    private router: Router,
  ) { }

  ngOnInit() {
    this.dialogRef.updateSize('940px');
    this.name = this.data.name || '';
    const messages = this.data.availableMessages || [];
    this.total = messages.length;
    const rows = messages.reduce((count, message) => count + 1 + (message.snippets || []).length, 0);
    if (rows > OPEN_ALL_UP_TO) {
      messages.forEach((message) => this.closed[message.id] = message.id !== this.data.selectedMessageId);
    }
    this.rebuild();
    const current = this.visible.find((row) => row.current);
    if (current) {
      this.select(current);
      this.reveal(this.indexOf(current));
    }
  }

  ngOnDestroy() {
    this.stopRendering();
  }

  get verb(): string {
    return this.data.confirmLabel || 'Attach';
  }

  get confirmText(): string {
    return this.selected ? this.verb + ' ' + this.selected.type : this.verb;
  }

  /** Confirming what is attached already would change nothing. */
  get canConfirm(): boolean {
    return !!this.selected && !this.selected.current && this.nameGiven;
  }

  get nameGiven(): boolean {
    return !this.data.askName || !!this.name.trim();
  }

  typeName(name: string) {
    this.name = name || '';
    this.nameTyped = true;
  }

  /** Closes with the name alone: the caller makes its section, and an example is attached to it later. */
  confirmWithoutExample() {
    if (!this.data.askName || !this.nameGiven) {
      return;
    }
    this.dialogRef.close({ messageId: null, snippetId: null, label: null, name: this.name.trim() });
  }

  get searching(): boolean {
    return !!this.term.trim();
  }

  search(term: string) {
    this.term = term || '';
    this.rebuild();
    if (this.selected && !this.visible.some((row) => row.key === this.selected.key)) {
      this.select(null);
    }
  }

  /** Groups by profile in the guide's order. A search keeps a message when it or one of its snippets matches. */
  rebuild() {
    const term = this.term.trim().toLowerCase();
    const groups: IPickGroup[] = [];
    const byProfile: { [name: string]: IPickGroup } = {};
    for (const message of (this.data.availableMessages || [])) {
      const entry = this.entryFor(message, term);
      if (!entry) {
        continue;
      }
      const profile = this.profileOf(message);
      if (!byProfile[profile]) {
        byProfile[profile] = { label: profile, messages: [] };
        groups.push(byProfile[profile]);
      }
      byProfile[profile].messages.push(entry);
    }
    this.groups = groups;
    // Messages of one profile may not follow each other in the input, so the walk order is taken from the groups.
    this.visible = this.inGroupOrder(groups);
  }

  toggle(entry: IPickMessage, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    this.closed[entry.row.message.id] = entry.open;
    this.rebuild();
    if (this.selected && !this.visible.some((row) => row.key === this.selected.key)) {
      this.select(this.visible.find((row) => row.key === entry.row.key) || null);
    }
  }

  select(row: IPickRow) {
    this.selected = row;
    if (row && this.data.askName && !this.nameTyped) {
      this.name = row.label;
    }
    this.stopRendering();
    if (!row) {
      this.preview = { text: '', loading: false, note: '' };
      return;
    }
    if (row.type === 'message') {
      this.preview = { text: row.message.message || '', loading: false, note: '' };
      return;
    }
    this.preview = this.snippetText[row.key] || this.loadSnippet(row);
  }

  isSelected(row: IPickRow): boolean {
    return !!this.selected && this.selected.key === row.key;
  }

  indexOf(row: IPickRow): number {
    return this.visible.findIndex((candidate) => candidate.key === row.key);
  }

  /** Arrow keys walk the list from the search box too, so that typing and choosing need no mouse. */
  onKey(event: KeyboardEvent) {
    const field = event.target as HTMLElement;
    const inSearch = !!field && field.tagName === 'INPUT';
    if (event.key === 'Enter') {
      if (this.canConfirm) {
        event.preventDefault();
        this.confirm();
      }
      return;
    }
    // The name field keeps its own keys: walking the list from it would also rename the section.
    if (inSearch && field.id === 'pick-name') {
      return;
    }
    if (!inSearch && (event.key === 'ArrowRight' || event.key === 'ArrowLeft')) {
      this.openOrClose(event);
      return;
    }
    const index = this.selected ? this.indexOf(this.selected) : -1;
    const next = this.target(event.key, index, inSearch);
    if (next === null) {
      return;
    }
    event.preventDefault();
    if (next >= 0 && next < this.visible.length && next !== index) {
      this.select(this.visible[next]);
      this.reveal(next);
    }
  }

  confirm() {
    if (!this.canConfirm) {
      return;
    }
    const result: ISelectMessageSnippetDialogResult = {
      messageId: this.selected.message.id,
      snippetId: this.selected.snippet ? this.selected.snippet.id : null,
      label: this.selected.label,
    };
    if (this.data.askName) {
      result.name = this.name.trim();
    }
    this.dialogRef.close(result);
  }

  choose(row: IPickRow) {
    this.select(row);
    this.confirm();
  }

  cancel() {
    this.dialogRef.close(null);
  }

  openExampleMessages() {
    this.dialogRef.close(null);
    this.router.navigate(['/example-messages', this.data.igId]);
  }

  private entryOf(messageId: string): IPickMessage {
    for (const group of this.groups) {
      const found = group.messages.find((entry) => entry.row.message.id === messageId);
      if (found) {
        return found;
      }
    }
    return null;
  }

  /** The row an arrow key leads to, or null when the key is not one of the list's. */
  private target(key: string, index: number, inSearch: boolean): number {
    if (key === 'ArrowDown') {
      return Math.min(this.visible.length - 1, index + 1);
    }
    if (key === 'ArrowUp') {
      return Math.max(0, index - 1);
    }
    if (key === 'Home' && !inSearch) {
      return 0;
    }
    if (key === 'End' && !inSearch) {
      return this.visible.length - 1;
    }
    return null;
  }

  private openOrClose(event: KeyboardEvent) {
    if (!this.selected || this.searching) {
      return;
    }
    const entry = this.entryOf(this.selected.message.id);
    if (entry && entry.total && entry.open !== (event.key === 'ArrowRight')) {
      event.preventDefault();
      this.toggle(entry);
    }
  }

  private profileOf(message: IAvailableMessage): string {
    return message.profileName || 'Unknown Profile';
  }

  private entryFor(message: IAvailableMessage, term: string): IPickMessage {
    const snippets = message.snippets || [];
    const ownMatch = !term || this.matches(term, [message.name, message.description, this.profileOf(message)]);
    const shown = ownMatch ? snippets : snippets.filter((snippet) => this.matches(term, [snippet.name, snippet.description]));
    if (!ownMatch && !shown.length) {
      return null;
    }
    return {
      row: this.messageRow(message),
      snippets: shown.map((snippet) => this.snippetRow(message, snippet)),
      total: snippets.length,
      open: !!term || !this.closed[message.id],
    };
  }

  private inGroupOrder(groups: IPickGroup[]): IPickRow[] {
    const rows: IPickRow[] = [];
    for (const group of groups) {
      for (const entry of group.messages) {
        rows.push(entry.row);
        if (entry.open) {
          entry.snippets.forEach((row) => rows.push(row));
        }
      }
    }
    return rows;
  }

  private matches(term: string, values: string[]): boolean {
    return values.some((value) => !!value && value.toLowerCase().indexOf(term) !== -1);
  }

  private messageRow(message: IAvailableMessage): IPickRow {
    const count = (message.snippets || []).length;
    const size = count ? count + (count === 1 ? ' snippet' : ' snippets') : 'No snippet';
    return {
      key: 'message:' + message.id,
      type: 'message',
      message,
      label: message.name,
      detail: message.description ? message.description + ' · ' + size : 'Whole message · ' + size,
      current: message.id === this.data.selectedMessageId && !this.data.selectedSnippetId,
    };
  }

  private snippetRow(message: IAvailableMessage, snippet: IExampleMessageSnippet): IPickRow {
    const count = (snippet.messageReferences || []).length;
    return {
      key: 'snippet:' + message.id + ':' + snippet.id,
      type: 'snippet',
      message,
      snippet,
      label: snippet.name,
      detail: snippet.description || (count + (count === 1 ? ' element' : ' elements') + ' of the message'),
      current: message.id === this.data.selectedMessageId && snippet.id === this.data.selectedSnippetId,
    };
  }

  private loadSnippet(row: IPickRow): IPreview {
    if (!this.data.igId) {
      return { text: '', loading: false, note: 'The text of a snippet is shown once it is in the guide.' };
    }
    const done = (preview: IPreview) => {
      this.snippetText[row.key] = preview;
      if (this.isSelected(row)) {
        this.preview = preview;
      }
    };
    this.rendering = this.exampleMessagesService.renderSnippet(this.data.igId, row.message.id, row.snippet.id).subscribe(
      (result) => {
        const text = result && !result.snippetNotFound ? (result.renderedContent || '') : '';
        done({
          text,
          loading: false,
          note: text ? '' : 'This snippet selects nothing in the message as it is now.',
        });
      },
      () => done({ text: '', loading: false, note: 'The text of this snippet could not be loaded.' }),
    );
    return { text: '', loading: true, note: '' };
  }

  private stopRendering() {
    if (this.rendering) {
      this.rendering.unsubscribe();
      this.rendering = null;
    }
  }

  private reveal(index: number) {
    setTimeout(() => {
      const host: HTMLElement = this.list && this.list.nativeElement;
      const element = host && host.querySelector('#pick-row-' + index);
      if (element && (element as any).scrollIntoView) {
        (element as any).scrollIntoView({ block: 'nearest' });
      }
    });
  }
}
