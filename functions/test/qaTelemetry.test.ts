import {describe,expect,it} from 'vitest';
import {qaTelemetryInput} from '../src/validation/input';

const now=new Date().toISOString();
const base={anonymousDeviceId:'SOV41_QA',requestId:'6f1f6f1f-1111-4111-8111-111111111111',events:[{eventId:'7f2f7f2f-2222-4222-8222-222222222222',type:'PROCESS_START',timestamp:now,data:{daily_steps:1,hourly_steps:{'23':1}}}]};

describe('QA telemetry schema',()=>{
 it('accepts the bounded event schema and hourly map',()=>expect(qaTelemetryInput(base).events).toHaveLength(1));
 it('rejects unknown fields and raw identifiers',()=>{
  expect(()=>qaTelemetryInput({...base,events:[{...base.events[0],data:{uid:'raw'}}]})).toThrow('invalid_telemetry_schema');
  expect(()=>qaTelemetryInput({...base,unexpected:true})).toThrow('invalid_request');
 });
 it('rejects an oversized batch',()=>expect(()=>qaTelemetryInput({...base,events:Array.from({length:51},(_,i)=>({...base.events[0],eventId:`7f2f7f2f-2222-4222-8222-${String(i).padStart(12,'0')}`}))})).toThrow('invalid_telemetry_batch'));
});
