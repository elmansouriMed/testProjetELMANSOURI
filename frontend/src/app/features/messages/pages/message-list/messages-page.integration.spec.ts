// createdBy ELMANSOURI Mohamed
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { apiErrorInterceptor } from '@core/interceptors/error.interceptor';
import { SAMPLE_MESSAGE } from '../../testing/message.fixture';
import { MessagesPage } from './messages-page';

describe('Intégration de la page de consultation', () => {
  let fixture: ComponentFixture<MessagesPage>;
  let http: HttpTestingController;
  const originalDialog = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');

  beforeAll(() =>
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: vi.fn(),
    }),
  );
  afterAll(() => {
    if (originalDialog)
      Object.defineProperty(HTMLDialogElement.prototype, 'showModal', originalDialog);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  });
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MessagesPage],
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MessagesPage);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  function button(text: string): HTMLButtonElement {
    const found = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((element) => element.textContent?.trim() === text);
    if (!found) throw new Error(`Bouton absent : ${text}`);
    return found;
  }
  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function initialPage(): void {
    http.expectOne('/api/messages?limit=25').flush({
      items: [SAMPLE_MESSAGE],
      nextCursor: null,
      hasMore: false,
    });
  }

  it('relie la saisie, la recherche HTTP, le tableau et la pagination avec les mêmes filtres', async () => {
    initialPage();
    await render();
    const source: HTMLInputElement = fixture.nativeElement.querySelector(
      'input[formControlName="source"]',
    );
    source.value = 'BO-SWIFT';
    source.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne((req) => req.params.get('source') === 'BO-SWIFT');
    expect(request.request.params.has('beforeId')).toBe(false);
    request.flush({
      items: [{ ...SAMPLE_MESSAGE, sourceApplication: 'BO-SWIFT' }],
      nextCursor: '43',
      hasMore: true,
    });
    await render();
    expect(fixture.nativeElement.querySelector('tbody .source-label').textContent).toContain(
      'BO-SWIFT',
    );
    button('Plus anciens').click();
    const next = http.expectOne((req) => req.params.get('beforeId') === '43');
    expect(next.request.params.get('source')).toBe('BO-SWIFT');
    next.flush({
      items: [{ ...SAMPLE_MESSAGE, id: '42', sourceApplication: 'BO-SWIFT' }],
      nextCursor: null,
      hasMore: false,
    });
    await render();
    expect(fixture.nativeElement.querySelector('.pagination').textContent).toContain('Page 2');
    expect(button('Plus anciens').disabled).toBe(true);
  });

  it('ouvre le détail depuis le tableau et affiche une erreur 404 sans perdre la liste', async () => {
    initialPage();
    await render();
    fixture.nativeElement.querySelector('.message-link').click();
    http
      .expectOne('/api/messages/43')
      .flush({ detail: 'technical data' }, { status: 404, statusText: 'Not Found' });
    await render();
    expect(fixture.nativeElement.querySelector('dialog [role="alert"]').textContent).toContain(
      'introuvable',
    );
    expect(fixture.nativeElement.textContent).not.toContain('technical data');
    button('Fermer').click();
    await render();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('remplace une erreur de stockage par les résultats après une actualisation réussie', async () => {
    http
      .expectOne('/api/messages?limit=25')
      .flush({ detail: 'SELECT secret' }, { status: 503, statusText: 'Unavailable' });
    await render();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('stockage');
    expect(fixture.nativeElement.textContent).not.toContain('SELECT secret');
    button('Actualiser').click();
    initialPage();
    await render();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
  });
});
