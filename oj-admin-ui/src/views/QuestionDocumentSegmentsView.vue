<template>
  <main class="page">
    <header class="header">
      <div>
        <h1 class="page-title">文档切片管理</h1>
        <p class="subtitle">
          Question {{ questionIdDisplay }} / Document {{ docIdDisplay }}
          <span v-if="docTitleDisplay"> - {{ docTitleDisplay }}</span>
        </p>
      </div>
      <button class="btn btn-light" @click="goBack">Back</button>
    </header>

    <section class="panel">
      <div class="section-header">
        <div>
          <h2 class="section-title">Hit@K Tests</h2>
          <p class="section-subtitle">Latest task summary and task history for the current document.</p>
        </div>
        <button class="btn btn-light" :disabled="taskLoading" @click="loadHitkTasks">
          {{ taskLoading ? 'Refreshing...' : 'Refresh' }}
        </button>
      </div>

      <div v-if="taskErrorMessage" class="state state-error" role="alert" aria-live="assertive">
        <span>{{ taskErrorMessage }}</span>
        <button class="btn btn-light" @click="loadHitkTasks">Retry</button>
      </div>
      <div v-else-if="taskLoading && hitkTasks.length === 0" class="loading-skeleton task-skeleton">
        <div class="skeleton-block"></div>
        <div class="skeleton-block"></div>
        <div class="skeleton-row" v-for="index in 4" :key="`task-skeleton-${index}`"></div>
      </div>
      <div v-else class="task-layout">
        <div class="task-summary-card">
          <div class="task-card-title">Latest Task</div>
          <div v-if="latestHitkTask" class="task-metrics">
            <div><span class="metric-label">Task ID</span><span>{{ latestHitkTask.taskId }}</span></div>
            <div>
              <span class="metric-label">Status</span>
              <span class="status-chip" :class="`status-chip-${resolveStatusMeta(latestHitkTask.status).variant}`">
                <span class="status-icon">{{ resolveStatusMeta(latestHitkTask.status).icon }}</span>
                {{ resolveStatusMeta(latestHitkTask.status).label }}
              </span>
            </div>
            <div><span class="metric-label">Total</span><span>{{ latestHitkTask.totalCount }}</span></div>
            <div><span class="metric-label">Hit</span><span>{{ latestHitkTask.hitCount }}</span></div>
            <div><span class="metric-label">Miss</span><span>{{ latestHitkTask.missCount }}</span></div>
            <div><span class="metric-label">Hit Rate</span><span>{{ formatRate(latestHitkTask.hitRate) }}</span></div>
            <div><span class="metric-label">Created</span><span>{{ formatDateTime(latestHitkTask.createTime) }}</span></div>
            <div><span class="metric-label">Details</span><span>{{ latestHitkTask.details?.length ?? 0 }}</span></div>
          </div>
          <div v-else class="empty-state compact-empty-state">
            <p class="empty-title">No Hit@K tasks yet</p>
            <p class="empty-subtitle">Run a task for selected segments to build summary and history.</p>
          </div>
        </div>

        <div class="task-history">
          <div class="task-card-title">History</div>
          <div v-if="hitkTasks.length === 0" class="empty-state compact-empty-state">
            <p class="empty-title">No history records</p>
            <p class="empty-subtitle">Created tasks for this document will appear here.</p>
          </div>
          <div v-else class="table-wrap">
            <table class="table task-history-table">
              <thead>
                <tr>
                  <th class="num-col">Task ID</th>
                  <th>Status</th>
                  <th class="num-col">Total</th>
                  <th class="num-col">Hit</th>
                  <th class="num-col">Miss</th>
                  <th class="num-col">Hit Rate</th>
                  <th class="date-col">Created At</th>
                  <th class="num-col">Details</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in hitkTasks" :key="task.taskId">
                  <td class="num-col">{{ task.taskId }}</td>
                  <td>
                    <span class="status-chip" :class="`status-chip-${resolveStatusMeta(task.status).variant}`">
                      <span class="status-icon">{{ resolveStatusMeta(task.status).icon }}</span>
                      {{ resolveStatusMeta(task.status).label }}
                    </span>
                  </td>
                  <td class="num-col">{{ task.totalCount }}</td>
                  <td class="num-col">{{ task.hitCount }}</td>
                  <td class="num-col">{{ task.missCount }}</td>
                  <td class="num-col">{{ formatRate(task.hitRate) }}</td>
                  <td class="date-col">{{ formatDateTime(task.createTime) }}</td>
                  <td class="num-col">{{ task.details?.length ?? 0 }}</td>
                  <td class="ops">
                    <button class="link-btn" :disabled="hitkTaskDetailLoading" @click="openHitkTaskDetailDialog(task)">
                      Detail
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="section-header">
        <div>
          <h2 class="section-title">RAGAS Tasks</h2>
          <p class="section-subtitle">Batch evaluation task summary and history for the current document.</p>
        </div>
        <button class="btn btn-light" :disabled="ragasTaskLoading" @click="loadRagasTasks">
          {{ ragasTaskLoading ? 'Refreshing...' : 'Refresh' }}
        </button>
      </div>

      <div v-if="ragasTaskErrorMessage" class="state state-error" role="alert" aria-live="assertive">
        <span>{{ ragasTaskErrorMessage }}</span>
        <button class="btn btn-light" @click="loadRagasTasks">Retry</button>
      </div>
      <div v-else-if="ragasTaskLoading && ragasTasks.length === 0" class="loading-skeleton task-skeleton">
        <div class="skeleton-block"></div>
        <div class="skeleton-block"></div>
        <div class="skeleton-row" v-for="index in 4" :key="`ragas-task-skeleton-${index}`"></div>
      </div>
      <div v-else class="task-layout">
        <div class="task-summary-card">
          <div class="task-card-title">Latest Task</div>
          <div v-if="latestRagasTask" class="task-metrics">
            <div><span class="metric-label">Task ID</span><span>{{ latestRagasTask.taskId }}</span></div>
            <div>
              <span class="metric-label">Status</span>
              <span class="status-chip" :class="`status-chip-${resolveStatusMeta(latestRagasTask.status).variant}`">
                <span class="status-icon">{{ resolveStatusMeta(latestRagasTask.status).icon }}</span>
                {{ resolveStatusMeta(latestRagasTask.status).label }}
              </span>
            </div>
            <div><span class="metric-label">Total</span><span>{{ latestRagasTask.totalCount }}</span></div>
            <div><span class="metric-label">Success</span><span>{{ latestRagasTask.successCount }}</span></div>
            <div><span class="metric-label">Failed</span><span>{{ latestRagasTask.failureCount }}</span></div>
            <div><span class="metric-label">AnsRel</span><span>{{ formatScore(latestRagasTask.averageAnswerRelevancy) }}</span></div>
            <div><span class="metric-label">Faith</span><span>{{ formatScore(latestRagasTask.averageFaithfulness) }}</span></div>
            <div><span class="metric-label">CtxPrec</span><span>{{ formatScore(latestRagasTask.averageContextPrecision) }}</span></div>
            <div><span class="metric-label">CtxRecall</span><span>{{ formatScore(latestRagasTask.averageContextRecall) }}</span></div>
            <div><span class="metric-label">Created</span><span>{{ formatDateTime(latestRagasTask.createTime) }}</span></div>
          </div>
          <div v-else class="empty-state compact-empty-state">
            <p class="empty-title">No RAGAS tasks yet</p>
            <p class="empty-subtitle">Submit a batch evaluation task for selected segments to build summary and history.</p>
          </div>
        </div>

        <div class="task-history">
          <div class="task-card-title">History</div>
          <div v-if="ragasTasks.length === 0" class="empty-state compact-empty-state">
            <p class="empty-title">No history records</p>
            <p class="empty-subtitle">Submitted RAGAS tasks for this document will appear here.</p>
          </div>
          <div v-else class="table-wrap">
            <table class="table task-history-table">
              <thead>
                <tr>
                  <th class="num-col">Task ID</th>
                  <th>Status</th>
                  <th class="num-col">Total</th>
                  <th class="num-col">Success</th>
                  <th class="num-col">Failed</th>
                  <th class="num-col">AnsRel</th>
                  <th class="num-col">Faith</th>
                  <th class="num-col">CtxPrec</th>
                  <th class="num-col">CtxRecall</th>
                  <th class="date-col">Created At</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in ragasTasks" :key="task.taskId">
                  <td class="num-col">{{ task.taskId }}</td>
                  <td>
                    <span class="status-chip" :class="`status-chip-${resolveStatusMeta(task.status).variant}`">
                      <span class="status-icon">{{ resolveStatusMeta(task.status).icon }}</span>
                      {{ resolveStatusMeta(task.status).label }}
                    </span>
                  </td>
                  <td class="num-col">{{ task.totalCount }}</td>
                  <td class="num-col">{{ task.successCount }}</td>
                  <td class="num-col">{{ task.failureCount }}</td>
                  <td class="num-col">{{ formatScore(task.averageAnswerRelevancy) }}</td>
                  <td class="num-col">{{ formatScore(task.averageFaithfulness) }}</td>
                  <td class="num-col">{{ formatScore(task.averageContextPrecision) }}</td>
                  <td class="num-col">{{ formatScore(task.averageContextRecall) }}</td>
                  <td class="date-col">{{ formatDateTime(task.createTime) }}</td>
                  <td class="ops">
                    <button class="link-btn" :disabled="ragasTaskDetailLoading" @click="openRagasTaskDetailDialog(task)">
                      Detail
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="section-header">
        <div>
          <h2 class="section-title">Segments</h2>
          <p class="section-subtitle">Select segments, edit `hitkQuestion` / RAGAS fields, and run related operations.</p>
        </div>
      </div>

      <div class="toolbar">
        <label class="checkbox-label">
          <input
            ref="selectAllCheckboxRef"
            type="checkbox"
            :checked="allVisibleSelected"
            :disabled="visibleSegmentIds.length === 0"
            aria-label="Select all visible segments"
            @change="toggleSelectAllVisible"
          />
          <span>Select visible</span>
        </label>
        <button class="btn btn-light" :disabled="visibleSegmentIds.length === 0" @click="invertVisibleSelection">
          Invert Visible
        </button>
        <span class="toolbar-text toolbar-stat">
          Selected <strong>{{ selectedSegmentIds.length }}</strong> total / Visible selected
          <strong>{{ selectedVisibleCount }}</strong> of {{ visibleSegmentIds.length }}
        </span>
        <button
          class="btn btn-generate"
          :disabled="selectedSegmentIds.length === 0 || batchGenerating"
          @click="generateHitkQuestionsForSelection"
        >
          {{ batchGenerating ? 'Generating...' : 'Generate Hit@K Questions' }}
        </button>
        <button
          class="btn btn-ragas-generate"
          :disabled="selectedSegmentIds.length === 0 || ragasBatchGenerating"
          @click="generateRagasForSelection"
        >
          {{ ragasBatchGenerating ? 'Generating...' : 'Generate RAGAS QA' }}
        </button>
        <button
          class="btn btn-run"
          :disabled="selectedSegmentIds.length === 0 || batchTesting"
          @click="createHitkTestTaskForSelection"
        >
          {{ batchTesting ? 'Submitting...' : 'Run Hit@K Tests' }}
        </button>
        <button
          class="btn btn-ragas-answer"
          :disabled="selectedSegmentIds.length === 0 || ragasAnswerGenerating"
          @click="generateRagasAnswersForSelection"
        >
          {{ ragasAnswerGenerating ? 'Generating...' : 'Generate RAGAS Answers' }}
        </button>
        <button
          class="btn btn-ragas-evaluate"
          :disabled="selectedSegmentIds.length === 0 || ragasEvaluating"
          @click="evaluateRagasForSelection"
        >
          {{ ragasEvaluating ? 'Submitting...' : 'Submit RAGAS Task' }}
        </button>
        <button class="btn btn-light" :disabled="selectedSegmentIds.length === 0" @click="clearSelection">
          Clear Selection
        </button>
      </div>

      <div
        v-if="actionMessage"
        class="feedback-banner feedback-success"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        <span class="feedback-icon">OK</span>
        <span>{{ actionMessage }}</span>
      </div>
      <div
        v-if="actionErrorMessage"
        class="feedback-banner feedback-error"
        role="alert"
        aria-live="assertive"
        aria-atomic="true"
      >
        <span class="feedback-icon">!</span>
        <span>{{ actionErrorMessage }}</span>
      </div>

      <div v-if="loading" class="loading-skeleton">
        <div class="skeleton-row" v-for="index in 6" :key="`segment-skeleton-${index}`"></div>
      </div>
      <div v-else-if="errorMessage" class="state state-error" role="alert" aria-live="assertive">
        <span>{{ errorMessage }}</span>
        <button class="btn btn-light" @click="loadSegments">Retry</button>
      </div>
      <div v-else-if="records.length === 0" class="empty-state">
        <p class="empty-title">No segments on this page</p>
        <p class="empty-subtitle">Try another page or refresh after generating document segments.</p>
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
              <template v-for="segment in records" :key="segment.segmentId">
                <tr :class="{ 'row-selected': isSegmentSelected(segment.segmentId) }">
                  <td class="checkbox-col">
                    <input
                      type="checkbox"
                      :checked="isSegmentSelected(segment.segmentId)"
                      :aria-label="`Select segment ${segment.segmentId}`"
                      @change="toggleSegmentSelection(segment.segmentId)"
                    />
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
                                />
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
                                <button class="link-btn" @click="openSegmentDetailDialog(child)">Detail</button>
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
          <button class="btn btn-light" :disabled="loading || current <= 1" @click="changePage(current - 1)">
            Prev
          </button>
          <span>Page {{ current }} / {{ totalPagesDisplay }}, Total {{ total }}</span>
          <button
            class="btn btn-light"
            :disabled="loading || current >= totalPagesDisplay"
            @click="changePage(current + 1)"
          >
            Next
          </button>
        </div>
      </div>
    </section>

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
              {{ singleActionLoading ? 'Running...' : 'Generate Answer' }}
            </button>
            <button
              class="btn btn-light btn-small"
              :disabled="singleActionLoading"
              @click="evaluateRagasForSegment(activeDetailSegment)"
            >
              {{ singleActionLoading ? 'Running...' : 'Run RAGAS Eval' }}
            </button>
            <button
              class="btn btn-light btn-small btn-danger-outline"
              :disabled="getSegmentRagas(activeDetailSegment.segmentId) == null || ragasDeleteLoadingMap[activeDetailSegment.segmentId] === true"
              @click="deleteSegmentRagas(activeDetailSegment)"
            >
              {{ ragasDeleteLoadingMap[activeDetailSegment.segmentId] === true ? 'Deleting...' : 'Delete RAGAS' }}
            </button>
          </div>
          <div v-if="ragasSaveErrorMap[activeDetailSegment.segmentId]" class="inline-error">
            {{ ragasSaveErrorMap[activeDetailSegment.segmentId] }}
          </div>
        </div>
        <div v-else class="state state-error">Segment not found. Please refresh page and retry.</div>
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
              {{ ragasTaskDetailLoading ? 'Refreshing...' : 'Refresh' }}
            </button>
            <button class="link-btn" @click="closeRagasTaskDetailDialog">Close</button>
          </div>
        </header>

        <div v-if="ragasTaskDetailLoading" class="task-detail-skeleton">
          <div class="skeleton-block"></div>
          <div class="skeleton-block"></div>
          <div class="skeleton-row" v-for="index in 4" :key="`ragas-task-detail-skeleton-${index}`"></div>
        </div>
        <div v-else-if="ragasTaskDetailErrorMessage" class="state state-error">
          <p>{{ ragasTaskDetailErrorMessage }}</p>
          <button class="btn btn-light btn-small" @click="reloadActiveRagasTaskDetail">Retry</button>
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

              <div class="ragas-score-grid detail-score-grid">
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
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  adminQuestionApi,
  type AdminHitkTaskDetailVO,
  type AdminHitkTaskVO,
  type AdminRagasTaskDetailVO,
  type AdminRagasTaskVO,
  type AdminQuestionDocumentSegmentRagasVO,
  type AdminQuestionDocumentSegmentVO,
} from '@/api/adminQuestion'

type SegmentRow = AdminQuestionDocumentSegmentVO
type HitkTestTask = AdminHitkTaskVO
type HitkTaskDetail = AdminHitkTaskDetailVO
type RagasTask = AdminRagasTaskVO
type RagasTaskDetail = AdminRagasTaskDetailVO
type StatusVariant = 'success' | 'danger' | 'warning' | 'info' | 'neutral'
type StatusMeta = {
  label: string
  variant: StatusVariant
  icon: string
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const taskLoading = ref(false)
const taskErrorMessage = ref('')
const ragasTaskLoading = ref(false)
const ragasTaskErrorMessage = ref('')
const actionMessage = ref('')
const actionErrorMessage = ref('')
const batchGenerating = ref(false)
const batchTesting = ref(false)
const ragasBatchGenerating = ref(false)
const ragasAnswerGenerating = ref(false)
const ragasEvaluating = ref(false)

const current = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref<SegmentRow[]>([])
const hitkTasks = ref<HitkTestTask[]>([])
const ragasTasks = ref<RagasTask[]>([])

const expandedParentIds = ref<number[]>([])
const selectedSegmentIds = ref<number[]>([])
const childRowsMap = ref<Record<number, SegmentRow[]>>({})
const childLoadingMap = ref<Record<number, boolean>>({})
const childErrorMap = ref<Record<number, string>>({})
const saveLoadingMap = ref<Record<number, boolean>>({})
const saveErrorMap = ref<Record<number, string>>({})
const segmentDraftMap = ref<Record<number, string>>({})
const ragasMap = ref<Record<number, AdminQuestionDocumentSegmentRagasVO>>({})
const ragasDraftMap = ref<Record<number, { question: string; standardAnswer: string }>>({})
const ragasSaveLoadingMap = ref<Record<number, boolean>>({})
const ragasDeleteLoadingMap = ref<Record<number, boolean>>({})
const ragasSaveErrorMap = ref<Record<number, string>>({})
const selectAllCheckboxRef = ref<HTMLInputElement | null>(null)
const segmentDetailVisible = ref(false)
const detailSegmentId = ref<number | null>(null)
const hitkTaskDetailVisible = ref(false)
const hitkTaskDetailLoading = ref(false)
const hitkTaskDetailErrorMessage = ref('')
const activeHitkTaskId = ref<number | null>(null)
const activeHitkTaskDetail = ref<HitkTaskDetail | null>(null)
const ragasTaskDetailVisible = ref(false)
const ragasTaskDetailLoading = ref(false)
const ragasTaskDetailErrorMessage = ref('')
const activeRagasTaskId = ref<number | null>(null)
const activeRagasTaskDetail = ref<RagasTaskDetail | null>(null)
const singleActionLoading = ref(false)

const questionIdDisplay = computed(() => route.params.questionId ?? '-')
const docIdDisplay = computed(() => route.params.docId ?? '-')
const docTitleDisplay = computed(() => {
  const value = route.query.docTitle
  return typeof value === 'string' ? value : ''
})
const totalPagesDisplay = computed(() => {
  if (total.value <= 0) {
    return 1
  }
  return Math.max(1, Math.ceil(total.value / size.value))
})

const visibleRows = computed<SegmentRow[]>(() => {
  const rows: SegmentRow[] = [...records.value]
  expandedParentIds.value.forEach((parentId) => {
    const children = childRowsMap.value[parentId] ?? []
    rows.push(...children)
  })
  return rows
})
const allLoadedRows = computed<SegmentRow[]>(() => {
  const rows: SegmentRow[] = [...records.value]
  Object.values(childRowsMap.value).forEach((children) => rows.push(...children))
  return rows
})
const activeDetailSegment = computed<SegmentRow | null>(() => {
  if (detailSegmentId.value == null) {
    return null
  }
  return allLoadedRows.value.find((item) => item.segmentId === detailSegmentId.value) ?? null
})

const visibleSegmentIds = computed(() => visibleRows.value.map((item) => item.segmentId))
const allVisibleSelected = computed(() => {
  if (visibleSegmentIds.value.length === 0) {
    return false
  }
  return visibleSegmentIds.value.every((segmentId) => selectedSegmentIds.value.includes(segmentId))
})
const partiallyVisibleSelected = computed(() => {
  if (visibleSegmentIds.value.length === 0) {
    return false
  }
  const selectedCount = visibleSegmentIds.value.filter((segmentId) => selectedSegmentIds.value.includes(segmentId)).length
  return selectedCount > 0 && selectedCount < visibleSegmentIds.value.length
})
const selectedVisibleCount = computed(
  () => visibleSegmentIds.value.filter((segmentId) => selectedSegmentIds.value.includes(segmentId)).length,
)
const latestHitkTask = computed(() => hitkTasks.value[0] ?? null)
const latestRagasTask = computed(() => ragasTasks.value[0] ?? null)

watch([allVisibleSelected, partiallyVisibleSelected], async () => {
  await nextTick()
  if (selectAllCheckboxRef.value) {
    selectAllCheckboxRef.value.indeterminate = partiallyVisibleSelected.value
  }
})

watch(activeDetailSegment, (segment) => {
  if (segmentDetailVisible.value && detailSegmentId.value != null && segment == null) {
    segmentDetailVisible.value = false
    detailSegmentId.value = null
  }
})

const getErrorMessage = (error: unknown) => (error instanceof Error ? error.message : 'Operation failed')

const parseRouteId = (value: unknown) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

const resolveRouteIds = () => {
  const questionId = parseRouteId(route.params.questionId)
  const docId = parseRouteId(route.params.docId)
  if (questionId == null || docId == null) {
    throw new Error('Invalid route params')
  }
  return { questionId, docId }
}

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

const formatRate = (value: number | null | undefined) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-'
  }
  return `${(value * 100).toFixed(2)}%`
}

const formatScore = (value: number | null | undefined) => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-'
  }
  return value.toFixed(3)
}

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

// 兼容后端不同版本字段，统一映射到页面使用的 RAGAS 结构。
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
    faithfulness: toNumberOrNull(
      record.faithfulness,
      scoreObj.faithfulness,
      metricsObj.faithfulness,
    ),
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

// 缁熶竴澶勭悊鏂囨灞曠ず锛岄伩鍏嶇┖鍊煎奖鍝嶇渷鐣ュ睍绀恒€?
const toDisplayText = (value: string | null | undefined) => {
  if (typeof value !== 'string') {
    return '-'
  }
  const normalized = value.trim()
  return normalized.length > 0 ? normalized : '-'
}

// 鍒ゆ柇鍒嗘鏂囨。鏄惁瀛樺湪鍙睍绀哄唴瀹癸紝鐢ㄤ簬鎮诞鏌ョ湅鍏ㄦ枃銆?
const hasTooltipContent = (value: string | null | undefined) => {
  return typeof value === 'string' && value.trim().length > 0
}

// 淇濈暀鍘熷鎹㈣鍐呭锛屽厑璁稿湪鎮诞灞備腑鏌ョ湅鍒嗘鍏ㄦ枃銆?
const toTooltipText = (value: string | null | undefined) => {
  if (!hasTooltipContent(value)) {
    return '-'
  }
  return value ?? '-'
}

// 统一生成 RAGAS 单元格预览文本，保证主表行高稳定。
const getRagasPreviewText = (segment: SegmentRow) => {
  return `Q: ${toDisplayText(getSegmentRagasQuestionDraft(segment))} | A: ${toDisplayText(getSegmentRagasStandardAnswerDraft(segment))}`
}

// 统一生成 RAGAS 悬浮详情文本，支持换行查看问答内容。
const getRagasPreviewTitle = (segment: SegmentRow) => {
  return `Q: ${toDisplayText(getSegmentRagasQuestionDraft(segment))}\nA: ${toDisplayText(getSegmentRagasStandardAnswerDraft(segment))}`
}

// 判断 RAGAS 预览是否存在可查看详情内容，避免空数据显示悬浮层。
const hasRagasPreviewContent = (segment: SegmentRow) => {
  return hasTooltipContent(getSegmentRagasQuestionDraft(segment)) || hasTooltipContent(getSegmentRagasStandardAnswerDraft(segment))
}

// 打开切片详情弹窗并定位当前操作行。
const openSegmentDetailDialog = (segment: SegmentRow) => {
  detailSegmentId.value = segment.segmentId
  segmentDetailVisible.value = true
}

// 关闭切片详情弹窗并清理定位状态。
const closeSegmentDetailDialog = () => {
  segmentDetailVisible.value = false
  detailSegmentId.value = null
}

// 打开 Hit@K 任务详情弹窗并拉取任务详情数据。
const openHitkTaskDetailDialog = async (task: HitkTestTask) => {
  activeHitkTaskId.value = task.taskId
  hitkTaskDetailVisible.value = true
  await loadHitkTaskDetail(task.taskId)
}

// 关闭 Hit@K 任务详情弹窗并清理缓存数据。
const closeHitkTaskDetailDialog = () => {
  hitkTaskDetailVisible.value = false
  hitkTaskDetailLoading.value = false
  hitkTaskDetailErrorMessage.value = ''
  activeHitkTaskId.value = null
  activeHitkTaskDetail.value = null
}

// 刷新当前打开的任务详情，便于在弹窗中直接重试。
const reloadActiveHitkTaskDetail = async () => {
  if (activeHitkTaskId.value == null) {
    return
  }
  await loadHitkTaskDetail(activeHitkTaskId.value)
}

const openRagasTaskDetailDialog = async (task: RagasTask) => {
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

// 读取单个 Hit@K 任务详情并更新弹窗状态。
const loadHitkTaskDetail = async (taskId: number) => {
  try {
    const { questionId, docId } = resolveRouteIds()
    hitkTaskDetailLoading.value = true
    hitkTaskDetailErrorMessage.value = ''
    activeHitkTaskDetail.value = await adminQuestionApi.getQuestionDocumentSegmentHitkTestTaskDetail(questionId, docId, taskId)
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

// 缁熶竴鏄犲皠浠诲姟/鍒嗘鐘舵€佸埌鍥炬爣涓庨鑹叉爣绛俱€?
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

const sortTasksByCreateTime = <T extends { taskId: number; createTime: string | null }>(tasks: T[]) =>
  [...tasks].sort((left, right) => {
    const leftTime = left.createTime ? new Date(left.createTime).getTime() : 0
    const rightTime = right.createTime ? new Date(right.createTime).getTime() : 0
    if (leftTime !== rightTime) {
      return rightTime - leftTime
    }
    return right.taskId - left.taskId
  })

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

const loadRagasBySegmentIds = async (segmentIds: number[]) => {
  const normalizedSegmentIds = Array.from(new Set(segmentIds.filter((id) => Number.isFinite(id) && id > 0)))
  if (normalizedSegmentIds.length === 0) {
    return
  }
  const { questionId, docId } = resolveRouteIds()
  const ragasRecords = await adminQuestionApi.listQuestionDocumentSegmentRagas(questionId, docId, {
    segmentIds: normalizedSegmentIds,
  })
  mergeRagasRecords(ragasRecords)
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

const loadSegments = async () => {
  try {
    const { questionId, docId } = resolveRouteIds()
    loading.value = true
    errorMessage.value = ''
    actionErrorMessage.value = ''

    const pageResponse = await adminQuestionApi.pageQuestionDocumentSegments(questionId, docId, {
      current: current.value,
      size: size.value,
    })
    records.value = pageResponse.records as SegmentRow[]
    current.value = pageResponse.current
    size.value = pageResponse.size
    total.value = pageResponse.total
    expandedParentIds.value = []
    childRowsMap.value = {}
    childLoadingMap.value = {}
    childErrorMap.value = {}
    ragasMap.value = {}
    ragasDraftMap.value = {}
    ragasSaveLoadingMap.value = {}
    ragasDeleteLoadingMap.value = {}
    ragasSaveErrorMap.value = {}
    await loadRagasBySegmentIds(records.value.map((segment) => segment.segmentId))
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
    records.value = []
    total.value = 0
    ragasMap.value = {}
  } finally {
    loading.value = false
  }
}

const loadHitkTasks = async () => {
  try {
    const { questionId, docId } = resolveRouteIds()
    taskLoading.value = true
    taskErrorMessage.value = ''

    const tasks = await adminQuestionApi.listQuestionDocumentSegmentHitkTestTasks(questionId, docId)
    hitkTasks.value = sortTasksByCreateTime(tasks ?? [])
  } catch (error) {
    taskErrorMessage.value = getErrorMessage(error)
    hitkTasks.value = []
  } finally {
    taskLoading.value = false
  }
}

const loadRagasTasks = async () => {
  try {
    const { questionId, docId } = resolveRouteIds()
    ragasTaskLoading.value = true
    ragasTaskErrorMessage.value = ''
    const tasks = await adminQuestionApi.listQuestionDocumentSegmentRagasTasks(questionId, docId)
    ragasTasks.value = sortTasksByCreateTime(tasks ?? [])
  } catch (error) {
    ragasTaskErrorMessage.value = getErrorMessage(error)
    ragasTasks.value = []
  } finally {
    ragasTaskLoading.value = false
  }
}

const isParentExpanded = (segmentId: number) => expandedParentIds.value.includes(segmentId)
const isSegmentSelected = (segmentId: number) => selectedSegmentIds.value.includes(segmentId)

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
  records.value = records.value.map((segment) =>
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

const toggleSegmentSelection = (segmentId: number) => {
  if (isSegmentSelected(segmentId)) {
    selectedSegmentIds.value = selectedSegmentIds.value.filter((id) => id !== segmentId)
    return
  }
  selectedSegmentIds.value = [...selectedSegmentIds.value, segmentId]
}

const toggleSelectAllVisible = () => {
  const visibleIds = visibleSegmentIds.value
  if (visibleIds.length === 0) {
    return
  }

  if (allVisibleSelected.value) {
    selectedSegmentIds.value = selectedSegmentIds.value.filter((segmentId) => !visibleIds.includes(segmentId))
    return
  }

  const nextSelectedIds = new Set(selectedSegmentIds.value)
  visibleIds.forEach((segmentId) => nextSelectedIds.add(segmentId))
  selectedSegmentIds.value = Array.from(nextSelectedIds)
}

const invertVisibleSelection = () => {
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
  selectedSegmentIds.value = nextSelected
}

const clearSelection = () => {
  selectedSegmentIds.value = []
}

const removeSelectedSegmentIds = (segmentIds: number[]) => {
  if (segmentIds.length === 0 || selectedSegmentIds.value.length === 0) {
    return 0
  }

  const removedIdSet = new Set(segmentIds)
  const nextSelected = selectedSegmentIds.value.filter((segmentId) => !removedIdSet.has(segmentId))
  const removedCount = selectedSegmentIds.value.length - nextSelected.length
  if (removedCount > 0) {
    selectedSegmentIds.value = nextSelected
  }
  return removedCount
}

const toggleParentSegment = async (segment: SegmentRow) => {
  if (!segment.parentSegment) {
    return
  }
  const segmentId = segment.segmentId
  if (isParentExpanded(segmentId)) {
    const childSegmentIds = (childRowsMap.value[segmentId] ?? []).map((child) => child.segmentId)
    const removedCount = removeSelectedSegmentIds(childSegmentIds)
    expandedParentIds.value = expandedParentIds.value.filter((id) => id !== segmentId)
    if (removedCount > 0) {
      actionMessage.value = `Collapsed parent segment ${segmentId}. Cleared ${removedCount} hidden child selections.`
      actionErrorMessage.value = ''
    }
    return
  }

  expandedParentIds.value = [...expandedParentIds.value, segmentId]
  if (childRowsMap.value[segmentId]) {
    return
  }

  try {
    const { questionId, docId } = resolveRouteIds()
    childLoadingMap.value = {
      ...childLoadingMap.value,
      [segmentId]: true,
    }
    childErrorMap.value = {
      ...childErrorMap.value,
      [segmentId]: '',
    }

    const children = await adminQuestionApi.listQuestionDocumentChildSegments(questionId, docId, segmentId)
    childRowsMap.value = {
      ...childRowsMap.value,
      [segmentId]: children as SegmentRow[],
    }
    await loadRagasBySegmentIds((children as SegmentRow[]).map((child) => child.segmentId))
  } catch (error) {
    childErrorMap.value = {
      ...childErrorMap.value,
      [segmentId]: getErrorMessage(error),
    }
  } finally {
    childLoadingMap.value = {
      ...childLoadingMap.value,
      [segmentId]: false,
    }
  }
}

const saveSegmentHitkQuestion = async (segment: SegmentRow) => {
  if (!isSegmentHitkQuestionDirty(segment)) {
    return
  }

  try {
    const { questionId, docId } = resolveRouteIds()
    const nextValue = getSegmentHitkQuestionDraft(segment)
    saveLoadingMap.value = {
      ...saveLoadingMap.value,
      [segment.segmentId]: true,
    }
    saveErrorMap.value = {
      ...saveErrorMap.value,
      [segment.segmentId]: '',
    }

    await adminQuestionApi.updateQuestionDocumentSegmentHitkQuestion(questionId, docId, {
      updates: [
        {
          segmentId: segment.segmentId,
          hitkQuestion: nextValue,
        },
      ],
    })

    patchSegmentHitkQuestion(segment.segmentId, nextValue)
    resetSegmentHitkQuestionDraft(segment.segmentId)
    actionMessage.value = `Saved Hit@K question for segment ${segment.segmentId}.`
    actionErrorMessage.value = ''
  } catch (error) {
    saveErrorMap.value = {
      ...saveErrorMap.value,
      [segment.segmentId]: getErrorMessage(error),
    }
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

  const questionDraft = getSegmentRagasQuestionDraft(segment).trim()
  const standardAnswerDraft = getSegmentRagasStandardAnswerDraft(segment).trim()
  if (questionDraft.length === 0 || standardAnswerDraft.length === 0) {
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: 'Question and standard answer must not be blank.',
    }
    return
  }

  try {
    const { questionId, docId } = resolveRouteIds()
    ragasSaveLoadingMap.value = {
      ...ragasSaveLoadingMap.value,
      [segment.segmentId]: true,
    }
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: '',
    }
    const currentRagas = getSegmentRagas(segment.segmentId)
    const result = await adminQuestionApi.updateQuestionDocumentSegmentRagas(questionId, docId, {
      updates: [
        {
          ragasId: currentRagas?.ragasId ?? currentRagas?.id ?? undefined,
          segmentId: segment.segmentId,
          question: questionDraft,
          standardAnswer: standardAnswerDraft,
        },
      ],
    })
    mergeRagasRecords(result)
    resetSegmentRagasDraft(segment.segmentId)
    actionMessage.value = `Saved RAGAS record for segment ${segment.segmentId}.`
    actionErrorMessage.value = ''
  } catch (error) {
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: getErrorMessage(error),
    }
  } finally {
    ragasSaveLoadingMap.value = {
      ...ragasSaveLoadingMap.value,
      [segment.segmentId]: false,
    }
  }
}

const deleteSegmentRagas = async (segment: SegmentRow) => {
  const currentRagas = getSegmentRagas(segment.segmentId)
  if (currentRagas == null) {
    return
  }
  try {
    const { questionId, docId } = resolveRouteIds()
    ragasDeleteLoadingMap.value = {
      ...ragasDeleteLoadingMap.value,
      [segment.segmentId]: true,
    }
    await adminQuestionApi.deleteQuestionDocumentSegmentRagas(questionId, docId, {
      ragasIds: currentRagas.ragasId != null ? [currentRagas.ragasId] : undefined,
      segmentIds: currentRagas.ragasId == null ? [segment.segmentId] : undefined,
    })
    const { [segment.segmentId]: _removed, ...nextMap } = ragasMap.value
    ragasMap.value = nextMap
    resetSegmentRagasDraft(segment.segmentId)
    actionMessage.value = `Deleted RAGAS record for segment ${segment.segmentId}.`
    actionErrorMessage.value = ''
  } catch (error) {
    ragasSaveErrorMap.value = {
      ...ragasSaveErrorMap.value,
      [segment.segmentId]: getErrorMessage(error),
    }
  } finally {
    ragasDeleteLoadingMap.value = {
      ...ragasDeleteLoadingMap.value,
      [segment.segmentId]: false,
    }
  }
}

// 统一封装弹窗内单条分片操作，避免重复处理加载态与错误提示。
const runSingleSegmentAction = async (action: () => Promise<string>) => {
  if (singleActionLoading.value) {
    return
  }
  try {
    singleActionLoading.value = true
    actionErrorMessage.value = ''
    actionMessage.value = ''
    actionMessage.value = await action()
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    singleActionLoading.value = false
  }
}

// 在详情弹窗内按单条分片触发 Hit@K 问题生成。
const generateHitkQuestionForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const { questionId, docId } = resolveRouteIds()
    await adminQuestionApi.generateQuestionDocumentSegmentHitkQuestion(questionId, docId, {
      segmentIds: [segment.segmentId],
    })
    await loadSegments()
    return `Generated Hit@K question for segment ${segment.segmentId}.`
  })
}

// 在详情弹窗内按单条分片触发 RAGAS 问答生成。
const generateRagasForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const { questionId, docId } = resolveRouteIds()
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagas(questionId, docId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `Generated RAGAS QA for segment ${segment.segmentId}.`
  })
}

// 在详情弹窗内按单条分片触发 RAGAS 回答生成。
const generateRagasAnswerForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const { questionId, docId } = resolveRouteIds()
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagasAnswers(questionId, docId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `Generated RAGAS answer for segment ${segment.segmentId}.`
  })
}

// 在详情弹窗内按单条分片执行 RAGAS 评估。
const evaluateRagasForSegment = async (segment: SegmentRow) => {
  await runSingleSegmentAction(async () => {
    const { questionId, docId } = resolveRouteIds()
    const result = await adminQuestionApi.evaluateQuestionDocumentSegmentRagas(questionId, docId, {
      segmentIds: [segment.segmentId],
    })
    mergeRagasRecords(result)
    return `Completed RAGAS evaluation for segment ${segment.segmentId}.`
  })
}

const generateRagasForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  try {
    const { questionId, docId } = resolveRouteIds()
    ragasBatchGenerating.value = true
    actionMessage.value = ''
    actionErrorMessage.value = ''
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagas(questionId, docId, {
      segmentIds: selectedSegmentIds.value,
    })
    mergeRagasRecords(result)
    actionMessage.value = `Generated RAGAS QA for ${selectedSegmentIds.value.length} selected segments.`
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    ragasBatchGenerating.value = false
  }
}

const generateRagasAnswersForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  try {
    const { questionId, docId } = resolveRouteIds()
    ragasAnswerGenerating.value = true
    actionMessage.value = ''
    actionErrorMessage.value = ''
    const result = await adminQuestionApi.generateQuestionDocumentSegmentRagasAnswers(questionId, docId, {
      segmentIds: selectedSegmentIds.value,
    })
    mergeRagasRecords(result)
    actionMessage.value = `Generated RAGAS answers for ${selectedSegmentIds.value.length} selected segments.`
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    ragasAnswerGenerating.value = false
  }
}

const evaluateRagasForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }
  try {
    const { questionId, docId } = resolveRouteIds()
    ragasEvaluating.value = true
    actionMessage.value = ''
    actionErrorMessage.value = ''
    const task = await adminQuestionApi.createQuestionDocumentSegmentRagasTask(questionId, docId, {
      segmentIds: selectedSegmentIds.value,
    })
    actionMessage.value = `Submitted RAGAS task #${task.taskId} for ${selectedSegmentIds.value.length} selected segments.`
    await loadRagasTasks()
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    ragasEvaluating.value = false
  }
}

const generateHitkQuestionsForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }

  try {
    const { questionId, docId } = resolveRouteIds()
    batchGenerating.value = true
    actionMessage.value = ''
    actionErrorMessage.value = ''

    await adminQuestionApi.generateQuestionDocumentSegmentHitkQuestion(questionId, docId, {
      segmentIds: selectedSegmentIds.value,
    })

    actionMessage.value = `Generated Hit@K questions for ${selectedSegmentIds.value.length} selected segments.`
    await loadSegments()
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    batchGenerating.value = false
  }
}

const createHitkTestTaskForSelection = async () => {
  if (selectedSegmentIds.value.length === 0) {
    return
  }

  try {
    const { questionId, docId } = resolveRouteIds()
    batchTesting.value = true
    actionMessage.value = ''
    actionErrorMessage.value = ''

    await adminQuestionApi.createQuestionDocumentSegmentHitkTestTask(questionId, docId, {
      segmentIds: selectedSegmentIds.value,
    })

    actionMessage.value = `Created Hit@K test task for ${selectedSegmentIds.value.length} selected segments.`
    await loadHitkTasks()
  } catch (error) {
    actionErrorMessage.value = getErrorMessage(error)
  } finally {
    batchTesting.value = false
  }
}

const changePage = async (nextPage: number) => {
  if (nextPage < 1 || nextPage > totalPagesDisplay.value || nextPage === current.value) {
    return
  }
  current.value = nextPage
  await loadSegments()
}

const goBack = async () => {
  await router.push('/questions')
}

onMounted(async () => {
  await Promise.all([loadSegments(), loadHitkTasks(), loadRagasTasks()])
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

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.subtitle {
  margin: 0;
  color: #64748b;
}

.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
  min-width: 0;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title {
  margin: 0;
  font-size: 18px;
}

.section-subtitle {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar-text {
  color: #475569;
  font-size: 13px;
}

.toolbar-stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #dbe3ee;
  border-radius: 999px;
  background: #f8fafc;
}

.toolbar-stat strong {
  color: #0f172a;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #334155;
}

.checkbox-label input {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.task-layout {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
  max-width: 100%;
}

.task-summary-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  padding: 14px;
}

.task-history {
  min-width: 0;
}

.task-card-title {
  margin-bottom: 10px;
  font-weight: 600;
  color: #0f172a;
}

.task-metrics {
  display: grid;
  gap: 8px;
}

.task-metrics div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #1e293b;
}

.metric-label {
  color: #64748b;
}

.feedback-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 500;
}

.feedback-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 20px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.feedback-success {
  color: #065f46;
  border-color: #a7f3d0;
  background: #ecfdf5;
}

.feedback-success .feedback-icon {
  color: #047857;
  background: #d1fae5;
}

.feedback-error {
  color: #991b1b;
  border-color: #fecaca;
  background: #fef2f2;
}

.feedback-error .feedback-icon {
  color: #b91c1c;
  background: #fee2e2;
}

.loading-skeleton {
  display: grid;
  gap: 10px;
  padding: 4px 0;
}

.task-skeleton {
  grid-template-columns: 1fr;
}

.skeleton-row,
.skeleton-block {
  border-radius: 8px;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton-pulse 1.2s ease-in-out infinite;
}

.skeleton-row {
  height: 16px;
}

.skeleton-block {
  height: 38px;
}

@keyframes skeleton-pulse {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}

.empty-state {
  margin: 4px 0;
  padding: 18px 14px;
  border-radius: 10px;
  border: 1px dashed #d6dee8;
  background: #f8fafc;
  color: #475569;
}

.compact-empty-state {
  padding: 12px;
}

.empty-title {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

.empty-subtitle {
  margin: 6px 0 0;
  font-size: 12px;
}

.table-wrap {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
}

.task-history-table {
  min-width: 0;
}

.segment-table {
  min-width: 0;
}

.child-table {
  min-width: 0;
  margin-top: 0;
}

.table th,
.table td {
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
  vertical-align: top;
  text-align: left;
  background: #fff;
  min-width: 0;
}

.table th:last-child,
.table td:last-child {
  border-right: none;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #f8fafc;
}

.table tbody tr td {
  transition: background-color 0.15s ease;
}

.table tbody tr:hover > td {
  background: #f8fafc;
}

.table tbody tr:active > td {
  background: #ecf3fd;
}

.row-selected > td {
  background: #e0f2fe;
}

.row-selected:hover > td {
  background: #bae6fd;
}

.checkbox-col {
  width: 72px;
  white-space: nowrap;
}

.num-col {
  text-align: right !important;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.date-col {
  text-align: center !important;
  white-space: nowrap;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 18px;
  border: 1px solid transparent;
  white-space: nowrap;
}

.status-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 14px;
  height: 14px;
  font-size: 10px;
  font-weight: 700;
}

.status-chip-success {
  color: #166534;
  border-color: #86efac;
  background: #f0fdf4;
}

.status-chip-danger {
  color: #b91c1c;
  border-color: #fca5a5;
  background: #fef2f2;
}

.status-chip-warning {
  color: #b45309;
  border-color: #fcd34d;
  background: #fffbeb;
}

.status-chip-info {
  color: #1d4ed8;
  border-color: #93c5fd;
  background: #eff6ff;
}

.status-chip-neutral {
  color: #334155;
  border-color: #cbd5e1;
  background: #f8fafc;
}

.segment-text {
  max-width: 100%;
}

.segment-text-popover,
.detail-popover {
  position: relative;
}

.segment-text-popover-content,
.detail-popover-content {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 20;
  display: none;
  margin: 0;
  padding: 10px 12px;
  width: min(680px, 72vw);
  max-height: 320px;
  overflow: auto;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
  color: #0f172a;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.18);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}

.segment-text-popover:hover .segment-text-popover-content,
.segment-text-popover:focus-within .segment-text-popover-content,
.detail-popover:hover .detail-popover-content,
.detail-popover:focus-within .detail-popover-content {
  display: block;
}

.text-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.text-ellipsis-multiline {
  display: -webkit-box;
  max-width: 100%;
  line-height: 1.45;
  white-space: normal;
  word-break: break-word;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.compact-preview {
  -webkit-line-clamp: 2;
}

.hitk-preview {
  margin-bottom: 8px;
  color: #334155;
}

.hitk-cell {
  width: 32%;
  min-width: 0;
}

.ragas-cell {
  width: 38%;
  min-width: 0;
}

.ragas-preview {
  margin-bottom: 8px;
  color: #334155;
}

.ragas-textarea {
  min-height: 72px;
}

.ragas-score-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.score-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border-radius: 999px;
  padding: 2px 8px;
  border: 1px solid #dbe3ee;
  background: #f8fafc;
  color: #334155;
  font-size: 11px;
}

.hitk-textarea {
  width: 100%;
  min-height: 88px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 10px;
  resize: vertical;
  font: inherit;
  color: #0f172a;
  background: #fff;
}

.hitk-textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.row-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.btn-small {
  padding: 4px 10px;
  font-size: 12px;
}

.btn-ragas-generate {
  background: #0f766e;
  border-color: #115e59;
  color: #fff;
}

.btn-ragas-generate.btn:not(:disabled):hover {
  background: #115e59;
  border-color: #134e4a;
  color: #fff;
}

.btn-ragas-answer {
  background: #b45309;
  border-color: #92400e;
  color: #fff;
}

.btn-ragas-answer.btn:not(:disabled):hover {
  background: #92400e;
  border-color: #78350f;
  color: #fff;
}

.btn-ragas-evaluate {
  background: #0369a1;
  border-color: #075985;
  color: #fff;
}

.btn-ragas-evaluate.btn:not(:disabled):hover {
  background: #075985;
  border-color: #0c4a6e;
  color: #fff;
}

.btn-danger-outline:not(:disabled) {
  border-color: #fca5a5;
  color: #b91c1c;
  background: #fff;
}

.inline-error {
  margin-top: 6px;
  color: #dc2626;
  font-size: 12px;
}

.tag {
  display: inline-block;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 18px;
  background: #f1f5f9;
  color: #334155;
}

.tag-parent {
  background: #dbeafe;
  color: #1d4ed8;
}

.child-cell {
  background: #f8fafc;
}

.child-table-wrap {
  margin-top: 8px;
}

.child-error {
  margin-top: 0;
}

.ops {
  white-space: nowrap;
}

.ops-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.ops-placeholder {
  color: #94a3b8;
  font-size: 12px;
}

.segment-table > tbody > tr > td:not(.child-cell),
.child-table > tbody > tr > td {
  height: 88px;
}

.compact-cell {
  vertical-align: middle;
}

.state {
  padding: 16px 0;
  color: #475569;
}

.compact-state {
  padding: 8px 0;
}

.state-error {
  color: #dc2626;
}

.state-success {
  color: #15803d;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.45);
}

.modal {
  width: min(920px, 100%);
  max-height: 90vh;
  overflow: auto;
  border-radius: 12px;
  background: #fff;
  padding: 16px;
}

.modal-large {
  width: min(1080px, 100%);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.segment-detail-modal {
  width: min(1040px, 100%);
}

.hitk-task-detail-modal {
  width: min(1160px, 100%);
}

.segment-detail-body {
  display: grid;
  gap: 12px;
}

.hitk-task-detail-body {
  display: grid;
  gap: 12px;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.detail-meta-grid p {
  margin: 0;
  font-size: 13px;
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

.detail-field {
  display: grid;
  gap: 6px;
}

.detail-field-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.detail-readonly {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
}

.detail-textarea {
  min-height: 110px;
}

.detail-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-score-grid {
  margin-top: 0;
}

.pagination {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  padding: 6px 12px;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.08s ease;
}

.btn:not(:disabled):hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.btn:not(:disabled):active {
  transform: translateY(1px);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-light {
  background: #f8fafc;
}

.btn-generate {
  border-color: #0f766e;
  background: #0f766e;
  color: #ffffff;
}

.btn-generate:not(:disabled):hover {
  border-color: #115e59;
  background: #115e59;
}

.btn-run {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #ffffff;
}

.btn-run:not(:disabled):hover {
  border-color: #1e40af;
  background: #1e40af;
}

.link-btn {
  border: none;
  padding: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  transition: color 0.15s ease;
}

.link-btn:not(:disabled):hover {
  color: #1d4ed8;
}

.link-btn:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

@media (max-width: 1200px) {
  .task-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .pagination {
    justify-content: flex-start;
  }

  .toolbar {
    align-items: stretch;
    gap: 8px;
  }

  .toolbar > .btn,
  .toolbar > .checkbox-label,
  .toolbar > .toolbar-stat {
    width: 100%;
  }

  .toolbar > .btn {
    min-height: 40px;
  }

  .toolbar > .checkbox-label {
    min-height: 40px;
    padding: 8px 10px;
    border: 1px solid #dbe3ee;
    border-radius: 8px;
    background: #fff;
  }

  .toolbar > .checkbox-label input {
    width: 18px;
    height: 18px;
  }

  .toolbar-stat {
    width: 100%;
    justify-content: center;
  }
}

/* UI override: data-dense but readable layout */
.page {
  --c-primary: #1e40af;
  --c-primary-soft: #eff6ff;
  --c-bg: #f8fafc;
  --c-border: #dbe3ee;
  --c-text: #0f172a;
  --c-text-muted: #475569;
  --c-success: #166534;
  --c-danger: #b91c1c;
  --c-warning: #b45309;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  background: linear-gradient(180deg, #f8fbff 0%, #f8fafc 100%);
  border-radius: var(--radius-md);
}

.header {
  grid-column: 1 / -1;
  padding: 6px 2px 10px;
  border-bottom: 1px solid #e6edf7;
}

.page > .panel {
  border-radius: 14px;
  border-color: var(--c-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.page > .panel:first-of-type {
  position: static;
  top: auto;
  max-height: none;
  overflow: visible;
  background: #fcfdff;
}

.page > .panel:last-of-type {
  min-width: 0;
  background: #fff;
}

.task-layout {
  grid-template-columns: 1fr;
  gap: 12px;
}

.task-summary-card {
  background: var(--c-bg);
  border-color: var(--c-border);
}

.task-history .table-wrap {
  max-height: 420px;
  overflow: auto;
}

.section-header {
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf2f7;
}

.section-title {
  color: var(--c-text);
  letter-spacing: 0.01em;
}

.section-subtitle,
.subtitle,
.toolbar-text {
  color: var(--c-text-muted);
}

.toolbar {
  gap: 8px;
  padding: 10px;
  border: 1px solid #e8eef5;
  border-radius: 10px;
  background: #fbfdff;
}

.toolbar-stat {
  border-color: var(--c-border);
  background: #fff;
}

.table-wrap {
  border-color: var(--c-border);
  border-radius: 12px;
}

.table {
  min-width: 1120px;
}

.task-history-table {
  min-width: 780px;
}

.child-table {
  min-width: 980px;
}

.table th,
.table td {
  padding: 10px 10px;
  border-color: #e6edf5;
}

.table thead th {
  background: #f7faff;
  color: #1e293b;
  font-weight: 600;
}

.table tbody tr:hover > td {
  background: #f8fbff;
}

.row-selected > td {
  background: #eaf4ff;
}

.row-selected:hover > td {
  background: #dcecff;
}

.status-chip {
  min-height: 22px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-icon {
  min-width: 14px;
}

.feedback-banner {
  border-left: 4px solid transparent;
  border-radius: 10px;
}

.feedback-success {
  color: var(--c-success);
  border-color: #86efac;
  border-left-color: #16a34a;
  background: #f0fdf4;
}

.feedback-error {
  color: var(--c-danger);
  border-color: #fecaca;
  border-left-color: #dc2626;
  background: #fef2f2;
}

.btn {
  min-height: 36px;
  padding: 6px 12px;
  font-weight: 600;
  border-color: #cfd8e3;
}

.btn-light {
  background: #fff;
}

.btn-generate {
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
}

.btn-run {
  border-color: var(--c-primary);
  background: var(--c-primary);
  color: #fff;
}

.btn:not(:disabled):hover {
  background: var(--c-primary-soft);
  border-color: #93c5fd;
}

.btn-generate:not(:disabled):hover,
.btn-run:not(:disabled):hover {
  filter: brightness(0.95);
}

.btn-small {
  min-height: 32px;
  padding: 4px 10px;
}

.link-btn {
  font-weight: 600;
}

.hitk-textarea {
  min-height: 96px;
  border-color: #cbd5e1;
}

.hitk-textarea:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.18);
}

.inline-error {
  color: var(--c-danger);
  font-weight: 500;
}

.pagination {
  padding-top: 8px;
  border-top: 1px solid #edf2f7;
}

button:focus-visible,
input:focus-visible,
textarea:focus-visible,
.link-btn:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

@media (max-width: 1279px) {
  .page {
    grid-template-columns: 1fr;
  }

  .page > .panel:first-of-type {
    position: static;
    max-height: none;
    overflow: visible;
  }
}

@media (max-width: 900px) {
  .toolbar > .btn,
  .toolbar > .checkbox-label,
  .toolbar > .toolbar-stat {
    width: 100%;
  }

  .btn,
  .btn-small {
    min-height: 40px;
  }

  .pagination {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

/* Pixel tune v2: dense readability refinement */
.page {
  gap: 14px;
}

.header {
  align-items: center;
  padding-bottom: 12px;
}

.panel {
  padding: 14px;
}

.section-title,
.task-card-title {
  font-family: var(--font-family-heading);
  letter-spacing: 0.01em;
}

.toolbar {
  padding: 11px;
  gap: 9px;
}

.toolbar .btn {
  min-height: 38px;
}

.feedback-icon {
  min-width: 30px;
  height: 22px;
}

.table th {
  font-size: 11.5px;
  letter-spacing: 0.02em;
}

.table td {
  line-height: 1.5;
}

.segment-text-popover-content,
.detail-popover-content {
  width: min(720px, 74vw);
}

.row-actions .btn-small {
  min-width: 74px;
}

.pagination span {
  font-weight: 600;
  letter-spacing: 0.01em;
}

@media (max-width: 900px) {
  .page {
    padding: 14px;
  }

  .panel {
    padding: 12px;
  }
}

.page-title {
  margin: 0;
  font-family: var(--font-family-heading);
  font-size: 24px;
  line-height: 1.22;
  color: #123f8e;
  letter-spacing: 0.01em;
}

.header .subtitle {
  margin: 4px 0 0;
  font-size: 13px;
}

@media (max-width: 900px) {
  .page-title {
    font-size: 21px;
  }
}

/* 可用性增强：放大控件、修复长文本溢出，并提供更易操作的行内交互。 */
.table th,
.table td {
  font-size: 13px;
  padding: 12px 12px;
}

.segment-table {
  min-width: 1400px;
}

.segment-text,
.hitk-cell,
.ragas-cell {
  min-width: 0;
}

.text-ellipsis-multiline {
  line-height: 1.6;
  overflow-wrap: anywhere;
  word-break: break-word;
  -webkit-line-clamp: 4;
}

.text-expanded {
  display: block;
  overflow: visible;
  -webkit-line-clamp: initial;
  -webkit-box-orient: initial;
}

.text-toggle-btn {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.4;
}

.segment-text-popover-content,
.detail-popover-content {
  width: min(900px, 86vw);
  max-height: 420px;
  font-size: 13px;
  line-height: 1.65;
}

.btn {
  min-height: 42px;
  padding: 8px 14px;
  font-size: 14px;
}

.btn-small {
  min-height: 36px;
  min-width: 92px;
  padding: 6px 14px;
  font-size: 13px;
}

.checkbox-label input,
.checkbox-col input[type='checkbox'] {
  width: 18px;
  height: 18px;
}

.hitk-textarea,
.ragas-textarea {
  min-height: 124px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.6;
}

.row-actions {
  flex-wrap: wrap;
  row-gap: 8px;
  column-gap: 10px;
}

.ragas-score-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.score-pill {
  min-height: 28px;
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 1200px) {
  .segment-table {
    min-width: 1280px;
  }

  .ragas-score-grid {
    grid-template-columns: 1fr;
  }

  .detail-meta-grid,
  .hitk-task-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .btn,
  .btn-small {
    min-height: 44px;
  }

  .hitk-textarea,
  .ragas-textarea {
    min-height: 136px;
  }

  .detail-meta-grid,
  .hitk-task-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>



