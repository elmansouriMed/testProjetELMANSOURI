// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-loader',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p role="status">{{ message() }}</p>`,
  styles: `
    p {
      padding: 24px;
      color: #687b89;
      font-size: 13px;
    }
  `,
})
export class Loader {
  readonly message = input('Chargement des messages…');
}
