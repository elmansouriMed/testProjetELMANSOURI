// createdBy ELMANSOURI Mohamed
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { apiErrorInterceptor } from '@core/interceptors/error.interceptor';
import { EMPTY_FILTERS, EMPTY_PAGE } from '../models/message.model';
import { SAMPLE_MESSAGE } from '../testing/message.fixture';
import { MessagesStore } from './messages.store';

describe('MessagesStore', () => {
  let store: MessagesStore;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MessagesStore,
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(MessagesStore);
  });
  afterEach(() => http.verify());

  it('annule une recherche obsolète et conserve seulement la réponse courante', () => {
    const previous = http.expectOne('/api/messages?limit=25');
    store.search({ ...EMPTY_FILTERS, source: 'BO-SEPA' });
    expect(previous.cancelled).toBe(true);
    http
      .expectOne((request) => request.params.get('source') === 'BO-SEPA')
      .flush({ items: [SAMPLE_MESSAGE], nextCursor: null, hasMore: false });
    expect(store.page().items).toEqual([SAMPLE_MESSAGE]);
    expect(store.state().loading).toBe(false);
  });
  it('reste utilisable après une erreur HTTP', () => {
    http.expectOne('/api/messages?limit=25').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(store.state().error).toContain('stockage');
    expect(store.state().loading).toBe(false);
    store.refresh();
    http.expectOne('/api/messages?limit=25').flush(EMPTY_PAGE);
    expect(store.state().error).toBeNull();
  });
  it('conserve les filtres lors du passage à la page suivante et du retour', () => {
    http.expectOne('/api/messages?limit=25').flush(EMPTY_PAGE);
    store.search({ ...EMPTY_FILTERS, source: 'BO-SEPA' });
    http
      .expectOne((request) => request.params.get('source') === 'BO-SEPA')
      .flush({ items: [SAMPLE_MESSAGE], nextCursor: '43', hasMore: true });
    store.older();
    const older = http.expectOne((request) => request.params.get('beforeId') === '43');
    expect(older.request.params.get('source')).toBe('BO-SEPA');
    older.flush({
      items: [{ ...SAMPLE_MESSAGE, id: '42' }],
      nextCursor: null,
      hasMore: false,
    });
    expect(store.pageNumber()).toBe(2);
    store.newer();
    const newer = http.expectOne((request) => request.url === '/api/messages');
    expect(newer.request.params.has('beforeId')).toBe(false);
    expect(newer.request.params.get('source')).toBe('BO-SEPA');
    newer.flush(EMPTY_PAGE);
    expect(store.pageNumber()).toBe(1);
  });
  it('annule le chargement du détail quand la fenêtre est fermée', () => {
    http.expectOne('/api/messages?limit=25').flush(EMPTY_PAGE);
    store.select(SAMPLE_MESSAGE);
    const detail = http.expectOne('/api/messages/43');
    store.closeDetail();
    expect(detail.cancelled).toBe(true);
    expect(store.selectedMessage()).toBeNull();
    expect(store.detailState().loading).toBe(false);
  });
  it('libère les requêtes quand son contexte Angular est détruit', () => {
    const request = http.expectOne('/api/messages?limit=25');
    TestBed.resetTestingModule();
    expect(request.cancelled).toBe(true);
  });

  it('annule le premier détail quand un autre message est sélectionné', () => {
    http.expectOne('/api/messages?limit=25').flush(EMPTY_PAGE);
    store.select(SAMPLE_MESSAGE);
    const first = http.expectOne('/api/messages/43');
    const selected = { ...SAMPLE_MESSAGE, id: '9007199254740993' };
    store.select(selected);
    expect(first.cancelled).toBe(true);
    http.expectOne('/api/messages/9007199254740993').flush({
      metadata: selected,
      textPayload: 'second',
      base64Payload: null,
    });
    expect(store.selectedMessage()).toEqual(selected);
    expect(store.detailState().data?.textPayload).toBe('second');
  });

  it('ignore les doubles clics de pagination pendant le chargement et la fin de liste', () => {
    http
      .expectOne('/api/messages?limit=25')
      .flush({ items: [SAMPLE_MESSAGE], nextCursor: '43', hasMore: true });
    store.older();
    const request = http.expectOne((req) => req.params.get('beforeId') === '43');
    store.older();
    store.newer();
    http.expectNone((req) => req.url === '/api/messages');
    expect(store.pageNumber()).toBe(2);
    request.flush(EMPTY_PAGE);
    store.older();
    http.expectNone((req) => req.url === '/api/messages');
    expect(store.pageNumber()).toBe(2);
  });

  it('une nouvelle recherche supprime le curseur et l’historique de la précédente', () => {
    http
      .expectOne('/api/messages?limit=25')
      .flush({ items: [SAMPLE_MESSAGE], nextCursor: '43', hasMore: true });
    store.older();
    http.expectOne((req) => req.params.get('beforeId') === '43').flush(EMPTY_PAGE);
    store.search({ ...EMPTY_FILTERS, source: 'BO-SWIFT' });
    const request = http.expectOne((req) => req.params.get('source') === 'BO-SWIFT');
    expect(request.request.params.has('beforeId')).toBe(false);
    request.flush(EMPTY_PAGE);
    store.newer();
    http.expectNone((req) => req.url === '/api/messages');
    expect(store.pageNumber()).toBe(1);
  });
});
