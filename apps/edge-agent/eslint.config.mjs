import { defineConfig, globalIgnores } from "eslint/config";
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import prettierConfig from "eslint-config-prettier";

// Config mínima para um projeto Node.js/TypeScript puro (sem Next.js), por
// isso não reaproveita eslint-config-next (apps/web) — apenas as regras
// recomendadas de JS/TS + integração com Prettier, no mesmo espírito
// (defineConfig + prettier por último para desativar regras de formatação
// conflitantes) usado em apps/web/eslint.config.mjs.
const eslintConfig = defineConfig([
  js.configs.recommended,
  ...tseslint.configs.recommended,
  prettierConfig,
  globalIgnores(["dist/**", "node_modules/**"]),
]);

export default eslintConfig;
