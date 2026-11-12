import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { PublicWebExportConfigurationComponent } from './public-web-export-configuration.component';
import { contrastOnWhite } from '../../models/public-web-export-configuration';

describe('Public web publication settings', () => {
  let component: PublicWebExportConfigurationComponent;
  beforeEach(() => {
    component = new PublicWebExportConfigurationComponent();
    component.config = { igGeneralConfiguration: {} } as any;
    component.ngOnChanges();
  });

  it('adds optional defaults without marking legacy configurations dirty', () => {
    let changes = 0;
    component.detectChange.subscribe(() => changes++);
    component.ngOnChanges();
    expect(component.theme.primaryColor).toBe('#021a4c');
    expect(component.theme.includeExampleLibrary).toBe(true);
    expect(component.examples.include).toBe(true);
    expect(changes).toBe(0);
  });

  it('keeps all palette links readable and resets only the colors', () => {
    component.theme.organizationName = 'Publication owner';
    component.palette.forEach((palette) => {
      expect(contrastOnWhite(palette.link)).toBeGreaterThanOrEqual(4.5);
      component.applyPalette(palette);
      expect(component.valid).toBe(true);
    });
    component.resetColors();
    expect(component.theme.primaryColor).toBe('#021a4c');
    expect(component.theme.organizationName).toBe('Publication owner');
  });

  it('requires readable links and a meaningful logo alternative', () => {
    component.theme.linkColor = '#ffffff';
    expect(component.valid).toBe(false);
    component.resetColors();
    component.theme.logoDataUrl = 'data:image/png;base64,image';
    component.theme.logoAlt = '   ';
    expect(component.valid).toBe(false);
    component.theme.logoAlt = 'Organization logo';
    expect(component.valid).toBe(true);
    component.removeLogo();
    expect(component.theme.logoDataUrl).toBe('');
    expect(component.valid).toBe(true);
  });

  it('does not modify built-in configurations through action methods', () => {
    component.viewOnly = true;
    component.applyPalette(component.palette[1]);
    component.removeLogo();
    expect(component.theme.primaryColor).toBe('#021a4c');
  });

  it('rejects unsupported or oversized uploads without changing saved branding', () => {
    const input = { files: [{ type: 'image/svg+xml', size: 20 }], value: 'logo.svg' };
    component.uploadLogo({ target: input } as any);
    expect(component.uploadError).toContain('PNG');
    expect(component.theme.logoDataUrl).toBe('');
    input.files = [{ type: 'image/png', size: 1024 * 1024 + 1 }];
    component.uploadLogo({ target: input } as any);
    expect(component.uploadError).toContain('1 MiB');
  });
});

describe('Public web settings template', () => {
  let fixture: ComponentFixture<PublicWebExportConfigurationComponent>;
  beforeEach(async(() => {
    TestBed.configureTestingModule({ declarations: [PublicWebExportConfigurationComponent], imports: [FormsModule] }).compileComponents();
  }));

  it('renders labeled controls and disables original configuration inputs', () => {
    fixture = TestBed.createComponent(PublicWebExportConfigurationComponent);
    fixture.componentInstance.config = { igGeneralConfiguration: {} } as any;
    fixture.componentInstance.viewOnly = true;
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#pw-organization')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('label[for="pw-logo"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
  });
});
