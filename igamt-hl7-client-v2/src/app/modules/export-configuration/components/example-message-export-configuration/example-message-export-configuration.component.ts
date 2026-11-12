import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';

@Component({
  selector: 'app-example-message-export-configuration',
  templateUrl: './example-message-export-configuration.component.html',
  styleUrls: ['./example-message-export-configuration.component.css'],
})
export class ExampleMessageExportConfigurationComponent implements OnInit, OnChanges {

  @Input()
  config: any;

  @Input()
  viewOnly: boolean;

  @Output()
  detectChange: EventEmitter<any> = new EventEmitter<any>();

  fullText = true;
  snapshot = false;
  relativeLink = true;
  igamtLink = false;

  constructor() { }

  ngOnInit() {
    this.ensure();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.config) {
      this.ensure();
    }
  }

  triggerChange() {
    this.detectChange.emit(this.config);
  }

  applyChoices(source?: string) {
    if (!this.config || !this.config.exampleMessages) {
      return;
    }
    if (source === 'relative' && this.relativeLink) {
      this.snapshot = true;
    }
    if (source === 'snapshot' && !this.snapshot) {
      this.relativeLink = false;
    }
    this.config.exampleMessages.mode = this.contentMode(this.fullText, this.snapshot);
    this.config.exampleMessages.linkTarget = this.linkMode(this.relativeLink, this.igamtLink);
    this.triggerChange();
  }

  private ensure() {
    if (!this.config) {
      return;
    }
    if (!this.config.exampleMessages) {
      this.config.exampleMessages = {
        include: true,
        mode: 'INLINE',
        exportAllMessages: false,
        snippetMode: 'LINE',
        linkTarget: 'RELATIVE',
        folderName: 'ExampleMessages',
        includeDescription: true,
      };
    }
    if (!this.config.exampleMessages.mode) {
      this.config.exampleMessages.mode = 'INLINE';
    }
    if (!this.config.exampleMessages.snippetMode) {
      this.config.exampleMessages.snippetMode = 'LINE';
    }
    if (!this.config.exampleMessages.linkTarget) {
      this.config.exampleMessages.linkTarget = 'RELATIVE';
    }
    if (!this.config.exampleMessages.folderName) {
      this.config.exampleMessages.folderName = 'ExampleMessages';
    }
    if (this.config.exampleMessages.includeDescription === undefined || this.config.exampleMessages.includeDescription === null) {
      this.config.exampleMessages.includeDescription = true;
    }
    const mode = this.config.exampleMessages.mode;
    const link = this.config.exampleMessages.linkTarget;
    this.fullText = mode !== 'SEPARATE_FILES' && mode !== 'NONE';
    this.snapshot = mode === 'SEPARATE_FILES' || mode === 'BOTH';
    this.relativeLink = link !== 'IGAMT' && link !== 'NONE';
    this.igamtLink = link === 'IGAMT' || link === 'BOTH';
  }

  private contentMode(text: boolean, snapshot: boolean): string {
    if (text && snapshot) {
      return 'BOTH';
    }
    if (snapshot) {
      return 'SEPARATE_FILES';
    }
    if (text) {
      return 'INLINE';
    }
    return 'NONE';
  }

  private linkMode(relative: boolean, igamt: boolean): string {
    if (relative && igamt) {
      return 'BOTH';
    }
    if (igamt) {
      return 'IGAMT';
    }
    if (relative) {
      return 'RELATIVE';
    }
    return 'NONE';
  }
}
