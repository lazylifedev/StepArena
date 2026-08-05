import {describe,expect,it} from 'vitest'; import {officialSteps,rewardSteps} from '../src/domain/models';
describe('step policy',()=>{it('caps official and reward independently',()=>{expect(officialSteps(100001)).toBe(100000);expect(rewardSteps(100001)).toBe(30000)});it('never makes negative steps',()=>expect(officialSteps(-1)).toBe(0))});
