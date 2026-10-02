// Bundles the MCP server into dist/cli.js. The workspace packages
// @pony/client and @pony/shared are inlined; every other npm package stays
// an external runtime dependency.
import { build } from "esbuild";
import { readFileSync } from "node:fs";

const pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8"));

await build({
  entryPoints: ["src/cli.ts"],
  outfile: "dist/cli.js",
  bundle: true,
  platform: "node",
  format: "esm",
  target: "node20",
  external: Object.keys(pkg.dependencies).flatMap((name) => [name, `${name}/*`]),
  define: { __PONY_MCP_VERSION__: JSON.stringify(pkg.version) },
  legalComments: "none",
  logLevel: "info",
});
