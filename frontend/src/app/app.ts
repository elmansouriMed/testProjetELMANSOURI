// createdBy ELMANSOURI Mohamed
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from './core/layout/header/header';

@Component({
  selector: 'app-root',
  imports: [Header, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-header /><router-outlet />`,
})
export class App {}
