// createdBy ELMANSOURI Mohamed
import { Routes } from '@angular/router';

export const MESSAGES_ROUTES: Routes = [
  {
    path: '',
    title: 'Messages de paiement',
    loadComponent: () =>
      import('./pages/message-list/messages-page').then((module) => module.MessagesPage),
  },
];
