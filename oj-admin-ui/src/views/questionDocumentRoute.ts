export const buildQuestionDocumentSegmentRoute = (
  questionId: number,
  docId: number,
  docTitle?: string | null,
) => {
  // 仅在标题有效时透传查询参数，避免空字符串污染 URL。
  const normalizedTitle = (docTitle ?? '').trim()
  const query = normalizedTitle.length > 0 ? { docTitle: normalizedTitle } : {}
  return {
    name: 'admin-question-document-segments',
    params: {
      questionId,
      docId,
    },
    query,
  }
}
