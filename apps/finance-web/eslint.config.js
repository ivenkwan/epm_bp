import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';

export default tseslint.config(
    { ignores: ['dist', 'node_modules', 'src/api/generated'] },
    {
      files: ['src/**/*.{ts,tsx}', 'tests/**/*.{ts,tsx}'],
      extends: [js.configs.recommended, ...tseslint.configs.strictTypeChecked],
      languageOptions: {
        ecmaVersion: 2022,
        globals: globals.browser,
        parserOptions: {
          tsconfigRootDir: import.meta.dirname,
          project: ['./tsconfig.json'],
        },
      },
      plugins: {
        'react-hooks': reactHooks,
      },
      rules: {
        // Unreviewed `any` is not accepted anywhere in the application.
        '@typescript-eslint/no-explicit-any': 'error',
        '@typescript-eslint/no-unsafe-assignment': 'error',
        '@typescript-eslint/no-unsafe-member-access': 'error',
        '@typescript-eslint/no-unsafe-call': 'error',
        'react-hooks/rules-of-hooks': 'error',
        'react-hooks/exhaustive-deps': 'error',
      },
    },
);
