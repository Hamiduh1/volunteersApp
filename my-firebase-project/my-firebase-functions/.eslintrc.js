/* eslint-disable max-len */ // Disable max-len for this config file
module.exports = {
  root: true,
  env: {
    es6: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
    "plugin:import/errors",
    "plugin:import/warnings",
    "plugin:import/typescript",
    "google",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: ["tsconfig.json", "tsconfig.dev.json"],
    sourceType: "module",
    tsconfigRootDir: __dirname,
  },
  ignorePatterns: [
    "/lib/**/*", // Ignore built files.
    "/generated/**/*", // Ignore generated files.
    // Immutable recovery snapshot: compile it, but do not require a full
    // style migration before preserving its deployed function identities.
    "/src/legacyDeployedFunctions/**/*",
  ],
  plugins: [
    "@typescript-eslint",
    "import",
  ],
  rules: {
    "quotes": ["error", "double"],
    "import/no-unresolved": 0, // Consider fixing import paths instead
    "indent": ["error", 2],
    // We are disabling max-len for THIS file via the top comment
    // The rule might still apply to your src/index.ts based on extends
    // if the comment there is removed.
  },
};
