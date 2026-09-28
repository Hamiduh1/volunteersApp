"use strict";

const {spawnSync} = require("node:child_process");

const [, , resourceDir, script] = process.argv;
if (!resourceDir || !script) {
  throw new Error("Usage: node scripts/run-functions-predeploy.cjs <functions-dir> <npm-script>");
}

const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";
const result = spawnSync(npmCommand, ["run", script], {
  cwd: resourceDir,
  stdio: "inherit",
  shell: process.platform === "win32",
});

if (result.error) throw result.error;
process.exitCode = result.status === 0 ? 0 : (result.status || 1);
