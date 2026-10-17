// createdBy ELMANSOURI Mohamed
import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { App } from './app/app';
import { appConfig } from './app/app.config';

registerLocaleData(localeFr);
bootstrapApplication(App, appConfig).catch(console.error);
