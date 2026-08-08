import {describe,expect,it} from 'vitest'; import {challengeDurationForProject,competitionSteps,officialSteps,rewardSteps,PRODUCTION_CHALLENGE_DURATION_MS,QA_CHALLENGE_DURATION_MS} from '../src/domain/models';
describe('step policy',()=>{
 it('uses actual competition steps and caps them at 100000',()=>{
  expect(competitionSteps(6000)).toBe(6000);
  expect(competitionSteps(8000)).toBe(8000);
  expect(competitionSteps(99000)).toBe(99000);
  expect(competitionSteps(105000)).toBe(100000);
  expect(competitionSteps(120000)).toBe(100000);
 });
 it('caps official and reward independently',()=>{expect(officialSteps(100001)).toBe(100000);expect(rewardSteps(100001)).toBe(30000)});
 it('keeps reward boundaries independent from competition cap',()=>{expect(rewardSteps(29999)).toBe(29999);expect(rewardSteps(30000)).toBe(30000);expect(rewardSteps(30001)).toBe(30000);expect(rewardSteps(100000)).toBe(30000)});
 it('never makes negative steps',()=>expect(officialSteps(-1)).toBe(0));
 it('keeps one-hour duration QA-only and defaults production to 24 hours',()=>{expect(challengeDurationForProject('steparena-dev')).toBe(QA_CHALLENGE_DURATION_MS);expect(challengeDurationForProject('production-project')).toBe(PRODUCTION_CHALLENGE_DURATION_MS);expect(challengeDurationForProject(undefined)).toBe(PRODUCTION_CHALLENGE_DURATION_MS)});
});
