import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { IExportConfiguration, IPublicWebExportConfiguration } from '../../models/default-export-configuration.interface';

import { contrastOnWhite, publicWebDefaults } from '../../models/public-web-export-configuration';

@Component({
  selector: 'app-public-web-export-configuration',
  templateUrl: './public-web-export-configuration.component.html',
  styleUrls: ['./public-web-export-configuration.component.scss'],
})
export class PublicWebExportConfigurationComponent implements OnChanges {
  @Input() config: IExportConfiguration;
  @Input() viewOnly = false;
  @Output() detectChange = new EventEmitter<any>();
  @Output() validityChange = new EventEmitter<boolean>(true);
  theme: IPublicWebExportConfiguration = publicWebDefaults();
  uploadError = '';
  palette = [
    { name: 'IGAMT', primary: '#021a4c', accent: '#F3C60D', link: '#075780' },
    { name: 'Ocean', primary: '#123B4A', accent: '#A5E5D8', link: '#075B75' },
    { name: 'Forest', primary: '#173E2C', accent: '#D5E7BA', link: '#21623D' },
    { name: 'Plum', primary: '#42264D', accent: '#E8CCEF', link: '#6E337F' },
  ];

  ngOnChanges() {
    if (!this.config) { return; }
    this.theme = { ...publicWebDefaults(), ...(this.config.publicWebConfiguration || {}) };
    ['organizationName', 'logoDataUrl', 'logoAlt', 'footerText'].forEach((key) => {
      if (typeof this.theme[key] !== 'string') { this.theme[key] = ''; }
    });
    this.config.publicWebConfiguration = this.theme;
    const general: any = (this.config as any).igGeneralConfiguration;
    if (general && !general.exampleMessages) {
      general.exampleMessages = { include: true, mode: 'INLINE', snippetMode: 'LINE', linkTarget: 'RELATIVE',
        exportAllMessages: false, folderName: 'ExampleMessages', includeDescription: true };
    }
    this.validityChange.emit(this.valid);
  }

  get examples(): any {
    const general = this.config && (this.config as any).igGeneralConfiguration;
    return general && general.exampleMessages;
  }

  get linkContrast(): number { return contrastOnWhite(this.theme.linkColor); }
  get valid(): boolean {
    return /^#[0-9a-f]{6}$/i.test(this.theme.primaryColor || '') &&
      /^#[0-9a-f]{6}$/i.test(this.theme.accentColor || '') && this.linkContrast >= 4.5 &&
      (!this.theme.logoDataUrl || !!(this.theme.logoAlt || '').trim());
  }

  changed() {
    if (this.viewOnly) { return; }
    this.validityChange.emit(this.valid);
    this.detectChange.emit(this.config);
  }

  applyPalette(choice: { primary: string; accent: string; link: string }) {
    if (this.viewOnly) { return; }
    this.theme.primaryColor = choice.primary;
    this.theme.accentColor = choice.accent;
    this.theme.linkColor = choice.link;
    this.changed();
  }

  resetColors() { this.applyPalette(this.palette[0]); }

  removeLogo() {
    if (this.viewOnly) { return; }
    this.theme.logoDataUrl = '';
    this.theme.logoAlt = '';
    this.uploadError = '';
    this.changed();
  }

  uploadLogo(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    if (!file || this.viewOnly) { return; }
    this.uploadError = '';
    if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type) || file.size > 1024 * 1024) {
      this.uploadError = 'Choose a PNG, JPEG, WebP or GIF image no larger than 1 MiB.';
      input.value = '';
      return;
    }
    const theme = this.theme;
    const reader = new FileReader();
    reader.onerror = () => { this.uploadError = 'The logo could not be read. Choose another image.'; };
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const image = new Image();
      image.onerror = () => { this.uploadError = 'This file could not be opened as an image. Choose another logo.'; };
      image.onload = () => {
        if (this.theme !== theme) { return; }
        this.theme.logoDataUrl = dataUrl;
        this.changed();
      };
      image.src = dataUrl;
    };
    reader.readAsDataURL(file);
    input.value = '';
  }
}
