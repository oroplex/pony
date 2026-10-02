import { describe, expect, it } from "vitest";

import { takeDisplay } from "./repl-args.ts";

describe("takeDisplay", () => {
  it("reads a --main flag at the end, like a trailing bare word used to", () => {
    expect(takeDisplay(["10", "20", "--main"])).toEqual({ args: ["10", "20"], display: "main" });
  });

  it("reads a --main or --background flag anywhere in the line", () => {
    expect(takeDisplay(["--main", "10", "20"])).toEqual({ args: ["10", "20"], display: "main" });
    expect(takeDisplay(["tap", "--background", "3", "4"])).toEqual({ args: ["tap", "3", "4"], display: "background" });
    expect(takeDisplay(["0", "0", "--background", "100", "200", "300"])).toEqual({
      args: ["0", "0", "100", "200", "300"],
      display: "background",
    });
  });

  it("still accepts a bare trailing main or background", () => {
    expect(takeDisplay(["10", "20", "main"])).toEqual({ args: ["10", "20"], display: "main" });
    expect(takeDisplay(["background"])).toEqual({ args: [], display: "background" });
  });

  it("accepts the --display=main form the MCP's display argument mirrors", () => {
    expect(takeDisplay(["--display=main", "5", "6"])).toEqual({ args: ["5", "6"], display: "main" });
    expect(takeDisplay(["7", "8", "--display=background"])).toEqual({ args: ["7", "8"], display: "background" });
  });

  it("leaves a screenshot filename in place while taking the display flag", () => {
    expect(takeDisplay(["out.jpg", "--main"])).toEqual({ args: ["out.jpg"], display: "main" });
    expect(takeDisplay(["--main"]).args[0] ?? "screenshot.jpg").toBe("screenshot.jpg");
  });

  it("lets a later flag win and returns no display when none is given", () => {
    expect(takeDisplay(["1", "--main", "2", "--background"]).display).toBe("background");
    expect(takeDisplay(["1", "2", "3"])).toEqual({ args: ["1", "2", "3"] });
  });

  it("keeps a bare non-trailing word as a positional arg, not a display", () => {
    // Only `--main`/`--background` roam freely; a bare word selects only when trailing.
    expect(takeDisplay(["main", "10", "20"])).toEqual({ args: ["main", "10", "20"] });
  });
});
