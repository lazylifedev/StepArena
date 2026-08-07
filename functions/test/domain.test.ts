import {describe,expect,it} from 'vitest'; import {competitionSteps,officialSteps,rewardSteps} from '../src/domain/models';
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
});
