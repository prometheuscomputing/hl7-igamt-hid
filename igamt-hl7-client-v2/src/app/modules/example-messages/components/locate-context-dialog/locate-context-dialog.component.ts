import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Type } from '../../../shared/constants/type.enum';
import { IExampleMessageLocation, IExampleMessageLocationContext } from '../../domain/example-messages.model';

/**
 * An element of a message is described by its profile, its segment, the datatypes on the way
 * down to it, and the value sets bound to it. This lists them, outermost first, and one click
 * opens the chosen one.
 */
@Component({
  selector: 'app-locate-context-dialog',
  templateUrl: './locate-context-dialog.component.html',
  styleUrls: ['./locate-context-dialog.component.scss'],
})
export class LocateContextDialogComponent {

  structure: IExampleMessageLocationContext[];
  valueSets: IExampleMessageLocationContext[];

  constructor(
    public dialogRef: MatDialogRef<LocateContextDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: IExampleMessageLocation,
  ) {
    const contexts = data.contexts || [];
    this.structure = contexts.filter((context) => context.kind !== 'VALUESET');
    this.valueSets = contexts.filter((context) => context.kind === 'VALUESET');
  }

  badgeType(context: IExampleMessageLocationContext): Type {
    switch (context && context.kind) {
      case 'PROFILE': return Type.CONFORMANCEPROFILE;
      case 'SEGMENT': return Type.SEGMENT;
      case 'VALUESET': return Type.VALUESET;
      default: return Type.DATATYPE;
    }
  }

  /** What the resource is, and what the server says about its use here when that adds something. */
  detail(context: IExampleMessageLocationContext): string {
    const kind = this.kindLabel(context);
    const name = context.resourceName || context.label;
    const more = context.detail && context.detail !== name && context.detail !== context.label ? context.detail : '';
    return more ? kind + ' · ' + more : kind;
  }

  open(context: IExampleMessageLocationContext) {
    this.dialogRef.close(context);
  }

  private kindLabel(context: IExampleMessageLocationContext): string {
    switch (context && context.kind) {
      case 'PROFILE': return 'Conformance profile';
      case 'SEGMENT': return 'Segment';
      case 'VALUESET': return 'Value set';
      default: return 'Datatype';
    }
  }
}
