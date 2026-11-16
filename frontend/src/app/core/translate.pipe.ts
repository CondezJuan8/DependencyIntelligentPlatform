import { inject,Pipe,PipeTransform } from '@angular/core';
import { PreferencesService } from './preferences.service';
@Pipe({name:'t',standalone:true,pure:false})
export class TranslatePipe implements PipeTransform {private readonly preferences=inject(PreferencesService);transform(value:unknown):string{return this.preferences.text(value);}}
