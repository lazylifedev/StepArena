export type ProgressInput={localDate:string;timezone:string;totalSteps:number;eligibleSteps:number;restrictedSteps:number;excludedSteps:number;integrityVersion:number;sourceRevision:string;requestId:string};
export const MAX_OFFICIAL=100_000, MAX_REWARD=30_000;
export const officialSteps=(eligible:number)=>Math.min(Math.max(eligible,0),MAX_OFFICIAL);
export const competitionSteps=officialSteps;
export const rewardSteps=(eligible:number)=>Math.min(officialSteps(eligible),MAX_REWARD);
export function validDate(v:string){return /^\d{4}-\d{2}-\d{2}$/.test(v)}
export function validTimezone(v:string){return typeof v==='string'&&v.length>0&&v.length<=100}
