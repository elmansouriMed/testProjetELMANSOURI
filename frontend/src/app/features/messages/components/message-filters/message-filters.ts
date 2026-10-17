// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MessageFilters, MessageStatus } from '../../models/message.model';

export function dateRangeValidator(control: AbstractControl): ValidationErrors | null {
  const from = String(control.get('from')?.value ?? '');
  const to = String(control.get('to')?.value ?? '');
  const start = from ? new Date(from).getTime() : null;
  const end = to ? new Date(to).getTime() : null;
  return (start !== null && !Number.isFinite(start)) ||
    (end !== null && !Number.isFinite(end)) ||
    (start !== null && end !== null && start >= end)
    ? { dateRange: true }
    : null;
}

@Component({
  selector: 'app-message-filters',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './message-filters.html',
  styleUrl: './message-filters.css',
})
export class MessageFiltersComponent {
  private readonly builder = inject(FormBuilder).nonNullable;
  readonly filtersApplied = output<MessageFilters>();
  readonly error = signal<string | null>(null);
  readonly form = this.builder.group(
    {
      mqMessageId: ['', Validators.maxLength(512)],
      source: ['', Validators.maxLength(128)],
      status: this.builder.control<MessageStatus | ''>(''),
      correlationId: ['', Validators.maxLength(512)],
      from: '',
      to: '',
    },
    { validators: dateRangeValidator },
  );

  apply(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(
        this.form.hasError('dateRange')
          ? 'La date de début doit précéder la date de fin.'
          : 'Un filtre dépasse la longueur autorisée.',
      );
      return;
    }
    const values = this.form.getRawValue();
    this.error.set(null);
    this.filtersApplied.emit({
      ...values,
      from: values.from ? new Date(values.from).toISOString() : '',
      to: values.to ? new Date(values.to).toISOString() : '',
    });
  }
  reset(): void {
    this.form.reset();
    this.apply();
  }
}
