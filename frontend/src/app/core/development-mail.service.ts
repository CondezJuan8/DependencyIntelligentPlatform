import { effect,inject,Injectable,signal } from '@angular/core';
import { SessionApiService } from './session-api.service';
@Injectable({providedIn:'root'})
export class DevelopmentMailService{
 readonly api=inject(SessionApiService);readonly enabled=signal(false);readonly ownMail=signal(false);
 constructor(){effect(()=>{const id=this.api.account()?.id;this.enabled.set(false);this.ownMail.set(false);if(id)this.api.get<{enabled:boolean;ownMail:boolean}>('/api/account/mail-preview/availability').subscribe({next:value=>{if(this.api.account()?.id===id){this.enabled.set(value.enabled);this.ownMail.set(value.ownMail);}},error:()=>this.enabled.set(false)});});}
}
