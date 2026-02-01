import * as monaco from 'monaco-editor';

const LANGUAGE_ID = 'branchline';

const keywords = [
  'TRANSFORM',
  'OPTIONS',
  'BUFFER',
  'LET',
  'SET',
  'APPEND',
  'OUTPUT',
  'IF',
  'ELSE',
  'RETURN',
  'TRY',
  'CATCH',
  'RETRY',
  'ABORT',
  'FUNC',
  'TYPE',
  'SHARED',
  'SINGLE',
  'MANY',
  'FOR',
  'IN',
  'WHERE',
  'TRUE',
  'FALSE',
  'NULL'
];

let registered = false;

export function ensureBranchlineLanguage() {
  if (registered) {
    return;
  }
  const alreadyRegistered = monaco.languages.getLanguages().some((lang) => lang.id === LANGUAGE_ID);
  if (alreadyRegistered) {
    registered = true;
    return;
  }

  monaco.languages.register({ id: LANGUAGE_ID });
  monaco.languages.setLanguageConfiguration(LANGUAGE_ID, {
    comments: {
      lineComment: '//'
    },
    autoClosingPairs: [
      { open: '{', close: '}' },
      { open: '(', close: ')' },
      { open: '[', close: ']' },
      { open: '"', close: '"', notIn: ['string'] }
    ],
    brackets: [
      ['{', '}'],
      ['[', ']'],
      ['(', ')']
    ]
  });

  monaco.languages.setMonarchTokensProvider(LANGUAGE_ID, {
    keywords,
    operators: ['+', '-', '*', '/', '%', '??', '||', '&&', '==', '!=', '<', '<=', '>', '>='],
    symbols: /[=><!~?:&|+\-*\/\^%]+/,
    tokenizer: {
      root: [
        [/[a-zA-Z_][\w\-]*/, {
          cases: {
            '@keywords': 'keyword',
            '@default': 'identifier'
          }
        }],
        { include: '@whitespace' },
        [/"([^"\\]|\\.)*$/, 'string.invalid'],
        [/"/, { token: 'string.quote', bracket: '@open', next: '@string' }],
        [/[{}()[\]]/, '@brackets'],
        [/[0-9]+\.[0-9]+/, 'number.float'],
        [/[0-9]+/, 'number'],
        [/[@$][a-zA-Z_][\w\-]*/, 'identifier'],
        [/@symbols/, 'operator']
      ],
      string: [
        [/[^\\"\n]+/, 'string'],
        [/\\./, 'string.escape'],
        [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }]
      ],
      whitespace: [
        [/\s+/, 'white'],
        [/\/\/.*$/, 'comment']
      ]
    }
  });

  registered = true;
}

export const BRANCHLINE_LANGUAGE_ID = LANGUAGE_ID;
