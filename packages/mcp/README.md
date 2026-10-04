# pony-mcp

MCP server for [Pony](https://pony.karlmagendavid.com): it lets an AI assistant see and drive your Android phone through the Pony app, over an end-to-end encrypted relay.

You need the Pony app on an Android 11+ phone (see the [project README](https://github.com/oroplex/pony#install)) and Node.js 20+.

> **Not on npm yet.** Until `pony-mcp` is published, run it from a clone (see [MCP setup](https://github.com/oroplex/pony#mcp-setup)). The `npx` commands below work once it's live.

## Use with an MCP host (stdio), once published on npm

```json
{
  "mcpServers": {
    "pony": {
      "command": "npx",
      "args": ["-y", "pony-mcp", "--listen"]
    }
  }
}
```

The assistant calls `pair` and shows a QR code or a `pony://pair` link. Scan it with the phone, compare the six-digit safety code, and tap **It matches**. Acting tools return `not_confirmed` until that happens. With `--listen` the pairing is kept in `~/.pony/mcp.json` (encrypted, mode `600`) and rejoined on start.

## Streamable HTTP, once published on npm

```bash
npx -y pony-mcp --listen --http 43123
```

Then point the MCP host at `http://127.0.0.1:43123/mcp` (localhost only).

## Options

| Flag | Env | Meaning |
| --- | --- | --- |
| `--listen` | `PONY_LISTEN=1` | Keep the pairing and accept asks typed or spoken on the phone |
| `--http <port>` | | Serve streamable HTTP on 127.0.0.1 instead of stdio |
| `--relay <url>` | `PONY_RELAY` | Relay URL (default `https://relay.pony.karlmagendavid.com`) |
| `--client <name>` | `PONY_CLIENT_NAME` | Name shown on the phone |
| `--state <path>` | `PONY_STATE` | Pairing file for listen mode |
| `--help` | | Show usage |

## License

MIT
