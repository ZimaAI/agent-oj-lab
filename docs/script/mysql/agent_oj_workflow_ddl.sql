create table ai_model_config
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    model_key   varchar(100)                       not null comment '模型唯一标识',
    model_name  varchar(200)                       not null comment '模型名称',
    model_type  varchar(32)                        not null comment '模型类型: CHAT/EMBEDDING',
    provider    varchar(64)                        not null comment '模型提供方',
    base_url    varchar(500)                       null comment '模型调用地址',
    api_key     varchar(500)                       null comment '模型密钥',
    enabled     int      default 1                 not null comment '0:禁用, 1:启用',
    config_json text                               null comment '扩展配置JSON',
    remark      varchar(500)                       null comment '备注',
    is_delete   int      default 0                 not null comment '0:否, 1:是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_model_key_is_delete
        unique (model_key, is_delete)
)
    comment 'AI模型配置表';

create index idx_model_type_enabled_is_delete
    on ai_model_config (model_type, enabled, is_delete);

create table ai_model_default
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    model_type  varchar(32)                        not null comment '模型类型: CHAT/EMBEDDING',
    model_key   varchar(100)                       not null comment '默认模型唯一标识',
    is_delete   int      default 0                 not null comment '0:否, 1:是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_model_type_is_delete
        unique (model_type, is_delete)
)
    comment 'AI默认模型表';

create table algorithm_code
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    question_id        bigint                             not null comment '算法题ID',
    language           varchar(32)                        not null comment '编程语言',
    function_name      varchar(128)                       not null comment '语言函数名',
    code_skeleton      text                               null comment '语言代码骨架',
    reference_answer   text                               null comment '语言参考答案',
    generate_model_key varchar(128)                       null comment '生成模型标识',
    trace_id           varchar(64)                        null comment '请求链路标识符',
    agent_name         varchar(128)                       null comment 'Agent 名称',
    is_delete          int      default 0                 not null comment '0:否 1:是',
    create_time        datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_question_language
        unique (question_id, language)
)
    comment '算法题语言代码表';

create index idx_algorithm_code_question_id
    on algorithm_code (question_id);

create table algorithm_question
(
    id                        bigint auto_increment comment '主键ID'
        primary key,
    user_id                   bigint                                not null comment '用户ID',
    title                     varchar(255)                          not null comment '题目标题',
    description               text                                  not null comment '题目描述',
    difficulty                varchar(16)                           not null comment '难度级别: EASY, MEDIUM, HARD',
    type                      varchar(20) default 'AI'              not null comment '题目来源类型：AI-智能生成，SYSTEM-管理员创建',
    generate_model_key        varchar(128)                          null comment '生成题目的模型标识',
    question_digest           varchar(64)                           null comment '题目摘要哈希',
    conversation_id           varchar(64)                           not null comment '会话标识符',
    trace_id                  varchar(64)                           not null comment '请求链标识符',
    agent_name                varchar(128)                          null comment 'Agent 名称',
    vector_sync_status        varchar(32) default 'PENDING'         not null comment '向量同步状态快照（PENDING:待同步, SUCCESS:成功, FAILED:失败）',
    vector_sync_error_message varchar(2000)                         null comment '最近一次向量同步错误信息',
    is_delete                 int         default 0                 not null comment '0:否, 1:是',
    create_time               datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time               datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    shared_function_name      varchar(128)                          null comment '共享函数名',
    shared_code_skeleton      text                                  null comment '共享伪代码骨架',
    shared_test_cases         json                                  null comment '共享测试用例'
)
    comment '算法题目表';

create index idx_algorithm_question_type_is_delete
    on algorithm_question (type, is_delete);

create index idx_conversation_id
    on algorithm_question (conversation_id);

create index idx_difficulty
    on algorithm_question (difficulty);

create index idx_trace_id
    on algorithm_question (trace_id);

create index idx_vector_sync_status
    on algorithm_question (vector_sync_status);

create table algorithm_question_knowledge_document
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    question_id bigint                             not null comment '算法题ID',
    doc_id      bigint                             not null comment '知识文档ID',
    is_delete   int      default 0                 not null comment '0:否, 1:是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_question_doc
        unique (question_id, doc_id)
)
    comment '算法题-知识文档关联表';

create index idx_doc_id
    on algorithm_question_knowledge_document (doc_id);

create index idx_question_id
    on algorithm_question_knowledge_document (question_id);

create table algorithm_question_tag
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    question_id bigint                             not null comment '算法题目ID',
    tag_id      bigint                             not null comment '标签ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_question_tag
        unique (question_id, tag_id) comment '防止重复关联'
)
    comment '算法题目-标签关联表';

create index idx_question_id
    on algorithm_question_tag (question_id)
    comment '按题目查询标签';

create index idx_tag_id
    on algorithm_question_tag (tag_id)
    comment '按标签查询题目';

create table algorithm_question_vector_sync_log
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    question_id   bigint                             not null comment '算法题ID',
    sync_status   varchar(32)                        not null comment '同步结果（PENDING, SUCCESS, FAILED）',
    error_code    varchar(64)                        null comment '错误编码（如 EMBEDDING_FAILED、DIMENSION_MISMATCH、PG_UPSERT_FAILED、SUCCESS_BOOKKEEPING_FAILED）',
    error_message varchar(2000)                      null comment '错误信息',
    trace_id      varchar(64)                        null comment '请求链标识符',
    is_delete     int      default 0                 not null comment '0:否, 1:是',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '算法题向量同步日志表';

create index idx_question_id
    on algorithm_question_vector_sync_log (question_id);

create index idx_trace_id
    on algorithm_question_vector_sync_log (trace_id);

create table code_evaluation
(
    id                        bigint auto_increment comment '主键ID'
        primary key,
    user_id                   bigint                             not null comment '用户ID',
    submission_id             bigint                             not null comment '代码提交ID',
    algorithm_question_id     bigint                             not null comment '算法题ID',
    conversation_message_id   bigint                             null comment '会话消息ID',
    correctness_score         int                                not null comment '正确性分数（0-10）',
    time_complexity_score     int                                not null comment '时间复杂度分数（0-10）',
    space_complexity_score    int                                not null comment '空间复杂度分数（0-10）',
    overall_score             int                                null comment '综合评分（0-100）',
    test_results              json                               null comment '测试执行结果列表（JSON 格式）',
    time_complexity_analysis  text                               null comment '时间复杂度分析说明',
    space_complexity_analysis text                               null comment '空间复杂度分析说明',
    code_quality_analysis     text                               null comment '代码质量分析',
    suggestions               text                               null comment '优化建议',
    summary                   text                               null comment '评估总结',
    evaluate_model_key        varchar(128)                       null comment '评估模型标识',
    conversation_id           varchar(64)                        not null comment '会话标识符',
    trace_id                  varchar(64)                        not null comment '请求链标识符',
    agent_name                varchar(128)                       null comment 'Agent 名称',
    is_delete                 int      default 0                 not null comment '0:否, 1:是',
    create_time               datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time               datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_submission_id
        unique (submission_id)
)
    comment '代码评估结果表';

create index idx_algorithm_question_id
    on code_evaluation (algorithm_question_id);

create index idx_conversation_id
    on code_evaluation (conversation_id);

create index idx_conversation_message_id
    on code_evaluation (conversation_message_id);

create index idx_trace_id
    on code_evaluation (trace_id);

create index idx_user_id
    on code_evaluation (user_id);

create table code_submission
(
    id                      bigint auto_increment comment '主键ID'
        primary key,
    user_id                 bigint                             not null comment '用户ID',
    algorithm_question_id   bigint                             not null comment '算法题ID',
    code                    text                               not null comment '提交的代码',
    language                varchar(32)                        not null comment '编程语言',
    execute_status          varchar(32)                        null comment '执行状态: PENDING, RUNNING, SUCCESS, FAILED',
    execute_time_ms         int                                null comment '执行耗时（毫秒）',
    error_message           text                               null comment '执行错误信息',
    test_results            json                               null comment '测试用例执行结果（JSON 格式）',
    pass_count              int                                null comment '通过的测试用例数量',
    total_count             int                                null comment '总测试用例数量',
    conversation_id         varchar(64)                        null comment '会话ID',
    conversation_message_id bigint                             null comment '会话消息ID',
    trace_id                varchar(64)                        null comment '请求链标识符',
    is_delete               int      default 0                 not null comment '0:否, 1:是',
    create_time             datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time             datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '代码提交记录表';

create index idx_conversation_id
    on code_submission (conversation_id);

create index idx_question_id
    on code_submission (algorithm_question_id);

create index idx_user_id
    on code_submission (user_id);

create index idx_user_question
    on code_submission (user_id, algorithm_question_id);

create table conversation
(
    id                  bigint auto_increment comment '会话ID'
        primary key,
    user_id             bigint                                 not null comment '用户ID',
    title               varchar(255) default '新会话'          not null comment '会话标题',
    conversation_id     varchar(64)                            not null comment '工作流会话标识符',
    current_question_id bigint                                 null comment '当前算法题ID',
    session_status      varchar(32)  default 'ACTIVE'          not null comment '会话状态: ACTIVE(进行中), ARCHIVED(已归档), CLOSED(已关闭)',
    last_message_time   datetime                               null comment '最后一条消息时间',
    is_delete           int          default 0                 not null comment '0:否, 1:是',
    create_time         datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time         datetime     default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_conversation_conversation_id
        unique (conversation_id)
)
    comment '用户会话表';

create index idx_user_id_is_delete
    on conversation (user_id, is_delete);

create index idx_user_id_update_time
    on conversation (user_id, update_time);

create table conversation_message
(
    id              bigint auto_increment comment '消息ID'
        primary key,
    conversation_id varchar(64)                           not null comment '会话ID',
    sender          varchar(32)                           not null comment '发送者: USER, AGENT',
    message_type    varchar(32) default 'PLAIN'           not null comment '消息类型: PLAIN(普通消息), QUESTION_REQUEST(题目请求), QUESTION_RESULT(题目结果), CODE_SUBMISSION(代码提交), CODE_EVALUATION(代码评估), SYSTEM_NOTICE(系统通知)',
    content         text                                  not null comment '消息内容',
    question_id     bigint                                null comment '关联算法题ID',
    submission_id   bigint                                null comment '关联代码提交ID',
    evaluation_id   bigint                                null comment '关联代码评估ID',
    trace_id        varchar(64)                           null comment '链路追踪ID',
    sequence_no     int                                   not null comment '会话内消息顺序号',
    result_type     varchar(64)                           null comment '结果类型: CODE_QUESTION, CODE_EVALUATION',
    result_data     json                                  null comment '结构化结果数据',
    create_time     datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint idx_conversation_id_sequence_no
        unique (conversation_id, sequence_no)
)
    comment '会话消息表';

create index idx_conversation_id_create_time
    on conversation_message (conversation_id, create_time);

create index idx_evaluation_id
    on conversation_message (evaluation_id);

create index idx_question_id
    on conversation_message (question_id);

create index idx_submission_id
    on conversation_message (submission_id);

create index idx_trace_id
    on conversation_message (trace_id);

create table file_record
(
    id                bigint auto_increment comment 'primary key id'
        primary key,
    user_id           bigint                             not null comment 'user id',
    bucket_name       varchar(128)                       not null comment 'minio bucket name',
    object_name       varchar(512)                       not null comment 'minio object name',
    original_filename varchar(255)                       not null comment 'original file name',
    file_size         bigint                             not null comment 'file size in bytes',
    content_type      varchar(128)                       null comment 'mime content type',
    file_url          varchar(1024)                      not null comment 'public file url',
    is_delete         int      default 0                 not null comment '0:not deleted 1:deleted',
    create_time       datetime default CURRENT_TIMESTAMP null comment 'create time',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment 'update time',
    constraint uk_file_record_bucket_object
        unique (bucket_name, object_name)
)
    comment 'uploaded file metadata table';

create index idx_file_record_create_time
    on file_record (create_time);

create index idx_file_record_user_id_is_delete
    on file_record (user_id, is_delete);

create table flyway_schema_history
(
    installed_rank int                                 not null
        primary key,
    version        varchar(50)                         null,
    description    varchar(200)                        not null,
    type           varchar(20)                         not null,
    script         varchar(1000)                       not null,
    checksum       int                                 null,
    installed_by   varchar(100)                        not null,
    installed_on   timestamp default CURRENT_TIMESTAMP not null,
    execution_time int                                 not null,
    success        tinyint(1)                          not null
);

create index flyway_schema_history_s_idx
    on flyway_schema_history (success);

create table graph_thread
(
    thread_id   varchar(36)          not null
        primary key,
    thread_name varchar(255)         null,
    is_released tinyint(1) default 0 not null,
    constraint IDX_GRAPH_THREAD_NAME_RELEASED
        unique (thread_name, is_released)
);

create table graph_checkpoint
(
    checkpoint_id varchar(36)                         not null
        primary key,
    thread_id     varchar(36)                         not null,
    node_id       varchar(255)                        null,
    next_node_id  varchar(255)                        null,
    state_data    json                                not null,
    saved_at      timestamp default CURRENT_TIMESTAMP null,
    constraint GRAPH_FK_THREAD
        foreign key (thread_id) references graph_thread (thread_id)
            on delete cascade
);

create table knowledge_document
(
    doc_id              bigint auto_increment comment '文档ID'
        primary key,
    doc_title           varchar(1024)                      not null comment '文档标题',
    upload_user         varchar(255)                       null comment '上传用户',
    doc_url             varchar(2048)                      null comment '文档URL',
    converted_doc_url   varchar(2048)                      null comment '转换后的文档URL',
    expire_date         date                               null comment '文档失效日期',
    status              varchar(32)                        not null comment '状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    accessible_by       varchar(1024)                      null comment '可见范围',
    description         varchar(512)                       null comment '文档描述',
    knowledge_base_type varchar(32)                        null comment '知识库类型：DOCUMENT_SEARCH(文档搜索型，走 RAG 流程), DATA_QUERY(数据查询型，面向结构化数据)',
    extension           text                               null comment '扩展字段，保存JSON字符串',
    created_at          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at          datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '修改时间',
    lock_version        int      default 0                 not null comment '乐观锁版本号',
    deleted             tinyint  default 0                 not null comment '是否删除：0-未删除，1-已删除'
)
    comment '知识文档表' collate = utf8mb4_unicode_ci;

create index idx_created_at
    on knowledge_document (created_at);

create index idx_status
    on knowledge_document (status);

create index idx_status_doc_id
    on knowledge_document (status, doc_id);

create table knowledge_segment
(
    id             bigint auto_increment comment '片段ID'
        primary key,
    text           longtext                           not null comment '文本内容',
    chunk_id       varchar(255)                       null comment '分片ID',
    metadata       varchar(2048)                      null comment '元数据',
    document_id    bigint                             not null comment '所属文档ID',
    chunk_order    int                                not null comment '顺序',
    embedding_id   varchar(255)                       null comment '嵌入ID',
    status         varchar(255)                       null comment '状态：STORED, VECTOR_STORED',
    skip_embedding int                                null comment '是否跳过嵌入生成',
    hitk_question  varchar(1024)                      null comment '知识片段 Hit@K 测试问题',
    created_at     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '修改时间',
    lock_version   int      default 0                 not null comment '乐观锁版本号',
    deleted        tinyint  default 0                 not null comment '是否删除：0-未删除，1-已删除'
)
    comment '知识片段表' collate = utf8mb4_unicode_ci;

create index idx_document_id
    on knowledge_segment (document_id);

create index idx_document_id_chunk_order
    on knowledge_segment (document_id, chunk_order);

create index idx_document_status_skip
    on knowledge_segment (document_id, status, skip_embedding);

create index idx_status
    on knowledge_segment (status);

create table knowledge_segment_hitk_task
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    question_id   bigint                                   not null comment '算法题ID',
    document_id   bigint                                   not null comment '知识文档ID',
    total_count   int            default 0                 not null comment '测试分片总数',
    hit_count     int            default 0                 not null comment '命中分片数',
    miss_count    int            default 0                 not null comment '未命中分片数',
    hit_rate      decimal(10, 4) default 0.0000            not null comment '命中率',
    status        varchar(32)                              not null comment '任务状态：COMPLETED、FAILED',
    error_message varchar(2048)                            null comment '错误信息',
    remark        varchar(1024)                            null comment '任务备注',
    details_json  longtext                                 null comment '任务明细JSON',
    is_delete     int            default 0                 not null comment '0:否, 1:是',
    create_time   datetime       default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '知识片段 Hit@K 测试任务表' collate = utf8mb4_unicode_ci;

create index idx_create_time_status
    on knowledge_segment_hitk_task (create_time, status);

create index idx_document_create_time
    on knowledge_segment_hitk_task (document_id, create_time);

create index idx_question_document
    on knowledge_segment_hitk_task (question_id, document_id);

create table knowledge_segment_ragas_task
(
    id                        bigint auto_increment comment '主键ID'
        primary key,
    question_id               bigint                             not null comment '算法题ID',
    document_id               bigint                             not null comment '知识文档ID',
    total_count               int      default 0                 not null comment '任务总记录数',
    success_count             int      default 0                 not null comment '成功记录数',
    failure_count             int      default 0                 not null comment '失败记录数',
    average_answer_relevancy  decimal(10, 4)                     null comment '答案相关性均值',
    average_faithfulness      decimal(10, 4)                     null comment '忠实度均值',
    average_context_precision decimal(10, 4)                     null comment '上下文精确率均值',
    average_context_recall    decimal(10, 4)                     null comment '上下文召回率均值',
    status                    varchar(32)                        not null comment '任务状态：COMPLETED、FAILED',
    error_message             varchar(2048)                      null comment '任务错误信息',
    details_json              longtext                           null comment '任务明细JSON',
    is_delete                 int      default 0                 not null comment '0:否, 1:是',
    create_time               datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time               datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '知识片段RAGAS评估任务表' collate = utf8mb4_unicode_ci;

create index idx_ragas_task_create_time_status
    on knowledge_segment_ragas_task (create_time, status);

create index idx_ragas_task_document_create_time
    on knowledge_segment_ragas_task (document_id, create_time);

create index idx_ragas_task_question_document
    on knowledge_segment_ragas_task (question_id, document_id);

create table knowledge_segment_ragas
(
    id                         bigint auto_increment comment '主键ID'
        primary key,
    question_id                bigint                             not null comment '算法题ID',
    document_id                bigint                             not null comment '知识文档ID',
    segment_id                 bigint                             not null comment '知识片段ID',
    ragas_question             varchar(1024)                      null comment 'RAGAS评估问题',
    standard_answer            longtext                           null comment '标准答案',
    generated_answer           longtext                           null comment '模型生成回答',
    retrieved_segment_ids_json longtext                           null comment '检索命中的知识片段ID列表JSON',
    retrieved_contexts_json    longtext                           null comment '检索命中的知识片段内容列表JSON',
    rewritten_questions_json   longtext                           null comment '问题改写结果列表JSON',
    context_precision          decimal(10, 6)                     null comment '上下文精确率',
    context_recall             decimal(10, 6)                     null comment '上下文召回率',
    faithfulness               decimal(10, 6)                     null comment '忠实度',
    answer_relevancy           decimal(10, 6)                     null comment '答案相关性',
    answer_similarity          decimal(10, 6)                     null comment '答案相似度',
    answer_correctness         decimal(10, 6)                     null comment '答案正确性',
    overall_score              decimal(10, 6)                     null comment '综合得分',
    status                     varchar(32)                        null comment '状态：INIT、ANSWER_GENERATED、EVALUATED、FAILED',
    metadata                   longtext                           null comment '扩展元数据JSON',
    is_delete                  int      default 0                 not null comment '0:否, 1:是',
    create_time                datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time                datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_segment_delete
        unique (segment_id, is_delete)
)
    comment '知识片段RAGAS评估表' collate = utf8mb4_unicode_ci;

create index idx_question_document_delete
    on knowledge_segment_ragas (question_id, document_id, is_delete);

create index idx_segment_delete
    on knowledge_segment_ragas (segment_id, is_delete);

create index idx_status_delete
    on knowledge_segment_ragas (status, is_delete);

create table permission
(
    id              bigint auto_increment comment '权限ID'
        primary key,
    permission_code varchar(100)                       not null comment '权限编码（如 user:create）',
    permission_name varchar(100)                       not null comment '权限名称',
    permission_type tinyint                            not null comment '权限类型：1-功能模块, 2-API端点',
    parent_id       bigint   default 0                 not null comment '父权限ID（0表示顶级权限）',
    description     varchar(255)                       null comment '权限描述',
    is_delete       int      default 0                 not null comment '是否删除：0-否, 1-是',
    create_time     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint permission_code
        unique (permission_code)
)
    comment '权限表';

create index idx_parent_id
    on permission (parent_id);

create index idx_permission_code
    on permission (permission_code);

create table question_sync_compensation_task
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    question_id       bigint                             not null comment '题目ID',
    operation_type    varchar(32)                        not null comment '操作类型：UPDATE、DELETE',
    status            varchar(32)                        not null comment '状态：PENDING、PROCESSING、SUCCESS、FAILED',
    error_message     varchar(512)                       null comment '最近错误信息',
    retry_count       int      default 0                 not null comment '重试次数',
    last_attempt_time datetime                           null comment '最后尝试时间',
    is_delete         int      default 0                 not null comment '0:否, 1:是',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '题目双库同步补偿任务表';

create index idx_question_sync_comp_task_question_id
    on question_sync_compensation_task (question_id)
    comment '按题目查询补偿任务';

create index idx_question_sync_comp_task_status
    on question_sync_compensation_task (status)
    comment '按任务状态查询';

create table role
(
    id          bigint auto_increment comment '角色ID'
        primary key,
    role_code   varchar(50)                        not null comment '角色编码（唯一标识）',
    role_name   varchar(100)                       not null comment '角色名称',
    description varchar(255)                       null comment '角色描述',
    is_default  tinyint  default 0                 not null comment '是否默认角色：0-否, 1-是',
    is_delete   int      default 0                 not null comment '是否删除：0-否, 1-是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint role_code
        unique (role_code)
)
    comment '角色表';

create index idx_role_code
    on role (role_code);

create table role_permission
(
    id            bigint auto_increment comment '关联ID'
        primary key,
    role_id       bigint                             not null comment '角色ID',
    permission_id bigint                             not null comment '权限ID',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_role_permission
        unique (role_id, permission_id)
)
    comment '角色权限关联表';

create index idx_permission_id
    on role_permission (permission_id);

create index idx_role_id
    on role_permission (role_id);

create table tag
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    tag_name    varchar(32)                        not null comment '标签名称',
    tag_type    varchar(32)                        not null comment '标签类型',
    description varchar(255)                       null comment '标签描述',
    is_delete   int      default 0                 not null comment '0:否, 1:是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_tag_name
        unique (tag_name)
)
    comment '标签字典表';

create table trace
(
    id                                  bigint auto_increment comment '主键'
        primary key,
    trace_id                            varchar(64)                        not null comment '链路追踪ID',
    conversation_id                     varchar(64)                        not null comment '会话ID',
    user_id                             bigint                             null comment '用户ID',
    request_message                     text                               null comment '本次用户输入',
    current_algorithm_question_id       bigint                             null comment '当前算法题ID',
    current_algorithm_question_snapshot longtext                           null comment '当前算法题快照JSON',
    status                              varchar(32)                        not null comment '链路状态：RUNNING/SUCCESS/FAILED',
    error_message                       text                               null comment '链路失败原因',
    create_time                         datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time                         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_trace_trace_id
        unique (trace_id)
)
    comment 'workflow链路记录表';

create index idx_trace_conversation_id
    on trace (conversation_id);

create index idx_trace_user_id
    on trace (user_id);

create table trace_item
(
    id                bigint auto_increment comment '主键'
        primary key,
    trace_id          varchar(64)                        not null comment '链路追踪ID',
    node_name         varchar(128)                       not null comment '节点名称',
    item_type         varchar(32)                        not null comment '记录类型：LLM/TOOL/NODE',
    item_key          varchar(128)                       not null comment '节点内唯一标识',
    round_no          int      default 0                 not null comment '节点内部轮次，单轮节点固定为0',
    tool_name         varchar(128)                       null comment '工具名称',
    input_payload     longtext                           null comment '输入JSON文本',
    output_payload    longtext                           null comment '输出JSON文本',
    input_summary     varchar(512)                       null comment '输入摘要',
    output_summary    varchar(512)                       null comment '输出摘要',
    prompt_tokens     int                                null comment '输入token数',
    completion_tokens int                                null comment '输出token数',
    total_tokens      int                                null comment '总token数',
    status            varchar(32)                        not null comment '记录状态：RUNNING/SUCCESS/FAILED',
    error_message     text                               null comment '失败原因',
    start_timestamp   bigint                             null,
    end_timestamp     bigint                             null,
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_trace_item_round_item_key
        unique (trace_id, node_name, round_no, item_key)
)
    comment 'workflow节点记录表';

create index idx_trace_item_trace_id
    on trace_item (trace_id);

create index idx_trace_item_trace_node
    on trace_item (trace_id, node_name);

create index idx_trace_item_trace_time
    on trace_item (trace_id, create_time);

create table user
(
    id            bigint auto_increment comment '用户ID'
        primary key,
    username      varchar(50)                        not null comment '用户名',
    password      varchar(255)                       null comment '密码（BCrypt加密，临时账号可为空）',
    user_type     tinyint  default 1                 not null comment '用户类型：0-超级管理员, 1-临时账号',
    need_password tinyint  default 1                 not null comment '是否需要密码：0-否, 1-是',
    is_active     tinyint  default 1                 not null comment '是否激活：0-失效, 1-激活',
    valid_from    datetime                           null comment '有效期开始时间（临时账号）',
    valid_until   datetime                           null comment '有效期结束时间（临时账号）',
    is_delete     int      default 0                 not null comment '是否删除：0-否, 1-是',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    trial_count   int      default -1                not null comment '剩余体验次数，-1表示无限制',
    constraint username
        unique (username)
)
    comment '用户表';

create index idx_is_active
    on user (is_active);

create index idx_user_type
    on user (user_type);

create index idx_username
    on user (username);

create table user_login_log
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    username    varchar(50)                        not null comment '用户名',
    login_ip    varchar(64)                        not null comment '登录IP',
    login_time  datetime default CURRENT_TIMESTAMP not null comment '登录时间',
    is_delete   int      default 0                 not null comment '0:否, 1:是',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '用户登录日志表';

create index idx_user_login_log_login_time
    on user_login_log (login_time)
    comment '按登录时间倒序查询';

create index idx_user_login_log_user_id
    on user_login_log (user_id)
    comment '按用户查询登录日志';

create table user_role
(
    id          bigint auto_increment comment '关联ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    role_id     bigint                             not null comment '角色ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_role
        unique (user_id, role_id)
)
    comment '用户角色关联表';

create index idx_role_id
    on user_role (role_id);

create index idx_user_id
    on user_role (user_id);

