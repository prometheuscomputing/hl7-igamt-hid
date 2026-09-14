import { of } from 'rxjs';
import { ExportToolComponent, ToolExportStepType } from './export-tool.component';

describe('ExportToolComponent', () => {
  let igService: { exportToTesting: jasmine.Spy };
  let component: ExportToolComponent;

  beforeEach(() => {
    igService = { exportToTesting: jasmine.createSpy('exportToTesting') };
    component = new ExportToolComponent(
      null,
      null,
      null,
      null,
      null,
      { igId: 'ig-1', tools: [] },
      igService as any,
      null,
    );
    component.tool = { label: 'GVT', url: 'https://example.test/gvt/' } as any;
    component.ids = { conformanceProfilesId: ['cp-1'], compositeProfilesId: [] };
  });

  it('requires a tool scope and at least one profile before the selection can proceed', () => {
    expect(component.isSelected()).toBe(false);
    component.selectDomain({ domain: 'demo', name: 'Demo' });
    expect(component.isSelected()).toBe(true);
    component.ids = { conformanceProfilesId: [], compositeProfilesId: [] };
    expect(component.isSelected()).toBe(false);
  });

  it('reports a missing scope instead of spinning without a request', () => {
    component.submit();

    expect(igService.exportToTesting).not.toHaveBeenCalled();
    expect(component.current).toBe(ToolExportStepType.BUNDLE_GENERATION);
    expect(component.exportInProgress).toBe(false);
    expect(component.exportFailed).toBe(true);
    expect(component.exportError).toContain('scope');
  });

  it('pushes to the selected scope and shows the tool report when the push is refused', () => {
    igService.exportToTesting.and.returnValue(of({ success: false, report: '<p>bad</p>', message: 'invalid' }));
    component.selectDomain({ domain: 'demo', name: 'Demo' });

    component.submit();

    expect(igService.exportToTesting).toHaveBeenCalled();
    expect(igService.exportToTesting.calls.mostRecent().args[8]).toBe('demo');
    expect(component.exportInProgress).toBe(false);
    expect(component.exportFailed).toBe(true);
    expect(component.HTMLErrorReport).toBe('<p>bad</p>');
  });
});
