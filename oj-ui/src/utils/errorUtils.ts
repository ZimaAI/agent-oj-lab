function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

export function getErrorStatus(error: unknown): number | undefined {
  if (!isObjectRecord(error)) {
    return undefined
  }

  return typeof error.status === 'number' ? error.status : undefined
}
