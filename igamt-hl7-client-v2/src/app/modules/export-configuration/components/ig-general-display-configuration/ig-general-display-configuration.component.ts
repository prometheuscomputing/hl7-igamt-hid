import { Component, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Input } from '@angular/core';
import { Output } from '@angular/core';
import { EventEmitter } from '@angular/core';

@Component({
  selector: 'app-ig-general-display-configuration',
  templateUrl: './ig-general-display-configuration.component.html',
  styleUrls: ['./ig-general-display-configuration.component.css'],
})
export class IgGeneralDisplayConfigurationComponent implements OnInit, OnChanges {

  @Input()
  config: any;

  @Input()
  viewOnly: boolean;

  @Input()
  derived: boolean;

  @Input()
  origin = null;

  @Output()
  detectChange: EventEmitter<any> = new EventEmitter<any>();

  constructor() { }

  ngOnInit() {
    this.ensureExampleMessages();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.config) {
      this.ensureExampleMessages();
    }
  }

  private ensureExampleMessages() {
    if (this.config && !this.config.exampleMessages) {
      this.config.exampleMessages = {
        include: true,
        mode: 'INLINE',
        exportAllMessages: false,
      };
    }
  }

  triggerChange() {
    this.detectChange.emit(this.config);
  }
  applyChange(event: any) {
    this.config.deltaMode = event.active;
    this.config.deltaConfig = event.config;
    this.triggerChange();
  }
}
