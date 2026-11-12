import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { Observable, Subscription } from 'rxjs';
import { debounceTime, filter } from 'rxjs/operators';
import { ConformanceVerb, INarrativeRequirement } from '../../../shared/models/content.interface';
import { FroalaService } from '../../../shared/services/froala.service';
import { INarrative } from '../ig-section-editor/ig-section-editor.component';

interface IRequirementCandidate {
  id: string;
  verb: ConformanceVerb;
  text: string;
  selected: boolean;
  /** Normalized full line used to strip from Froala HTML */
  matchText: string;
}

@Component({
  selector: 'app-narrative-section-form',
  templateUrl: './narrative-section-form.component.html',
  styleUrls: ['./narrative-section-form.component.scss'],
})
export class NarrativeSectionFormComponent implements OnInit, OnDestroy {

  private changes: Subscription;
  private loadingData = false;

  sectionForm: FormGroup;
  froalaConfig$: Observable<any>;
  verbs: ConformanceVerb[] = ['SHALL', 'SHOULD', 'MAY'];

  /** Inline identify-review panel (no MatDialog needed). */
  identifyOpen = false;
  identifyCandidates: IRequirementCandidate[] = [];
  identifyStripFromContent = false;
  identifyMessage = '';

  @Output()
  form: EventEmitter<FormGroup>;
  @Input()
  viewOnly: boolean;
  @Input()
  set data(data: INarrative) {
    if (!data) {
      return;
    }
    this.loadingData = true;
    this.cancelIdentify();
    this.sectionForm.patchValue({
      id: data.id,
      label: data.label,
      description: data.description,
    }, { emitEvent: false });
    this.setRequirements(data.requirements || []);
    this.loadingData = false;
  }

  constructor(private froalaService: FroalaService) {
    this.form = new EventEmitter<FormGroup>();
    this.sectionForm = new FormGroup({
      id: new FormControl(''),
      label: new FormControl('', [Validators.required]),
      description: new FormControl(''),
      requirements: new FormArray([]),
    });
  }

  ngOnInit() {
    this.changes = this.sectionForm.valueChanges
      .pipe(filter(() => !this.loadingData), debounceTime(200))
      .subscribe(
        () => {
          this.form.emit(this.sectionForm);
        },
      );
    this.froalaConfig$ = this.froalaService.getConfig(true);
  }

  ngOnDestroy() {
    if (this.changes) {
      this.changes.unsubscribe();
    }
  }

  get requirements(): FormArray {
    return this.sectionForm.get('requirements') as FormArray;
  }

  getDescription() {
    return this.sectionForm.getRawValue()['description'];
  }

  addRequirement() {
    const position = this.requirements.length + 1;
    this.requirements.push(this.createRequirementGroup({
      id: this.newId(),
      position,
      verb: 'SHALL',
      text: '',
    }));
    this.form.emit(this.sectionForm);
  }

  removeRequirement(index: number) {
    this.requirements.removeAt(index);
    this.reindexPositions();
    this.form.emit(this.sectionForm);
  }

  moveRequirement(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= this.requirements.length) {
      return;
    }
    const ctrl = this.requirements.at(index);
    this.requirements.removeAt(index);
    this.requirements.insert(target, ctrl);
    this.reindexPositions();
    this.form.emit(this.sectionForm);
  }

  /** Demote one structured requirement back into Froala as a bullet. */
  demoteRequirement(index: number) {
    const row = this.requirements.at(index);
    if (!row) {
      return;
    }
    const verb = (row.get('verb').value || 'SHALL') as ConformanceVerb;
    const text = (row.get('text').value || '').trim();
    if (!text) {
      return;
    }
    const bullet = '<li><strong>' + this.escapeHtml(verb) + '</strong> ' + this.escapeHtml(text) + '</li>';
    let html = this.getDescription() || '';
    if (/<\/ul>\s*$/i.test(html)) {
      html = html.replace(/<\/ul>\s*$/i, bullet + '</ul>');
    } else {
      html = (html || '') + '<ul>' + bullet + '</ul>';
    }
    this.sectionForm.get('description').setValue(html);
    this.requirements.removeAt(index);
    this.reindexPositions();
    this.form.emit(this.sectionForm);
  }

  openIdentify() {
    this.identifyMessage = '';
    this.identifyCandidates = this.scanDescriptionForRequirements(this.getDescription() || '');
    if (!this.identifyCandidates.length) {
      this.identifyMessage = 'No SHALL / SHOULD / MAY lines found in Content (looks at list items and paragraphs).';
      this.identifyOpen = true;
      return;
    }
    this.identifyStripFromContent = false;
    this.identifyOpen = true;
  }

  cancelIdentify() {
    this.identifyOpen = false;
    this.identifyCandidates = [];
    this.identifyMessage = '';
  }

  selectAllIdentify(selected: boolean) {
    this.identifyCandidates.forEach((c) => c.selected = selected);
  }

  applyIdentify() {
    const picked = this.identifyCandidates.filter((c) => c.selected && (c.text || '').trim());
    if (!picked.length) {
      this.identifyMessage = 'Pick at least one candidate.';
      return;
    }

    const existing = new Set(
      this.requirements.controls.map((c) => this.normKey(c.get('verb').value, c.get('text').value)),
    );

    let added = 0;
    picked.forEach((c) => {
      const key = this.normKey(c.verb, c.text);
      if (existing.has(key)) {
        return;
      }
      existing.add(key);
      this.requirements.push(this.createRequirementGroup({
        id: this.newId(),
        position: this.requirements.length + 1,
        verb: c.verb,
        text: c.text.trim(),
      }));
      added++;
    });

    if (this.identifyStripFromContent && picked.length) {
      const matchSet = new Set(picked.map((c) => c.matchText));
      const cleaned = this.stripMatchedBlocks(this.getDescription() || '', matchSet);
      this.sectionForm.get('description').setValue(cleaned);
    }

    this.reindexPositions();
    this.form.emit(this.sectionForm);
    this.identifyOpen = false;
    this.identifyCandidates = [];
    this.identifyMessage = added
      ? ('Added ' + added + ' requirement' + (added === 1 ? '' : 's') + '.')
      : 'Nothing new to add (already in the table).';
  }

  /**
   * Scan Froala HTML for list items / paragraphs that start with SHALL|SHOULD|MAY.
   * Human confirms before anything is written to the requirements table.
   */
  private scanDescriptionForRequirements(html: string): IRequirementCandidate[] {
    if (!html || !html.trim()) {
      return [];
    }
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const nodes = Array.prototype.slice.call(doc.body.querySelectorAll('li, p')) as Element[];
    const verbRe = /^(SHALL|SHOULD|MAY)\b[\s:\-–—]*/i;
    const out: IRequirementCandidate[] = [];
    const seen = new Set<string>();

    nodes.forEach((el) => {
      // Never promote a container wholesale: it may contain a nested list,
      // managed example, table or diagram that plain requirement text cannot preserve.
      if (el.querySelector('li, p, .fr-exmsg, img, table, figure, pre')) {
        return;
      }
      const raw = (el.textContent || '').replace(/\s+/g, ' ').trim();
      if (!raw) {
        return;
      }
      const m = raw.match(verbRe);
      if (!m) {
        return;
      }
      const verb = m[1].toUpperCase() as ConformanceVerb;
      if (this.verbs.indexOf(verb) < 0) {
        return;
      }
      const text = raw.slice(m[0].length).trim();
      if (!text) {
        return;
      }
      const key = this.normKey(verb, text);
      if (seen.has(key)) {
        return;
      }
      // Skip if already in the structured table
      const already = this.requirements.controls.some(
        (c) => this.normKey(c.get('verb').value, c.get('text').value) === key,
      );
      if (already) {
        return;
      }
      seen.add(key);
      out.push({
        id: this.newId(),
        verb,
        text,
        selected: true,
        matchText: raw,
      });
    });
    return out;
  }

  private stripMatchedBlocks(html: string, matchTexts: Set<string>): string {
    const doc = new DOMParser().parseFromString(html || '', 'text/html');
    const nodes = Array.prototype.slice.call(doc.body.querySelectorAll('li, p')) as Element[];
    nodes.forEach((el) => {
      // Match the scan's leaf-only rule, including widgets with empty text.
      if (el.querySelector('li, p, .fr-exmsg, img, table, figure, pre')) {
        return;
      }
      const raw = (el.textContent || '').replace(/\s+/g, ' ').trim();
      if (matchTexts.has(raw)) {
        const parent = el.parentElement;
        el.parentNode.removeChild(el);
        // Drop empty ul/ol left behind
        if (parent && /^(UL|OL)$/i.test(parent.tagName) && !(parent.textContent || '').trim() &&
            !parent.querySelector('.fr-exmsg, img, table, figure, pre')) {
          parent.parentNode && parent.parentNode.removeChild(parent);
        }
      }
    });
    return doc.body.innerHTML;
  }

  private normKey(verb: string, text: string): string {
    return (verb || '').toUpperCase() + '|' + (text || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  private escapeHtml(s: string): string {
    return (s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  get narrativeLengthWarning(): boolean {
    const doc = new DOMParser().parseFromString(this.getDescription() || '', 'text/html');
    doc.body.querySelectorAll('.fr-exmsg').forEach((widget) => widget.parentNode.removeChild(widget));
    const paragraphs = Array.prototype.slice.call(doc.body.querySelectorAll('p')) as Element[];
    return paragraphs.filter((p) => (p.textContent || '').trim()).length > 3 ||
      (doc.body.textContent || '').trim().split(/\s+/).length > 300;
  }

  private setRequirements(list: INarrativeRequirement[]) {
    while (this.requirements.length) {
      this.requirements.removeAt(0);
    }
    const sorted = (list || []).filter((r) => !!r).slice().sort((a, b) => (a.position || 0) - (b.position || 0));
    sorted.forEach((r, i) => {
      this.requirements.push(this.createRequirementGroup({
        id: r.id || this.newId(),
        position: r.position != null ? r.position : i + 1,
        verb: r.verb || 'SHALL',
        text: r.text || '',
      }));
    });
  }

  private createRequirementGroup(r: INarrativeRequirement): FormGroup {
    return new FormGroup({
      id: new FormControl(r.id),
      position: new FormControl(r.position),
      verb: new FormControl(r.verb || 'SHALL', [Validators.required]),
      text: new FormControl(r.text || '', [Validators.required, Validators.pattern(/\S/)]),
    });
  }

  private reindexPositions() {
    this.requirements.controls.forEach((c, i) => {
      c.get('position').setValue(i + 1, { emitEvent: false });
    });
  }

  private newId(): string {
    return 'req' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  }
}
