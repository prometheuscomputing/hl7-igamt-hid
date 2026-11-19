import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { Subscription } from 'rxjs';
import { LoadPayloadData } from 'src/app/modules/dam-framework/store';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { MessageService } from '../../../dam-framework/services/message.service';
import {
  ACTOR_ICONS,
  ActorCapability,
  actorInitials,
  IExampleActor,
  IgExampleMessages,
} from '../../domain/example-messages.model';
import { ExampleMessagesService } from '../../services/example-messages.service';

@Component({
  selector: 'app-actors-editor',
  templateUrl: './actors-editor.component.html',
  styleUrls: ['./actors-editor.component.scss'],
})
export class ActorsEditorComponent implements OnInit, OnDestroy {
  doc: IgExampleMessages;
  actors: IExampleActor[] = [];
  saving = false;
  dirty = false;
  iconPickerFor: string = null;
  confirmRemove: string = null;
  nameTouched: { [id: string]: boolean } = {};
  readonly icons = ACTOR_ICONS;
  readonly capabilities: { value: ActorCapability, label: string, icon: string }[] = [
    { value: 'SENDER', label: 'Sends', icon: 'fa-upload' },
    { value: 'BOTH', label: 'Both', icon: 'fa-exchange' },
    { value: 'RECEIVER', label: 'Receives', icon: 'fa-download' },
  ];
  private sub = new Subscription();

  constructor(
    private store: Store<any>,
    private exampleMessagesService: ExampleMessagesService,
    private messageService: MessageService,
  ) { }

  ngOnInit() {
    this.sub.add(this.store.select(selectIgExampleMessages).subscribe((doc) => {
      if (!doc || this.saving) {
        return;
      }
      this.doc = doc;
      this.reset();
    }));
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.iconPickerFor = null;
    this.confirmRemove = null;
  }

  get viewOnly(): boolean {
    return !!(this.doc && this.doc.viewOnly);
  }

  get invalid(): boolean {
    const names = this.actors.map((actor) => (actor.name || '').trim().toLowerCase());
    return names.some((name, index) => !name || names.indexOf(name) !== index);
  }

  nameError(actor: IExampleActor): string {
    const name = (actor.name || '').trim().toLowerCase();
    if (!name) {
      return 'Name is required';
    }
    return this.actors.filter((item) => (item.name || '').trim().toLowerCase() === name).length > 1
      ? 'Name is already used' : '';
  }

  showNameError(actor: IExampleActor): string {
    const error = this.nameError(actor);
    if (error === 'Name is required' && !this.nameTouched[actor.id]) {
      return '';
    }
    return error;
  }

  initials(actor: IExampleActor): string {
    return actorInitials(actor.name);
  }

  usageCount(actor: IExampleActor): number {
    return ((this.doc && this.doc.scenarios) || [])
      .filter((scenario) => scenario.leftActorId === actor.id || scenario.rightActorId === actor.id).length;
  }

  touch() {
    this.dirty = true;
  }

  add() {
    const actor: IExampleActor = {
      id: 'actor-' + Date.now().toString(16),
      name: '',
      system: '',
      organization: '',
      icon: '',
      capability: 'BOTH',
    };
    this.actors = this.actors.concat(actor);
    this.touch();
  }

  toggleIconPicker(actor: IExampleActor) {
    if (this.viewOnly) {
      return;
    }
    this.iconPickerFor = this.iconPickerFor === actor.id ? null : actor.id;
  }

  setIcon(actor: IExampleActor, icon: string) {
    actor.icon = icon;
    this.iconPickerFor = null;
    this.touch();
  }

  setCapability(actor: IExampleActor, capability: ActorCapability) {
    if (this.viewOnly) {
      return;
    }
    actor.capability = capability;
    this.touch();
  }

  remove(actor: IExampleActor) {
    if (this.usageCount(actor) > 0 && this.confirmRemove !== actor.id) {
      this.confirmRemove = actor.id;
      return;
    }
    this.confirmRemove = null;
    this.actors = this.actors.filter((item) => item.id !== actor.id);
    this.touch();
  }

  reset() {
    this.actors = ((this.doc && this.doc.actors) || []).map((actor) => ({ ...actor }));
    this.dirty = false;
    this.confirmRemove = null;
    this.iconPickerFor = null;
    this.nameTouched = {};
  }

  save() {
    if (!this.doc || this.viewOnly || this.invalid) {
      return;
    }
    const kept = this.actors.map((actor) => actor.id);
    const scenarios = (this.doc.scenarios || []).map((scenario) => {
      const leftActorId = kept.indexOf(scenario.leftActorId) >= 0 ? scenario.leftActorId : '';
      const rightActorId = kept.indexOf(scenario.rightActorId) >= 0 ? scenario.rightActorId : '';
      const steps = leftActorId && rightActorId ? scenario.steps : [];
      return { ...scenario, leftActorId, rightActorId, steps };
    });
    this.saving = true;
    this.exampleMessagesService.saveExchange(this.doc.id, {
      actors: this.actors,
      scenarios,
    }).subscribe((message) => {
      this.saving = false;
      if (message && message.data) {
        this.store.dispatch(new LoadPayloadData(message.data));
        this.store.dispatch(this.messageService.messageToAction(message));
      }
    }, (err) => {
      this.saving = false;
      this.store.dispatch(this.messageService.actionFromError(err));
    });
  }
}
