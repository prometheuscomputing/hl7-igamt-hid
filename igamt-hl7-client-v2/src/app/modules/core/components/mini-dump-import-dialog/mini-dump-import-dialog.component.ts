import { HttpErrorResponse } from '@angular/common/http';
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { IMiniDumpOptions, IMiniDumpValidationResult, MiniDumpService } from '../../services/mini-dump.service';

export interface IMiniDumpImportDialogData {
  file: File;
  options: IMiniDumpOptions;
}

type StepStatus = 'pending' | 'active' | 'done' | 'failed';

interface IImportStep {
  id: string;
  label: string;
  status: StepStatus;
}

@Component({
  selector: 'app-mini-dump-import-dialog',
  templateUrl: './mini-dump-import-dialog.component.html',
  styleUrls: ['./mini-dump-import-dialog.component.scss'],
})
export class MiniDumpImportDialogComponent implements OnInit {
  steps: IImportStep[] = [
    { id: 'structure', label: 'Validating archive structure', status: 'pending' },
    { id: 'images', label: 'Verifying referenced images', status: 'pending' },
    { id: 'import', label: 'Importing resources', status: 'pending' },
  ];
  validationSummary: IMiniDumpValidationResult | null = null;
  errorMessage: string | null = null;
  completed = false;
  working = true;

  constructor(
    public dialogRef: MatDialogRef<MiniDumpImportDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: IMiniDumpImportDialogData,
    private miniDumpService: MiniDumpService,
  ) {
    dialogRef.disableClose = true;
  }

  ngOnInit(): void {
    this.runImportWorkflow();
  }

  close(): void {
    this.dialogRef.close(this.completed);
  }

  private runImportWorkflow(): void {
    this.setStepStatus('structure', 'active');
    this.miniDumpService.validateMiniDump(this.data.file, this.data.options).subscribe(
      (result) => {
        this.validationSummary = result;
        this.setStepStatus('structure', 'done');
        this.setStepStatus('images', 'done');
        this.setStepStatus('import', 'active');
        this.miniDumpService.importMiniDump(this.data.file, this.data.options).subscribe(
          () => {
            this.setStepStatus('import', 'done');
            this.completed = true;
            this.working = false;
          },
          (err: HttpErrorResponse) => {
            this.fail(err);
          },
        );
      },
      (err: HttpErrorResponse) => {
        this.fail(err, this.detectFailedStep(err));
      },
    );
  }

  private detectFailedStep(err: HttpErrorResponse): string {
    const message = this.extractErrorMessage(err).toLowerCase();
    if (message.includes('image') || message.includes('gridfs')) {
      return 'images';
    }
    return 'structure';
  }

  private fail(err: HttpErrorResponse, stepId: string = 'import'): void {
    this.working = false;
    this.errorMessage = this.extractErrorMessage(err);
    const failedIndex = this.steps.findIndex((step) => step.id === stepId);
    this.steps.forEach((step, index) => {
      if (index < failedIndex) {
        step.status = 'done';
      } else if (index === failedIndex) {
        step.status = 'failed';
      } else {
        step.status = 'pending';
      }
    });
  }

  private extractErrorMessage(err: HttpErrorResponse): string {
    const payload = err.error;
    if (payload && typeof payload.text === 'string' && payload.text.trim()) {
      return payload.text;
    }
    if (payload && typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
    return 'Import failed. No data was imported. Please contact your IGAMT administrator for assistance.';
  }

  private setStepStatus(stepId: string, status: StepStatus): void {
    const step = this.steps.find((item) => item.id === stepId);
    if (step) {
      step.status = status;
    }
  }
}
