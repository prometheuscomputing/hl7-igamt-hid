import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { IExampleMessageDTO, IExampleMessageLocation, IExampleMessageUsage, IExampleMessageValidationResult, IgExampleMessages } from '../domain/example-messages.model';
import { IMessage } from '../../dam-framework/models/messages/message.class';

export interface ICreateExampleMessageSnippetRequest {
  name: string;
  messageReferences: string[];
}

export interface ISnippetRenderResult {
  snippetId: string;
  snippetName: string;
  snippetDescription: string;
  messageId: string;
  messageName: string;
  fullMessage: string;
  renderedContent: string;
  parts: ISnippetPart[];
  snippetNotFound: boolean;
  warnings: string[];
}

export interface ISnippetPart {
  positionalPath: string;
  hl7Path: string;
  elementName: string;
  elementType: string;
  content: string;
  startLine: number;
  endLine: number;
  highlightStart: { line: number; column: number };
  highlightEnd: { line: number; column: number };
}

@Injectable({
  providedIn: 'root',
})
export class ExampleMessagesService {

  constructor(private http: HttpClient) { }

  public getIgExampleMessages(id: string): Observable<IgExampleMessages> {
    return this.http.get<IgExampleMessages>(`api/example-messages/${id}`);
  }

  public getExampleMessage(id: string, messageId: string): Observable<IExampleMessageDTO> {
    return this.http.get<IExampleMessageDTO>(`api/example-messages/${id}/message/${messageId}`);
  }

  public createExampleMessage(id: string, data: { name: string, profileId: string }): Observable<IMessage<IgExampleMessages>> {
    return this.http.post<IMessage<IgExampleMessages>>(`api/example-messages/${id}/message`, data);
  }

  public saveExampleMessage(id: string, messageId: string, data: { message: string, narrative: string, name?: string, description?: string }): Observable<IMessage<IExampleMessageDTO>> {
    return this.http.post<IMessage<IExampleMessageDTO>>(`api/example-messages/${id}/message/${messageId}`, data);
  }

  public parseExampleMessage(id: string, messageId: string): Observable<any[]> {
    return this.http.get<any[]>(`api/example-messages/${id}/message/${messageId}/parse`);
  }

  public validateExampleMessage(id: string, messageId: string, message: string): Observable<IExampleMessageValidationResult> {
    return this.http.post<IExampleMessageValidationResult>(`api/example-messages/${id}/message/${messageId}/validate`, { message });
  }

  public locateExampleMessageElement(id: string, messageId: string, positionalPath?: string, hl7Path?: string): Observable<IExampleMessageLocation> {
    const params: { [key: string]: string } = {};
    if (positionalPath) {
      params.positionalPath = positionalPath;
    }
    if (hl7Path) {
      params.hl7Path = hl7Path;
    }
    return this.http.get<IExampleMessageLocation>(`api/example-messages/${id}/message/${messageId}/locate`, { params });
  }

  public copyExampleMessage(id: string, messageId: string, data: { name: string }): Observable<IMessage<IgExampleMessages>> {
    return this.http.post<IMessage<IgExampleMessages>>(`api/example-messages/${id}/message/${messageId}/copy`, data);
  }

  public createExampleMessageSnippet(id: string, messageId: string, data: ICreateExampleMessageSnippetRequest): Observable<IMessage<IgExampleMessages>> {
    return this.http.post<IMessage<IgExampleMessages>>(`api/example-messages/${id}/message/${messageId}/snippet`, data);
  }

  public copySnippet(id: string, messageId: string, snippetId: string, data: { name: string }): Observable<IMessage<IgExampleMessages>> {
    return this.http.post<IMessage<IgExampleMessages>>(`api/example-messages/${id}/message/${messageId}/snippet/${snippetId}/copy`, data);
  }

  public renderSnippet(igId: string, messageId: string, snippetId: string): Observable<ISnippetRenderResult> {
    return this.http.get<ISnippetRenderResult>(`api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}/render`);
  }

  public renderFromPaths(igId: string, messageId: string, positionalPaths: string[]): Observable<ISnippetRenderResult> {
    return this.http.post<ISnippetRenderResult>(`api/example-messages/${igId}/message/${messageId}/render`, { positionalPaths });
  }

  public saveSnippet(igId: string, messageId: string, snippetId: string, data: { name: string, description: string }): Observable<IMessage<any>> {
    return this.http.post<IMessage<any>>(`api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}`, data);
  }

  public exportAll(igId: string): Observable<Blob> {
    return this.http.get(`api/example-messages/${igId}/export`, { responseType: 'blob' });
  }

  public exportMessageFile(igId: string, messageId: string, format: 'html' | 'txt' | 'validation'): Observable<Blob> {
    return this.http.get(`api/example-messages/${igId}/message/${messageId}/export`, {
      params: { format },
      responseType: 'blob',
    });
  }

  public exportSnippetFile(igId: string, messageId: string, snippetId: string, format: 'html' | 'txt'): Observable<Blob> {
    return this.http.get(`api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}/export`, {
      params: { format },
      responseType: 'blob',
    });
  }

  public usages(igId: string, messageId: string, snippetId?: string): Observable<IExampleMessageUsage[]> {
    const url = snippetId
      ? `api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}/usages`
      : `api/example-messages/${igId}/message/${messageId}/usages`;
    return this.http.get<IExampleMessageUsage[]>(url);
  }

  public detach(igId: string, messageId: string, snippetId: string, sectionId: string, kind: string): Observable<IMessage<IExampleMessageUsage[]>> {
    const url = snippetId
      ? `api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}/detach`
      : `api/example-messages/${igId}/message/${messageId}/detach`;
    return this.http.post<IMessage<IExampleMessageUsage[]>>(url, { sectionId, kind });
  }

  public deleteMessage(igId: string, messageId: string): Observable<IMessage<IgExampleMessages>> {
    return this.http.delete<IMessage<IgExampleMessages>>(`api/example-messages/${igId}/message/${messageId}`);
  }

  public deleteSnippet(igId: string, messageId: string, snippetId: string): Observable<IMessage<IgExampleMessages>> {
    return this.http.delete<IMessage<IgExampleMessages>>(`api/example-messages/${igId}/message/${messageId}/snippet/${snippetId}`);
  }

}
