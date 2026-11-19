import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

window.addEventListener('pageshow', event => {
  if (event.persisted) window.location.reload();
});

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
