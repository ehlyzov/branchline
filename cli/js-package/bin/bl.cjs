#!/usr/bin/env node

const path = require('node:path');
const kotlinDir = path.resolve(__dirname, '..', 'kotlin');
require(path.join(kotlinDir, 'branchline-cli.js'));
