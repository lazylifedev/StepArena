import {HttpsError} from 'firebase-functions/v2/https';
import {ProgressInput,validDate,validTimezone} from '../domain/models';
export function progressInput(data:unknown):ProgressInput{
 const d=data as Record<string,unknown>; const ints=['totalSteps','eligibleSteps','restrictedSteps','excludedSteps','integrityVersion'];
 if(!d||typeof d!=='object'||!validDate(String(d.localDate))||!validTimezone(String(d.timezone))||typeof d.sourceRevision!=='string'||typeof d.requestId!=='string'||!/^[-\w:.]{1,128}$/.test(d.requestId as string)||ints.some(k=>!Number.isInteger(d[k])||(d[k] as number)<0)) throw new HttpsError('invalid-argument','invalid_progress');
 if((d.eligibleSteps as number)+(d.restrictedSteps as number)+(d.excludedSteps as number)!==(d.totalSteps as number)) throw new HttpsError('invalid-argument','inconsistent_steps');
 if(new Date(`${d.localDate}T00:00:00Z`).getTime()>Date.now()+86400000) throw new HttpsError('invalid-argument','future_date');
 return d as ProgressInput;
}
export function strictObject(data:unknown, allowed:string[]):Record<string,unknown>{
 const d=data as Record<string,unknown>;
 if(!d||typeof d!=='object'||Array.isArray(d)||Object.keys(d).some(k=>!allowed.includes(k))) throw new HttpsError('invalid-argument','invalid_request');
 return d;
}
export function requestIdInput(data:unknown){
 const d=strictObject(data,['reservationId','requestId']);
 if(typeof d.reservationId!=='string'||d.reservationId.length<1||d.reservationId.length>128||typeof d.requestId!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(d.requestId)) throw new HttpsError('invalid-argument','invalid_request');
 return d as {reservationId:string;requestId:string};
}
