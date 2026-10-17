// createdBy ELMANSOURI Mohamed
import { TestBed } from '@angular/core/testing';
import { MessageFiltersComponent } from './message-filters';
import { EMPTY_FILTERS } from '../../models/message.model';

describe('MessageFiltersComponent', () => {
  function create() {
    return TestBed.createComponent(MessageFiltersComponent).componentInstance;
  }
  beforeEach(() => TestBed.configureTestingModule({ imports: [MessageFiltersComponent] }));
  it('refuse une période inversée sans lancer de recherche', () => {
    const component = create();
    const applied = vi.fn();
    component.filtersApplied.subscribe(applied);
    component.form.patchValue({
      from: '2026-10-18T12:00',
      to: '2026-10-17T12:00',
    });
    component.apply();
    expect(applied).not.toHaveBeenCalled();
    expect(component.error()).toContain('date de début');
  });
  it('convertit les dates locales en dates ISO pour l’API', () => {
    const component = create();
    const applied = vi.fn();
    component.filtersApplied.subscribe(applied);
    component.form.patchValue({ from: '2026-10-17T12:00', source: 'BO-SEPA' });
    component.apply();
    expect(applied).toHaveBeenCalledWith(
      expect.objectContaining({
        source: 'BO-SEPA',
        from: new Date('2026-10-17T12:00').toISOString(),
      }),
    );
  });
  it('valide aussi les limites de saisie sans dépendre du maxlength HTML', () => {
    const component = create();
    const applied = vi.fn();
    component.filtersApplied.subscribe(applied);
    component.form.patchValue({ source: 'a'.repeat(129) });
    component.apply();
    expect(applied).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
  });

  it('réinitialise les filtres et l’erreur avant d’émettre une recherche vide', () => {
    const component = create();
    const applied = vi.fn();
    component.filtersApplied.subscribe(applied);
    component.form.patchValue({
      source: 'BO-SEPA',
      from: '2026-10-18T12:00',
      to: '2026-10-17T12:00',
    });
    component.apply();
    expect(component.error()).not.toBeNull();
    component.reset();
    expect(component.error()).toBeNull();
    expect(component.form.valid).toBe(true);
    expect(applied).toHaveBeenCalledExactlyOnceWith(EMPTY_FILTERS);
  });

  it('refuse aussi une période vide dont les deux bornes sont égales', () => {
    const component = create();
    const applied = vi.fn();
    component.filtersApplied.subscribe(applied);
    component.form.patchValue({ from: '2026-10-17T12:00', to: '2026-10-17T12:00' });
    component.apply();
    expect(applied).not.toHaveBeenCalled();
    expect(component.form.hasError('dateRange')).toBe(true);
  });
});
