import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { er7Lines, IEr7Summary, IEr7Token, summarizeEr7, tokenizeEr7Line } from '../../functions/er7';

/** Read-only ER7 text, coloured like the message editor, with what MSH says above it. */
@Component({
  selector: 'app-er7-view',
  templateUrl: './er7-view.component.html',
  styleUrls: ['./er7-view.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Er7ViewComponent implements OnChanges {

  static readonly MAX_LINES = 150;

  @Input() text: string;
  /** Show message type, version, profile and segment count above the text. Off for a snippet, which has no MSH of its own. */
  @Input() showSummary = true;
  /** Off to show only what MSH says, next to an editor that already shows the text. */
  @Input() showText = true;
  @Input() maxHeight = '260px';
  @Input() emptyLabel = 'No message text.';

  lines: IEr7Token[][] = [];
  hidden = 0;
  summary: IEr7Summary = summarizeEr7('');

  ngOnChanges() {
    const all = er7Lines(this.text);
    this.summary = summarizeEr7(this.text);
    this.hidden = Math.max(0, all.length - Er7ViewComponent.MAX_LINES);
    this.lines = all.slice(0, Er7ViewComponent.MAX_LINES).map((line) => tokenizeEr7Line(line));
  }
}
