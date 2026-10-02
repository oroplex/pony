#!/usr/bin/env node
// Regenerates app/src/main/java/app/pony/companion/ui/design/PonyIcons.kt from Lucide SVGs.
// Usage: node android/tools/lucide-icons.mjs
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, "../app/src/main/java/app/pony/companion/ui/design/PonyIcons.kt");
const base = "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons";

// Kotlin name -> Lucide icon file.
const icons = {
  Home: "house",
  History: "rotate-ccw-clock",
  Settings: "settings-2",
  Mic: "mic",
  MicOff: "mic-off",
  Send: "arrow-up",
  Stop: "square",
  Close: "x",
  Check: "check",
  CheckCircle: "circle-check",
  ChevronRight: "chevron-right",
  ChevronDown: "chevron-down",
  Back: "arrow-left",
  Sparkles: "sparkles",
  Shield: "shield-check",
  Lock: "lock",
  EyeOff: "eye-off",
  Eye: "eye",
  Bolt: "zap",
  Battery: "battery-charging",
  Bell: "bell",
  Phone: "phone",
  Wifi: "wifi",
  WifiOff: "wifi-off",
  Cloud: "cloud",
  Link: "link-2",
  Qr: "qr-code",
  Scan: "scan-line",
  Keyboard: "keyboard",
  Bot: "bot",
  Brain: "brain",
  Calculator: "calculator",
  Timer: "timer",
  Message: "message-square",
  Camera: "camera",
  Trash: "trash",
  Info: "info",
  Alert: "triangle-alert",
  AlertCircle: "circle-alert",
  Refresh: "refresh-cw",
  Download: "download",
  External: "external-link",
  Copy: "copy",
  Moon: "moon",
  Sun: "sun",
  Phone2: "smartphone",
  Tap: "pointer",
  Type: "type",
  Swipe: "move",
  Speak: "volume-2",
  Ask: "message-circle-question-mark",
  Pause: "pause",
  Play: "play",
  Key: "key-round",
  Globe: "globe",
  Server: "server",
  Radio: "radio",
  Activity: "activity",
  Sliders: "sliders-horizontal",
  Palette: "palette",
  Wand: "wand-sparkles",
  Image: "image",
  Plus: "plus",
  Unplug: "unplug",
  Clock: "clock",
  Layers: "layers",
  Fingerprint: "fingerprint-pattern",
  Hand: "hand",
  Search: "search",
  ArrowRight: "arrow-right",
  Rocket: "rocket",
  Moon2: "moon-star",
  Accessibility: "person-standing",
  Waves: "audio-lines",
  Command: "command",
  Terminal: "terminal",
  Package: "package",
  Circle: "circle",
  ScreenShare: "screen-share",
  PhoneOff: "phone-off",
  Monitor: "monitor-smartphone",
};

const num = (v) => Number.parseFloat(v);
const fmt = (n) => (Math.round(n * 1000) / 1000).toString();

function attrs(tag) {
  const out = {};
  for (const m of tag.matchAll(/([a-zA-Z0-9:-]+)="([^"]*)"/g)) out[m[1]] = m[2];
  return out;
}

function toPath(el, a) {
  switch (el) {
    case "path":
      return a.d;
    case "circle": {
      const cx = num(a.cx), cy = num(a.cy), r = num(a.r);
      return `M${fmt(cx - r)},${fmt(cy)}a${fmt(r)},${fmt(r)} 0 1,0 ${fmt(2 * r)},0a${fmt(r)},${fmt(r)} 0 1,0 ${fmt(-2 * r)},0`;
    }
    case "ellipse": {
      const cx = num(a.cx), cy = num(a.cy), rx = num(a.rx), ry = num(a.ry);
      return `M${fmt(cx - rx)},${fmt(cy)}a${fmt(rx)},${fmt(ry)} 0 1,0 ${fmt(2 * rx)},0a${fmt(rx)},${fmt(ry)} 0 1,0 ${fmt(-2 * rx)},0`;
    }
    case "rect": {
      const x = num(a.x ?? 0), y = num(a.y ?? 0), w = num(a.width), h = num(a.height);
      const rx = Math.min(num(a.rx ?? a.ry ?? 0), w / 2, h / 2);
      if (!rx) return `M${fmt(x)},${fmt(y)}h${fmt(w)}v${fmt(h)}h${fmt(-w)}z`;
      return [
        `M${fmt(x + rx)},${fmt(y)}`,
        `h${fmt(w - 2 * rx)}`,
        `a${fmt(rx)},${fmt(rx)} 0 0 1 ${fmt(rx)},${fmt(rx)}`,
        `v${fmt(h - 2 * rx)}`,
        `a${fmt(rx)},${fmt(rx)} 0 0 1 ${fmt(-rx)},${fmt(rx)}`,
        `h${fmt(-(w - 2 * rx))}`,
        `a${fmt(rx)},${fmt(rx)} 0 0 1 ${fmt(-rx)},${fmt(-rx)}`,
        `v${fmt(-(h - 2 * rx))}`,
        `a${fmt(rx)},${fmt(rx)} 0 0 1 ${fmt(rx)},${fmt(-rx)}z`,
      ].join("");
    }
    case "line":
      return `M${a.x1},${a.y1}L${a.x2},${a.y2}`;
    case "polyline":
    case "polygon": {
      const pts = a.points.trim().split(/[\s,]+/).map(num);
      let d = `M${fmt(pts[0])},${fmt(pts[1])}`;
      for (let i = 2; i < pts.length; i += 2) d += `L${fmt(pts[i])},${fmt(pts[i + 1])}`;
      return el === "polygon" ? `${d}z` : d;
    }
    default:
      return null;
  }
}

const entries = [];
for (const [name, file] of Object.entries(icons)) {
  const res = await fetch(`${base}/${file}.svg`);
  if (!res.ok) throw new Error(`${file}: ${res.status}`);
  const svg = await res.text();
  const paths = [];
  for (const m of svg.matchAll(/<(path|circle|rect|line|polyline|polygon|ellipse)\b([^>]*)\/?>/g)) {
    const d = toPath(m[1], attrs(m[2]));
    if (d) paths.push(d.replace(/\s+/g, " ").trim());
  }
  entries.push({ name, file, paths });
}

const body = entries
  .map(({ name, file, paths }) => {
    const list = paths.map((p) => `            "${p}",`).join("\n");
    return `    /** Lucide \`${file}\` */\n    val ${name}: ImageVector by lazy {\n        icon(\n            "${name}",\n${list}\n        )\n    }`;
  })
  .join("\n\n");

const kotlin = `package app.pony.companion.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pony's stroke icon set, generated from Lucide (ISC license, see
 * assets/licenses/LICENSE-Lucide.txt) by android/tools/lucide-icons.mjs.
 * Tint follows the caller. Do not edit by hand.
 */
object PonyIcons {
    const val STROKE = 1.75f

${body}

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = "Pony.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
`;

writeFileSync(out, kotlin);
console.log(`wrote ${entries.length} icons to ${out}`);
