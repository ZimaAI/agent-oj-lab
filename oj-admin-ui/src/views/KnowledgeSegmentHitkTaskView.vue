<template>
  <main class="page">
    <header class="page-header">
      <h1 class="page-title">知识片段与 Hit@K 任务</h1>
      <p class="page-subtitle">全量知识片段列表与文档切片管理页面对齐，支持同款字段、布局与批量操作。</p>
    </header>

    <section class="panel">
      <header class="panel-header">
        <h2>全量知识片段</h2>
        <p>列表字段、交互与文档切片管理页保持一致，支持跨文档批量执行 Hit@K / RAGAS 操作。</p>
      </header>

      <div class="toolbar">
        <input
          v-model.trim="segmentQuery.keyword"
          class="input"
          type="text"
          placeholder="按片段内容 / chunkId / hitkQuestion 搜索"
          @keyup.enter="searchSegments"
        >
        <button class="btn btn-primary" :disabled="segmentLoading" @click="searchSegments">搜索</button>
        <button class="btn btn-light" :disabled="segmentLoading" @click="resetSegmentFilters">重置</button>
        <button class="btn btn-light" :disabled="segmentLoading" @click="loadKnowledgeSegments">刷新</button>
      </div>

      <div class="toolbar segment-toolbar">
        <label class="checkbox-label">
          <input
            ref="selectAllSegmentCheckboxRef"
            type="checkbox"
            :checked="allVisibleSegmentsSelected"
            :disabled="visibleSegmentIds.length === 0"
            aria-label="Select all visible segments"
            @change="toggleSelectAllVisibleSegments"
          >
          <span>选中可见</span>
        </label>
        <button class="btn btn-light" :disabled="visibleSegmentIds.length === 0" @click="invertVisibleSegmentSelection">
          反选可见
        </button>
        <span class="toolbar-text toolbar-stat">
          已选中 <strong>{{ selectedSegmentIds.length }}</strong> 条 / 当前可见
          <strong>{{ selectedVisibleSegmentCount }}</strong> 条（共 {{ visibleSegmentIds.length }} 条）
        </span>
        <button
          class="btn btn-generate"
          :disabled="selectedSegmentIds.length === 0 || segmentBatchGenerating"
          @click="generateHitkQuestionsForSelection"
        >
          {{ segmentBatchGenerating ? '生成中...' : '生成 Hit@K 问题' }}
        </button>
        <button
          class="btn btn-ragas-generate"
          :disabled="selectedSegmentIds.length === 0 || ragasBatchGenerating"
          @click="generateRagasForSelection"
        >
          {{ ragasBatchGenerating ? '生成中...' : '生成 RAGAS QA' }}
        </button>
        <button
          class="btn btn-run"
          :disabled="selectedSegmentIds.length === 0 || segmentBatchTesting"
          @click="createHitkTestTaskForSelection"
        >
          {{ segmentBatchTesting ? '提交中...' : '运行 Hit@K 测试' }}
        </button>
        <button
          class="btn btn-ragas-answer"
          :disabled="selectedSegmentIds.length === 0 || ragasAnswerGenerating"
          @click="generateRagasAnswersForSelection"
        >
          {{ ragasAnswerGenerating ? '生成中...' : '生成 RAGAS 答案' }}
        </button>
        <button
          class="btn btn-ragas-evaluate"
          :disabled="selectedSegmentIds.length === 0 || ragasEvaluating"
          @click="evaluateRagasForSelection"
        >
          {{ ragasEvaluating ? '提交中...' : '提交 RAGAS 任务' }}
        </button>
        <button class="btn btn-light" :disabled="selectedSegmentIds.length === 0" @click="clearSegmentSelection">
          清空选中
        </button>
      </div>

      <div
        v-if="segmentActionMessage"
        class="feedback-banner feedback-success"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        <span class="feedback-icon">OK</span>
        <span>{{ segmentActionMessage }}</span>
      </div>
      <div
        v-if="segmentActionErrorMessage"
        class="feedback-banner feedback-error"
        role="alert"
        aria-live="assertive"
        aria-atomic="true"
      >
        <span class="feedback-icon">!</span>
        <span>{{ segmentActionErrorMessage }}</span>
      </div>

      <div v-if="segmentErrorMessage" class="state state-error">
        <span>{{ segmentErrorMessage }}</span>
        <button class="btn btn-light" @click="loadKnowledgeSegments">重试</button>
      </div>
      <div v-else-if="segmentLoading" class="loading-skeleton">
        <div class="skeleton-row" v-for="index in 6" :key="`segment-skeleton-${index}`"></div>
      </div>
      <div v-else-if="knowledgeSegments.length === 0" class="empty-state">
        <p class="empty-title">暂无知识片段数据</p>
        <p class="empty-subtitle">请调整搜索条件，或等待文档切片生成后刷新。</p>
      </div>
      <div v-else>
        <div class="table-wrap">
          <table class="table segment-table">
            <thead>
              <tr>
                <th class="checkbox-col">Select</th>
                <th class="num-col">Chunk Order</th>
                <th class="num-col">Segment ID</th>
                <th>Type</th>
                <th>Status</th>
                <th class="date-col">Updated At</th>
                <th>Text</th>
                <th>Hit@K Question</th>
                <th>RAGAS</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="segment in knowledgeSegments" :key="segment.segmentId">
                <tr :class="{ 'row-selected': isSegmentSelected(segment.segmentId) }">
                  <td class="checkbox-col">
                    <input
                      type="checkbox"
                      :checked="isSegmentSelected(segment.segmentId)"
                      :aria-label="`Select segment ${segment.segmentId}`"
                      @change="toggleSegmentSelection(segment.segmentId)"
                    >
                  </td>
                  <td class="num-col">{{ segment.chunkOrder ?? '-' }}</td>
                  <td class="num-col">{{ segment.segmentId }}</td>
                  <td>
                    <span v-if="segment.parentSegment" class="tag tag-parent">Parent</span>
                    <span v-else class="tag">Normal</span>
                  </td>
                  <td>
                    <span class="status-chip" :class="`status-chip-${resolveStatusMeta(segment.status).variant}`">
                      <span class="status-icon">{{ resolveStatusMeta(segment.status).icon }}</span>
                      {{ resolveStatusMeta(segment.status).label }}
                    </span>
                  </td>
                  <td class="date-col">{{ formatDateTime(segment.updatedAt) }}</td>
                  <td class="segment-text compact-cell">
                    <div class="detail-popover">
                      <div class="text-ellipsis text-ellipsis-multiline compact-preview" :title="toTooltipText(segment.text)">
                        {{ toDisplayText(segment.text) }}
                      </div>
                      <pre v-if="hasTooltipContent(segment.text)" class="detail-popover-content">{{ toTooltipText(segment.text) }}</pre>
                    </div>
                  </td>
                  <td class="hitk-cell compact-cell">
                    <div class="detail-popover">
                      <div
                        class="text-ellipsis text-ellipsis-multiline compact-preview"
                        :title="toTooltipText(getSegmentHitkQuestionDraft(segment))"
                      >
                        {{ toDisplayText(getSegmentHitkQuestionDraft(segment)) }}
                      </div>
                      <pre
                        v-if="hasTooltipContent(getSegmentHitkQuestionDraft(segment))"
                        class="detail-popover-content"
                      >{{ toTooltipText(getSegmentHitkQuestionDraft(segment)) }}</pre>
                    </div>
                  </td>
                  <td class="ragas-cell compact-cell">
                    <div class="detail-popover">
                      <div class="text-ellipsis text-ellipsis-multiline compact-preview" :title="getRagasPreviewTitle(segment)">
                        {{ getRagasPreviewText(segment) }}
                      </div>
                      <pre v-if="hasRagasPreviewContent(segment)" class="detail-popover-content">{{ getRagasPreviewTitle(segment) }}</pre>
                    </div>
                  </td>
                  <td class="ops">
                    <div class="ops-group">
                      <button class="link-btn" @click="openSegmentDetailDialog(segment)">Detail</button>
                      <button
                        v-if="segment.parentSegment"
                        class="link-btn"
                        :disabled="childLoadingMap[segment.segmentId] === true"
                        @click="toggleParentSegment(segment)"
                      >
                        {{
                          isParentExpanded(segment.segmentId)
                            ? 'Collapse'
                            : childLoadingMap[segment.segmentId] === true
                              ? 'Loading...'
                              : `Expand (${segment.childSegmentCount})`
                        }}
                      </button>
                      <span v-else class="ops-placeholder">No child</span>
                      <button
                        class="link-btn"
                        :disabled="!canOpenSegmentDocument(segment)"
                        @click="openSegmentDocumentPage(segment)"
                      >
                        查看分片页
                      </button>
                    </div>
                  </td>
                </tr>
                <tr
                  v-if="segment.parentSegment && isParentExpanded(segment.segmentId)"
                  :key="`child-${segment.segmentId}`"
                >
                  <td class="child-cell" colspan="10">
                    <div v-if="childErrorMap[segment.segmentId]" class="state state-error child-error">
                      {{ childErrorMap[segment.segmentId] }}
                    </div>
                    <div v-else-if="childLoadingMap[segment.segmentId]" class="state">Loading child segments...</div>
                    <div v-else>
                      <div v-if="(childRowsMap[segment.segmentId] ?? []).length === 0" class="empty-state compact-empty-state">
                        <p class="empty-title">No child segments</p>
                        <p class="empty-subtitle">Child records will be shown here after indexing.</p>
                      </div>
                      <div v-else class="table-wrap child-table-wrap">
                        <table class="table child-table">
                          <thead>
                            <tr>
                              <th class="checkbox-col">Select</th>
                              <th class="num-col">Chunk Order</th>
                              <th class="num-col">Segment ID</th>
                              <th>Status</th>
                              <th class="date-col">Updated At</th>
                              <th>Text</th>
                              <th>Hit@K Question</th>
                              <th>RAGAS</th>
                              <th>Action</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr
                              v-for="child in childRowsMap[segment.segmentId]"
                              :key="child.segmentId"
                              :class="{ 'row-selected': isSegmentSelected(child.segmentId) }"
                            >
                              <td class="checkbox-col">
                                <input
                                  type="checkbox"
                                  :checked="isSegmentSelected(child.segmentId)"
                                  :aria-label="`Select segment ${child.segmentId}`"
                                  @change="toggleSegmentSelection(child.segmentId)"
                                >
                              </td>
                              <td class="num-col">{{ child.chunkOrder ?? '-' }}</td>
                              <td class="num-col">{{ child.segmentId }}</td>
                              <td>
                                <span class="status-chip" :class="`status-chip-${resolveStatusMeta(child.status).variant}`">
                                  <span class="status-icon">{{ resolveStatusMeta(child.status).icon }}</span>
                                  {{ resolveStatusMeta(child.status).label }}
                                </span>
                              </td>
                              <td class="date-col">{{ formatDateTime(child.updatedAt) }}</td>
                              <td class="segment-text compact-cell">
                                <div class="detail-popover">
                                  <div class="text-ellipsis text-ellipsis-multiline compact-preview" :title="toTooltipText(child.text)">
                                    {{ toDisplayText(child.text) }}
                                  </div>
                                  <pre v-if="hasTooltipContent(child.text)" class="detail-popover-content">{{ toTooltipText(child.text) }}</pre>
                                </div>
                              </td>
                              <td class="hitk-cell compact-cell">
                                <div class="detail-popover">
                                  <div
                                    class="text-ellipsis text-ellipsis-multiline compact-preview"
                                    :title="toTooltipText(getSegmentHitkQuestionDraft(child))"
                                  >
                                    {{ toDisplayText(getSegmentHitkQuestionDraft(child)) }}
                                  </div>
                                  <pre
                                    v-if="hasTooltipContent(getSegmentHitkQuestionDraft(child))"
                                    class="detail-popover-content"
                                  >{{ toTooltipText(getSegmentHitkQuestionDraft(child)) }}</pre>
                                </div>
                              </td>
                              <td class="ragas-cell compact-cell">
                                <div class="detail-popover">
                                  <div class="text-ellipsis text-ellipsis-multiline compact-preview" :title="getRagasPreviewTitle(child)">
                                    {{ getRagasPreviewText(child) }}
                                  </div>
                                  <pre v-if="hasRagasPreviewContent(child)" class="detail-popover-content">{{ getRagasPreviewTitle(child) }}</pre>
                                </div>
                              </td>
                              <td class="ops">
                                <div class="ops-group">
                                  <button class="link-btn" @click="openSegmentDetailDialog(child)">Detail</button>
                                  <button
                                    class="link-btn"
                                    :disabled="!canOpenSegmentDocument(child)"
                                    @click="openSegmentDocumentPage(child)"
                                  >
                                    查看分片页
                                  </button>
                                </div>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="btn btn-light" :disabled="segmentLoading || segmentQuery.current <= 1" @click="changeSegmentPage(segmentQuery.current - 1)">
            上一页
          </button>
          <span>第 {{ segmentQuery.current }} / {{ segmentTotalPages }} 页， 共 {{ segmentTotal }} 条</span>
          <button
            class="btn btn-light"
            :disabled="segmentLoading || segmentQuery.current >= segmentTotalPages"
            @click="changeSegmentPage(segmentQuery.current + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <div class="task-panel-grid">
      <section class="panel">
        <header class="panel-header">
          <h2>Hit@K 任务管理</h2>
          <p>支持时间段筛选、跨页勾选统计、任务备注和批量删除。</p>
        </header>

        <div class="toolbar toolbar-task">
          <select v-model="taskQuery.status" class="select">
            <option value="">全部状态</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="FAILED">FAILED</option>
          </select>
          <input v-model="taskQuery.startTime" class="input" type="datetime-local">
          <input v-model="taskQuery.endTime" class="input" type="datetime-local">
          <button class="btn btn-primary" :disabled="taskLoading" @click="searchTasks">搜索</button>
          <button class="btn btn-light" :disabled="taskLoading" @click="resetTaskFilters">重置</button>
          <button class="btn btn-light" :disabled="taskLoading" @click="loadHitkTasks">刷新</button>
        </div>

        <div class="selection-toolbar">
          <p>
            已跨页选中 <strong>{{ selectedTaskIds.length }}</strong> 个任务
          </p>
          <div class="selection-actions">
            <button class="btn btn-light" :disabled="hitkTasks.length === 0" @click="selectCurrentTaskPage">选中本页</button>
            <button class="btn btn-light" :disabled="selectedTaskIds.length === 0" @click="clearTaskSelection">清空选中</button>
            <button class="btn btn-danger" :disabled="selectedTaskIds.length === 0 || batchDeleting" @click="batchDeleteSelectedTasks">
              {{ batchDeleting ? '删除中...' : '删除选中任务' }}
            </button>
          </div>
        </div>

        <div class="stats-card">
          <div>
            <p class="stats-label">任务数</p>
            <p class="stats-value">{{ taskStatistics.taskCount }}</p>
          </div>
          <div>
            <p class="stats-label">平均命中率</p>
            <p class="stats-value">{{ formatRate(taskStatistics.averageHitRate) }}</p>
          </div>
          <div>
            <p class="stats-label">平均总样本</p>
            <p class="stats-value">{{ formatNumber(taskStatistics.averageTotalCount) }}</p>
          </div>
          <div>
            <p class="stats-label">平均命中数</p>
            <p class="stats-value">{{ formatNumber(taskStatistics.averageHitCount) }}</p>
          </div>
          <div>
            <p class="stats-label">平均未命中数</p>
            <p class="stats-value">{{ formatNumber(taskStatistics.averageMissCount) }}</p>
          </div>
          <div>
            <p class="stats-label">总体命中率</p>
            <p class="stats-value">{{ formatRate(taskStatistics.overallHitRate) }}</p>
          </div>
        </div>
        <p v-if="taskStatisticsLoading" class="helper-text">正在统计选中任务...</p>
        <p v-if="taskStatisticsErrorMessage" class="state state-error">{{ taskStatisticsErrorMessage }}</p>
        <p v-if="taskActionMessage" class="notice">{{ taskActionMessage }}</p>
        <div v-if="batchDeleteResult && batchDeleteResult.failureCount > 0" class="state state-error">
          <p>删除失败 {{ batchDeleteResult.failureCount }} 条：</p>
          <ul>
            <li v-for="item in batchDeleteFailures" :key="item.taskId">任务 {{ item.taskId }}：{{ item.message }}</li>
          </ul>
        </div>

        <div v-if="taskErrorMessage" class="state state-error">
          <span>{{ taskErrorMessage }}</span>
          <button class="btn btn-light" @click="loadHitkTasks">重试</button>
        </div>
        <div v-else-if="taskLoading" class="state">正在加载 Hit@K 任务...</div>
        <div v-else-if="hitkTasks.length === 0" class="state">暂无 Hit@K 任务</div>
        <div v-else class="table-wrap">
          <table class="table task-table">
            <thead>
              <tr>
                <th class="checkbox-col">
                  <input
                    type="checkbox"
                    :checked="allSelectedOnTaskPage"
                    :indeterminate.prop="partiallySelectedOnTaskPage"
                    @change="toggleCurrentTaskPage($event)"
                  >
                </th>
                <th class="num-col">任务ID</th>
                <th>题目 / 文档</th>
                <th>状态</th>
                <th class="num-col">总数</th>
                <th class="num-col">命中</th>
                <th class="num-col">未命中</th>
                <th class="num-col">命中率</th>
                <th class="date-col">创建时间</th>
                <th>操作</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in hitkTasks" :key="task.taskId">
                <td class="checkbox-col">
                  <input
                    type="checkbox"
                    :checked="isTaskSelected(task.taskId)"
                    @change="toggleTaskSelection(task.taskId, $event)"
                  >
                </td>
                <td class="num-col">{{ task.taskId }}</td>
                <td>
                  <p class="cell-main">Q#{{ task.questionId }} · D#{{ task.documentId }}</p>
                </td>
                <td>{{ task.status ?? '-' }}</td>
                <td class="num-col">{{ task.totalCount ?? 0 }}</td>
                <td class="num-col">{{ task.hitCount ?? 0 }}</td>
                <td class="num-col">{{ task.missCount ?? 0 }}</td>
                <td class="num-col">{{ formatRate(task.hitRate) }}</td>
                <td class="date-col">{{ formatDateTime(task.createTime) }}</td>
                <td class="ops">
                  <div class="ops-group">
                    <button class="link-btn" :disabled="hitkTaskDetailLoading" @click="openHitkTaskDetailDialog(task)">查看详情</button>
                    <button class="link-btn" @click="openTaskDocumentSegments(task)">查看分片页</button>
                  </div>
                </td>
                <td>
                  <div class="remark-cell">
                    <input
                      class="input remark-input"
                      :value="getTaskRemarkDraft(task)"
                      @input="updateTaskRemarkDraft(task.taskId, ($event.target as HTMLInputElement).value)"
                    >
                    <button
                      class="btn btn-light btn-small"
                      :disabled="!isTaskRemarkDirty(task) || taskRemarkSavingMap[task.taskId] === true"
                      @click="saveTaskRemark(task)"
                    >
                      {{ taskRemarkSavingMap[task.taskId] === true ? '保存中...' : '保存' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button class="btn btn-light" :disabled="taskLoading || taskQuery.current <= 1" @click="changeTaskPage(taskQuery.current - 1)">
            上一页
          </button>
          <span>第 {{ taskQuery.current }} / {{ taskTotalPages }} 页， 共 {{ taskTotal }} 条</span>
          <button
            class="btn btn-light"
            :disabled="taskLoading || taskQuery.current >= taskTotalPages"
            @click="changeTaskPage(taskQuery.current + 1)"
          >
            下一页
          </button>
        </div>
      </section>

      <section class="panel">
        <header class="panel-header">
          <h2>RAGAS 任务管理</h2>
          <p>独立查看批量评估任务，并展示仅成功记录参与计算的四项平均分。</p>
        </header>

        <div class="toolbar toolbar-task">
          <select v-model="ragasTaskQuery.status" class="select">
            <option value="">全部状态</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="FAILED">FAILED</option>
          </select>
          <input v-model="ragasTaskQuery.startTime" class="input" type="datetime-local">
          <input v-model="ragasTaskQuery.endTime" class="input" type="datetime-local">
          <button class="btn btn-primary" :disabled="ragasTaskLoading" @click="searchRagasTasks">搜索</button>
          <button class="btn btn-light" :disabled="ragasTaskLoading" @click="resetRagasTaskFilters">重置</button>
          <button class="btn btn-light" :disabled="ragasTaskLoading" @click="loadRagasTasks">刷新</button>
        </div>

        <div v-if="ragasTaskErrorMessage" class="state state-error">
          <span>{{ ragasTaskErrorMessage }}</span>
          <button class="btn btn-light" @click="loadRagasTasks">重试</button>
        </div>
        <div v-else-if="ragasTaskLoading" class="state">正在加载 RAGAS 任务...</div>
        <div v-else-if="ragasTasks.length === 0" class="state">暂无 RAGAS 任务</div>
        <div v-else class="table-wrap">
          <table class="table task-table">
            <thead>
              <tr>
                <th class="num-col">任务ID</th>
                <th>题目 / 文档</th>
                <th>状态</th>
                <th class="num-col">总数</th>
                <th class="num-col">成功</th>
                <th class="num-col">失败</th>
                <th class="num-col">AnsRel</th>
                <th class="num-col">Faith</th>
                <th class="num-col">CtxPrec</th>
                <th class="num-col">CtxRecall</th>
                <th class="date-col">创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in ragasTasks" :key="task.taskId">
                <td class="num-col">{{ task.taskId }}</td>
                <td>
                  <p class="cell-main">Q#{{ task.questionId }} · D#{{ task.documentId }}</p>
                </td>
                <td>{{ task.status ?? '-' }}</td>
                <td class="num-col">{{ task.totalCount ?? 0 }}</td>
                <td class="num-col">{{ task.successCount ?? 0 }}</td>
                <td class="num-col">{{ task.failureCount ?? 0 }}</td>
                <td class="num-col">{{ formatScore(task.averageAnswerRelevancy) }}</td>
                <td class="num-col">{{ formatScore(task.averageFaithfulness) }}</td>
                <td class="num-col">{{ formatScore(task.averageContextPrecision) }}</td>
                <td class="num-col">{{ formatScore(task.averageContextRecall) }}</td>
                <td class="date-col">{{ formatDateTime(task.createTime) }}</td>
                <td class="ops">
                  <div class="ops-group">
                    <button class="link-btn" :disabled="ragasTaskDetailLoading" @click="openRagasTaskDetailDialog(task)">查看详情</button>
                    <button class="link-btn" @click="openTaskDocumentSegments(task)">查看分片页</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button
            class="btn btn-light"
            :disabled="ragasTaskLoading || ragasTaskQuery.current <= 1"
            @click="changeRagasTaskPage(ragasTaskQuery.current - 1)"
          >
            上一页
          </button>
          <span>第 {{ ragasTaskQuery.current }} / {{ ragasTaskTotalPages }} 页， 共 {{ ragasTaskTotal }} 条</span>
          <button
            class="btn btn-light"
            :disabled="ragasTaskLoading || ragasTaskQuery.current >= ragasTaskTotalPages"
            @click="changeRagasTaskPage(ragasTaskQuery.current + 1)"
          >
            下一页
          </button>
        </div>
      </section>
    </div>

    <div v-if="segmentDetailVisible" class="modal-mask" @click.self="closeSegmentDetailDialog">
      <section
        class="modal modal-large segment-detail-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="segment-detail-modal-title"
      >
        <header class="modal-header">
          <h2 id="segment-detail-modal-title">
            Segment Detail
            <span v-if="activeDetailSegment">#{{ activeDetailSegment.segmentId }}</span>
          </h2>
          <button class="link-btn" @click="closeSegmentDetailDialog">Close</button>
        </header>

        <div v-if="activeDetailSegment" class="segment-detail-body">
          <div class="detail-meta-grid">
            <p><strong>Segment ID:</strong> {{ activeDetailSegment.segmentId }}</p>
            <p><strong>Chunk:</strong> {{ activeDetailSegment.chunkOrder ?? '-' }}</p>
            <p><strong>Status:</strong> {{ resolveStatusMeta(activeDetailSegment.status).label }}</p>
            <p><strong>Updated:</strong> {{ formatDateTime(activeDetailSegment.updatedAt) }}</p>
            <p><strong>Question:</strong> {{ activeDetailSegment.questionTitle ?? '-' }} (Q#{{ activeDetailSegment.questionId ?? '-' }})</p>
            <p><strong>Document:</strong> {{ activeDetailSegment.documentTitle ?? '-' }} (D#{{ activeDetailSegment.documentId ?? '-' }})</p>
          </div>

          <label class="detail-field">
            <span class="detail-field-label">Text</span>
            <pre class="detail-readonly">{{ toTooltipText(activeDetailSegment.text) }}</pre>
          </label>

          <label class="detail-field">
            <span class="detail-field-label">Hit@K Question</span>
            <textarea
              class="hitk-textarea detail-textarea"
              :value="getSegmentHitkQuestionDraft(activeDetailSegment)"
              placeholder="Edit Hit@K question"
              @input="updateSegmentHitkQuestionDraft(activeDetailSegment.segmentId, ($event.target as HTMLTextAreaElement).value)"
              @keydown.ctrl.enter.prevent="saveSegmentHitkQuestion(activeDetailSegment)"
              @keydown.meta.enter.prevent="saveSegmentHitkQuestion(activeDetailSegment)"
            />
          </label>
          <div class="detail-action-row">
            <button
              class="btn btn-light btn-small"
              :disabled="!isSegmentHitkQuestionDirty(activeDetailSegment) || saveLoadingMap[activeDetailSegment.segmentId] === true"
              @click="saveSegmentHitkQuestion(activeDetailSegment)"
            >
              {{ saveLoadingMap[activeDetailSegment.segmentId] === true ? 'Saving...' : 'Save Hit@K' }}
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="!hasSegmentDraft(activeDetailSegment.segmentId)"
              @click="resetSegmentHitkQuestionDraft(activeDetailSegment.segmentId)"
            >
              Reset Hit@K
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="singleActionLoading"
              @click="generateHitkQuestionForSegment(activeDetailSegment)"
            >
              {{ singleActionLoading ? 'Running...' : 'Generate Hit@K' }}
            </button>
          </div>
          <div v-if="saveErrorMap[activeDetailSegment.segmentId]" class="inline-error">
            {{ saveErrorMap[activeDetailSegment.segmentId] }}
          </div>

          <label class="detail-field">
            <span class="detail-field-label">RAGAS Question</span>
            <textarea
              class="hitk-textarea ragas-textarea detail-textarea"
              :value="getSegmentRagasQuestionDraft(activeDetailSegment)"
              placeholder="Edit RAGAS question"
              @input="updateSegmentRagasQuestionDraft(activeDetailSegment.segmentId, ($event.target as HTMLTextAreaElement).value)"
              @keydown.ctrl.enter.prevent="saveSegmentRagas(activeDetailSegment)"
              @keydown.meta.enter.prevent="saveSegmentRagas(activeDetailSegment)"
            />
          </label>
          <label class="detail-field">
            <span class="detail-field-label">RAGAS Standard Answer</span>
            <textarea
              class="hitk-textarea ragas-textarea detail-textarea"
              :value="getSegmentRagasStandardAnswerDraft(activeDetailSegment)"
              placeholder="Edit RAGAS standard answer"
              @input="updateSegmentRagasStandardAnswerDraft(activeDetailSegment.segmentId, ($event.target as HTMLTextAreaElement).value)"
              @keydown.ctrl.enter.prevent="saveSegmentRagas(activeDetailSegment)"
              @keydown.meta.enter.prevent="saveSegmentRagas(activeDetailSegment)"
            />
          </label>
          <div class="ragas-score-grid detail-score-grid">
            <span class="score-pill">Faithfulness: {{ formatScore(getSegmentRagas(activeDetailSegment.segmentId)?.faithfulness) }}</span>
            <span class="score-pill">Answer Rel.: {{ formatScore(getSegmentRagas(activeDetailSegment.segmentId)?.answerRelevancy) }}</span>
            <span class="score-pill">Ctx Precision: {{ formatScore(getSegmentRagas(activeDetailSegment.segmentId)?.contextPrecision) }}</span>
          </div>
          <div class="detail-action-row">
            <button
              class="btn btn-light btn-small"
              :disabled="!isSegmentRagasDirty(activeDetailSegment) || ragasSaveLoadingMap[activeDetailSegment.segmentId] === true"
              @click="saveSegmentRagas(activeDetailSegment)"
            >
              {{ ragasSaveLoadingMap[activeDetailSegment.segmentId] === true ? 'Saving...' : 'Save RAGAS' }}
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="!hasSegmentRagasDraft(activeDetailSegment.segmentId)"
              @click="resetSegmentRagasDraft(activeDetailSegment.segmentId)"
            >
              Reset RAGAS
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="singleActionLoading"
              @click="generateRagasForSegment(activeDetailSegment)"
            >
              {{ singleActionLoading ? 'Running...' : 'Generate RAGAS QA' }}
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="singleActionLoading"
              @click="generateRagasAnswerForSegment(activeDetailSegment)"
            >
              {{ singleActionLoading ? 'Running...' : 'Generate RAGAS Answer' }}
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="singleActionLoading"
              @click="evaluateRagasForSegment(activeDetailSegment)"
            >
              {{ singleActionLoading ? 'Running...' : 'Run RAGAS Eval' }}
            </button>
          </div>
          <div v-if="ragasSaveErrorMap[activeDetailSegment.segmentId]" class="inline-error">
            {{ ragasSaveErrorMap[activeDetailSegment.segmentId] }}
          </div>

          <div class="detail-action-row">
            <button
              class="btn btn-light btn-small"
              :disabled="!canOpenSegmentDocument(activeDetailSegment)"
              @click="openSegmentDocumentPage(activeDetailSegment)"
            >
              打开文档切片管理页
            </button>
          </div>
        </div>
      </section>
    </div>

    <div v-if="hitkTaskDetailVisible" class="modal-mask" @click.self="closeHitkTaskDetailDialog">
      <section
        class="modal modal-large hitk-task-detail-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="hitk-task-detail-modal-title"
      >
        <header class="modal-header">
          <h2 id="hitk-task-detail-modal-title">
            Hit@K Task Detail
            <span v-if="activeHitkTaskDetail">#{{ activeHitkTaskDetail.taskId }}</span>
          </h2>
          <div class="detail-action-row">
            <button class="btn btn-light btn-small" :disabled="hitkTaskDetailLoading" @click="reloadActiveHitkTaskDetail">
              {{ hitkTaskDetailLoading ? 'Refreshing...' : 'Refresh' }}
            </button>
            <button class="link-btn" @click="closeHitkTaskDetailDialog">Close</button>
          </div>
        </header>

        <div v-if="hitkTaskDetailLoading" class="loading-skeleton task-detail-skeleton">
          <div class="skeleton-block"></div>
          <div class="skeleton-row" v-for="index in 3" :key="`hitk-task-detail-skeleton-${index}`"></div>
        </div>
        <div v-else-if="hitkTaskDetailErrorMessage" class="state state-error">
          <p>{{ hitkTaskDetailErrorMessage }}</p>
          <button class="btn btn-light btn-small" @click="reloadActiveHitkTaskDetail">Retry</button>
        </div>
        <div v-else-if="activeHitkTaskDetail" class="hitk-task-detail-body">
          <div class="hitk-task-meta-grid">
            <p><strong>Task ID:</strong> {{ activeHitkTaskDetail.taskId }}</p>
            <p><strong>Status:</strong> {{ resolveStatusMeta(activeHitkTaskDetail.status).label }}</p>
            <p><strong>Total:</strong> {{ activeHitkTaskDetail.totalCount }}</p>
            <p><strong>Hit:</strong> {{ activeHitkTaskDetail.hitCount }}</p>
            <p><strong>Miss:</strong> {{ activeHitkTaskDetail.missCount }}</p>
            <p><strong>Hit Rate:</strong> {{ formatRate(activeHitkTaskDetail.hitRate) }}</p>
            <p><strong>Created:</strong> {{ formatDateTime(activeHitkTaskDetail.createTime) }}</p>
            <p><strong>Error:</strong> {{ toDisplayText(activeHitkTaskDetail.errorMessage) }}</p>
          </div>

          <div v-if="activeHitkTaskDetail.segmentResults.length === 0" class="empty-state compact-empty-state">
            <p class="empty-title">No segment results</p>
            <p class="empty-subtitle">This task has no detail records.</p>
          </div>
          <div v-else class="hitk-task-segment-list">
            <article
              v-for="(item, itemIndex) in activeHitkTaskDetail.segmentResults"
              :key="`${activeHitkTaskDetail.taskId}-${item.segmentId}-${itemIndex}`"
              class="hitk-task-segment-card"
            >
              <header class="hitk-task-segment-header">
                <strong>Segment #{{ item.segmentId }}</strong>
                <span class="status-chip" :class="`status-chip-${resolveHitStatusMeta(item.hit).variant}`">
                  <span class="status-icon">{{ resolveHitStatusMeta(item.hit).icon }}</span>
                  {{ resolveHitStatusMeta(item.hit).label }}
                </span>
              </header>

              <label class="detail-field">
                <span class="detail-field-label">Segment Text</span>
                <pre class="detail-readonly">{{ toTooltipText(item.segmentText) }}</pre>
              </label>

              <label class="detail-field">
                <span class="detail-field-label">Hit@K Question</span>
                <pre class="detail-readonly">{{ toTooltipText(item.hitkQuestion) }}</pre>
              </label>

              <label class="detail-field">
                <span class="detail-field-label">Rewritten Questions</span>
                <ul v-if="item.rewrittenQuestions.length > 0" class="rewritten-question-list">
                  <li v-for="(rewrittenQuestion, index) in item.rewrittenQuestions" :key="`${item.segmentId}-${index}`">
                    {{ rewrittenQuestion }}
                  </li>
                </ul>
                <div v-else class="state compact-state">No rewritten questions.</div>
              </label>

              <label class="detail-field">
                <span class="detail-field-label">Retrieved Segments</span>
                <div v-if="item.retrievedSegments.length === 0" class="state compact-state">No retrieved segments.</div>
                <div v-else class="retrieved-segment-list">
                  <article
                    v-for="(retrievedSegment, retrievedIndex) in item.retrievedSegments"
                    :key="`${item.segmentId}-${retrievedSegment.segmentId}-${retrievedIndex}`"
                    class="retrieved-segment-card"
                  >
                    <p class="detail-subtitle">
                      Segment #{{ retrievedSegment.segmentId }} / Doc {{ retrievedSegment.documentId ?? '-' }} / Chunk
                      {{ retrievedSegment.chunkOrder ?? '-' }}
                    </p>
                    <div class="retrieved-score-row">
                      <span class="score-pill">Raw: {{ formatScore(retrievedSegment.rawSimilarity) }}</span>
                      <span class="score-pill">Similarity: {{ formatScore(retrievedSegment.similarityScore) }}</span>
                      <span class="score-pill">RRF: {{ formatScore(retrievedSegment.rrfScore) }}</span>
                      <span class="score-pill">Final: {{ formatScore(retrievedSegment.finalScore) }}</span>
                    </div>
                    <pre class="detail-readonly">{{ toTooltipText(retrievedSegment.text) }}</pre>
                  </article>
                </div>
              </label>
            </article>
          </div>
        </div>
        <div v-else class="state">No task detail available.</div>
      </section>
    </div>

    <div v-if="ragasTaskDetailVisible" class="modal-mask" @click.self="closeRagasTaskDetailDialog">
      <section
        class="modal modal-large hitk-task-detail-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ragas-task-detail-modal-title"
      >
        <header class="modal-header">
          <h2 id="ragas-task-detail-modal-title">
            RAGAS Task Detail
            <span v-if="activeRagasTaskDetail">#{{ activeRagasTaskDetail.taskId }}</span>
          </h2>
          <div class="detail-action-row">
            <button class="btn btn-light btn-small" :disabled="ragasTaskDetailLoading" @click="reloadActiveRagasTaskDetail">
              {{ ragasTaskDetailLoading ? '刷新中...' : '刷新' }}
            </button>
            <button class="link-btn" @click="closeRagasTaskDetailDialog">关闭</button>
          </div>
        </header>

        <div v-if="ragasTaskDetailLoading" class="loading-skeleton task-detail-skeleton">
          <div class="skeleton-block"></div>
          <div class="skeleton-row" v-for="index in 3" :key="`ragas-task-detail-skeleton-${index}`"></div>
        </div>
        <div v-else-if="ragasTaskDetailErrorMessage" class="state state-error">
          <p>{{ ragasTaskDetailErrorMessage }}</p>
          <button class="btn btn-light btn-small" @click="reloadActiveRagasTaskDetail">重试</button>
        </div>
        <div v-else-if="activeRagasTaskDetail" class="hitk-task-detail-body">
          <div class="hitk-task-meta-grid">
            <p><strong>Task ID:</strong> {{ activeRagasTaskDetail.taskId }}</p>
            <p><strong>Status:</strong> {{ resolveStatusMeta(activeRagasTaskDetail.status).label }}</p>
            <p><strong>Total:</strong> {{ activeRagasTaskDetail.totalCount }}</p>
            <p><strong>Success:</strong> {{ activeRagasTaskDetail.successCount }}</p>
            <p><strong>Failed:</strong> {{ activeRagasTaskDetail.failureCount }}</p>
            <p><strong>AnsRel:</strong> {{ formatScore(activeRagasTaskDetail.averageAnswerRelevancy) }}</p>
            <p><strong>Faith:</strong> {{ formatScore(activeRagasTaskDetail.averageFaithfulness) }}</p>
            <p><strong>CtxPrec:</strong> {{ formatScore(activeRagasTaskDetail.averageContextPrecision) }}</p>
            <p><strong>CtxRecall:</strong> {{ formatScore(activeRagasTaskDetail.averageContextRecall) }}</p>
            <p><strong>Created:</strong> {{ formatDateTime(activeRagasTaskDetail.createTime) }}</p>
            <p><strong>Error:</strong> {{ toDisplayText(activeRagasTaskDetail.errorMessage) }}</p>
          </div>

          <div v-if="activeRagasTaskDetail.details.length === 0" class="empty-state compact-empty-state">
            <p class="empty-title">No detail records</p>
            <p class="empty-subtitle">This task has no segment-level result items.</p>
          </div>
          <div v-else class="hitk-task-segment-list">
            <article
              v-for="(item, itemIndex) in activeRagasTaskDetail.details"
              :key="`${activeRagasTaskDetail.taskId}-${item.segmentId}-${itemIndex}`"
              class="hitk-task-segment-card"
            >
              <header class="hitk-task-segment-header">
                <div>
                  <strong>Segment #{{ item.segmentId }}</strong>
                  <p class="detail-subtitle">RAGAS #{{ item.ragasId ?? '-' }} / Q#{{ item.questionId ?? '-' }} / D#{{ item.documentId ?? '-' }}</p>
                </div>
                <span class="status-chip" :class="`status-chip-${resolveStatusMeta(item.status).variant}`">
                  <span class="status-icon">{{ resolveStatusMeta(item.status).icon }}</span>
                  {{ resolveStatusMeta(item.status).label }}
                </span>
              </header>

              <div class="ragas-score-grid">
                <span class="score-pill">AnsRel {{ formatScore(item.answerRelevancy) }}</span>
                <span class="score-pill">Faith {{ formatScore(item.faithfulness) }}</span>
                <span class="score-pill">CtxPrec {{ formatScore(item.contextPrecision) }}</span>
                <span class="score-pill">CtxRecall {{ formatScore(item.contextRecall) }}</span>
                <span class="score-pill">Overall {{ formatScore(item.overallScore) }}</span>
              </div>

              <p v-if="item.errorMessage" class="state state-error compact-state">{{ item.errorMessage }}</p>

              <label class="detail-field">
                <span class="detail-field-label">Question</span>
                <pre class="detail-readonly">{{ toDisplayText(item.ragasQuestion) }}</pre>
              </label>
              <label class="detail-field">
                <span class="detail-field-label">Standard Answer</span>
                <pre class="detail-readonly">{{ toDisplayText(item.standardAnswer) }}</pre>
              </label>
              <label class="detail-field">
                <span class="detail-field-label">Generated Answer</span>
                <pre class="detail-readonly">{{ toDisplayText(item.generatedAnswer) }}</pre>
              </label>
            </article>
          </div>
        </div>
        <div v-else class="state">No task detail available.</div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  adminQuestionApi,
  type AdminHitkTaskBatchDeleteResponse,
  type AdminHitkTaskDetailVO,
  type AdminHitkTaskStatisticsResponse,
  type AdminHitkTaskVO,
  type AdminKnowledgeSegmentVO,
  type AdminRagasTaskDetailVO,
  type AdminRagasTaskVO,
  type AdminQuestionDocumentSegmentRagasVO,
  type AdminQuestionDocumentSegmentVO,
} from '@/api/adminQuestion'
import { buildQuestionDocumentSegmentRoute } from '@/views/questionDocumentRoute'

type StatusVariant = 'success' | 'danger' | 'warning' | 'info' | 'neutral'
type StatusMeta = {
  label: string
  variant: StatusVariant
  icon: string
}

type SegmentRow = AdminKnowledgeSegmentVO & {
  parentChunkId: string | null
  parentSegment: boolean
  childSegmentCount: number
}

type SegmentContextGroup = {
  questionId: number
  documentId: number
  segmentIds: number[]
}

const router = useRouter()

// 初始化统计默认值，避免未选中时渲染异常。
const createEmptyTaskStatistics = (): AdminHitkTaskStatisticsResponse => ({
  taskCount: 0,
  averageHitRate: 0,
  averageTotalCount: 0,
  averageHitCount: 0,
  averageMissCount: 0,
  sumTotalCount: 0,
  sumHitCount: 0,
  sumMissCount: 0,
  overallHitRate: 0,
})

// 全量知识片段分页与列表状态。
const segmentQuery = reactive({
  keyword: '',
  current: 1,
  size: 20,
})
const segmentLoading = ref(false)
const segmentErrorMessage = ref('')
const segmentActionMessage = ref('')
const segmentActionErrorMessage = ref('')
const segmentBatchGenerating = ref(false)
const segmentBatchTesting = ref(false)
const ragasBatchGenerating = ref(false)
const ragasAnswerGenerating = ref(false)
const ragasEvaluating = ref(false)
const knowledgeSegments = ref<SegmentRow[]>([])
const segmentTotal = ref(0)

// 全量片段选择、父子展开与详情编辑状态。
const selectedSegmentIds = ref<number[]>([])
const selectedSegmentSnapshotMap = ref<Record<number, SegmentRow>>({})
const selectAllSegmentCheckboxRef = ref<HTMLInputElement | null>(null)
const expandedParentIds = ref<number[]>([])
const childRowsMap = ref<Record<number, SegmentRow[]>>({})
const childLoadingMap = ref<Record<number, boolean>>({})
const childErrorMap = ref<Record<number, string>>({})
const saveLoadingMap = ref<Record<number, boolean>>({})
const saveErrorMap = ref<Record<number, string>>({})
const segmentDraftMap = ref<Record<number, string>>({})
const ragasMap = ref<Record<number, AdminQuestionDocumentSegmentRagasVO>>({})
const ragasDraftMap = ref<Record<number, { question: string; standardAnswer: string }>>({})
const ragasSaveLoadingMap = ref<Record<number, boolean>>({})
const ragasSaveErrorMap = ref<Record<number, string>>({})
const segmentDetailVisible = ref(false)
const detailSegmentId = ref<number | null>(null)
const singleActionLoading = ref(false)

// Hit@K 任务分页与筛选状态。
const taskQuery = reactive({
  status: '',
  startTime: '',
  endTime: '',
  current: 1,
  size: 20,
})
const taskLoading = ref(false)
const taskErrorMessage = ref('')
const hitkTasks = ref<AdminHitkTaskVO[]>([])
const taskTotal = ref(0)

const ragasTaskQuery = reactive({
  status: '',
  startTime: '',
  endTime: '',
  current: 1,
  size: 20,
})
const ragasTaskLoading = ref(false)
const ragasTaskErrorMessage = ref('')
const ragasTasks = ref<AdminRagasTaskVO[]>([])
const ragasTaskTotal = ref(0)

// Hit@K 任务详情弹窗状态。
const hitkTaskDetailVisible = ref(false)
const hitkTaskDetailLoading = ref(false)
const hitkTaskDetailErrorMessage = ref('')
const activeHitkTaskContext = ref<{ taskId: number; questionId: number; documentId: number } | null>(null)
const activeHitkTaskDetail = ref<AdminHitkTaskDetailVO | null>(null)

const ragasTaskDetailVisible = ref(false)
const ragasTaskDetailLoading = ref(false)
const ragasTaskDetailErrorMessage = ref('')
const activeRagasTaskId = ref<number | null>(null)
const activeRagasTaskDetail = ref<AdminRagasTaskDetailVO | null>(null)

// 任务跨页选中与统计状态。
const selectedTaskIds = ref<number[]>([])
const taskStatistics = ref<AdminHitkTaskStatisticsResponse>(createEmptyTaskStatistics())
const taskStatisticsLoading = ref(false)
const taskStatisticsErrorMessage = ref('')
const statisticsRequestToken = ref(0)

// 任务批量删除和备注编辑状态。
const batchDeleting = ref(false)
const batchDeleteResult = ref<AdminHitkTaskBatchDeleteResponse | null>(null)
const taskActionMessage = ref('')
const taskRemarkDraftMap = ref<Record<number, string>>({})
const taskRemarkSavingMap = ref<Record<number, boolean>>({})

const segmentTotalPages = computed(() => Math.max(1, Math.ceil(segmentTotal.value / segmentQuery.size)))
const taskTotalPages = computed(() => Math.max(1, Math.ceil(taskTotal.value / taskQuery.size)))
const ragasTaskTotalPages = computed(() => Math.max(1, Math.ceil(ragasTaskTotal.value / ragasTaskQuery.size)))
const selectedTaskIdSet = computed(() => new Set(selectedTaskIds.value))
const allSelectedOnTaskPage = computed(
  () => hitkTasks.value.length > 0 && hitkTasks.value.every((task) => selectedTaskIdSet.value.has(task.taskId)),
)
const partiallySelectedOnTaskPage = computed(
  () => !allSelectedOnTaskPage.value && hitkTasks.value.some((task) => selectedTaskIdSet.value.has(task.taskId)),
)
const batchDeleteFailures = computed(() => (batchDeleteResult.value?.results ?? []).filter((item) => item.success === false))

// 统一计算全量片段当前可见行，保持与文档切片页一致。
const visibleSegmentRows = computed<SegmentRow[]>(() => {
  const rows: SegmentRow[] = [...knowledgeSegments.value]
  expandedParentIds.value.forEach((parentId) => {
    const children = childRowsMap.value[parentId] ?? []
    rows.push(...children)
  })
  return rows
})
const allLoadedSegmentRows = computed<SegmentRow[]>(() => {
  const rows: SegmentRow[] = [...knowledgeSegments.value]
  Object.values(childRowsMap.value).forEach((children) => rows.push(...children))
  return rows
})
const segmentRowMap = computed(() => {
  const map = new Map<number, SegmentRow>()
  allLoadedSegmentRows.value.forEach((segment) => {
    map.set(segment.segmentId, segment)
  })
  return map
})
const visibleSegmentIds = computed(() => visibleSegmentRows.value.map((item) => item.segmentId))
const allVisibleSegmentsSelected = computed(() => {
  if (visibleSegmentIds.value.length === 0) {
    return false
  }
  return visibleSegmentIds.value.every((segmentId) => selectedSegmentIds.value.includes(segmentId))
})
const partiallyVisibleSegmentsSelected = computed(() => {
  if (visibleSegmentIds.value.length === 0) {
    return false
  }
  const selectedCount = visibleSegmentIds.value.filter((segmentId) => selectedSegmentIds.value.includes(segmentId)).length
  return selectedCount > 0 && selectedCount < visibleSegmentIds.value.length
})
const selectedVisibleSegmentCount = computed(
  () => visibleSegmentIds.value.filter((segmentId) => selectedSegmentIds.value.includes(segmentId)).length,
)
const activeDetailSegment = computed<SegmentRow | null>(() => {
  if (detailSegmentId.value == null) {
    return null
  }
  return segmentRowMap.value.get(detailSegmentId.value) ?? null
})

watch([allVisibleSegmentsSelected, partiallyVisibleSegmentsSelected], async () => {
  await nextTick()
  if (selectAllSegmentCheckboxRef.value) {
    selectAllSegmentCheckboxRef.value.indeterminate = partiallyVisibleSegmentsSelected.value
  }
})

watch(activeDetailSegment, (segment) => {
  if (segmentDetailVisible.value && detailSegmentId.value != null && segment == null) {
    segmentDetailVisible.value = false
    detailSegmentId.value = null
  }
})

// 统一解析后端错误信息。
const getErrorMessage = (error: unknown) => {
  if (error && typeof error === 'object') {
    const maybeMessage = (error as { message?: unknown }).message
    if (typeof maybeMessage === 'string' && maybeMessage.trim().length > 0) {
      return maybeMessage
    }
  }
  return '请求失败，请稍后重试'
}

// 格式化时间字段，保证空值和非标准值都可展示。
const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

const formatRate = (value?: number | null) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-'
  }
  return `${(value * 100).toFixed(2)}%`
}

const formatNumber = (value?: number | null) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-'
  }
  return value.toFixed(2)
}

const formatScore = (value?: number | null) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-'
  }
  return value.toFixed(3)
}

// datetime-local 转 ISO LocalDateTime，保证后端可直接解析。
const normalizeDateTimeParam = (value: string) => {
  const trimmedValue = value.trim()
  if (!trimmedValue) {
    return undefined
  }
  return trimmedValue.length === 16 ? `${trimmedValue}:00` : trimmedValue
}

// 解析 metadata 中的父分片标识，用于对齐文档切片页的父子行展示。
const parseParentChunkId = (metadata: string | null | undefined) => {
  if (typeof metadata !== 'string' || metadata.trim().length === 0) {
    return null
  }
  try {
    const parsed = JSON.parse(metadata) as Record<string, unknown>
    const value = parsed.parentChunkId ?? parsed.parent_chunk_id ?? parsed.PARENT_CHUNK_ID
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim()
    }
  } catch {
    // metadata 非 JSON 时直接回落为空，不中断页面渲染。
  }
  return null
}

// 将全量片段接口行映射为页面统一行结构。
const toSegmentRow = (segment: AdminKnowledgeSegmentVO): SegmentRow => ({
  ...segment,
  parentChunkId: parseParentChunkId(segment.metadata),
  parentSegment: false,
  childSegmentCount: 0,
})

// 将子分片接口行映射为全量列表行结构，复用同一套表格渲染。
const toChildSegmentRow = (child: AdminQuestionDocumentSegmentVO, parent: SegmentRow): SegmentRow => ({
  segmentId: child.segmentId,
  questionId: parent.questionId,
  questionTitle: parent.questionTitle,
  documentId: child.documentId ?? parent.documentId,
  documentTitle: parent.documentTitle,
  chunkOrder: child.chunkOrder ?? null,
  chunkId: child.chunkId ?? null,
  status: child.status ?? null,
  skipEmbedding: child.skipEmbedding ?? null,
  hitkQuestion: child.hitkQuestion ?? null,
  text: child.text ?? null,
  metadata: child.metadata ?? null,
  createdAt: child.createdAt ?? null,
  updatedAt: child.updatedAt ?? null,
  parentChunkId: child.parentChunkId ?? parseParentChunkId(child.metadata),
  parentSegment: child.parentSegment === true,
  childSegmentCount: child.childSegmentCount ?? 0,
})

// 根据当前页数据推断父分片标记与子数量，保证类型列可用。
const applyParentSegmentHints = (rows: SegmentRow[]) => {
  const childCountByChunk = new Map<string, number>()
  for (const row of rows) {
    if (row.documentId == null || row.parentChunkId == null) {
      continue
    }
    const key = `${row.documentId}:${row.parentChunkId}`
    childCountByChunk.set(key, (childCountByChunk.get(key) ?? 0) + 1)
  }
  return rows.map((row) => {
    if (row.documentId == null || row.chunkId == null || row.parentChunkId != null) {
      return {
        ...row,
        parentSegment: false,
        childSegmentCount: 0,
      }
    }
    const childCount = childCountByChunk.get(`${row.documentId}:${row.chunkId}`) ?? 0
    return {
      ...row,
      parentSegment: childCount > 0,
      childSegmentCount: childCount,
    }
  })
}

// 统一处理文本展示，避免空值影响省略呈现。
const toDisplayText = (value: string | null | undefined) => {
  if (typeof value !== 'string') {
    return '-'
  }
  const normalized = value.trim()
  return normalized.length > 0 ? normalized : '-'
}

// 判断文本是否存在可查看内容。
const hasTooltipContent = (value: string | null | undefined) => {
  return typeof value === 'string' && value.trim().length > 0
}

// 保留原始换行，供悬浮层完整查看。
const toTooltipText = (value: string | null | undefined) => {
  if (!hasTooltipContent(value)) {
    return '-'
  }
  return value ?? '-'
}

// 兼容后端不同版本 RAGAS 字段命名。
const toNumberOrNull = (...values: unknown[]) => {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value
    }
    if (typeof value === 'string') {
      const parsed = Number(value)
      if (Number.isFinite(parsed)) {
        return parsed
      }
    }
  }
  return null
}

const toStringOrNull = (...values: unknown[]) => {
  for (const value of values) {
    if (typeof value === 'string') {
      const normalized = value.trim()
      if (normalized.length > 0) {
        return value
      }
    }
  }
  return null
}

const normalizeRagas = (raw: unknown): AdminQuestionDocumentSegmentRagasVO | null => {
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const record = raw as Record<string, unknown>
  const scoreObj = typeof record.scores === 'object' && record.scores != null ? (record.scores as Record<string, unknown>) : {}
  const metricsObj =
    typeof record.metrics === 'object' && record.metrics != null ? (record.metrics as Record<string, unknown>) : {}

  const segmentId = toNumberOrNull(record.segmentId, record.segment_id, record.knowledgeSegmentId, record.chunkId)
  if (segmentId == null) {
    return null
  }

  return {
    id: toNumberOrNull(record.id),
    ragasId: toNumberOrNull(record.ragasId, record.ragas_id),
    segmentId,
    question: toStringOrNull(record.question, record.ragasQuestion, record.ragas_question),
    standardAnswer: toStringOrNull(
      record.standardAnswer,
      record.standard_answer,
      record.referenceAnswer,
      record.groundTruth,
      record.ground_truth,
    ),
    generatedAnswer: toStringOrNull(
      record.generatedAnswer,
      record.generated_answer,
      record.answer,
      record.ojAnswer,
      record.oj_answer,
    ),
    contextPrecision: toNumberOrNull(
      record.contextPrecision,
      record.context_precision,
      scoreObj.contextPrecision,
      scoreObj.context_precision,
      metricsObj.contextPrecision,
      metricsObj.context_precision,
    ),
    contextRecall: toNumberOrNull(
      record.contextRecall,
      record.context_recall,
      scoreObj.contextRecall,
      scoreObj.context_recall,
      metricsObj.contextRecall,
      metricsObj.context_recall,
    ),
    faithfulness: toNumberOrNull(record.faithfulness, scoreObj.faithfulness, metricsObj.faithfulness),
    answerRelevancy: toNumberOrNull(
      record.answerRelevancy,
      record.answer_relevancy,
      scoreObj.answerRelevancy,
      scoreObj.answer_relevancy,
      metricsObj.answerRelevancy,
      metricsObj.answer_relevancy,
    ),
    answerSimilarity: toNumberOrNull(
      record.answerSimilarity,
      record.answer_similarity,
      scoreObj.answerSimilarity,
      scoreObj.answer_similarity,
      metricsObj.answerSimilarity,
      metricsObj.answer_similarity,
    ),
    answerCorrectness: toNumberOrNull(
      record.answerCorrectness,
      record.answer_correctness,
      scoreObj.answerCorrectness,
      scoreObj.answer_correctness,
      metricsObj.answerCorrectness,
      metricsObj.answer_correctness,
    ),
    status: toStringOrNull(record.status),
    metadata: typeof record.metadata === 'string' ? record.metadata : null,
    createTime: toStringOrNull(record.createTime, record.create_time),
    updateTime: toStringOrNull(record.updateTime, record.update_time),
  }
}

const normalizeRagasList = (payload: unknown): AdminQuestionDocumentSegmentRagasVO[] => {
  if (Array.isArray(payload)) {
    return payload
      .map((item) => normalizeRagas(item))
      .filter((item): item is AdminQuestionDocumentSegmentRagasVO => item != null)
  }
  if (!payload || typeof payload !== 'object') {
    return []
  }
  const record = payload as Record<string, unknown>
  if (Array.isArray(record.records)) {
    return record.records
      .map((item) => normalizeRagas(item))
      .filter((item): item is AdminQuestionDocumentSegmentRagasVO => item != null)
  }
  if (Array.isArray(record.items)) {
    return record.items
      .map((item) => normalizeRagas(item))
      .filter((item): item is AdminQuestionDocumentSegmentRagasVO => item != null)
  }
  return []
}

// 合并 RAGAS 记录到本地缓存，便于主表与详情弹窗同时读取。
const mergeRagasRecords = (payload: unknown) => {
  const normalizedList = normalizeRagasList(payload)
  if (normalizedList.length === 0) {
    return
  }
  const nextMap = { ...ragasMap.value }
  normalizedList.forEach((item) => {
    nextMap[item.segmentId] = item
  })
  ragasMap.value = nextMap
}

const getSegmentRagas = (segmentId: number) => ragasMap.value[segmentId] ?? null

const getSegmentRagasQuestionDraft = (segment: SegmentRow) => {
  const draft = ragasDraftMap.value[segment.segmentId]
  if (draft) {
    return draft.question
  }
  return getSegmentRagas(segment.segmentId)?.question ?? ''
}

const getSegmentRagasStandardAnswerDraft = (segment: SegmentRow) => {
  const draft = ragasDraftMap.value[segment.segmentId]
  if (draft) {
    return draft.standardAnswer
  }
  return getSegmentRagas(segment.segmentId)?.standardAnswer ?? ''
}

const hasSegmentRagasDraft = (segmentId: number) => Object.prototype.hasOwnProperty.call(ragasDraftMap.value, segmentId)

const updateSegmentRagasQuestionDraft = (segmentId: number, question: string) => {
  const currentDraft = ragasDraftMap.value[segmentId] ?? {
    question: getSegmentRagas(segmentId)?.question ?? '',
    standardAnswer: getSegmentRagas(segmentId)?.standardAnswer ?? '',
  }
  ragasDraftMap.value = {
    ...ragasDraftMap.value,
    [segmentId]: {
      ...currentDraft,
      question,
    },
  }
}

const updateSegmentRagasStandardAnswerDraft = (segmentId: number, standardAnswer: string) => {
  const currentDraft = ragasDraftMap.value[segmentId] ?? {
    question: getSegmentRagas(segmentId)?.question ?? '',
    standardAnswer: getSegmentRagas(segmentId)?.standardAnswer ?? '',
  }
  ragasDraftMap.value = {
    ...ragasDraftMap.value,
    [segmentId]: {
      ...currentDraft,
      standardAnswer,
    },
  }
}

const resetSegmentRagasDraft = (segmentId: number) => {
  const { [segmentId]: _removed, ...nextDraft } = ragasDraftMap.value
  ragasDraftMap.value = nextDraft
  const { [segmentId]: _removedError, ...nextError } = ragasSaveErrorMap.value
  ragasSaveErrorMap.value = nextError
}

const isSegmentRagasDirty = (segment: SegmentRow) => {
  const ragas = getSegmentRagas(segment.segmentId)
  const questionDraft = getSegmentRagasQuestionDraft(segment)
  const standardAnswerDraft = getSegmentRagasStandardAnswerDraft(segment)
  return questionDraft !== (ragas?.question ?? '') || standardAnswerDraft !== (ragas?.standardAnswer ?? '')
}

// 构建 RAGAS 预览，复用文档切片页的展示格式。
const getRagasPreviewText = (segment: SegmentRow) => {
  return `Q: ${toDisplayText(getSegmentRagasQuestionDraft(segment))} | A: ${toDisplayText(getSegmentRagasStandardAnswerDraft(segment))}`
}

const getRagasPreviewTitle = (segment: SegmentRow) => {
  return `Q: ${toDisplayText(getSegmentRagasQuestionDraft(segment))}\nA: ${toDisplayText(getSegmentRagasStandardAnswerDraft(segment))}`
}

const hasRagasPreviewContent = (segment: SegmentRow) => {
  return hasTooltipContent(getSegmentRagasQuestionDraft(segment)) || hasTooltipContent(getSegmentRagasStandardAnswerDraft(segment))
}

// 统一映射状态文本到标签样式。
const resolveStatusMeta = (status: string | null | undefined): StatusMeta => {
  const normalized = status?.trim()
  if (!normalized) {
    return { label: '-', variant: 'neutral', icon: '-' }
  }
  const lowerCaseStatus = normalized.toLowerCase()
  if (
    lowerCaseStatus.includes('success') ||
    lowerCaseStatus.includes('done') ||
    lowerCaseStatus.includes('finish') ||
    lowerCaseStatus.includes('completed') ||
    lowerCaseStatus.includes('pass')
  ) {
    return { label: normalized, variant: 'success', icon: 'OK' }
  }
  if (lowerCaseStatus.includes('fail') || lowerCaseStatus.includes('error')) {
    return { label: normalized, variant: 'danger', icon: 'X' }
  }
  if (lowerCaseStatus.includes('run') || lowerCaseStatus.includes('process') || lowerCaseStatus.includes('queue')) {
    return { label: normalized, variant: 'info', icon: '>' }
  }
  if (lowerCaseStatus.includes('wait') || lowerCaseStatus.includes('pending')) {
    return { label: normalized, variant: 'warning', icon: '~' }
  }
  return { label: normalized, variant: 'neutral', icon: 'i' }
}

// 统一映射命中结果状态，便于详情视图直接展示 HIT / MISS 标签。
const resolveHitStatusMeta = (hit: boolean | null | undefined): StatusMeta => {
  if (hit === true) {
    return { label: 'HIT', variant: 'success', icon: 'OK' }
  }
  return { label: 'MISS', variant: 'danger', icon: 'X' }
}

// 构建按题目与文档分组的片段集合，用于调用既有分片接口。
const buildContextGroupsByRows = (rows: SegmentRow[]) => {
  const groups = new Map<string, SegmentContextGroup>()
  rows.forEach((segment) => {
    if (segment.questionId == null || segment.documentId == null) {
      return
    }
    const key = `${segment.questionId}:${segment.documentId}`
    if (!groups.has(key)) {
      groups.set(key, {
        questionId: segment.questionId,
        documentId: segment.documentId,
        segmentIds: [],
      })
    }
    const group = groups.get(key)
    if (group) {
      group.segmentIds.push(segment.segmentId)
    }
  })
  return Array.from(groups.values()).map((group) => ({
    ...group,
    segmentIds: Array.from(new Set(group.segmentIds)),
  }))
}

const loadRagasByRows = async (rows: SegmentRow[]) => {
  const groups = buildContextGroupsByRows(rows)
  if (groups.length === 0) {
    return
  }
  for (const group of groups) {
    const ragasRecords = await adminQuestionApi.listQuestionDocumentSegmentRagas(group.questionId, group.documentId, {
      segmentIds: group.segmentIds,
    })
    mergeRagasRecords(ragasRecords)
  }
}

const loadKnowledgeSegments = async () => {
  try {
    segmentLoading.value = true
    segmentErrorMessage.value = ''
    const response = await adminQuestionApi.pageKnowledgeSegments({
      current: segmentQuery.current,
      size: segmentQuery.size,
      keyword: segmentQuery.keyword || undefined,
    })
    const rows = applyParentSegmentHints((response.records ?? []).map((segment) => toSegmentRow(segment)))
    knowledgeSegments.value = rows
    segmentTotal.value = response.total ?? 0

    expandedParentIds.value = []
    childRowsMap.value = {}
    childLoadingMap.value = {}
    childErrorMap.value = {}
    segmentDraftMap.value = {}
    saveLoadingMap.value = {}
    saveErrorMap.value = {}
    ragasMap.value = {}
    ragasDraftMap.value = {}
    ragasSaveLoadingMap.value = {}
    ragasSaveErrorMap.value = {}

    // 翻页后保留跨页勾选，并刷新当前页命中的片段快照。
    updateSelectedSegmentIds(selectedSegmentIds.value)

    await loadRagasByRows(rows)
  } catch (error) {
    segmentErrorMessage.value = getErrorMessage(error)
    knowledgeSegments.value = []
    segmentTotal.value = 0
    expandedParentIds.value = []
    childRowsMap.value = {}
    ragasMap.value = {}
  } finally {
    segmentLoading.value = false
  }
}

const searchSegments = async () => {
  segmentQuery.current = 1
  await loadKnowledgeSegments()
}

const resetSegmentFilters = async () => {
  segmentQuery.keyword = ''
  segmentQuery.current = 1
  await loadKnowledgeSegments()
}

const changeSegmentPage = async (nextPage: number) => {
  if (nextPage < 1 || nextPage === segmentQuery.current) {
    return
  }
  segmentQuery.current = nextPage
  await loadKnowledgeSegments()
}

const isParentExpanded = (segmentId: number) => expandedParentIds.value.includes(segmentId)
const isSegmentSelected = (segmentId: number) => selectedSegmentIds.value.includes(segmentId)

// 统一更新片段选中列表，并缓存跨页批量操作所需的片段上下文快照。
const updateSelectedSegmentIds = (segmentIds: number[]) => {
  const uniqueSegmentIds = Array.from(new Set(segmentIds))
  const nextSnapshotMap: Record<number, SegmentRow> = {}
  uniqueSegmentIds.forEach((segmentId) => {
    const snapshot = segmentRowMap.value.get(segmentId) ?? selectedSegmentSnapshotMap.value[segmentId]
    if (snapshot) {
      nextSnapshotMap[segmentId] = snapshot
    }
  })
  selectedSegmentIds.value = uniqueSegmentIds
  selectedSegmentSnapshotMap.value = nextSnapshotMap
}

// 优先从当前页读取片段，缺失时回退到跨页快照。
const getSelectedSegmentSnapshot = (segmentId: number) =>
  segmentRowMap.value.get(segmentId) ?? selectedSegmentSnapshotMap.value[segmentId]

const toggleSegmentSelection = (segmentId: number) => {
  if (isSegmentSelected(segmentId)) {
    updateSelectedSegmentIds(selectedSegmentIds.value.filter((id) => id !== segmentId))
    return
  }
  updateSelectedSegmentIds([...selectedSegmentIds.value, segmentId])
}

const toggleSelectAllVisibleSegments = () => {
  const visibleIds = visibleSegmentIds.value
  if (visibleIds.length === 0) {
    return
  }
  if (allVisibleSegmentsSelected.value) {
    updateSelectedSegmentIds(selectedSegmentIds.value.filter((segmentId) => !visibleIds.includes(segmentId)))
    return
  }
  const nextSelectedIds = new Set(selectedSegmentIds.value)
  visibleIds.forEach((segmentId) => nextSelectedIds.add(segmentId))
  updateSelectedSegmentIds(Array.from(nextSelectedIds))
}

const invertVisibleSegmentSelection = () => {
  const visibleIdSet = new Set(visibleSegmentIds.value)
  if (visibleIdSet.size === 0) {
    return
  }
  const previousSelected = new Set(selectedSegmentIds.value)
  const nextSelected = selectedSegmentIds.value.filter((segmentId) => !visibleIdSet.has(segmentId))
  visibleIdSet.forEach((segmentId) => {
    if (!previousSelected.has(segmentId)) {
      nextSelected.push(segmentId)
    }
  })
  updateSelectedSegmentIds(nextSelected)
}

const clearSegmentSelection = () => {
  updateSelectedSegmentIds([])
}

const removeSelectedSegmentIds = (segmentIds: number[]) => {
  if (segmentIds.length === 0 || selectedSegmentIds.value.length === 0) {
    return 0
  }
  const removedIdSet = new Set(segmentIds)
  const nextSelected = selectedSegmentIds.value.filter((segmentId) => !removedIdSet.has(segmentId))
  const removedCount = selectedSegmentIds.value.length - nextSelected.length
  if (removedCount > 0) {
    updateSelectedSegmentIds(nextSelected)
  }
  return removedCount
}

const canOpenSegmentDocument = (segment: SegmentRow | null | undefined) =>
  !!segment && segment.questionId != null && segment.documentId != null

const openSegmentDocumentPage = async (segment: SegmentRow) => {
  if (!canOpenSegmentDocument(segment)) {
    return
  }
  await router.push(buildQuestionDocumentSegmentRoute(segment.questionId!, segment.documentId!, segment.documentTitle))
}

const resolveSegmentContext = (segment: SegmentRow) => {
  if (segment.questionId == null || segment.documentId == null) {
    return null
  }
  return {
    questionId: segment.questionId,
    documentId: segment.documentId,
  }
}

const toggleParentSegment = async (segment: SegmentRow) => {
  if (!segment.parentSegment) {
    return
  }
  const segmentId = segment.segmentId
  if (isParentExpanded(segmentId)) {
    const childSegmentIds = (childRowsMap.value[segmentId] ?? []).map((child) => child.segmentId)
    removeSelectedSegmentIds(childSegmentIds)
    expandedParentIds.value = expandedParentIds.value.filter((id) => id !== segmentId)
    return
  }

  expandedParentIds.value = [...expandedParentIds.value, segmentId]
  if (childRowsMap.value[segmentId]) {
    return
  }

  const context = resolveSegmentContext(segment)
  if (context == null) {
    childErrorMap.value = {
      ...childErrorMap.value,
      [segmentId]: '当前片段缺少题目或文档关联，无法查询子分片',
    }
    expandedParentIds.value = expandedParentIds.value.filter((id) => id !== segmentId)
    return
  }

  try {
    childLoadingMap.value = {
      ...childLoadingMap.value,
      [segmentId]: true,
    }
    childErrorMap.value = {
      ...childErrorMap.value,
      [segmentId]: '',
    }

    const children = await adminQuestionApi.listQuestionDocumentChildSegments(context.questionId, context.documentId, segmentId)
    const childRows = (children ?? []).map((child) => toChildSegmentRow(child, segment))
    childRowsMap.value = {
      ...childRowsMap.value,
      [segmentId]: childRows,
    }
    knowledgeSegments.value = knowledgeSegments.value.map((item) =>
      item.segmentId === segmentId
        ? {
            ...item,
            parentSegment: childRows.length > 0,
            childSegmentCount: childRows.length,
          }
        : item,
    )
    if (childRows.length === 0) {
      expandedParentIds.value = expandedParentIds.value.filter((id) => id !== segmentId)
      return
    }
    await loadRagasByRows(childRows)
  } catch (error) {
    childErrorMap.value = {
      ...childErrorMap.value,
      [segmentId]: getErrorMessage(error),
    }
    expandedParentIds.value = expandedParentIds.value.filter((id) => id !== segmentId)
  } finally {
    childLoadingMap.value = {
      ...childLoadingMap.value,
      [segmentId]: false,
    }
  }
}

const openSegmentDetailDialog = (segment: SegmentRow) => {
  detailSegmentId.value = segment.segmentId
  segmentDetailVisible.value = true
}

const closeSegmentDetailDialog = () => {
  segmentDetailVisible.value = false
  detailSegmentId.value = null
}

// 打开 Hit@K 任务详情弹窗并拉取任务详情数据。
const openHitkTaskDetailDialog = async (task: AdminHitkTaskVO) => {
  activeHitkTaskContext.value = {
    taskId: task.taskId,
    questionId: task.questionId,
    documentId: task.documentId,
  }
  hitkTaskDetailVisible.value = true
  await loadHitkTaskDetail(activeHitkTaskContext.value)
}

// 关闭 Hit@K 任务详情弹窗并清理缓存数据。
const closeHitkTaskDetailDialog = () => {
  hitkTaskDetailVisible.value = false
  hitkTaskDetailLoading.value = false
  hitkTaskDetailErrorMessage.value = ''
  activeHitkTaskContext.value = null
  activeHitkTaskDetail.value = null
}

// 刷新当前打开任务的详情，便于弹窗内直接重试。
const reloadActiveHitkTaskDetail = async () => {
  if (activeHitkTaskContext.value == null) {
    return
  }
  await loadHitkTaskDetail(activeHitkTaskContext.value)
}

const openRagasTaskDetailDialog = async (task: AdminRagasTaskVO) => {
  activeRagasTaskId.value = task.taskId
  ragasTaskDetailVisible.value = true
  await loadRagasTaskDetail(task.taskId)
}

const closeRagasTaskDetailDialog = () => {
  ragasTaskDetailVisible.value = false
  ragasTaskDetailLoading.value = false
  ragasTaskDetailErrorMessage.value = ''
  activeRagasTaskId.value = null
  activeRagasTaskDetail.value = null
}

const reloadActiveRagasTaskDetail = async () => {
  if (activeRagasTaskId.value == null) {
    return
  }
  await loadRagasTaskDetail(activeRagasTaskId.value)
}

// 按任务维度读取详情，复用文档切片页的任务详情接口。
const loadHitkTaskDetail = async (task: { taskId: number; questionId: number; documentId: number }) => {
  try {
    hitkTaskDetailLoading.value = true
    hitkTaskDetailErrorMessage.value = ''
    activeHitkTaskDetail.value = await adminQuestionApi.getQuestionDocumentSegmentHitkTestTaskDetail(
      task.questionId,
      task.documentId,
      task.taskId,
    )
  } catch (error) {
    hitkTaskDetailErrorMessage.value = getErrorMessage(error)
    activeHitkTaskDetail.value = null
  } finally {
    hitkTaskDetailLoading.value = false
  }
}

const loadRagasTaskDetail = async (taskId: number) => {
  try {
    ragasTaskDetailLoading.value = true
    ragasTaskDetailErrorMessage.value = ''
    activeRagasTaskDetail.value = await adminQuestionApi.getRagasTaskDetail(taskId)
  } catch (error) {
    ragasTaskDetailErrorMessage.value = getErrorMessage(error)
    activeRagasTaskDetail.value = null
  } finally {
    ragasTaskDetailLoading.value = false
  }
}

const getSegmentHitkQuestionDraft = (segment: SegmentRow) => {
  return segmentDraftMap.value[segment.segmentId] ?? segment.hitkQuestion ?? ''
}

const hasSegmentDraft = (segmentId: number) => Object.prototype.hasOwnProperty.call(segmentDraftMap.value, segmentId)

const updateSegmentHitkQuestionDraft = (segmentId: number, value: string) => {
  segmentDraftMap.value = {
    ...segmentDraftMap.value,
    [segmentId]: value,
  }
}

const resetSegmentHitkQuestionDraft = (segmentId: number) => {
  const { [segmentId]: _removed, ...rest } = segmentDraftMap.value
  segmentDraftMap.value = rest
  const { [segmentId]: _removedError, ...errorRest } = saveErrorMap.value
  saveErrorMap.value = errorRest
}

const isSegmentHitkQuestionDirty = (segment: SegmentRow) => getSegmentHitkQuestionDraft(segment) !== (segment.hitkQuestion ?? '')

const patchSegmentHitkQuestion = (segmentId: number, hitkQuestion: string) => {
  knowledgeSegments.value = knowledgeSegments.value.map((segment) =>
    segment.segmentId === segmentId
      ? {
          ...segment,
          hitkQuestion,
        }
      : segment,
  )
  const nextChildRowsMap: Record<number, SegmentRow[]> = {}
  Object.entries(childRowsMap.value).forEach(([parentId, children]) => {
    nextChildRowsMap[Number(parentId)] = children.map((child) =>
      child.segmentId === segmentId
        ? {
            ...child,
            hitkQuestion,
          }
        : child,
    )
  })
  childRowsMap.value = nextChildRowsMap
}

const saveSegmentHitkQuestion = async (segment: SegmentRow) => {
  if (!isSegmentHitkQuestionDirty(segment)) {
    return
  }
  const context = resolveSegmentContext(segment)
  if (context == null) {
    saveErrorMap.value = {
      ...saveErrorMap.value,
      [segment.segmentId]: '当前片段缺少题目或文档关联，无法保存 Hit@K 问题',
    }
    return
  }
  try {
    saveLoadingMap.value = {
      ...saveLoadingMap.value,
      [segment.segmentId]: true,
    }
    saveErrorMap.value = {
      ...saveErrorMap.value,
      [segment.segmentId]: '',
    }
    const nextValue = getSegmentHitkQuestionDraft(segment)
    await adminQuestionApi.updateQuestionDocumentSegmentHitkQuestion(context.questionId, context.documentId, {
      updates: [
        {
          segmentId: segment.segmentId,
          hitkQuestion: nextValue,
        },
      ],
    })
    patchSegmentHitkQuestion(segment.segmentId, nextValue)
    resetSegmentHitkQuestionDraft(segment.segmentId)
    segmentActionMessage.value = `片段 ${segment.segmentId} 的 Hit@K 问题已更新`
    segmentActionErrorMessage.value = ''
  } catch (error) {
    const message = getErrorMessage(error)
    saveErrorMap.value = {
      ...saveErrorMap.value,
      [segment.segmentId]: message,
    }
    segmentActionErrorMessage.value = message
  } finally {
    saveLoadingMap.value = {
      ...saveLoadingMap.value,
      [segment.segmentId]: false,
    }
  }
}

const saveSegmentRagas = async (segment: SegmentRow) => {
  if (!isSegmentRagasDirty(segment)) {
    return
  }
  const context = resolveSegmentContext(segment)
  if (context == null) {
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: '当前片段缺少题目或文档关联，无法保存 RAGAS',
    }
    return
  }
  const questionDraft = getSegmentRagasQuestionDraft(segment).trim()
  const standardAnswerDraft = getSegmentRagasStandardAnswerDraft(segment).trim()
  if (!questionDraft || !standardAnswerDraft) {
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: 'RAGAS 问题与标准答案均不能为空',
    }
    return
  }
  try {
    ragasSaveLoadingMap.value = {
      ...ragasSaveLoadingMap.value,
      [segment.segmentId]: true,
    }
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: '',
    }
    const currentRagas = getSegmentRagas(segment.segmentId)
    const result = await adminQuestionApi.updateQuestionDocumentSegmentRagas(context.questionId, context.documentId, {
      updates: [
        {
          id: currentRagas?.id ?? undefined,
          ragasId: currentRagas?.ragasId ?? currentRagas?.id ?? undefined,
          segmentId: segment.segmentId,
          question: questionDraft,
          standardAnswer: standardAnswerDraft,
        },
      ],
    })
    mergeRagasRecords(result)
    resetSegmentRagasDraft(segment.segmentId)
    segmentActionMessage.value = `片段 ${segment.segmentId} 的 RAGAS 已更新`
    segmentActionErrorMessage.value = ''
  } catch (error) {
    const message = getErrorMessage(error)
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: message,
    }
    segmentActionErrorMessage.value = message
  } finally {
    ragasSaveLoadingMap.value = {
      ...ragasSaveLoadingMap.value,
      [segment.segmentId]: false,
    }
  }
}

const runSingleSegmentAction = async (action: () => Promise<string>) => {
  try {
    singleActionLoading.value = true
    segmentActionErrorMessage.value = ''
    segmentActionMessage.value = await action()
  } catch (error) {
    segmentActionErrorMessage.value = getErrorMessage(error)
  } finally {
    singleActionLoading.value = false
  }
}

const generateHitkQuestionForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const context = resolveSegmentContext(segment)
    if (context == null) {
      throw new Error('当前片段缺少题目或文档关联，无法生成 Hit@K 问题')
    }
    await adminQuestionApi.generateQuestionDocumentSegmentHitkQuestion(context.questionId, context.documentId, {
      segmentIds: [segment.segmentId],
    })
    await loadKnowledgeSegments()
    return `已为片段 ${segment.segmentId} 生成 Hit@K 问题`
  })
}

const generateRagasForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const context = resolveSegmentContext(segment)
    if (context == null) {
      throw new Error('当前片段缺少题目或文档关联，无法生成 RAGAS QA')
    }
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagas(context.questionId, context.documentId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `已为片段 ${segment.segmentId} 生成 RAGAS QA`
  })
}

const generateRagasAnswerForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const context = resolveSegmentContext(segment)
    if (context == null) {
      throw new Error('当前片段缺少题目或文档关联，无法生成 RAGAS 答案')
    }
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagasAnswers(context.questionId, context.documentId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `已为片段 ${segment.segmentId} 生成 RAGAS 答案`
  })
}

const evaluateRagasForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const context = resolveSegmentContext(segment)
    if (context == null) {
      throw new Error('当前片段缺少题目或文档关联，无法执行 RAGAS 评估')
    }
    const result = await adminQuestionApi.evaluateQuestionDocumentSegmentRagas(context.questionId, context.documentId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `已完成片段 ${segment.segmentId} 的 RAGAS 评估`
  })
}

const collectSelectedSegmentGroups = () => {
  const groups = new Map<string, SegmentContextGroup>()
  let invalidCount = 0
  selectedSegmentIds.value.forEach((segmentId) => {
    const segment = getSelectedSegmentSnapshot(segmentId)
    if (!segment) {
      invalidCount += 1
      return
    }
    const context = resolveSegmentContext(segment)
    if (context == null) {
      invalidCount += 1
      return
    }
    const key = `${context.questionId}:${context.documentId}`
    if (!groups.has(key)) {
      groups.set(key, {
        questionId: context.questionId,
        documentId: context.documentId,
        segmentIds: [],
      })
    }
    const group = groups.get(key)
    if (group) {
      group.segmentIds.push(segmentId)
    }
  })
  return {
    groups: Array.from(groups.values()).map((group) => ({
      ...group,
      segmentIds: Array.from(new Set(group.segmentIds)),
    })),
    invalidCount,
  }
}

const buildSegmentActionErrorSummary = (invalidCount: number, failures: string[]) => {
  const segments: string[] = []
  if (invalidCount > 0) {
    segments.push(`${invalidCount} 个片段缺少题目/文档关联`)
  }
  if (failures.length > 0) {
    segments.push(...failures)
  }
  return segments.join('；')
}

const generateHitkQuestionsForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  const { groups, invalidCount } = collectSelectedSegmentGroups()
  if (groups.length === 0) {
    segmentActionErrorMessage.value = '已选片段缺少题目/文档关联，无法批量生成 Hit@K 问题'
    return
  }
  try {
    segmentBatchGenerating.value = true
    segmentActionMessage.value = ''
    segmentActionErrorMessage.value = ''
    const failures: string[] = []
    let successCount = 0
    for (const group of groups) {
      try {
        await adminQuestionApi.generateQuestionDocumentSegmentHitkQuestion(group.questionId, group.documentId, {
          segmentIds: group.segmentIds,
        })
        successCount += group.segmentIds.length
      } catch (error) {
        failures.push(`Q#${group.questionId}/D#${group.documentId} 失败：${getErrorMessage(error)}`)
      }
    }
    await loadKnowledgeSegments()
    if (successCount > 0) {
      segmentActionMessage.value = `已为 ${successCount} 个片段生成 Hit@K 问题`
    }
    const errorSummary = buildSegmentActionErrorSummary(invalidCount, failures)
    if (errorSummary) {
      segmentActionErrorMessage.value = `生成 Hit@K 问题部分失败：${errorSummary}`
    }
  } finally {
    segmentBatchGenerating.value = false
  }
}

const createHitkTestTaskForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  const { groups, invalidCount } = collectSelectedSegmentGroups()
  if (groups.length === 0) {
    segmentActionErrorMessage.value = '已选片段缺少题目/文档关联，无法创建 Hit@K 任务'
    return
  }
  try {
    segmentBatchTesting.value = true
    segmentActionMessage.value = ''
    segmentActionErrorMessage.value = ''
    const failures: string[] = []
    let createdTaskCount = 0
    for (const group of groups) {
      try {
        await adminQuestionApi.createQuestionDocumentSegmentHitkTestTask(group.questionId, group.documentId, {
          segmentIds: group.segmentIds,
        })
        createdTaskCount += 1
      } catch (error) {
        failures.push(`Q#${group.questionId}/D#${group.documentId} 失败：${getErrorMessage(error)}`)
      }
    }
    await loadHitkTasks()
    if (createdTaskCount > 0) {
      segmentActionMessage.value = `已创建 ${createdTaskCount} 个 Hit@K 测试任务`
    }
    const errorSummary = buildSegmentActionErrorSummary(invalidCount, failures)
    if (errorSummary) {
      segmentActionErrorMessage.value = `创建 Hit@K 任务部分失败：${errorSummary}`
    }
  } finally {
    segmentBatchTesting.value = false
  }
}

const generateRagasForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  const { groups, invalidCount } = collectSelectedSegmentGroups()
  if (groups.length === 0) {
    segmentActionErrorMessage.value = '已选片段缺少题目/文档关联，无法生成 RAGAS QA'
    return
  }
  try {
    ragasBatchGenerating.value = true
    segmentActionMessage.value = ''
    segmentActionErrorMessage.value = ''
    const failures: string[] = []
    let successCount = 0
    for (const group of groups) {
      try {
        const result = await adminQuestionApi.generateQuestionDocumentSegmentRagas(group.questionId, group.documentId, {
          segmentIds: group.segmentIds,
        })
        mergeRagasRecords(result)
        successCount += group.segmentIds.length
      } catch (error) {
        failures.push(`Q#${group.questionId}/D#${group.documentId} 失败：${getErrorMessage(error)}`)
      }
    }
    if (successCount > 0) {
      segmentActionMessage.value = `已为 ${successCount} 个片段生成 RAGAS QA`
    }
    const errorSummary = buildSegmentActionErrorSummary(invalidCount, failures)
    if (errorSummary) {
      segmentActionErrorMessage.value = `生成 RAGAS QA 部分失败：${errorSummary}`
    }
  } finally {
    ragasBatchGenerating.value = false
  }
}

const generateRagasAnswersForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  const { groups, invalidCount } = collectSelectedSegmentGroups()
  if (groups.length === 0) {
    segmentActionErrorMessage.value = '已选片段缺少题目/文档关联，无法生成 RAGAS 答案'
    return
  }
  try {
    ragasAnswerGenerating.value = true
    segmentActionMessage.value = ''
    segmentActionErrorMessage.value = ''
    const failures: string[] = []
    let successCount = 0
    for (const group of groups) {
      try {
        const result = await adminQuestionApi.generateQuestionDocumentSegmentRagasAnswers(group.questionId, group.documentId, {
          segmentIds: group.segmentIds,
        })
        mergeRagasRecords(result)
        successCount += group.segmentIds.length
      } catch (error) {
        failures.push(`Q#${group.questionId}/D#${group.documentId} 失败：${getErrorMessage(error)}`)
      }
    }
    if (successCount > 0) {
      segmentActionMessage.value = `已为 ${successCount} 个片段生成 RAGAS 答案`
    }
    const errorSummary = buildSegmentActionErrorSummary(invalidCount, failures)
    if (errorSummary) {
      segmentActionErrorMessage.value = `生成 RAGAS 答案部分失败：${errorSummary}`
    }
  } finally {
    ragasAnswerGenerating.value = false
  }
}

const evaluateRagasForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  const { groups, invalidCount } = collectSelectedSegmentGroups()
  if (groups.length === 0) {
    segmentActionErrorMessage.value = '已选片段缺少题目/文档关联，无法执行 RAGAS 评估'
    return
  }
  try {
    ragasEvaluating.value = true
    segmentActionMessage.value = ''
    segmentActionErrorMessage.value = ''
    const failures: string[] = []
    const createdTaskIds: number[] = []
    for (const group of groups) {
      try {
        const task = await adminQuestionApi.createQuestionDocumentSegmentRagasTask(group.questionId, group.documentId, {
          segmentIds: group.segmentIds,
        })
        createdTaskIds.push(task.taskId)
      } catch (error) {
        failures.push(`Q#${group.questionId}/D#${group.documentId} 失败：${getErrorMessage(error)}`)
      }
    }
    await loadRagasTasks()
    if (createdTaskIds.length > 0) {
      segmentActionMessage.value = `已创建 ${createdTaskIds.length} 个 RAGAS 评估任务：${createdTaskIds.join('、')}`
    }
    const errorSummary = buildSegmentActionErrorSummary(invalidCount, failures)
    if (errorSummary) {
      segmentActionErrorMessage.value = `创建 RAGAS 任务部分失败：${errorSummary}`
    }
  } finally {
    ragasEvaluating.value = false
  }
}

// 加载 Hit@K 任务分页数据，并同步备注草稿。
const loadHitkTasks = async () => {
  const startTime = normalizeDateTimeParam(taskQuery.startTime)
  const endTime = normalizeDateTimeParam(taskQuery.endTime)
  if (startTime && endTime && startTime > endTime) {
    taskErrorMessage.value = '开始时间不能晚于结束时间'
    return
  }
  try {
    taskLoading.value = true
    taskErrorMessage.value = ''
    const response = await adminQuestionApi.pageHitkTasks({
      current: taskQuery.current,
      size: taskQuery.size,
      status: taskQuery.status || undefined,
      startTime,
      endTime,
    })
    hitkTasks.value = response.records ?? []
    taskTotal.value = response.total ?? 0
    syncTaskRemarkDrafts()
  } catch (error) {
    taskErrorMessage.value = getErrorMessage(error)
    hitkTasks.value = []
    taskTotal.value = 0
  } finally {
    taskLoading.value = false
  }
}

const loadRagasTasks = async () => {
  const startTime = normalizeDateTimeParam(ragasTaskQuery.startTime)
  const endTime = normalizeDateTimeParam(ragasTaskQuery.endTime)
  if (startTime && endTime && startTime > endTime) {
    ragasTaskErrorMessage.value = '开始时间不能晚于结束时间'
    return
  }
  try {
    ragasTaskLoading.value = true
    ragasTaskErrorMessage.value = ''
    const response = await adminQuestionApi.pageRagasTasks({
      current: ragasTaskQuery.current,
      size: ragasTaskQuery.size,
      status: ragasTaskQuery.status || undefined,
      startTime,
      endTime,
    })
    ragasTasks.value = response.records ?? []
    ragasTaskTotal.value = response.total ?? 0
  } catch (error) {
    ragasTaskErrorMessage.value = getErrorMessage(error)
    ragasTasks.value = []
    ragasTaskTotal.value = 0
  } finally {
    ragasTaskLoading.value = false
  }
}

const searchTasks = async () => {
  taskQuery.current = 1
  await loadHitkTasks()
}

const searchRagasTasks = async () => {
  ragasTaskQuery.current = 1
  await loadRagasTasks()
}

const resetTaskFilters = async () => {
  taskQuery.status = ''
  taskQuery.startTime = ''
  taskQuery.endTime = ''
  taskQuery.current = 1
  await loadHitkTasks()
}

const resetRagasTaskFilters = async () => {
  ragasTaskQuery.status = ''
  ragasTaskQuery.startTime = ''
  ragasTaskQuery.endTime = ''
  ragasTaskQuery.current = 1
  await loadRagasTasks()
}

const changeTaskPage = async (nextPage: number) => {
  if (nextPage < 1 || nextPage === taskQuery.current) {
    return
  }
  taskQuery.current = nextPage
  await loadHitkTasks()
}

const changeRagasTaskPage = async (nextPage: number) => {
  if (nextPage < 1 || nextPage === ragasTaskQuery.current) {
    return
  }
  ragasTaskQuery.current = nextPage
  await loadRagasTasks()
}

// 根据当前选中任务加载统计卡片数据。
const loadTaskStatistics = async () => {
  if (selectedTaskIds.value.length === 0) {
    taskStatistics.value = createEmptyTaskStatistics()
    taskStatisticsErrorMessage.value = ''
    return
  }
  statisticsRequestToken.value += 1
  const currentToken = statisticsRequestToken.value
  try {
    taskStatisticsLoading.value = true
    taskStatisticsErrorMessage.value = ''
    const response = await adminQuestionApi.calculateHitkTaskStatistics({
      taskIds: [...selectedTaskIds.value],
    })
    if (currentToken !== statisticsRequestToken.value) {
      return
    }
    taskStatistics.value = response
  } catch (error) {
    if (currentToken !== statisticsRequestToken.value) {
      return
    }
    taskStatisticsErrorMessage.value = getErrorMessage(error)
  } finally {
    if (currentToken === statisticsRequestToken.value) {
      taskStatisticsLoading.value = false
    }
  }
}

const updateSelectedTaskIds = (taskIds: number[]) => {
  selectedTaskIds.value = Array.from(new Set(taskIds))
  void loadTaskStatistics()
}

const isTaskSelected = (taskId: number) => selectedTaskIdSet.value.has(taskId)

const toggleTaskSelection = (taskId: number, event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    updateSelectedTaskIds([...selectedTaskIds.value, taskId])
    return
  }
  updateSelectedTaskIds(selectedTaskIds.value.filter((id) => id !== taskId))
}

const toggleCurrentTaskPage = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    updateSelectedTaskIds([...selectedTaskIds.value, ...hitkTasks.value.map((task) => task.taskId)])
    return
  }
  const pageTaskIdSet = new Set(hitkTasks.value.map((task) => task.taskId))
  updateSelectedTaskIds(selectedTaskIds.value.filter((id) => !pageTaskIdSet.has(id)))
}

const selectCurrentTaskPage = () => {
  updateSelectedTaskIds([...selectedTaskIds.value, ...hitkTasks.value.map((task) => task.taskId)])
}

const clearTaskSelection = () => {
  updateSelectedTaskIds([])
}

// 批量删除选中任务，并保留失败任务供继续处理。
const batchDeleteSelectedTasks = async () => {
  if (selectedTaskIds.value.length === 0) {
    return
  }
  if (!window.confirm(`确认删除已选中的 ${selectedTaskIds.value.length} 个任务吗？`)) {
    return
  }
  try {
    batchDeleting.value = true
    taskActionMessage.value = ''
    const response = await adminQuestionApi.batchDeleteHitkTasks({
      taskIds: selectedTaskIds.value,
    })
    batchDeleteResult.value = response
    taskActionMessage.value = `批量删除完成：成功 ${response.successCount}，失败 ${response.failureCount}`
    const failedTaskIdSet = new Set(
      (response.results ?? [])
        .filter((item) => item.success === false)
        .map((item) => item.taskId),
    )
    updateSelectedTaskIds(selectedTaskIds.value.filter((taskId) => failedTaskIdSet.has(taskId)))
    await loadHitkTasks()
  } catch (error) {
    taskErrorMessage.value = getErrorMessage(error)
  } finally {
    batchDeleting.value = false
  }
}

// 同步当前页任务备注草稿，避免切页后编辑状态丢失。
const syncTaskRemarkDrafts = () => {
  const nextDraftMap = { ...taskRemarkDraftMap.value }
  for (const task of hitkTasks.value) {
    if (nextDraftMap[task.taskId] === undefined) {
      nextDraftMap[task.taskId] = task.remark ?? ''
    }
  }
  taskRemarkDraftMap.value = nextDraftMap
}

const getTaskRemarkDraft = (task: AdminHitkTaskVO) => taskRemarkDraftMap.value[task.taskId] ?? task.remark ?? ''

const updateTaskRemarkDraft = (taskId: number, value: string) => {
  taskRemarkDraftMap.value = {
    ...taskRemarkDraftMap.value,
    [taskId]: value,
  }
}

const isTaskRemarkDirty = (task: AdminHitkTaskVO) => getTaskRemarkDraft(task) !== (task.remark ?? '')

// 保存单个任务备注并回写当前列表记录。
const saveTaskRemark = async (task: AdminHitkTaskVO) => {
  if (!isTaskRemarkDirty(task)) {
    return
  }
  try {
    taskRemarkSavingMap.value = {
      ...taskRemarkSavingMap.value,
      [task.taskId]: true,
    }
    const savedTask = await adminQuestionApi.updateHitkTaskRemark(task.taskId, {
      remark: getTaskRemarkDraft(task).trim() || null,
    })
    hitkTasks.value = hitkTasks.value.map((item) => {
      if (item.taskId !== task.taskId) {
        return item
      }
      return {
        ...item,
        remark: savedTask.remark,
      }
    })
    taskRemarkDraftMap.value = {
      ...taskRemarkDraftMap.value,
      [task.taskId]: savedTask.remark ?? '',
    }
    taskActionMessage.value = `任务 ${task.taskId} 备注已更新`
  } catch (error) {
    taskErrorMessage.value = getErrorMessage(error)
  } finally {
    taskRemarkSavingMap.value = {
      ...taskRemarkSavingMap.value,
      [task.taskId]: false,
    }
  }
}

const openTaskDocumentSegments = async (task: { questionId: number; documentId: number }) => {
  await router.push(buildQuestionDocumentSegmentRoute(task.questionId, task.documentId))
}

onMounted(async () => {
  await Promise.all([loadKnowledgeSegments(), loadHitkTasks(), loadRagasTasks()])
})
</script>

<style scoped>
.page {
  padding: 20px;
  display: grid;
  gap: 16px;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.page-header {
  display: grid;
  gap: 6px;
}

.page-title {
  margin: 0;
  color: #123d8a;
  font-size: 24px;
}

.page-subtitle {
  margin: 0;
  color: #496285;
  font-size: 13px;
}

.panel {
  border: 1px solid #d7e1f1;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  display: grid;
  gap: 12px;
  min-width: 0;
}

.task-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  min-width: 0;
}

.panel > * {
  min-width: 0;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  color: #1e3a8a;
}

.panel-header p {
  margin: 4px 0 0;
  color: #5b6b82;
  font-size: 13px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segment-toolbar {
  align-items: center;
}

.toolbar-task .input,
.toolbar-task .select {
  min-width: 180px;
}

.toolbar-text {
  color: #334155;
  font-size: 13px;
  line-height: 1.4;
}

.toolbar-stat strong {
  color: #0f172a;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #334155;
  font-size: 13px;
}

.checkbox-label input {
  width: 16px;
  height: 16px;
  accent-color: #2563eb;
}

.input,
.select {
  border: 1px solid #c8d4e8;
  border-radius: 8px;
  min-height: 36px;
  padding: 7px 10px;
}

.input {
  min-width: 280px;
}

.btn {
  border: 1px solid transparent;
  border-radius: 8px;
  min-height: 36px;
  padding: 7px 12px;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-primary {
  background: #2563eb;
  color: #fff;
}

.btn-light {
  background: #eef2f7;
  color: #1f2937;
  border-color: #d5deea;
}

.btn-danger {
  background: #dc2626;
  color: #fff;
}

.btn-generate {
  background: #0ea5e9;
  color: #fff;
}

.btn-ragas-generate {
  background: #14b8a6;
  color: #fff;
}

.btn-run {
  background: #2563eb;
  color: #fff;
}

.btn-ragas-answer {
  background: #0f766e;
  color: #fff;
}

.btn-ragas-evaluate {
  background: #0f766e;
  color: #fff;
}

.btn-small {
  min-height: 30px;
  padding: 4px 8px;
}

.feedback-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
}

.feedback-success {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.feedback-error {
  border-color: #fca5a5;
  background: #fff5f5;
  color: #b91c1c;
}

.feedback-icon {
  font-weight: 700;
}

.loading-skeleton {
  display: grid;
  gap: 8px;
}

.skeleton-row {
  height: 16px;
  border-radius: 6px;
  background: linear-gradient(90deg, #eef4ff 25%, #f8fbff 50%, #eef4ff 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

@keyframes skeleton {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.state {
  border: 1px dashed #d3dce9;
  border-radius: 8px;
  padding: 12px;
}

.compact-state {
  padding: 8px;
  font-size: 13px;
}

.state-error {
  color: #b91c1c;
  border-color: #fca5a5;
  background: #fff5f5;
}

.notice {
  margin: 0;
  border: 1px solid #86efac;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  padding: 8px 10px;
}

.helper-text {
  margin: 0;
  color: #475569;
  font-size: 13px;
}

.selection-toolbar {
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #f4f8ff;
  padding: 10px;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
}

.selection-toolbar p {
  margin: 0;
  color: #334155;
}

.selection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stats-card {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.stats-card > div {
  border: 1px solid #dbe5f4;
  border-radius: 10px;
  background: #f8fbff;
  padding: 8px 10px;
}

.stats-label {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.stats-value {
  margin: 6px 0 0;
  color: #1e3a8a;
  font-size: 18px;
  font-weight: 700;
}

.empty-state {
  border: 1px dashed #d5deea;
  border-radius: 10px;
  padding: 16px;
  background: #fafcff;
}

.compact-empty-state {
  padding: 12px;
}

.empty-title {
  margin: 0;
  font-weight: 600;
  color: #334155;
}

.empty-subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.table-wrap {
  width: 100%;
  max-width: 100%;
  border: 1px solid #dbe4f2;
  border-radius: 10px;
  overflow-x: auto;
  overflow-y: hidden;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.segment-table {
  min-width: 1720px;
}

.child-table {
  min-width: 1500px;
}

.task-table {
  min-width: 1400px;
}

.table th,
.table td {
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}

.table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f6faff;
  font-size: 12px;
  color: #334155;
}

.table tbody tr:hover {
  background: #f8fbff;
}

.segment-table > tbody > tr > td:not(.child-cell),
.child-table > tbody > tr > td {
  min-height: 68px;
}

.row-selected > td {
  background: #ebf5ff;
}

.row-selected:hover > td {
  background: #dbeeff;
}

.checkbox-col {
  width: 42px;
  text-align: center !important;
}

.num-col {
  text-align: right !important;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.date-col {
  white-space: nowrap;
  text-align: center !important;
}

.cell-main,
.cell-sub {
  margin: 0;
}

.cell-sub {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.segment-text,
.hitk-cell,
.ragas-cell {
  min-width: 260px;
}

.compact-cell {
  max-width: 360px;
}

.detail-popover {
  position: relative;
}

.detail-popover-content {
  display: none;
  position: absolute;
  z-index: 20;
  left: 0;
  top: calc(100% + 6px);
  max-width: 540px;
  min-width: 280px;
  max-height: 280px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #0f172a;
  color: #e2e8f0;
  border: 1px solid #1e293b;
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.28);
}

.detail-popover:hover .detail-popover-content,
.detail-popover:focus-within .detail-popover-content {
  display: block;
}

.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-ellipsis-multiline {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: normal;
}

.compact-preview {
  color: #1f2937;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid transparent;
  white-space: nowrap;
}

.status-icon {
  font-weight: 700;
  font-size: 11px;
}

.status-chip-success {
  color: #166534;
  background: #dcfce7;
  border-color: #86efac;
}

.status-chip-danger {
  color: #991b1b;
  background: #fee2e2;
  border-color: #fca5a5;
}

.status-chip-warning {
  color: #92400e;
  background: #fef3c7;
  border-color: #fcd34d;
}

.status-chip-info {
  color: #075985;
  background: #e0f2fe;
  border-color: #7dd3fc;
}

.status-chip-neutral {
  color: #334155;
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  color: #1d4ed8;
  background: #dbeafe;
}

.tag-parent {
  color: #155e75;
  background: #cffafe;
}

.ops {
  white-space: nowrap;
}

.ops-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ops-placeholder {
  color: #64748b;
  font-size: 12px;
}

.child-cell {
  padding: 0 !important;
}

.child-table-wrap {
  border: none;
  border-top: 1px dashed #d5deea;
  border-radius: 0;
  margin: 0;
}

.child-error {
  margin: 10px;
}

.remark-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}

.remark-input {
  min-width: 220px;
}

.link-btn {
  border: none;
  background: transparent;
  color: #2563eb;
  padding: 0;
  cursor: pointer;
}

.link-btn:not(:disabled):hover {
  color: #1d4ed8;
}

.link-btn:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
}

.pagination span {
  color: #334155;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  z-index: 2000;
}

.modal {
  width: min(1100px, 100%);
  max-height: 90vh;
  overflow: auto;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #dbe4f2;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.28);
}

.modal-large {
  width: min(1180px, 100%);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fff;
}

.modal-header h2 {
  margin: 0;
  font-size: 18px;
  color: #1e3a8a;
}

.segment-detail-body {
  display: grid;
  gap: 12px;
  padding: 14px 16px 18px;
}

.hitk-task-detail-modal {
  width: min(1160px, 100%);
}

.hitk-task-detail-body {
  display: grid;
  gap: 12px;
  padding: 14px 16px 18px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.detail-meta-grid p {
  margin: 0;
  color: #334155;
}

.hitk-task-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.hitk-task-meta-grid p {
  margin: 0;
  font-size: 13px;
  color: #334155;
}

.hitk-task-segment-list {
  display: grid;
  gap: 10px;
}

.hitk-task-segment-card {
  display: grid;
  gap: 10px;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  background: #ffffff;
  padding: 12px;
}

.hitk-task-segment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.rewritten-question-list {
  margin: 0;
  padding: 0 0 0 18px;
  display: grid;
  gap: 4px;
  color: #334155;
}

.retrieved-segment-list {
  display: grid;
  gap: 8px;
}

.retrieved-segment-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  background: #f8fafc;
}

.retrieved-score-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.detail-subtitle {
  margin: 0 0 6px;
  color: #475569;
  font-size: 12px;
}

.task-detail-skeleton {
  display: grid;
  gap: 8px;
}

.skeleton-block {
  height: 42px;
  border-radius: 8px;
  background: linear-gradient(90deg, #eef4ff 25%, #f8fbff 50%, #eef4ff 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

.detail-field {
  display: grid;
  gap: 6px;
}

.detail-field-label {
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.detail-readonly {
  margin: 0;
  border: 1px solid #d5deea;
  border-radius: 8px;
  background: #f8fbff;
  color: #1f2937;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
}

.detail-textarea {
  min-height: 96px;
}

.hitk-textarea {
  border: 1px solid #c8d4e8;
  border-radius: 8px;
  padding: 8px 10px;
  width: 100%;
  font: inherit;
  resize: vertical;
  color: #1f2937;
}

.hitk-textarea:focus {
  outline: 2px solid #93c5fd;
  outline-offset: 1px;
}

.ragas-textarea {
  min-height: 80px;
}

.detail-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.inline-error {
  color: #b91c1c;
  font-size: 13px;
}

.ragas-score-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.score-pill {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1e40af;
  font-size: 12px;
  padding: 4px 10px;
}

@media (max-width: 1280px) {
  .task-panel-grid {
    grid-template-columns: 1fr;
  }

  .stats-card {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .segment-toolbar {
    align-items: flex-start;
  }

  .toolbar-text {
    width: 100%;
  }

  .detail-meta-grid {
    grid-template-columns: 1fr;
  }

  .hitk-task-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page {
    padding: 12px;
  }

  .input {
    min-width: 0;
    width: 100%;
  }

  .toolbar,
  .selection-actions {
    width: 100%;
  }

  .stats-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hitk-task-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
