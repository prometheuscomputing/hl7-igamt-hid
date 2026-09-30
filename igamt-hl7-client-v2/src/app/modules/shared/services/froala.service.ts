import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Store } from '@ngrx/store';
import { combineLatest, Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';
import { selectFroalaConfig } from '../../../root-store/config/config.reducer';
import * as fromDocumentation from '../../../root-store/documentation/documentation.reducer';
import { selectIgId } from '../../../root-store/ig/ig-edit/ig-edit.selectors';

import * as fromIgamtSelectors from 'src/app/root-store/dam-igamt/igamt.selectors';
import { registerFroalaExampleMessagePlugin, withExampleMessageToolbarButton, froalaToolbarButtons } from '../froala/example-message.plugin';
import { FroalaExampleMessageService } from './froala-example-message.service';

@Injectable({
  providedIn: 'root',
})
export class FroalaService {
  config;
  constructor(
    private http: HttpClient,
    private store: Store<any>,
    private froalaExampleMessageService: FroalaExampleMessageService,
  ) {
    registerFroalaExampleMessagePlugin();
    const staticConfig = {};
    this.config = {
      ...staticConfig,
      placeholderText: '',
      imageAllowedTypes: ['jpeg', 'jpg', 'png', 'gif'],
      fileUploadURL: '/api/storage/upload',
      fileAllowedTypes: ['application/pdf', 'application/msword', 'application/x-pdf', 'text/plain', 'application/xml', 'text/xml'],
      charCounterCount: false,
      quickInsertTags: [''],
      immediateAngularModelUpdate: true,
      events: {},
      imageResize: true,
      pastePlain: true,
    };
  }
  getConfig(): Observable<any> {
    return combineLatest(this.store.select(fromIgamtSelectors.selectWorkspaceActive), this.store.select(selectIgId), this.store.select(selectFroalaConfig)).pipe(
      map(([active, igId, conf]) => {
        const exampleMessageConfig = this.exampleMessageEditorOptions(igId);
        return {
          ...this.config,
          ...exampleMessageConfig,
          key: conf.key,
          imageUploadURL: '/api/storage/upload/',
          imageManagerLoadURL: '/api/storage/file',
          imageUploadParams: {
            ig: igId,
            type: active.editor.resourceType,
            id: active.display.id,
          },
          height: 300,
          events: {
            ... this.config.events,
            'froalaEditor.image.removed': ($img, obj1, obj2) => {
              const index = obj2[0].src.indexOf('=');
              const name = obj2[0].src.substring(index + 1);
              this.http.delete('/api/storage/file?name=' + name + '&ig=' + igId + '&type=' + active.editor.resourceType + '&id=' + active.display.id).subscribe();
            },
            'froalaEditor.initialized': (e, editor) => {
              if (igId && editor && editor.exampleMessage) {
                editor.exampleMessage.renderAll();
              }
            },
          },
        };
      }));
  }

  private exampleMessageEditorOptions(igId: string): any {
    if (!igId) {
      return {};
    }
    registerFroalaExampleMessagePlugin();
    const fe = (window as any).$ && ((window as any).$.FE || (window as any).$.FroalaEditor);
    const defaults = fe && fe.DEFAULTS ? fe.DEFAULTS : {};
    const options: any = {
      igamtExampleMessagePick: (editor) => this.froalaExampleMessageService.pickAndInsert(editor, igId),
      igamtExampleMessageRender: (editor, persist) => this.froalaExampleMessageService.renderEditor(editor, igId, persist),
    };
    const toolbarButtons = froalaToolbarButtons(fe);
    if (toolbarButtons.length) {
      options.toolbarButtons = withExampleMessageToolbarButton(toolbarButtons);
    }
    const toolbarButtonsMD = froalaToolbarButtons(fe, 'MD');
    if (toolbarButtonsMD.length) {
      options.toolbarButtonsMD = withExampleMessageToolbarButton(toolbarButtonsMD);
    }
    const toolbarButtonsSM = froalaToolbarButtons(fe, 'SM');
    if (toolbarButtonsSM.length) {
      options.toolbarButtonsSM = withExampleMessageToolbarButton(toolbarButtonsSM);
    }
    const toolbarButtonsXS = froalaToolbarButtons(fe, 'XS');
    if (toolbarButtonsXS.length) {
      options.toolbarButtonsXS = withExampleMessageToolbarButton(toolbarButtonsXS);
    }
    if (defaults.pluginsEnabled) {
      options.pluginsEnabled = defaults.pluginsEnabled.concat(['exampleMessage']);
    }
    return options;
  }

  getDocumentationConfig(): Observable<any> {
    return combineLatest(this.store.select(fromDocumentation.selectWorkspaceActive), this.store.select(selectFroalaConfig)).pipe(
      take(1),
      map(([active, conf]) => {
        return {
          ...this.config,
          key: conf.key,
          imageUploadURL: '/api/storage/upload/',
          imageManagerLoadURL: '/api/storage/file',
          imageUploadParams: {
            ig: 'doc',
            type: active.editor.resourceType,
            id: active.display.id,
          },
          events: {
            ... this.config.events,
            'froalaEditor.image.removed': ($img, obj1, obj2) => {
              const index = obj2[0].src.indexOf('=');
              const name = obj2[0].src.substring(index + 1);
              this.http.delete('/api/storage/file?name=' + name + '&ig=' + 'doc' + '&type=' + active.editor.resourceType + '&id=' + active.display.id).pipe(take(1)).subscribe();
            },
          },
        };
      }));
  }
}
