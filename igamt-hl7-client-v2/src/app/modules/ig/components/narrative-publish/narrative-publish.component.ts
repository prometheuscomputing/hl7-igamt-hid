import { Component, OnDestroy, OnInit } from '@angular/core';
import { contrastOnWhite, publicWebDefaults } from '../../../export-configuration/models/public-web-export-configuration';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Actions } from '@ngrx/effects';
import { Action, Store } from '@ngrx/store';
import { Observable, Subscription, of } from 'rxjs';
import { map, filter, switchMap, catchError } from 'rxjs/operators';
import { AbstractEditorComponent } from '../../../core/components/abstract-editor-component/abstract-editor-component.component';
import * as fromDAM from '../../../dam-framework/store/index';
import { Type } from '../../../shared/constants/type.enum';
import { IDisplayElement } from '../../../shared/models/display-element.interface';
import { EditorID } from '../../../shared/models/editor.enum';
import { IgDocument } from '../../models/ig/ig-document.class';
import { NarrativeCardMapperService } from '../../services/narrative-card-mapper.service';
import { IgService } from '../../services/ig.service';
import * as fromIgEdit from '../../../../root-store/ig/ig-edit/ig-edit.index';
import { INarrativeCard, INarrativePublishModel } from './narrative-card.model';

@Component({
  selector: 'app-narrative-publish',
  templateUrl: './narrative-publish.component.html',
  styleUrls: ['./narrative-publish.component.scss'],
})
export class NarrativePublishComponent extends AbstractEditorComponent implements OnInit, OnDestroy {
  model: INarrativePublishModel = { title: '', cards: [], order: [] };
  cardMap: { [id: string]: INarrativeCard } = {};
  current: INarrativeCard | null = null;
  breadcrumbs: INarrativeCard[] = [];
  childCards: INarrativeCard[] = [];
  prevCard: INarrativeCard | null = null;
  nextCard: INarrativeCard | null = null;
  safeNarrative: SafeHtml = '';
  tocRoots: INarrativeCard[] = [];
  expanded: { [id: string]: boolean } = {};
  copyFlash = false;
  loading = true;
  error = '';
  tocQuery = '';
  private visibleCardIds: { [id: string]: boolean } = {};
  branding = publicWebDefaults();
  brandStyles: { [key: string]: string } = {};
  private focusTimer: any;
  private sub = new Subscription();

  constructor(
    store: Store<any>,
    actions$: Actions,
    private igService: IgService,
    private mapper: NarrativeCardMapperService,
    private sanitizer: DomSanitizer,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    super(
      {
        id: EditorID.NARRATIVE_PUBLISH,
        title: 'Public web IG',
        resourceType: Type.IGDOCUMENT,
      },
      actions$,
      store,
    );
  }

  ngOnInit() {
    this.sub.add(
      this.store.select(fromIgEdit.selectIgDocument).pipe(
        filter((ig: IgDocument) => !!ig),
        switchMap((ig: IgDocument) => {
          this.loading = true;
          this.error = '';
          return this.igService.previewNarrativeCards(ig.id).pipe(catchError(() => {
            this.error = 'The publication preview could not be loaded. Try reopening it.';
            return of({ title: '', cards: [], order: [] } as INarrativePublishModel);
          }));
        }),
      ).subscribe((model: INarrativePublishModel) => {
        this.loading = false;
        this.model = model;
        this.branding = { ...publicWebDefaults(), ...(model.branding || {}) };
        this.brandStyles = {
          '--np-primary': this.branding.primaryColor,
          '--np-primary-ink': contrastOnWhite(this.branding.primaryColor) >= 4.5 ? this.branding.primaryColor : '#021a4c',
          '--np-accent': this.branding.accentColor,
          '--np-link': this.branding.linkColor,
          '--np-accent-ink': this.contrastText(this.branding.accentColor),
        };
        this.cardMap = this.mapper.byId(model);
        this.tocRoots = model.cards.filter((c) => !c.parentId);
        this.filterToc();
        model.cards.forEach((c) => {
          if (this.expanded[c.id] === undefined) { this.expanded[c.id] = true; }
        });
        const q = this.route.snapshot.queryParamMap.get('section');
        const startId = (q && this.cardMap[q]) ? q : (model.order[0] || null);
        if (startId) { this.selectCard(startId, false); }
      }),
    );

    this.sub.add(
      this.route.queryParamMap.subscribe((params) => {
        const id = params.get('section');
        if (id && this.cardMap[id]) {
          this.selectCard(id, false);
        }
      }),
    );
    this.sub.add(this.route.fragment.subscribe(() => this.focusDestination()));
  }

  private focusDestination() {
    clearTimeout(this.focusTimer);
    this.focusTimer = setTimeout(() => {
      const fragment = this.route.snapshot.fragment;
      const target = document.getElementById(fragment || 'np-card-title');
      if (target) { target.focus(); if (fragment) { target.scrollIntoView(); } }
    });
  }

  selectCard(id: string, updateUrl: boolean = true) {
    const card = this.cardMap[id];
    if (!card) {
      return;
    }
    this.current = card;
    this.safeNarrative = this.sanitizer.bypassSecurityTrustHtml(card.narrativeHtml || '');
    this.childCards = (card.childIds || []).map((cid) => this.cardMap[cid]).filter((c) => !!c);
    this.breadcrumbs = this.buildBreadcrumbs(card);
    const idx = this.model.order.indexOf(card.id);
    this.prevCard = idx > 0 ? this.cardMap[this.model.order[idx - 1]] : null;
    this.nextCard = idx >= 0 && idx < this.model.order.length - 1 ? this.cardMap[this.model.order[idx + 1]] : null;
    // expand ancestors in TOC
    let p = card.parentId;
    while (p) {
      this.expanded[p] = true;
      p = this.cardMap[p] ? this.cardMap[p].parentId : null;
    }
    this.focusDestination();
    if (updateUrl) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { section: card.id },
        queryParamsHandling: 'merge',
        fragment: null,
        replaceUrl: false,
      });
    }
  }

  private buildBreadcrumbs(card: INarrativeCard): INarrativeCard[] {
    const chain: INarrativeCard[] = [];
    let cur: INarrativeCard | null = card;
    while (cur) {
      chain.unshift(cur);
      cur = cur.parentId ? this.cardMap[cur.parentId] : null;
    }
    return chain;
  }

  private contrastText(color: string): string {
    const hex = /^#[0-9a-f]{6}$/i.test(color || '') ? color : '#F3C60D';
    const channels = [1, 3, 5].map((i) => {
      const value = parseInt(hex.slice(i, i + 2), 16) / 255;
      return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    });
    const luminance = .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
    return luminance > .179 ? '#000000' : '#ffffff';
  }

  filterToc() {
    this.visibleCardIds = {};
    const query = (this.tocQuery || '').trim().toLowerCase();
    if (!query) { return; }
    this.model.cards.forEach((card) => {
      if ((card.number + ' ' + card.title).toLowerCase().indexOf(query) < 0) { return; }
      let item: INarrativeCard = card;
      const seen = new Set<string>();
      while (item && !seen.has(item.id)) {
        seen.add(item.id);
        this.visibleCardIds[item.id] = true;
        item = item.parentId ? this.cardMap[item.parentId] : null;
      }
    });
  }

  isTocVisible(id: string): boolean {
    return !(this.tocQuery || '').trim() || !!this.visibleCardIds[id];
  }

  get hasTocResults(): boolean {
    return !(this.tocQuery || '').trim() || Object.keys(this.visibleCardIds).length > 0;
  }

  toggle(id: string, event: Event) {
    event.stopPropagation();
    this.expanded[id] = !this.expanded[id];
  }

  childrenOf(id: string): INarrativeCard[] {
    const card = this.cardMap[id];
    if (!card) {
      return [];
    }
    return (card.childIds || []).map((cid) => this.cardMap[cid]).filter((c) => !!c);
  }

  copyLink() {
    if (!this.current) {
      return;
    }
    const url = window.location.href;
    const clipboard = (navigator as any).clipboard;
    if (clipboard && clipboard.writeText) {
      clipboard.writeText(url).then(() => {
        this.copyFlash = true;
        setTimeout(() => this.copyFlash = false, 1500);
      }).catch(() => { this.error = 'Copy failed. Copy the link from the address bar.'; });
    } else {
      this.error = 'Copy the link from the address bar.';
    }
  }

  goPrev() {
    if (this.prevCard) {
      this.selectCard(this.prevCard.id);
    }
  }

  goNext() {
    if (this.nextCard) {
      this.selectCard(this.nextCard.id);
    }
  }

  onEditorSave(action: fromDAM.EditorSave): Observable<Action> {
    return of();
  }

  editorDisplayNode(): Observable<IDisplayElement> {
    return this.document$.pipe(
      map((document) => this.igService.igToIDisplayElement(document as IgDocument)),
    );
  }

  onDeactivate(): void {
  }

  ngOnDestroy() {
    clearTimeout(this.focusTimer);
    this.sub.unsubscribe();
  }
}
