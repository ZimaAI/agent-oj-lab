import { http } from '@/utils/http'
import type { Tag, IPage } from '@/types/problem'

/**
 * 标签 API
 */
export const tagApi = {
  /**
   * 查询所有标签（不分页）
   */
  getAllTags() {
    return http.get<Tag[]>('/api/tag/list')
  },

  /**
   * 分页查询标签列表
   */
  listTags(params: { current?: number; size?: number; keyword?: string }) {
    return http.get<IPage<Tag>>('/api/tag/page', {
      current: params.current || 1,
      size: params.size || 20,
      keyword: params.keyword
    })
  }
}
