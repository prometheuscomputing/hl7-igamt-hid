import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { isDuplicateExampleName } from '../../domain/example-messages.model';

@Component({
  selector: 'app-create-dialog',
  templateUrl: './create-dialog.component.html',
  styleUrls: ['./create-dialog.component.scss'],
})
export class CreateDialogComponent implements OnInit {

  name: string;
  existingNames: string[] = [];

  constructor(
    public dialogRef: MatDialogRef<CreateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
  ) { }

  ngOnInit() {
    this.existingNames = (this.data && this.data.existingNames) || [];
    this.name = (this.data && this.data.name) || '';
  }

  get nameTaken(): boolean {
    return isDuplicateExampleName(this.name, this.existingNames);
  }

  get canSubmit(): boolean {
    return !!(this.name && this.name.trim()) && !this.nameTaken;
  }

  submit() {
    if (!this.canSubmit) {
      return;
    }
    this.dialogRef.close({ name: this.name.trim() });
  }

}
