import { Component, Inject, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../../dam-framework/components/fragments/confirm-dialog/confirm-dialog.component';
import { IExampleMessageUsage } from '../../domain/example-messages.model';
import { ExampleMessagesService } from '../../services/example-messages.service';

export interface IDeleteUsageDialogData {
  igId: string;
  messageId: string;
  snippetId?: string;
  name: string;
  kind: 'message' | 'snippet';
}

@Component({
  selector: 'app-delete-usage-dialog',
  templateUrl: './delete-usage-dialog.component.html',
})
export class DeleteUsageDialogComponent implements OnInit {

  usages: IExampleMessageUsage[] = [];
  loading = true;
  working = false;
  error = '';

  constructor(
    public dialogRef: MatDialogRef<DeleteUsageDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: IDeleteUsageDialogData,
    private dialog: MatDialog,
    private exampleMessagesService: ExampleMessagesService,
  ) { }

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.error = '';
    this.exampleMessagesService.usages(this.data.igId, this.data.messageId, this.data.snippetId).subscribe(
      (usages) => {
        this.usages = usages || [];
        this.loading = false;
      },
      (error) => {
        this.loading = false;
        this.error = this.readError(error);
      },
    );
  }

  kindLabel(usage: IExampleMessageUsage): string {
    return usage.kind === 'NARRATIVE' ? 'Narrative' : 'Message example section';
  }

  sectionLink(usage: IExampleMessageUsage): string[] {
    const segment = usage.sectionType === 'MESSAGESECTION' ? 'messagesection' : 'text';
    return ['/ig', this.data.igId, segment, usage.sectionId];
  }

  confirmDetach(usage?: IExampleMessageUsage) {
    const place = usage ? ((usage.path || '') + ' ' + (usage.label || '')).trim() : 'every place it is used';
    const item = this.data.kind === 'snippet' ? 'snippet' : 'example message';
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        action: usage ? 'Detach reference' : 'Detach all references',
        question: 'Detaching ' + place + ' removes this ' + item + ' from the guide. This cannot be undone. Do you want to continue?',
      },
    }).afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.detach(usage);
      }
    });
  }

  detach(usage?: IExampleMessageUsage) {
    this.working = true;
    this.error = '';
    this.exampleMessagesService.detach(
      this.data.igId,
      this.data.messageId,
      this.data.snippetId,
      usage ? usage.sectionId : null,
      usage ? usage.kind : null,
    ).subscribe(
      (response) => {
        this.usages = (response && response.data) || [];
        this.working = false;
      },
      (error) => {
        this.working = false;
        this.error = this.readError(error);
      },
    );
  }

  deleteItem() {
    if (this.usages.length) {
      return;
    }
    this.working = true;
    this.error = '';
    const request = this.data.kind === 'snippet'
      ? this.exampleMessagesService.deleteSnippet(this.data.igId, this.data.messageId, this.data.snippetId)
      : this.exampleMessagesService.deleteMessage(this.data.igId, this.data.messageId);
    request.subscribe(
      (response) => {
        this.working = false;
        this.dialogRef.close({ deleted: true, data: response && response.data });
      },
      (error) => {
        this.working = false;
        this.error = this.readError(error);
      },
    );
  }

  private readError(error: any): string {
    const body = error && error.error;
    if (body && body.text) {
      return body.text;
    }
    if (body && body.message) {
      return body.message;
    }
    return 'The request failed.';
  }
}
