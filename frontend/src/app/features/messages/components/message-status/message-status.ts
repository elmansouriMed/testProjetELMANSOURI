// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MessageStatus } from '../../models/message.model';

@Component({
  selector: 'app-message-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class.quarantined]="status() === 'QUARANTINED'">{{
    status() === 'STORED' ? 'Archivé' : 'En quarantaine'
  }}</span>`,
  styles: `
    span {
      display: inline-block;
      background: #e9f5ee;
      color: #276d49;
      border-radius: 4px;
      padding: 5px 7px;
      font-size: 10px;
      white-space: nowrap;
    }
    span.quarantined {
      background: #fff1dc;
      color: #936318;
    }
  `,
})
export class MessageStatusComponent {
  readonly status = input.required<MessageStatus>();
}
