import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { normalizeEr7, summarizeEr7 } from '../../../shared/functions/er7';
import { isDuplicateExampleName } from '../../domain/example-messages.model';

/** Text larger than this is not an example message. The server refuses it too. */
const MAX_FILE_BYTES = 1024 * 1024;

/**
 * Asks for the name of a new example message or snippet. With `data.allowMessage` it also takes the
 * ER7 text, pasted, dropped or read from a file, and returns it as `message`.
 */
@Component({
  selector: 'app-create-dialog',
  templateUrl: './create-dialog.component.html',
  styleUrls: ['./create-dialog.component.scss'],
})
export class CreateDialogComponent implements OnInit {

  name: string;
  existingNames: string[] = [];
  message = '';
  fileError: string = null;
  dragging = false;
  /** The message editor's colours, without its gutters. Tab leaves the editor, as in any other field of a dialog. */
  editorOptions = {
    mode: 'hl7v2',
    lineNumbers: true,
    lineWrapping: false,
    dragDrop: false,
    extraKeys: { 'Tab': false, 'Shift-Tab': false },
    placeholder: 'Paste the ER7 text here, or drop a file. Leave it empty to write the message in the editor.',
  };

  constructor(
    public dialogRef: MatDialogRef<CreateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
  ) { }

  ngOnInit() {
    this.existingNames = (this.data && this.data.existingNames) || [];
    this.name = (this.data && this.data.name) || '';
    if (this.data && this.data.allowMessage) {
      this.dialogRef.updateSize('700px');
    }
  }

  get nameTaken(): boolean {
    return isDuplicateExampleName(this.name, this.existingNames);
  }

  get nameEmpty(): boolean {
    return this.name != null && this.name.length > 0 && !String(this.name).trim();
  }

  get canSubmit(): boolean {
    return !!(this.name && this.name.trim()) && !this.nameTaken && !this.tooLarge;
  }

  get tooLarge(): boolean {
    return this.message.length > MAX_FILE_BYTES;
  }

  get hasMessage(): boolean {
    return !!this.message.trim();
  }

  get notAMessage(): boolean {
    return this.hasMessage && !summarizeEr7(this.message).startsWithMsh;
  }

  pickFile(input: HTMLInputElement) {
    const file = input.files && input.files[0];
    input.value = '';
    this.readFile(file);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragging = true;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragging = false;
    const files = event.dataTransfer && event.dataTransfer.files;
    if (files && files.length) {
      this.readFile(files[0]);
    }
  }

  clearMessage() {
    this.message = '';
    this.fileError = null;
  }

  submit() {
    if (!this.canSubmit) {
      return;
    }
    const result: { name: string, message?: string } = { name: this.name.trim() };
    if (this.data.allowMessage && this.hasMessage) {
      result.message = normalizeEr7(this.message);
    }
    this.dialogRef.close(result);
  }

  private readFile(file: File) {
    this.fileError = null;
    if (!file) {
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.fileError = file.name + ' is larger than 1 MB, which is too large for an example message.';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result || '');
      if (text.indexOf('\u0000') !== -1) {
        this.fileError = file.name + ' is not a text file.';
        return;
      }
      this.message = normalizeEr7(text);
      if (!(this.name && this.name.trim())) {
        this.name = file.name.replace(/\.[^.]+$/, '');
      }
    };
    reader.onerror = () => this.fileError = file.name + ' could not be read.';
    reader.readAsText(file);
  }

}
