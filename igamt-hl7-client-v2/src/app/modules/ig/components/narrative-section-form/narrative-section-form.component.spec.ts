import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { NarrativeSectionFormComponent } from './narrative-section-form.component';

describe('NarrativeSectionFormComponent structured requirements', () => {
  let component: NarrativeSectionFormComponent;
  beforeEach(() => {
    component = new NarrativeSectionFormComponent({ getConfig: () => of({}) } as any);
  });
  afterEach(() => component.ngOnDestroy());

  it('does not mark a loaded section dirty, but emits actual changes', fakeAsync(() => {
    let changes = 0;
    component.form.subscribe(() => changes++);
    component.ngOnInit();
    component.data = {
      id: 'section', label: 'Topic', description: '<p>Existing content</p>',
      requirements: [{ id: 'stable', position: 1, verb: 'SHALL', text: 'accept the value' }],
    };
    tick(250);
    expect(changes).toBe(0);
    component.sectionForm.get('label').setValue('Renamed topic');
    tick(250);
    expect(changes).toBe(1);
    expect(component.requirements.at(0).get('id').value).toBe('stable');
  }));

  it('keeps a nested example and diagram when promoting a separate requirement', () => {
    const html = '<ul><li>SHALL keep the diagram <img src="diagram.png" alt="Diagram"></li>' +
      '<li><p>SHOULD accept the value</p><div class="fr-exmsg" data-message-id="message"></div></li></ul>';
    component.sectionForm.get('description').setValue(html);
    component.openIdentify();
    expect(component.identifyCandidates.length).toBe(1);
    expect(component.identifyStripFromContent).toBe(false);
    component.identifyStripFromContent = true;
    component.applyIdentify();
    const doc = new DOMParser().parseFromString(component.getDescription(), 'text/html');
    expect(doc.querySelector('img').getAttribute('src')).toBe('diagram.png');
    expect(doc.querySelector('.fr-exmsg').getAttribute('data-message-id')).toBe('message');
    expect(component.requirements.at(0).get('text').value).toBe('accept the value');
  });

  it('reorders rows without changing their requirement IDs and permits explicit clear', () => {
    component.data = { id: 'section', label: 'Topic', description: '', requirements: [
      { id: 'first', position: 1, verb: 'SHALL', text: 'first' },
      { id: 'second', position: 2, verb: 'MAY', text: 'second' },
    ] };
    component.moveRequirement(1, -1);
    expect(component.requirements.at(0).get('id').value).toBe('second');
    expect(component.requirements.at(0).get('position').value).toBe(1);
    component.removeRequirement(1);
    component.removeRequirement(0);
    expect(component.sectionForm.getRawValue().requirements).toEqual([]);
  });

  it('rejects a statement consisting only of whitespace', () => {
    component.addRequirement();
    component.requirements.at(0).get('text').setValue('   ');
    expect(component.requirements.at(0).invalid).toBe(true);
  });
});
