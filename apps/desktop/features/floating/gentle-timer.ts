export function createGentleTimer(hide: () => void) {
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => clearTimeout(timer)
  function schedule(delay: number) {
    cancel()
    timer = setTimeout(hide, delay)
  }

  return {
    start: (riseMs: number) => schedule(riseMs + 4000),
    enter: cancel,
    leave: () => schedule(1500),
    cancel,
  }
}
