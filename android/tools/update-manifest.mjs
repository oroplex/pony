#!/usr/bin/env node
// Writes latest.json for the in-app updater from a signed release APK.
//
//   node android/tools/update-manifest.mjs pony-0.5.0.apk [--out latest.json] [--notes CHANGELOG.md]
//
// Upload latest.json and the APK (under the name printed as "apk") to
// https://download.pony.karlmagendavid.com/. The phone only installs an update
// whose SHA-256 matches and whose signing certificate matches the installed app.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";

const args = process.argv.slice(2);
const apk = args.find((arg) => !arg.startsWith("--"));
const option = (name, fallback) => {
  const at = args.indexOf(`--${name}`);
  return at >= 0 && args[at + 1] ? args[at + 1] : fallback;
};
if (!apk || !existsSync(apk)) {
  console.error("usage: update-manifest.mjs <signed-release.apk> [--out latest.json] [--notes CHANGELOG.md]");
  process.exit(2);
}

const buildTools = findBuildTools();
const badging = execFileSync(join(buildTools, "aapt2"), ["dump", "badging", apk], { encoding: "utf8" });
const pkg = /package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/.exec(badging);
const minSdk = /(?:minSdkVersion|sdkVersion):'(\d+)'/.exec(badging);
if (!pkg) fail("Couldn't read the package name and version from the APK.");
const [, packageName, versionCode, versionName] = pkg;
if (packageName !== "app.pony.companion") fail(`This is ${packageName}. Updates are for the release app, app.pony.companion.`);

try {
  execFileSync(join(buildTools, "apksigner"), ["verify", apk], { stdio: "pipe" });
} catch {
  fail("The APK isn't signed. Sign it with the Pony release key first.");
}

const bytes = readFileSync(apk);
const notesFile = option("notes", new URL("../../CHANGELOG.md", import.meta.url).pathname);
const manifest = {
  versionCode: Number(versionCode),
  versionName,
  apk: option("name", `pony-${versionName}.apk`),
  sha256: createHash("sha256").update(bytes).digest("hex"),
  size: statSync(apk).size,
  publishedAt: new Date().toISOString().slice(0, 10),
  minSdk: minSdk ? Number(minSdk[1]) : 29,
  changelog: releaseNotes(notesFile, versionName),
};

const out = option("out", "latest.json");
writeFileSync(out, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Wrote ${out} for ${versionName} (${versionCode}).`);
console.log(`Upload ${basename(apk)} as ${manifest.apk} next to it.`);

/** The first bullet list under "## <version>". */
function releaseNotes(file, version) {
  if (!existsSync(file)) return [];
  const lines = readFileSync(file, "utf8").split("\n");
  const start = lines.findIndex((line) => line.trim() === `## ${version}`);
  if (start < 0) return [];
  const notes = [];
  for (const line of lines.slice(start + 1)) {
    if (line.startsWith("#")) break;
    const bullet = /^\s*[-*]\s+(.*)$/.exec(line);
    if (bullet) notes.push(bullet[1].trim());
    else if (notes.length && !line.trim()) break;
  }
  return notes;
}

function findBuildTools() {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!sdk) fail("Set ANDROID_HOME to your Android SDK.");
  const root = join(sdk, "build-tools");
  const versions = existsSync(root) ? readdirSync(root).sort(compareVersions) : [];
  if (!versions.length) fail("No Android build-tools found under ANDROID_HOME.");
  return join(root, versions[versions.length - 1]);
}

function compareVersions(a, b) {
  const pa = a.split(/[.-]/).map(Number);
  const pb = b.split(/[.-]/).map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i += 1) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d;
  }
  return 0;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
