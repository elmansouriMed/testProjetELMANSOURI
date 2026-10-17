// createdBy ELMANSOURI Mohamed
import { TestBed } from '@angular/core/testing';
import { SAMPLE_MESSAGE } from '../../testing/message.fixture';
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
  it('affiche les éventuelles balises HTML comme du texte', () => {
    const fixture = TestBed.createComponent(MessageDetailComponent);
    const payload = '<img src="x" onerror="alert(1)">';
    fixture.componentRef.setInput('message', SAMPLE_MESSAGE);
    fixture.componentRef.setInput('detail', {
      metadata: SAMPLE_MESSAGE,
      textPayload: payload,
      base64Payload: null,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.payload').textContent).toBe(payload);
    expect(fixture.nativeElement.querySelector('.payload img')).toBeNull();
  });
});
