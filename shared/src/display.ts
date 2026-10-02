/** Same rules as the phone. The relay URL is not an input: transport does not pick the display. */

export type DisplayName = "main" | "background";

export function displayChoice(
  prefOn: boolean,
  background: boolean | undefined,
  display?: string,
): DisplayName {
  const named = display?.trim().toLowerCase();
  if (named === "main" || named === "foreground") return "main";
  if (named === "background") return "background";
  if (background !== undefined) return background ? "background" : "main";
  return prefOn ? "background" : "main";
}
