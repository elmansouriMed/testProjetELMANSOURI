// createdBy ELMANSOURI Mohamed
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  input,
  output,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Loader } from '@shared/components/loader/loader';
import { MessageDetail, MessageSummary } from '../../models/message.model';
import { downloadMessage } from '../../utils/message-download';
import { MessageStatusComponent } from '../message-status/message-status';

@Component({
  selector: 'app-message-detail',
  imports: [DatePipe, Loader, MessageStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './message-detail.html',
  styleUrl: './message-detail.css',
})
export class MessageDetailComponent {
  readonly message = input.required<MessageSummary>();
  readonly detail = input<MessageDetail | null>(null);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly closed = output<void>();
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }
  cancel(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }
  backdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closed.emit();
  }
  download(): void {
    const detail = this.detail();
    if (detail) downloadMessage(detail);
  }
}
