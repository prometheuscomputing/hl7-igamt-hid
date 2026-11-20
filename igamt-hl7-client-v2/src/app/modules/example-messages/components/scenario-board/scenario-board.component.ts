import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { Subscription } from 'rxjs';
import { LoadPayloadData } from 'src/app/modules/dam-framework/store';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { MessageService } from '../../../dam-framework/services/message.service';
import { getLabel } from '../../../shared/components/display-section/display-section.component';
import { IDisplayElement } from '../../../shared/models/display-element.interface';
import {
  actorCapabilityLabel,
  actorInitials,
  IExampleActor,
  IExampleMessage,
  IExampleMessageSnippet,
  IExampleScenario,
  IProfileExampleMessages,
  IScenarioStep,
  IgExampleMessages,
} from '../../domain/example-messages.model';
import { ExampleMessagesService, ISnippetRenderResult } from '../../services/example-messages.service';

type Side = 'left' | 'right';
type Direction = 'ltr' | 'rtl';

interface IComposer {
  index: number;
  editingId: string;
  direction: Direction;
  profileId: string;
  exampleIds: string[];
  pinnedIds: string[];
  search: string;
  title: string;
  description: string;
  trigger: string;
}

export interface IPreviewRow {
  kind: 'line' | 'gap';
  n: number;
  segment?: string;
  before?: string;
  mark?: string;
  after?: string;
  pinned?: boolean;
  pinLabel?: string;
  hidden?: number;
}

interface IPinMark {
  line: number;
  label: string;
  startColumn: number;
  endColumn: number;
  singleLine: boolean;
}

const COLLAPSED_LINES = 4;
const VIEW_STORAGE_KEY = 'igamt.scenario-board.view';
const ZOOM_STORAGE_KEY = 'igamt.scenario-board.zoom';
const MIN_ZOOM = 0.4;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

function clampZoom(value: number): number {
  if (!isFinite(value)) {
    return 1;
  }
  return Math.round(Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value)) * 100) / 100;
}

function readStoredZoom(): number {
  try {
    const stored = parseFloat(localStorage.getItem(ZOOM_STORAGE_KEY));
    return isNaN(stored) ? 1 : clampZoom(stored);
  } catch (e) {
    return 1;
  }
}

export type BoardView = 'compact' | 'detailed';

@Component({
  selector: 'app-scenario-board',
  templateUrl: './scenario-board.component.html',
  styleUrls: ['./scenario-board.component.scss'],
})
export class ScenarioBoardComponent implements OnInit, OnDestroy {
  doc: IgExampleMessages;
  scenario: IExampleScenario;
  composer: IComposer = null;
  actorPicker: Side = null;
  actorSearch = '';
  saving = false;
  notice = '';
  view: BoardView = localStorage.getItem(VIEW_STORAGE_KEY) === 'detailed' ? 'detailed' : 'compact';
  /** Board scale. Uses CSS zoom so layout, scrolling and clicks follow the smaller size. */
  zoom = readStoredZoom();
  readonly minZoom = MIN_ZOOM;
  readonly maxZoom = MAX_ZOOM;
  @ViewChild('stage') stageRef: ElementRef;
  shown: { [key: string]: boolean } = {};
  expanded: { [key: string]: boolean } = {};
  renders: { [key: string]: ISnippetRenderResult } = {};
  private requested: { [key: string]: boolean } = {};
  private sub = new Subscription();

  constructor(
    private store: Store<any>,
    private route: ActivatedRoute,
    private exampleMessagesService: ExampleMessagesService,
    private messageService: MessageService,
  ) { }

  ngOnInit() {
    this.sub.add(this.store.select(selectIgExampleMessages).subscribe((doc) => {
      this.doc = doc;
      this.scenario = this.readScenario(doc);
      this.loadPinnedRenders();
    }));
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.actorPicker = null;
    this.composer = null;
  }

  get viewOnly(): boolean {
    return !!(this.doc && this.doc.viewOnly);
  }

  get actors(): IExampleActor[] {
    return (this.doc && this.doc.actors) || [];
  }

  get profiles(): IProfileExampleMessages[] {
    return (this.doc && this.doc.profileExampleMessages) || [];
  }

  get left(): IExampleActor {
    return this.scenario ? this.actor(this.scenario.leftActorId) : null;
  }

  get right(): IExampleActor {
    return this.scenario ? this.actor(this.scenario.rightActorId) : null;
  }

  get lifelinesReady(): boolean {
    return !!(this.left && this.right && this.left.id !== this.right.id);
  }

  actor(id: string): IExampleActor {
    return id ? this.actors.find((item) => item.id === id) : null;
  }

  sideActor(side: Side): IExampleActor {
    return side === 'left' ? this.left : this.right;
  }

  initials(actor: IExampleActor): string {
    return actorInitials(actor && actor.name);
  }

  capabilityLabel(actor: IExampleActor): string {
    return actor ? actorCapabilityLabel(actor.capability) : '';
  }

  actorDetail(actor: IExampleActor): string {
    if (!actor) {
      return '';
    }
    return [actor.organization, actor.system].filter((part) => !!part).join(' · ');
  }

  directionOf(step: IScenarioStep): Direction {
    return step.fromActorId === this.scenario.leftActorId ? 'ltr' : 'rtl';
  }

  profileGroup(profileId: string): IProfileExampleMessages {
    return this.profiles.find((group) => group.profile && group.profile.id === profileId);
  }

  profileLabel(group: IProfileExampleMessages): string {
    const profile = group && group.profile;
    return (profile && getLabel(profile.fixedName, profile.variableName)) || 'Profile';
  }

  profileTitle(profileId: string): string {
    return this.profileLabel(this.profileGroup(profileId));
  }

  profileOf(profileId: string): IDisplayElement {
    const group = this.profileGroup(profileId);
    return group ? group.profile : null;
  }

  exampleOf(id: string): IExampleMessage {
    for (const group of this.profiles) {
      const found = (group.exampleMessages || []).find((message) => message.id === id);
      if (found) {
        return found;
      }
    }
    return null;
  }

  examplesOf(profileId: string): IExampleMessage[] {
    const group = this.profileGroup(profileId);
    return (group && group.exampleMessages) || [];
  }

  stepExamples(step: IScenarioStep): IExampleMessage[] {
    return (step.exampleMessageIds || []).map((id) => this.exampleOf(id)).filter((example) => !!example);
  }

  snippetCount(example: IExampleMessage): number {
    return (example && example.snippets && example.snippets.length) || 0;
  }

  segmentCount(example: IExampleMessage): number {
    return this.lines(example).length;
  }

  plainText(html: string): string {
    if (!html) {
      return '';
    }
    // Tags become spaces so words from separate paragraphs stay apart; the parser then turns
    // character references such as &gt; or &amp; back into the characters they stand for.
    // DOMParser builds an inert document, so nothing in the description runs.
    const withoutTags = html.replace(/<[^>]*>/g, ' ');
    const text = new DOMParser().parseFromString(withoutTags, 'text/html').documentElement.textContent || '';
    return text.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
  }

  isPinned(step: IScenarioStep, snippetId: string): boolean {
    return (step.pinnedSnippetIds || []).indexOf(snippetId) >= 0;
  }

  pinnedOf(step: IScenarioStep, example: IExampleMessage): IExampleMessageSnippet[] {
    return (example.snippets || []).filter((snippet) => this.isPinned(step, snippet.id));
  }

  unpinnedOf(step: IScenarioStep, example: IExampleMessage): IExampleMessageSnippet[] {
    return (example.snippets || []).filter((snippet) => !this.isPinned(step, snippet.id));
  }

  render(exampleId: string, snippetId: string): ISnippetRenderResult {
    return this.renders[exampleId + '|' + snippetId];
  }

  previewKey(step: IScenarioStep, example: IExampleMessage): string {
    return step.id + '|' + example.id;
  }

  setView(view: BoardView) {
    this.view = view;
    localStorage.setItem(VIEW_STORAGE_KEY, view);
  }

  setZoom(value: number) {
    this.zoom = clampZoom(value);
    try {
      localStorage.setItem(ZOOM_STORAGE_KEY, String(this.zoom));
    } catch (e) { }
  }

  zoomIn() {
    this.setZoom(this.zoom + ZOOM_STEP);
  }

  zoomOut() {
    this.setZoom(this.zoom - ZOOM_STEP);
  }

  zoomReset() {
    this.setZoom(1);
  }

  /** Scale the board so the whole exchange, down to the last step, fits in the window. */
  zoomFit() {
    const stage: HTMLElement = this.stageRef && this.stageRef.nativeElement;
    if (!stage) {
      return;
    }
    const rect = stage.getBoundingClientRect();
    const naturalHeight = rect.height / (this.zoom || 1);
    const available = window.innerHeight - Math.max(rect.top, 0) - 16;
    if (naturalHeight <= 0 || available <= 0) {
      return;
    }
    this.setZoom(Math.min(1, Math.floor((available / naturalHeight) * 20) / 20));
  }

  zoomLabel(): string {
    return Math.round(this.zoom * 100) + '%';
  }

  /** Ctrl or Cmd with the mouse wheel zooms the board, like a map. */
  onStageWheel(event: WheelEvent) {
    if (!(event.ctrlKey || event.metaKey)) {
      return;
    }
    event.preventDefault();
    this.setZoom(this.zoom + (event.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP));
  }

  stepName(step: IScenarioStep): string {
    return step.title || this.profileTitle(step.profileId);
  }

  isShown(step: IScenarioStep, example: IExampleMessage): boolean {
    return !!this.shown[this.previewKey(step, example)];
  }

  toggleShown(step: IScenarioStep, example: IExampleMessage) {
    const key = this.previewKey(step, example);
    this.shown[key] = !this.shown[key];
  }

  isWide(step: IScenarioStep): boolean {
    const examples = this.stepExamples(step);
    return examples.length > 0 && (this.view === 'detailed' || examples.some((example) => this.isShown(step, example)));
  }

  toggleExpanded(step: IScenarioStep, example: IExampleMessage) {
    const key = this.previewKey(step, example);
    this.expanded[key] = !this.expanded[key];
  }

  previewRows(step: IScenarioStep, example: IExampleMessage): IPreviewRow[] {
    const lines = this.lines(example);
    const marks = this.pinMarks(step, example);
    const expanded = !!this.expanded[this.previewKey(step, example)];
    const visible = lines.map((_, index) => expanded || index < COLLAPSED_LINES || !!marks[index + 1]);
    const rows: IPreviewRow[] = [];
    let hidden = 0;
    lines.forEach((text, index) => {
      if (!visible[index]) {
        hidden++;
        return;
      }
      if (hidden) {
        rows.push({ kind: 'gap', n: -index, hidden });
        hidden = 0;
      }
      rows.push(this.lineRow(index + 1, text, marks[index + 1]));
    });
    if (hidden) {
      rows.push({ kind: 'gap', n: -lines.length - 1, hidden });
    }
    return rows;
  }

  hiddenLines(step: IScenarioStep, example: IExampleMessage): number {
    return this.previewRows(step, example)
      .filter((row) => row.kind === 'gap')
      .reduce((total, row) => total + row.hidden, 0);
  }

  trackRow(index: number, row: IPreviewRow): number {
    return row.n;
  }

  togglePin(step: IScenarioStep, snippetId: string, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (this.viewOnly) {
      return;
    }
    const pinned = this.isPinned(step, snippetId)
      ? (step.pinnedSnippetIds || []).filter((id) => id !== snippetId)
      : (step.pinnedSnippetIds || []).concat(snippetId);
    this.persist({
      ...this.scenario,
      steps: this.scenario.steps.map((item) => item.id === step.id ? { ...item, pinnedSnippetIds: pinned } : item),
    });
  }

  toggleActorPicker(side: Side) {
    if (this.viewOnly) {
      return;
    }
    this.actorSearch = '';
    this.actorPicker = this.actorPicker === side ? null : side;
  }

  filteredActors(): IExampleActor[] {
    const query = (this.actorSearch || '').trim().toLowerCase();
    if (!query) {
      return this.actors;
    }
    return this.actors.filter((actor) => [actor.name, actor.organization, actor.system]
      .some((value) => (value || '').toLowerCase().indexOf(query) >= 0));
  }

  chooseActor(side: Side, actor: IExampleActor) {
    const current = this.sideActor(side);
    const other = this.sideActor(side === 'left' ? 'right' : 'left');
    this.actorPicker = null;
    if (current && current.id === actor.id) {
      return;
    }
    if (other && other.id === actor.id) {
      this.swapSides();
      return;
    }
    const oldId = current ? current.id : null;
    const steps = (this.scenario.steps || []).map((step) => ({
      ...step,
      fromActorId: step.fromActorId === oldId ? actor.id : step.fromActorId,
      toActorId: step.toActorId === oldId ? actor.id : step.toActorId,
    }));
    this.persist({
      ...this.scenario,
      leftActorId: side === 'left' ? actor.id : this.scenario.leftActorId,
      rightActorId: side === 'right' ? actor.id : this.scenario.rightActorId,
      steps,
    });
  }

  swapSides() {
    if (!this.scenario || this.viewOnly) {
      return;
    }
    this.persist({
      ...this.scenario,
      leftActorId: this.scenario.rightActorId,
      rightActorId: this.scenario.leftActorId,
    });
  }

  directionAllowed(direction: Direction): boolean {
    if (!this.lifelinesReady) {
      return false;
    }
    const from = direction === 'ltr' ? this.left : this.right;
    const to = direction === 'ltr' ? this.right : this.left;
    return from.capability !== 'RECEIVER' && to.capability !== 'SENDER';
  }

  directionBlockedReason(direction: Direction): string {
    if (!this.lifelinesReady || this.directionAllowed(direction)) {
      return '';
    }
    const from = direction === 'ltr' ? this.left : this.right;
    const to = direction === 'ltr' ? this.right : this.left;
    if (from.capability === 'RECEIVER') {
      return from.name + ' only receives.';
    }
    return to.name + ' only sends.';
  }

  openComposer(index: number) {
    if (this.viewOnly || !this.lifelinesReady) {
      return;
    }
    const previous = index > 0 ? this.scenario.steps[index - 1] : null;
    let direction: Direction = previous && this.directionOf(previous) === 'ltr' ? 'rtl' : 'ltr';
    if (!this.directionAllowed(direction)) {
      direction = direction === 'ltr' ? 'rtl' : 'ltr';
    }
    this.notice = '';
    this.composer = {
      index,
      editingId: null,
      direction,
      profileId: '',
      exampleIds: [],
      pinnedIds: [],
      search: '',
      title: '',
      description: '',
      trigger: '',
    };
  }

  editStep(step: IScenarioStep, index: number) {
    if (this.viewOnly) {
      return;
    }
    this.composer = {
      index,
      editingId: step.id,
      direction: this.directionOf(step),
      profileId: step.profileId,
      exampleIds: (step.exampleMessageIds || []).slice(),
      pinnedIds: (step.pinnedSnippetIds || []).slice(),
      search: '',
      title: step.title || '',
      description: step.description || '',
      trigger: step.trigger || '',
    };
  }

  closeComposer() {
    this.composer = null;
  }

  setDirection(direction: Direction) {
    if (this.composer && this.directionAllowed(direction)) {
      this.composer.direction = direction;
    }
  }

  filteredProfiles(): IProfileExampleMessages[] {
    const query = (this.composer && this.composer.search || '').trim().toLowerCase();
    return this.profiles.filter((group) => !!group.profile)
      .filter((group) => !query || this.profileLabel(group).toLowerCase().indexOf(query) >= 0);
  }

  chooseProfile(profileId: string) {
    if (!this.composer) {
      return;
    }
    if (this.composer.profileId !== profileId) {
      this.composer.exampleIds = [];
      this.composer.pinnedIds = [];
    }
    this.composer.profileId = profileId;
  }

  isPicked(id: string): boolean {
    return !!this.composer && this.composer.exampleIds.indexOf(id) >= 0;
  }

  toggleExample(id: string) {
    if (!this.composer) {
      return;
    }
    if (this.isPicked(id)) {
      const example = this.exampleOf(id);
      const dropped = ((example && example.snippets) || []).map((snippet) => snippet.id);
      this.composer.exampleIds = this.composer.exampleIds.filter((item) => item !== id);
      this.composer.pinnedIds = this.composer.pinnedIds.filter((item) => dropped.indexOf(item) < 0);
    } else {
      this.composer.exampleIds = this.composer.exampleIds.concat(id);
    }
  }

  isComposerPinned(snippetId: string): boolean {
    return !!this.composer && this.composer.pinnedIds.indexOf(snippetId) >= 0;
  }

  toggleComposerPin(snippetId: string, event: Event) {
    event.stopPropagation();
    if (!this.composer) {
      return;
    }
    this.composer.pinnedIds = this.isComposerPinned(snippetId)
      ? this.composer.pinnedIds.filter((item) => item !== snippetId)
      : this.composer.pinnedIds.concat(snippetId);
  }

  canSaveComposer(): boolean {
    return !!this.composer && !!this.composer.profileId && this.directionAllowed(this.composer.direction);
  }

  saveComposer() {
    if (!this.canSaveComposer()) {
      return;
    }
    const composer = this.composer;
    const from = composer.direction === 'ltr' ? this.left.id : this.right.id;
    const to = composer.direction === 'ltr' ? this.right.id : this.left.id;
    const step: IScenarioStep = {
      id: composer.editingId || ('line-' + Date.now().toString(16)),
      fromActorId: from,
      toActorId: to,
      profileId: composer.profileId,
      exampleMessageIds: composer.exampleIds.slice(),
      pinnedSnippetIds: composer.pinnedIds.slice(),
      title: (composer.title || '').trim(),
      description: (composer.description || '').trim(),
      trigger: (composer.trigger || '').trim(),
    };
    const steps = (this.scenario.steps || []).slice();
    if (composer.editingId) {
      steps[composer.index] = step;
    } else {
      steps.splice(composer.index, 0, step);
    }
    this.composer = null;
    this.persist({ ...this.scenario, steps });
  }

  moveStep(index: number, offset: number) {
    const target = index + offset;
    const steps = (this.scenario.steps || []).slice();
    if (target < 0 || target >= steps.length) {
      return;
    }
    const moving = steps[index];
    steps[index] = steps[target];
    steps[target] = moving;
    this.persist({ ...this.scenario, steps });
  }

  removeStep(step: IScenarioStep) {
    this.persist({
      ...this.scenario,
      steps: this.scenario.steps.filter((item) => item.id !== step.id),
    });
  }

  private lines(example: IExampleMessage): string[] {
    return ((example && example.message) || '').split(/\r\n|\r|\n/).filter((line) => line.trim().length > 0);
  }

  private pinMarks(step: IScenarioStep, example: IExampleMessage): { [line: number]: IPinMark } {
    const marks: { [line: number]: IPinMark } = {};
    for (const snippet of this.pinnedOf(step, example)) {
      const rendered = this.render(example.id, snippet.id);
      for (const part of (rendered && rendered.parts) || []) {
        for (let line = part.startLine; line <= part.endLine; line++) {
          if (!marks[line]) {
            marks[line] = {
              line,
              label: snippet.name,
              startColumn: part.highlightStart ? part.highlightStart.column : 1,
              endColumn: part.highlightEnd ? part.highlightEnd.column : 0,
              singleLine: part.startLine === part.endLine,
            };
          }
        }
      }
    }
    return marks;
  }

  private lineRow(n: number, text: string, mark: IPinMark): IPreviewRow {
    const bar = text.indexOf('|');
    const segment = bar > 0 ? text.substring(0, bar) : text;
    const rest = bar > 0 ? text.substring(bar) : '';
    if (!mark) {
      return { kind: 'line', n, segment, before: rest, mark: '', after: '' };
    }
    if (!mark.singleLine || mark.startColumn <= 1 && (mark.endColumn <= 0 || mark.endColumn > text.length)) {
      return { kind: 'line', n, segment, before: rest, mark: '', after: '', pinned: true, pinLabel: mark.label };
    }
    const start = Math.max(mark.startColumn - 1, segment.length);
    const end = Math.min(mark.endColumn > 0 ? mark.endColumn - 1 : text.length, text.length);
    return {
      kind: 'line',
      n,
      segment,
      before: text.substring(segment.length, start),
      mark: text.substring(start, end),
      after: text.substring(end),
      pinned: true,
      pinLabel: mark.label,
    };
  }

  private loadPinnedRenders() {
    if (!this.doc || !this.scenario) {
      return;
    }
    for (const step of this.scenario.steps || []) {
      for (const example of this.stepExamples(step)) {
        for (const snippet of this.pinnedOf(step, example)) {
          const key = example.id + '|' + snippet.id;
          if (this.requested[key]) {
            continue;
          }
          this.requested[key] = true;
          this.exampleMessagesService.renderSnippet(this.doc.id, example.id, snippet.id).subscribe(
            (result) => this.renders = { ...this.renders, [key]: result },
            () => this.requested[key] = false,
          );
        }
      }
    }
  }

  private persist(scenario: IExampleScenario) {
    const scenarios = (this.doc.scenarios || []).map((item) => item.id === scenario.id ? scenario : item);
    this.scenario = scenario;
    this.loadPinnedRenders();
    this.saving = true;
    this.exampleMessagesService.saveExchange(this.doc.id, {
      actors: this.doc.actors || [],
      scenarios,
    }).subscribe((message) => {
      this.saving = false;
      if (message && message.data) {
        this.store.dispatch(new LoadPayloadData(message.data));
      }
    }, (err) => {
      this.saving = false;
      this.scenario = this.readScenario(this.doc);
      this.store.dispatch(this.messageService.actionFromError(err));
    });
  }

  private readScenario(doc: IgExampleMessages): IExampleScenario {
    const id = this.route.snapshot.paramMap.get('scenarioId');
    const found = ((doc && doc.scenarios) || []).find((item) => item.id === id);
    if (!found) {
      return null;
    }
    return {
      ...found,
      steps: (found.steps || []).map((step) => ({
        ...step,
        exampleMessageIds: (step.exampleMessageIds || []).slice(),
        pinnedSnippetIds: (step.pinnedSnippetIds || []).slice(),
      })),
    };
  }
}
