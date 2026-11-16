import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PreferencesService } from './preferences.service';
export const localeInterceptor:HttpInterceptorFn=(request,next)=>{
  const language=inject(PreferencesService).language();
  return next(request.url.startsWith('/api/')?request.clone({setHeaders:{'Accept-Language':language}}):request);
};
