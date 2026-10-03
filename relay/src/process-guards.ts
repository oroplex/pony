/**
 * Log fatal events and keep the process up. A thrown handler must not take
 * the relay offline; /health has to stay reachable after bad frames.
 */
export function installProcessGuards(
  log: (kind: string, err: unknown) => void = defaultLog,
): void {
  process.on("uncaughtException", (err) => {
    log("uncaughtException", err);
  });
  process.on("unhandledRejection", (reason) => {
    log("unhandledRejection", reason);
  });
}

function defaultLog(kind: string, err: unknown): void {
  console.error(`[relay] ${kind}`, err);
}
