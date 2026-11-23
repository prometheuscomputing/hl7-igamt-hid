import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { IExampleMessageHighlightRange, styleEr7Html } from '../../froala/example-message.plugin';
import { er7Lines, IEr7Summary, summarizeEr7 } from '../../functions/er7';

/**
 * Read-only ER7 text with what MSH says above it. The text is drawn by the function that draws an
 * example placed in a text section, so the colours and the highlight of a snippet are the same.
 */
@Component({
  selector: 'app-er7-view',
  templateUrl: './er7-view.component.html',
  styleUrls: ['./er7-view.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Er7ViewComponent implements OnChanges {

  static readonly MAX_LINES = 150;

  @Input() text: string;
  /** The parts to mark in yellow, as lines and columns of `text` counted from 0. */
  @Input() highlights: IExampleMessageHighlightRange[] = [];
  /** Show message type, version, profile and segment count above the text. Off for a snippet, which has no MSH of its own. */
  @Input() showSummary = true;
  /** Off to show only what MSH says, next to an editor that already shows the text. */
  @Input() showText = true;
  @Input() maxHeight = '260px';
  @Input() emptyLabel = 'No message text.';

  /** Escaped by styleEr7Html: only its own spans are markup. */
  html = '';
  hidden = 0;
  summary: IEr7Summary = summarizeEr7('');

  ngOnChanges() {
    // Lines are kept as they are, blank ones too, so that the highlights still point at the right place.
    const all = String(this.text || '').replace(/[\r\n]+$/, '').split(/\r\n|\n|\r/);
    this.summary = summarizeEr7(this.text);
    this.hidden = Math.max(0, all.length - Er7ViewComponent.MAX_LINES);
    this.html = er7Lines(this.text).length ? styleEr7Html(all.slice(0, Er7ViewComponent.MAX_LINES).join('\n'), this.highlights || []) : '';
  }
}
