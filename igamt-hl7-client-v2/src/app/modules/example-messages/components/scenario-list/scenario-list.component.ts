import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { Subscription } from 'rxjs';
import { LoadPayloadData } from 'src/app/modules/dam-framework/store';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { MessageService } from '../../../dam-framework/services/message.service';
import { actorInitials, IExampleActor, IExampleScenario, IgExampleMessages } from '../../domain/example-messages.model';
import { ExampleMessagesService } from '../../services/example-messages.service';

@Component({
  selector: 'app-scenario-list',
  templateUrl: './scenario-list.component.html',
  styleUrls: ['./scenario-list.component.scss'],
})
export class ScenarioListComponent implements OnInit, OnDestroy {
  doc: IgExampleMessages;
  creating = false;
  name = '';
  summary = '';
  saving = false;
  confirmRemove: string = null;
  private sub = new Subscription();

  constructor(
    private store: Store<any>,
    private route: ActivatedRoute,
    private router: Router,
    private exampleMessagesService: ExampleMessagesService,
    private messageService: MessageService,
  ) { }

  ngOnInit() {
    this.sub.add(this.store.select(selectIgExampleMessages).subscribe((doc) => {
      this.doc = doc;
    }));
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.creating = false;
    this.confirmRemove = null;
  }

  get viewOnly(): boolean {
    return !!(this.doc && this.doc.viewOnly);
  }

  get scenarios(): IExampleScenario[] {
    return (this.doc && this.doc.scenarios) || [];
  }

  actor(id: string): IExampleActor {
    return id ? ((this.doc && this.doc.actors) || []).find((item) => item.id === id) : null;
  }

  initials(actor: IExampleActor): string {
    return actorInitials(actor && actor.name);
  }

  nameTaken(): boolean {
    const name = (this.name || '').trim().toLowerCase();
    return !!name && this.scenarios.some((scenario) => (scenario.name || '').trim().toLowerCase() === name);
  }

  startCreate() {
    this.creating = true;
    this.name = '';
    this.summary = '';
  }

  create() {
    const name = (this.name || '').trim();
    if (!name || this.nameTaken() || !this.doc || this.viewOnly) {
      return;
    }
    const scenario: IExampleScenario = {
      id: 'scenario-' + Date.now().toString(16),
      name,
      summary: (this.summary || '').trim(),
      leftActorId: '',
      rightActorId: '',
      steps: [],
    };
    this.persist(this.scenarios.concat(scenario), () => {
      this.creating = false;
      this.router.navigate(['../scenario', scenario.id], { relativeTo: this.route });
    });
  }

  open(scenario: IExampleScenario) {
    this.router.navigate(['../scenario', scenario.id], { relativeTo: this.route });
  }

  askRemove(scenario: IExampleScenario, event: Event) {
    event.stopPropagation();
    this.confirmRemove = scenario.id;
  }

  remove(scenario: IExampleScenario, event: Event) {
    event.stopPropagation();
    this.confirmRemove = null;
    this.persist(this.scenarios.filter((item) => item.id !== scenario.id));
  }

  private persist(scenarios: IExampleScenario[], after?: () => void) {
    this.saving = true;
    this.exampleMessagesService.saveExchange(this.doc.id, {
      actors: this.doc.actors || [],
      scenarios,
    }).subscribe((message) => {
      this.saving = false;
      if (message && message.data) {
        this.store.dispatch(new LoadPayloadData(message.data));
      }
      if (after) {
        after();
      }
    }, (err) => {
      this.saving = false;
      this.store.dispatch(this.messageService.actionFromError(err));
    });
  }
}
