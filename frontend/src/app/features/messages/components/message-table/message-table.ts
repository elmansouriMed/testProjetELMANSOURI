// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MessageSummary } from '../../models/message.model';
import { MessageStatusComponent } from '../message-status/message-status';

@Component({
  selector: 'app-message-table',
  imports: [DatePipe, MessageStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './message-table.html',
  styleUrl: './message-table.css',
})
export class MessageTable {
  readonly messages = input.required<readonly MessageSummary[]>();
  readonly messageSelected = output<MessageSummary>();
}
