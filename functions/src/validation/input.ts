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
const telemetryDataKeys=new Set(['timestamp_utc','local_timestamp','timezone','app_version_name','version_code','build_flavor','device_model','android_version','api_level','app_process_start','app_process_stop','daily_steps','hourly_steps','sensor_counter','previous_sensor_counter','sensor_delta','tracking_requested','tracking_state','foreground_service_state','current_session','session_count','unallocated_steps','long_gap_increment','counter_reset','boot_session_state','distance','walking_duration','kcal','average_speed','total','eligible','restricted','excluded','unknown','integrity_version','classification_reason','source_revision','official_steps','submit_official_progress_started','accepted','duplicate','stale','failure','error_category','elapsed_time','last_successful_sync','retry_state','challenge_state','self_competition_steps','opponent_competition_steps','self_reward_steps','opponent_reward_steps','listener_state','restored_from','next_challenge','matchmaking_state','reservation_state','exception_class','sanitized_message','operation','retry_count','network_state','firebase_callable_failure','listener_failure','work_manager_failure','crash']);
const sensitiveKey=/(?:uid|email|token|secret|credential|password|ssid|bssid|challengeid|access.?key)/i;
const sensitiveValue=/(?:bearer\s+|access_token|refresh_token|AIza[0-9A-Za-z_-]{20,}|@)/i;
function telemetryMap(data:unknown, nested=false):Record<string,unknown>{
 const d=data as Record<string,unknown>;
 if(!d||typeof d!=='object'||Array.isArray(d))throw new HttpsError('invalid-argument','invalid_request');
 if(Object.keys(d).some(k=>sensitiveKey.test(k)||(!nested&&!telemetryDataKeys.has(k))|| (nested&&!/^\d{1,2}$/.test(k))))throw new HttpsError('invalid-argument','invalid_telemetry_schema');
 for(const [key,value] of Object.entries(d)){if(typeof value==='string'&&(value.length>512||sensitiveValue.test(value)))throw new HttpsError('invalid-argument','invalid_telemetry_value');if(value!==null&&typeof value==='object'&&!Array.isArray(value))telemetryMap(value,key==='hourly_steps');if(Array.isArray(value)&&value.length>128)throw new HttpsError('invalid-argument','invalid_telemetry_value');}
 return d;
}
function telemetryTimestamp(value:unknown):string{if(typeof value!=='string')throw new HttpsError('invalid-argument','invalid_telemetry_timestamp');const epoch=Date.parse(value);if(!Number.isFinite(epoch)||epoch<Date.now()-8*24*60*60*1000||epoch>Date.now()+10*60*1000)throw new HttpsError('invalid-argument','invalid_telemetry_timestamp');return value}
export function qaTelemetryInput(data:unknown){
 const d=strictObject(data,['anonymousDeviceId','requestId','events','snapshot']);
 if(typeof d.anonymousDeviceId!=='string'||!/^[A-Z0-9_]{3,64}$/.test(d.anonymousDeviceId))throw new HttpsError('invalid-argument','invalid_device_id');
 if(typeof d.requestId!=='string'||!/^[0-9a-f-]{36}$/i.test(d.requestId))throw new HttpsError('invalid-argument','invalid_request_id');
 if(!Array.isArray(d.events)||d.events.length>50)throw new HttpsError('invalid-argument','invalid_telemetry_batch');
 const events=d.events.map(value=>{const event=strictObject(value,['eventId','type','timestamp','data']);if(typeof event.eventId!=='string'||!/^[0-9a-f-]{36}$/i.test(event.eventId))throw new HttpsError('invalid-argument','invalid_event_id');if(typeof event.type!=='string'||!/^[A-Z][A-Z0-9_]{0,63}$/.test(event.type))throw new HttpsError('invalid-argument','invalid_event_type');return {eventId:event.eventId,type:event.type,timestamp:telemetryTimestamp(event.timestamp),data:telemetryMap(event.data)}});
 let snapshot:{snapshotId:string;timestamp:string;data:Record<string,unknown>}|undefined;
 if(d.snapshot!==undefined){const value=strictObject(d.snapshot,['snapshotId','timestamp','data']);if(typeof value.snapshotId!=='string'||!/^snapshot-[0-9]+$/.test(value.snapshotId))throw new HttpsError('invalid-argument','invalid_snapshot_id');snapshot={snapshotId:value.snapshotId,timestamp:telemetryTimestamp(value.timestamp),data:telemetryMap(value.data)}}
 return {anonymousDeviceId:d.anonymousDeviceId as string,requestId:d.requestId as string,events,snapshot};
}
