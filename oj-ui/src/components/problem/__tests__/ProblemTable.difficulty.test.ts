import { describe, expect, it } from 'vitest'

import { getDifficultyClass, getDifficultyLabel } from '../ProblemTable.vue'
import { Difficulty } from '@/types/problem'

describe('ProblemTable difficulty rendering', () => {
  it('maps EASY to 简单 and easy badge class', () => {
    expect(getDifficultyLabel(Difficulty.EASY)).toBe('简单')
    expect(getDifficultyClass(Difficulty.EASY)).toBe('difficulty-easy')
  })

  it('falls back to unknown for empty difficulty', () => {
    expect(getDifficultyLabel(null)).toBe('未知')
    expect(getDifficultyClass(undefined)).toBe('difficulty-unknown')
  })
})
