import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { IDisplayElement } from "../../shared/models/display-element.interface";

export interface IgExampleMessages {
    id: string;
    title: string;
    createdId?: string;
    viewOnly?: boolean;
    profileExampleMessages: IProfileExampleMessages[];
}

export interface IExampleMessageUsage {
    sectionId: string;
    sectionType: string;
    path: string;
    label: string;
    kind: 'MESSAGE_SECTION' | 'NARRATIVE' | string;
}

export interface IProfileExampleMessages {
    profile: IDisplayElement;
    exampleMessages: IExampleMessage[];
}

export interface IExampleMessage {
    id: string;
    name: string;
    description: string;
    message: string;
    narrativeHTML: string;
    profileId: string;
    snippets: IExampleMessageSnippet[];
}

export interface IExampleMessageSnippet {
    id: string;
    name: string;
    description: string;
    messageReferences: string[];
    narrativeHTML: string;
}

export interface IAvailableMessage {
    id: string;
    name: string;
    profileName: string;
    snippets: IExampleMessageSnippet[];
}

export interface ISnippetValidationInfo {
    snippetId: string;
    snippetName: string;
    totalReferences: number;
    resolvedReferences: number;
    brokenPaths: string[];
    fullyResolved: boolean;
}

export interface IExampleMessageDTO {
    id: string;
    name: string;
    description: string;
    message: string;
    narrativeHTML: string;
    profile: IDisplayElement;
    snippetValidations?: ISnippetValidationInfo[];
}

export interface IExampleMessageValidationEntry {
    classification: string;
    category: string;
    description: string;
    path: string;
    er7Path: string;
    igPath: string;
    positionalPath: string;
    line: number;
    column: number;
}

export interface IExampleMessageLocationHop {
    type: string;
    hl7Path: string;
    name: string;
    resourceType: string;
    resourceId: string;
    resourceName: string;
    pathId: string;
    positionalPath: string;
}

export interface IExampleMessageLocationContext {
    kind: string;
    label: string;
    detail: string;
    routeType: string;
    resourceId: string;
    resourceName: string;
    location: string;
    hl7Path: string;
}

export interface IExampleMessageLocation {
    igId: string;
    profileId: string;
    hl7Path: string;
    type: string;
    name: string;
    routeType: string;
    resourceId: string;
    resourceName: string;
    location: string;
    ambiguous: boolean;
    error: string;
    hops: IExampleMessageLocationHop[];
    contexts: IExampleMessageLocationContext[];
}

export interface IExampleMessageValidationResult {
    html: string;
    error: string;
    errors: number;
    alerts: number;
    warnings: number;
    affirmatives: number;
    informationals: number;
    entries: IExampleMessageValidationEntry[];
}

export interface MessageElement {
    name: string;
    positionalPath: string;
    profilePath: string;
    hl7Path: string;
    start: {
        line: number;
        column: number;
    };
    end: {
        line: number;
        column: number;
    };
}

export function normalizeExampleName(name: string): string {
    return (name || '').trim().toLowerCase();
}

export function siblingMessageNames(ig: IgExampleMessages, messageId: string): string[] {
    const names: string[] = [];
    for (const group of (ig && ig.profileExampleMessages) || []) {
        const messages = group.exampleMessages || [];
        if (!messages.some((message) => message.id === messageId)) {
            continue;
        }
        for (const message of messages) {
            if (message.id !== messageId) {
                names.push(message.name);
            }
        }
    }
    return names;
}

export function siblingSnippetNames(ig: IgExampleMessages, messageId: string, snippetId: string): string[] {
    for (const group of (ig && ig.profileExampleMessages) || []) {
        for (const message of group.exampleMessages || []) {
            if (message.id === messageId) {
                return (message.snippets || [])
                    .filter((snippet) => snippet.id !== snippetId)
                    .map((snippet) => snippet.name);
            }
        }
    }
    return [];
}

export function exampleNameValidator(existing: string[]): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const value = control.value == null ? '' : String(control.value);
        if (!value.trim()) {
            return { required: true };
        }
        if (isDuplicateExampleName(value, existing)) {
            return { duplicated: value.trim() + ' is already used' };
        }
        return null;
    };
}

export function exampleNameError(control: AbstractControl): string {
    if (!control || !control.errors) {
        return '';
    }
    if (control.errors.required) {
        return 'Name is required';
    }
    if (control.errors.duplicated) {
        return control.errors.duplicated;
    }
    return '';
}

export function isDuplicateExampleName(name: string, existing: string[]): boolean {
    const normalized = normalizeExampleName(name);
    if (!normalized) {
        return false;
    }
    return (existing || []).some((entry) => normalizeExampleName(entry) === normalized);
}

export function nextCopyName(base: string, existing: string[]): string {
    const names = (existing || []).map((entry) => normalizeExampleName(entry));
    const root = (base || 'Copy').trim() || 'Copy';
    let candidate = root + ' copy';
    let index = 2;
    while (names.indexOf(normalizeExampleName(candidate)) >= 0) {
        candidate = root + ' copy ' + index;
        index += 1;
    }
    return candidate;
}

export function withUpdatedMessageName(ig: IgExampleMessages, messageId: string, name: string): IgExampleMessages {
    if (!ig || !ig.profileExampleMessages) {
        return ig;
    }
    return {
        ...ig,
        profileExampleMessages: ig.profileExampleMessages.map((group) => ({
            ...group,
            exampleMessages: (group.exampleMessages || []).map((message) =>
                message.id === messageId ? { ...message, name } : message
            ),
        })),
    };
}

export function withUpdatedSnippetName(ig: IgExampleMessages, messageId: string, snippetId: string, name: string): IgExampleMessages {
    if (!ig || !ig.profileExampleMessages) {
        return ig;
    }
    return {
        ...ig,
        profileExampleMessages: ig.profileExampleMessages.map((group) => ({
            ...group,
            exampleMessages: (group.exampleMessages || []).map((message) => {
                if (message.id !== messageId) {
                    return message;
                }
                return {
                    ...message,
                    snippets: (message.snippets || []).map((snippet) =>
                        snippet.id === snippetId ? { ...snippet, name } : snippet
                    ),
                };
            }),
        })),
    };
}
