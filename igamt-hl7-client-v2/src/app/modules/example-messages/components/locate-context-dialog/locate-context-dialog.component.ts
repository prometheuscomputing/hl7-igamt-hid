import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { IExampleMessageLocation, IExampleMessageLocationContext } from '../../domain/example-messages.model';

@Component({
  selector: 'app-locate-context-dialog',
  templateUrl: './locate-context-dialog.component.html',
  styleUrls: ['./locate-context-dialog.component.scss'],
})
export class LocateContextDialogComponent {

  selectedKey: string;

  constructor(
    public dialogRef: MatDialogRef<LocateContextDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: IExampleMessageLocation,
  ) {
    const preferred = this.defaultContext();
    this.selectedKey = preferred ? this.contextKey(preferred) : null;
  }

  contextKey(context: IExampleMessageLocationContext): string {
    return (context.routeType || '') + '::' + (context.resourceId || '');
  }

  kindLabel(context: IExampleMessageLocationContext): string {
    if (!context || !context.kind) {
      return '';
    }
    if (context.kind === 'PROFILE') {
      return 'Profile';
    }
    if (context.kind === 'SEGMENT') {
      return 'Segment';
    }
    if (context.kind === 'VALUESET') {
      return 'Value set';
    }
    return 'Datatype';
  }

  openSelected() {
    const selected = (this.data.contexts || []).find((context) => this.contextKey(context) === this.selectedKey);
    this.dialogRef.close(selected || null);
  }

  private defaultContext(): IExampleMessageLocationContext {
    const contexts = this.data.contexts || [];
    if (!contexts.length) {
      return null;
    }
    const match = contexts.find((context) =>
      context.routeType === this.data.routeType && context.resourceId === this.data.resourceId);
    return match || contexts[contexts.length - 1];
  }
}
