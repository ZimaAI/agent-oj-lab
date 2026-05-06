/**
 * 节流函数 - 批量收集调用并在延迟后执行最后一次调用（尾部节流）
 *
 * 在指定时间间隔内，无论调用多少次，只会在延迟结束后执行一次，
 * 并使用最后一次调用的参数。适用于需要批量处理的场景，如流式更新。
 *
 * @template T - 函数类型
 * @param func - 需要节流的函数
 * @param delay - 节流延迟时间（毫秒）
 * @returns 节流后的函数
 *
 * @example
 * const throttledUpdate = throttle((value: string) => {
 *   console.log(value);
 * }, 100);
 *
 * throttledUpdate('a'); // 不立即执行，启动 100ms 定时器
 * throttledUpdate('b'); // 更新参数为 'b'
 * throttledUpdate('c'); // 更新参数为 'c'
 * // 100ms 后执行一次，输出 'c'
 */
export function throttle<T extends (...args: any[]) => any>(
  func: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  let lastArgs: Parameters<T> | null = null;

  return function throttled(...args: Parameters<T>): void {
    // 存储最新的参数
    lastArgs = args;

    // 如果没有活动的定时器，设置一个新的
    if (timeoutId === null) {
      timeoutId = setTimeout(() => {
        // 执行函数并传入最新的参数
        if (lastArgs !== null) {
          func(...lastArgs);
        }

        // 清理状态
        lastArgs = null;
        timeoutId = null;
      }, delay);
    }
  };
}
