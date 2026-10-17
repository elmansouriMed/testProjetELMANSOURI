// createdBy ELMANSOURI Mohamed
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'messages' },
  {
    path: 'messages',
    loadChildren: () =>
      import('./features/messages/messages.routes').then((module) => module.MESSAGES_ROUTES),
  },
  { path: '**', redirectTo: 'messages' },
];
