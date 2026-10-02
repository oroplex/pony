export type Display = "main" | "background";

/**
 * Pulls the display selector out of a REPL command's tokens. A `--main` or
 * `--background` flag (also `--display=main`/`--display=background`) may sit
 * anywhere in the line, the way the MCP takes a `display` argument; a bare
 * trailing `main`/`background` still works for the older muscle memory. A later
 * flag wins. Whatever is left is the command's positional arguments.
 */
export function takeDisplay(rest: string[]): { args: string[]; display?: Display } {
  let display: Display | undefined;
  const args: string[] = [];
  for (const token of rest) {
    const flag = displayFlag(token);
    if (flag) display = flag;
    else args.push(token);
  }
  if (display === undefined) {
    const last = args[args.length - 1];
    if (last === "main" || last === "background") {
      args.pop();
      display = last;
    }
  }
  return { args, display };
}

function displayFlag(token: string): Display | undefined {
  switch (token) {
    case "--main":
    case "--display=main":
      return "main";
    case "--background":
    case "--display=background":
      return "background";
    default:
      return undefined;
  }
}
