# Workflow Is Create-Only, Admin Publishes Immutable Question Versions

AI workflow generation is create-only for question-bank questions: it creates new questions but does not mutate an existing one. Existing questions can still evolve through admin-only version publication, where each question version is an immutable snapshot with a fixed default execution environment and a frozen judge package, and official submit consumes that frozen package so submission records remain traceable and auditable.
