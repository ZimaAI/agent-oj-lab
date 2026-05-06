# `questionId` 解析当前发布版本并绑定会话

Status: needs-triage

## What to build

让普通用户继续以 `questionId` 访问题目，但后端在题目详情、进入做题态、试运行和正式提交入口统一解析到当前对外生效的 `题目版本`，并把该版本固定到用户会话。这样既不要求前端直接感知 `questionVersionId`，又能保证同一会话内的运行和提交始终基于同一题义。

## Acceptance criteria

- [ ] 普通用户侧接口继续以 `questionId` 为主标识，但后端会统一解析到当前发布的 `题目版本`
- [ ] 用户开始做题后，会话绑定某个具体版本；会话中的试运行和正式提交都固定使用该版本，不因后续发布新版本自动漂移
- [ ] 新进入题目的会话默认绑定当前发布版本，历史版本不向普通用户暴露为直接可选入口
- [ ] 提交记录和运行上下文都能回溯到具体 `题目版本`，而不是继续依赖“当前题目最新内容”的隐式读取

## Blocked by

- `.scratch/stdin-stdout-judge-refactor/issues/04-immutable-version-and-frozen-judge-package-publication.md`
