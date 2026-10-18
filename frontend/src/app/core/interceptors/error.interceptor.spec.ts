// createdBy ELMANSOURI Mohamed
import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiError, apiErrorInterceptor } from './error.interceptor';

describe('apiErrorInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('traduit une panne SQL sans afficher les détails techniques du serveur', () => {
    let error: unknown;
    client.get('/api/messages').subscribe({ error: (value) => (error = value) });
    http
      .expectOne('/api/messages')
      .flush({ detail: 'jdbc:secret' }, { status: 503, statusText: 'Unavailable' });
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(503);
    expect((error as ApiError).message).not.toContain('secret');
  });
  it('ne modifie pas les erreurs des ressources hors API', () => {
    let error: unknown;
    client.get('/assets/test.json').subscribe({ error: (value) => (error = value) });
    http.expectOne('/assets/test.json').flush({}, { status: 404, statusText: 'Not Found' });
    expect(error).toBeInstanceOf(HttpErrorResponse);
  });

  it.each([
    [400, 'paramètres'],
    [404, 'introuvable'],
    [409, 'modifié'],
    [500, 'erreur'],
  ])('traduit le statut %i sans exposer le corps du serveur', (status, expected) => {
    let error: ApiError | undefined;
    client.get('/api/messages').subscribe({ error: (value) => (error = value) });
    http
      .expectOne('/api/messages')
      .flush(
        { detail: 'confidential technical data' },
        { status: Number(status), statusText: 'Error' },
      );
    expect(error).toBeInstanceOf(ApiError);
    expect(error?.status).toBe(status);
    expect(error?.message).toContain(String(expected));
    expect(error?.message).not.toContain('confidential');
  });

  it('distingue une panne réseau d’une réponse du serveur', () => {
    let error: ApiError | undefined;
    client.get('/api/messages').subscribe({ error: (value) => (error = value) });
    http.expectOne('/api/messages').error(new ProgressEvent('error'));
    expect(error?.status).toBe(0);
    expect(error?.message).toContain('connexion');
  });
});
