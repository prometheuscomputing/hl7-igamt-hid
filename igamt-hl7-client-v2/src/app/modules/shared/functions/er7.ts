/** One coloured piece of a line of ER7 text. The class names are those of the editor's hl7v2 mode. */
export interface IEr7Token {
  text: string;
  cls: string;
}

/** What the MSH segment says about a message. */
export interface IEr7Summary {
  segments: number;
  startsWithMsh: boolean;
  messageType: string;
  version: string;
  profile: string;
}

const SEPARATOR_CLASS: { [separator: string]: string } = {
  '|': 'cm-field-separator',
  '^': 'cm-component-separator',
  '&': 'cm-subcomponent-separator',
  '~': 'cm-continuation-separator',
};

/** Pasted or uploaded text as the editor stores it: one segment per line, no framing bytes, no blank lines. */
export function normalizeEr7(text: string): string {
  return er7Lines(text).join('\n');
}

export function er7Lines(text: string): string[] {
  return String(text || '')
    .replace(/[\u000b\u001c﻿]/g, '')
    .split(/\r\n|\n|\r/)
    .map((line) => line.replace(/\s+$/, ''))
    .filter((line) => line.length > 0);
}

export function tokenizeEr7Line(line: string): IEr7Token[] {
  const tokens: IEr7Token[] = [{ text: line.substring(0, 3), cls: 'cm-segment-name' }];
  let plain = '';
  for (let i = 3; i < line.length; i++) {
    const cls = SEPARATOR_CLASS[line.charAt(i)];
    if (cls) {
      if (plain) {
        tokens.push({ text: plain, cls: '' });
        plain = '';
      }
      tokens.push({ text: line.charAt(i), cls });
    } else {
      plain += line.charAt(i);
    }
  }
  if (plain) {
    tokens.push({ text: plain, cls: '' });
  }
  return tokens;
}

export function summarizeEr7(text: string): IEr7Summary {
  const lines = er7Lines(text);
  const summary: IEr7Summary = { segments: lines.length, startsWithMsh: false, messageType: '', version: '', profile: '' };
  const msh = lines.length ? lines[0] : '';
  if (msh.substring(0, 3) !== 'MSH' || msh.length < 8) {
    return summary;
  }
  summary.startsWithMsh = true;
  const repetition = msh.charAt(5);
  // MSH-1 is the field separator itself, so MSH-n is the piece at n - 1.
  const fields = msh.split(msh.charAt(3));
  summary.messageType = fields[8] || '';
  summary.version = (fields[11] || '').split(msh.charAt(4))[0];
  summary.profile = (fields[20] || '').split(repetition).filter((value) => !!value).join(', ');
  return summary;
}
