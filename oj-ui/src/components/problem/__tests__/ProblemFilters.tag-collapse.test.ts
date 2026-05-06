import { describe, expect, it } from 'vitest'

import {
  TAG_LIST_COLLAPSED_CLASS,
  TAG_LIST_EXPANDED_CLASS,
  resolveTagCollapseToggleText,
  resolveTagListClassName,
} from '../ProblemFilters.vue'

describe('ProblemFilters tag collapse helpers', () => {
  it('uses collapsed class and text by default state', () => {
    expect(resolveTagListClassName(false)).toBe(TAG_LIST_COLLAPSED_CLASS)
    expect(resolveTagCollapseToggleText(false)).toBe('展开标签')
  })

  it('uses expanded class and text when panel is expanded', () => {
    expect(resolveTagListClassName(true)).toBe(TAG_LIST_EXPANDED_CLASS)
    expect(resolveTagCollapseToggleText(true)).toBe('收起标签')
  })
})
