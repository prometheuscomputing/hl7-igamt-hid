import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface IMiniDumpOptions {
  archiveName?: string;
  format?: 'JSON' | 'BSON' | 'BOTH';
  mode?: 'OVERRIDE';
}

export interface IMiniDumpValidationResult {
  igCount: number;
  referencedImageCount: number;
  includedImageCount: number;
  exportedBy?: string;
}

@Injectable({
  providedIn: 'root',
})
export class MiniDumpService {
  constructor(private http: HttpClient) {}

  exportMiniDump(options: IMiniDumpOptions = {}): Observable<Blob> {
    let params = new HttpParams();
    if (options.format) {
      params = params.set('format', options.format);
    }
    if (options.archiveName) {
      params = params.set('archiveName', options.archiveName);
    }
    return this.http.get('/api/account/mini-dump', { responseType: 'blob', params });
  }

  validateMiniDump(file: File, options: IMiniDumpOptions = {}): Observable<IMiniDumpValidationResult> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<IMiniDumpValidationResult>('/api/account/mini-dump/validate', formData);
  }

  importMiniDump(file: File, options: IMiniDumpOptions = {}): Observable<void> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    const params = new HttpParams().set('mode', 'OVERRIDE');
    return this.http.post<void>('/api/account/mini-dump', formData, { params });
  }
}
