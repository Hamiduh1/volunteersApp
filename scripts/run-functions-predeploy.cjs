"use strict";

const {spawnSync} = require("node:child_process");

const [, , resourceDir, script] = process.argv;
if (!resourceDir || !script) {
  throw new Error("Usage: node scripts/run-functions-predeploy.cjs <functions-dir> <npm-script>");
}

// npm is npm.cmd on Windows, while Unix-like deployment runners use npm.
const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";
const result = spawnSync(npmCommand, ["run", script], {
  cwd: resourceDir,
  stdio: "inherit",
  // Windows resolves npm through its .cmd shim; Unix runs the binary directly.
  shell: process.platform === "win32",
});

if (result.error) throw result.error;
process.exitCode = result.status === 0 ? 0 : (result.status || 1);
