import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { EMPTY, Observable, of, BehaviorSubject, combineLatest } from 'rxjs';
import { map, tap, take, mergeMap, catchError, finalize, filter } from 'rxjs/operators';
import { MessageService } from '../../../dam-framework/services/message.service';
import { Type } from '../../../shared/constants/type.enum';
import { IDisplayElement } from '../../../shared/models/display-element.interface';
import { EditorID } from '../../../shared/models/editor.enum';
import { saveAs } from 'file-saver';
import { DeleteUsageDialogComponent } from '../delete-usage-dialog/delete-usage-dialog.component';
import { TableOfContentComponent } from '../table-of-content/table-of-content.component';
import { selectIgExampleMessages } from 'src/app/root-store/example-messages/example-messages.reducer';
import { ExampleMessagesService } from '../../services/example-messages.service';
import { LoadPayloadData } from 'src/app/modules/dam-framework/store';
import { MessageType } from 'src/app/modules/dam-framework/models/messages/message.class';
import * as fromDAM from 'src/app/modules/dam-framework/store/index';

@Component({
  selector: 'app-side-bar',
  templateUrl: './side-bar.component.html',
  styleUrls: ['./side-bar.component.scss'],
})
export class SideBarComponent {

  nodes: Observable<any[]>;
  viewOnly$: Observable<boolean>;
  id$: Observable<string>;
  activeMessageId$: BehaviorSubject<string> = new BehaviorSubject<string>(null);

  @ViewChild(TableOfContentComponent) toc: TableOfContentComponent;
  readonly SEGMENTS_REPO = 'segment-structures';
  readonly MESSAGES_REPO = 'message-structures';
  readonly STRUCTURE_EDITOR_URL = 'structure-editor';
  constructor(
    private store: Store<any>,
    private router: Router,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    private messageService: MessageService,
    private exampleMessageService: ExampleMessagesService,
  ) {
    // Extract messageId from child route on every navigation
    this.updateActiveMessageId();
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
    ).subscribe(() => {
      this.updateActiveMessageId();
    });
    this.id$ = this.store.select(selectIgExampleMessages).pipe(
      take(1),
      map((igExampleMessages) => igExampleMessages.id)
    );
    this.viewOnly$ = this.store.select(selectIgExampleMessages).pipe(
      map((igExampleMessages) => !!(igExampleMessages && igExampleMessages.viewOnly)),
    );
    this.nodes = combineLatest(
      this.store.select(selectIgExampleMessages),
      this.store.select(fromDAM.selectWorkspaceActive),
      this.store.select(fromDAM.selectWorkspaceCurrent),
    ).pipe(
      map(([igExampleMessages, active, current]) => {
        if (!igExampleMessages || !igExampleMessages.profileExampleMessages) {
          return [];
        }
        const activeId = active && active.display ? active.display.id : null;
        const editorId = active && active.editor ? active.editor.id : null;
        const currentData = current && current.data ? current.data as any : null;
        const currentName = currentData ? currentData.name : null;
        const currentSnippetName = currentData ? currentData.snippetName : null;
        const nodes: any[] = [];
        for (const profileExamples of igExampleMessages.profileExampleMessages) {
          nodes.push({
            ...profileExamples.profile,
            id: profileExamples.profile.id,
            label: this.getLabel(profileExamples.profile),
            children: [
              ...profileExamples.exampleMessages.map((message) => ({
                ...message,
                type: Type.EXAMPLEMESSAGE,
                label: editorId === EditorID.EXAMPLE_MESSAGE && message.id === activeId && currentName != null
                  ? currentName
                  : message.name,
                children: [
                  ...(message.snippets || []).map((snippet) => ({
                    ...snippet,
                    messageId: message.id,
                    type: Type.EXAMPLEMESSAGESNIPPET,
                    label: editorId === EditorID.EXAMPLE_SNIPPET && snippet.id === activeId && currentSnippetName != null
                      ? currentSnippetName
                      : snippet.name,
                  }))
                ]
              }))
            ]
          })
        }
        return nodes;
      })
    );
  }

  getLabel(display: IDisplayElement) {
    if (display.fixedName && display.fixedName.length > 0) {
      if (display.variableName && display.variableName.length > 0) {
        return display.fixedName + '#' + display.variableName;
      } else {
        return display.fixedName;
      }
    } else {
      if (display.variableName) {
        return display.variableName;
      }
    }
  }

  filterFn(value: any) {
    this.toc.filter(value);
  }

  collapseAll() {
    this.toc.collapseAll();
  }

  expandAll() {
    this.toc.expandAll();
  }

  createExampleMessage(data: { name: string, profileId: string }) {
    this.mutateExampleMessages(
      (id) => this.exampleMessageService.createExampleMessage(id, data),
      (createdId) => this.router.navigate(['message', createdId], { relativeTo: this.route }),
    );
  }

  copyExampleMessage(data: { messageId: string, name: string }) {
    this.mutateExampleMessages(
      (id) => this.exampleMessageService.copyExampleMessage(id, data.messageId, { name: data.name }),
      (createdId) => this.router.navigate(['message', createdId], { relativeTo: this.route }),
    );
  }

  deleteExampleMessage(data: { messageId: string, name: string }) {
    this.openDelete({ ...data, kind: 'message' });
  }

  deleteSnippet(data: { messageId: string, snippetId: string, name: string }) {
    this.openDelete({ ...data, kind: 'snippet' });
  }

  exportExampleMessage(data: { messageId: string, name: string, format: 'html' | 'txt' | 'validation' }) {
    this.id$.pipe(take(1)).subscribe((igId) => {
      this.exampleMessageService.exportMessageFile(igId, data.messageId, data.format).pipe(take(1)).subscribe((blob) => {
        saveAs(blob, this.exportFilename(data.name, data.format, 'example'));
      });
    });
  }

  exportSnippet(data: { messageId: string, snippetId: string, name: string, format: 'html' | 'txt' }) {
    this.id$.pipe(take(1)).subscribe((igId) => {
      this.exampleMessageService.exportSnippetFile(igId, data.messageId, data.snippetId, data.format).pipe(take(1)).subscribe((blob) => {
        saveAs(blob, this.exportFilename(data.name, data.format, 'snippet'));
      });
    });
  }

  private exportFilename(name: string, format: string, fallback: string): string {
    const base = (name || fallback).replace(/[\\/:*?"<>|]/g, '-');
    if (format === 'txt') {
      return base + '.txt';
    }
    if (format === 'validation') {
      return base + '-validation.html';
    }
    return base + '.html';
  }

  private openDelete(data: { messageId: string, snippetId?: string, name: string, kind: 'message' | 'snippet' }) {
    this.id$.pipe(take(1)).subscribe((igId) => {
      this.dialog.open(DeleteUsageDialogComponent, {
        data: { ...data, igId },
        width: '560px',
      }).afterClosed().subscribe((result) => {
        if (result && result.deleted && result.data) {
          this.store.dispatch(new LoadPayloadData(result.data));
          this.router.navigate(['/example-messages', igId]);
        }
      });
    });
  }

  copySnippet(data: { messageId: string, snippetId: string, name: string }) {
    this.mutateExampleMessages(
      (id) => this.exampleMessageService.copySnippet(id, data.messageId, data.snippetId, { name: data.name }),
      (createdId) => this.router.navigate(['message', data.messageId, 'snippet', createdId], { relativeTo: this.route }),
    );
  }

  private mutateExampleMessages(
    request: (id: string) => Observable<any>,
    afterSuccess?: (createdId: string) => void,
  ) {
    this.store.dispatch(new fromDAM.TurnOnLoader({
      blockUI: true,
    }));
    this.id$.pipe(
      take(1),
      mergeMap((id) => {
        return request(id).pipe(
          map((message) => {
            const actions = [];
            actions.push(this.messageService.messageToAction(message));
            if (message.status === MessageType.SUCCESS && message.data) {
              actions.push(new LoadPayloadData(message.data));
            }
            actions.forEach((action) => {
              this.store.dispatch(action);
            });
            if (message.status === MessageType.SUCCESS && afterSuccess) {
              const createdId = (message.data && message.data.createdId) || message.id;
              if (createdId) {
                afterSuccess(createdId);
              }
            }
          }),
          catchError((err) => {
            this.store.dispatch(this.messageService.actionFromError(err));
            return EMPTY;
          }),
        );
      }),
      finalize(() => {
        this.store.dispatch(new fromDAM.TurnOffLoader());
      })
    ).subscribe();
  }

  private updateActiveMessageId() {
    if (this.route.firstChild) {
      const messageId = this.route.firstChild.snapshot.paramMap.get('messageId');
      this.activeMessageId$.next(messageId);
    } else {
      this.activeMessageId$.next(null);
    }
  }

}
