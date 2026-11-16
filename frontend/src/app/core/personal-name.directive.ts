import { Directive,forwardRef } from '@angular/core';
import { AbstractControl,NG_VALIDATORS,ValidationErrors,Validator } from '@angular/forms';
export function validPersonalName(value:string):boolean{return value.length<=200 && /^[\p{L}\p{M}]+(?:[ '’\-][\p{L}\p{M}]+)*$/u.test(value.trim());}
@Directive({selector:'[appPersonalName]',standalone:true,providers:[{provide:NG_VALIDATORS,useExisting:forwardRef(()=>PersonalNameDirective),multi:true}]})
export class PersonalNameDirective implements Validator {validate(control:AbstractControl):ValidationErrors|null{return !control.value||validPersonalName(String(control.value))?null:{personalName:true};}}
