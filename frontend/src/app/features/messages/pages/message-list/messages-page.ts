// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Loader } from '@shared/components/loader/loader';
import { MessageFiltersComponent } from '../../components/message-filters/message-filters';
import { MessageTable } from '../../components/message-table/message-table';
import { MessageDetailComponent } from '../../components/message-detail/message-detail';
import { MessagesStore } from '../../state/messages.store';

@Component({
  selector: 'app-messages-page',
  imports: [Loader, MessageFiltersComponent, MessageTable, MessageDetailComponent],
  providers: [MessagesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './messages-page.html',
  styleUrl: './messages-page.css',
})
export class MessagesPage {
  readonly store = inject(MessagesStore);
}
