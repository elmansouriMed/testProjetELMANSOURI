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
});
