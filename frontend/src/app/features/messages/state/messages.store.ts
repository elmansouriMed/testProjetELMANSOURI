// createdBy ELMANSOURI Mohamed
import { Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '@core/interceptors/error.interceptor';
import { MessagesApi } from '../data-access/messages-api';
import {
  EMPTY_FILTERS,
  EMPTY_PAGE,
  LoadState,
  MessageDetail,
  MessageFilters,
  MessagePage,
  MessageSummary,
} from '../models/message.model';

interface Query {
  readonly filters: MessageFilters;
  readonly cursor: string | null;
}
const loadingPage: LoadState<MessagePage> = {
  data: EMPTY_PAGE,
  loading: true,
  error: null,
};
const idleDetail: LoadState<MessageDetail> = {
  data: null,
  loading: false,
  error: null,
};

/** Façade locale à la page : les composants ne connaissent ni HttpClient ni les abonnements. */
@Injectable()
export class MessagesStore {
  private readonly api = inject(MessagesApi);
  private readonly queries = new BehaviorSubject<Query>({
    filters: EMPTY_FILTERS,
    cursor: null,
  });
  private readonly selectedIds = new BehaviorSubject<string | null>(null);
  private previousCursors: (string | null)[] = [];
  private readonly currentPage = signal(1);
  private readonly selection = signal<MessageSummary | null>(null);
  readonly pageNumber = this.currentPage.asReadonly();
  readonly selectedMessage = this.selection.asReadonly();

  private readonly pageState$: Observable<LoadState<MessagePage>> = this.queries.pipe(
    // switchMap annule une requête devenue obsolète dès qu'une autre recherche commence.
    switchMap((query) =>
      this.api.list(query.filters, query.cursor).pipe(
        map((data): LoadState<MessagePage> => ({
          data,
          loading: false,
          error: null,
        })),
        catchError((error: unknown) =>
          of<LoadState<MessagePage>>({
            data: EMPTY_PAGE,
            loading: false,
            error:
              error instanceof ApiError ? error.message : 'Impossible de charger les messages.',
          }),
        ),
        startWith(loadingPage),
      ),
    ),
  );
  readonly state = toSignal(this.pageState$, { initialValue: loadingPage });
  readonly page = computed(() => this.state().data ?? EMPTY_PAGE);

  private readonly detailState$: Observable<LoadState<MessageDetail>> = this.selectedIds.pipe(
    switchMap((id) =>
      id === null
        ? of(idleDetail)
        : this.api.detail(id).pipe(
            map((data): LoadState<MessageDetail> => ({
              data,
              loading: false,
              error: null,
            })),
            catchError((error: unknown) =>
              of<LoadState<MessageDetail>>({
                data: null,
                loading: false,
                error:
                  error instanceof ApiError
                    ? error.message
                    : 'Le contenu du message est indisponible.',
              }),
            ),
            startWith<LoadState<MessageDetail>>({
              data: null,
              loading: true,
              error: null,
            }),
          ),
    ),
  );
  // toSignal gère la libération des deux abonnements à la destruction de la page.
  readonly detailState = toSignal(this.detailState$, {
    initialValue: idleDetail,
  });

  search(filters: MessageFilters): void {
    this.previousCursors = [];
    this.currentPage.set(1);
    this.queries.next({ filters: { ...filters }, cursor: null });
  }
  refresh(): void {
    this.search(this.queries.value.filters);
  }
  older(): void {
    if (this.state().loading || !this.page().nextCursor) return;
    this.previousCursors.push(this.queries.value.cursor);
    this.currentPage.update((page) => page + 1);
    this.queries.next({
      ...this.queries.value,
      cursor: this.page().nextCursor,
    });
  }
  newer(): void {
    if (this.state().loading || !this.previousCursors.length) return;
    const cursor = this.previousCursors.pop() ?? null;
    this.currentPage.update((page) => page - 1);
    this.queries.next({ ...this.queries.value, cursor });
  }
  select(message: MessageSummary): void {
    this.selection.set(message);
    this.selectedIds.next(message.id);
  }
  closeDetail(): void {
    this.selection.set(null);
    this.selectedIds.next(null);
  }
}
