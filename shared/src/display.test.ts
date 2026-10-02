import { describe, expect, it } from "vitest";

import { displayChoice } from "./display.ts";

describe("display choice is independent of the relay", () => {
  const relays = [
    "https://relay.pony.karlmagendavid.com",
    "http://100.64.8.8:8787",
    "ws://my-pc.tailnet.ts.net:8787",
  ];

  it("keeps background as the default on every relay", () => {
    for (const relay of relays) {
      expect(relay.length).toBeGreaterThan(0);
      expect(displayChoice(true, undefined, undefined)).toBe("background");
    }
  });

  it("lets a command force the main screen without changing the relay", () => {
    expect(displayChoice(true, false, undefined)).toBe("main");
    expect(displayChoice(true, undefined, "main")).toBe("main");
    expect(displayChoice(false, undefined, "background")).toBe("background");
  });
});
