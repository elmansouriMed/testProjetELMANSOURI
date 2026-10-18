// createdBy ELMANSOURI Mohamed
import { TestBed } from '@angular/core/testing';
import { SAMPLE_MESSAGE } from '../../testing/message.fixture';
import { MessageDetail } from '../../models/message.model';
import { MessageDetailComponent } from './message-detail';

describe('MessageDetailComponent', () => {
  const original = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
  beforeAll(() =>
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: vi.fn(),
    }),
  );
  afterAll(() => {
    if (original) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', original);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  });
  beforeEach(() => TestBed.configureTestingModule({ imports: [MessageDetailComponent] }));
  function render(detail: MessageDetail) {
    const fixture = TestBed.createComponent(MessageDetailComponent);
    fixture.componentRef.setInput('message', detail.metadata);
    fixture.componentRef.setInput('detail', detail);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }
  it('affiche les éventuelles balises HTML comme du texte', () => {
    const payload = '<img src="x" onerror="alert(1)">';
    const element = render({
      metadata: SAMPLE_MESSAGE,
      textPayload: payload,
      base64Payload: null,
    });
    expect(element.querySelector('.payload')?.textContent).toBe(payload);
    expect(element.querySelector('.payload img')).toBeNull();
  });
  it('permet de consulter un contenu binaire en Base64', () => {
    const element = render({
      metadata: { ...SAMPLE_MESSAGE, bodyType: 'BYTES' },
      textPayload: null,
      base64Payload: 'AAH/',
    });
    expect(element.querySelector('h3')?.textContent).toContain('Base64');
    expect(element.querySelector('.payload')?.textContent).toBe('AAH/');
  });
  it('explique une quarantaine sans afficher un contenu absent', () => {
    const element = render({
      metadata: {
        ...SAMPLE_MESSAGE,
        status: 'QUARANTINED',
        bodyType: 'UNSUPPORTED',
        rejectionReason: 'Type JMS non pris en charge',
      },
      textPayload: null,
      base64Payload: null,
    });
    expect(element.querySelector('.quarantine-note')?.textContent).toContain('Type JMS');
    expect(element.querySelector('.payload')).toBeNull();
    expect(element.textContent).toContain('pas disponible à la consultation');
  });
});
