#!/usr/bin/env node
/**
 * Validation driver for check-code-attribution scenario tests.
 *
 * Usage:
 *   node assert-scenarios.mjs validate <EXPECTED.json> <scenarios-dir>
 *   node assert-scenarios.mjs list-isolated <EXPECTED.json>
 *   node assert-scenarios.mjs list-main-java <EXPECTED.json> <scenarios-dir>
 *   node assert-scenarios.mjs routing-set <routing.json> <id> <jsonl-path>
 *   node assert-scenarios.mjs assert <EXPECTED.json> <dest-package-path> <routing.json>
 *
 * routing.json maps scenario id to Warden JSONL output path, e.g. { "main": "/tmp/..." }.
 * Non-isolated scenarios use the "main" entry when no dedicated id is present.
 */

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const ISOLATED_FILE_JAVA = /\.java$/i;
const ISOLATED_FILE_NOTICES = 'THIRD_PARTY_NOTICES.md';

/** Non-Java fixtures under scenarios/ that check-code-attribution-tests.sh requires. */
const REQUIRED_SCENARIO_FIXTURES = [
  'THIRD_PARTY_NOTICES.mismatch-snippet.md',
];

export function loadExpected(expectedPath) {
  return JSON.parse(fs.readFileSync(expectedPath, 'utf8'));
}

export function listIsolated(scenarios) {
  return scenarios.filter((s) => s.isolated);
}

/** Repo-relative path normalization for Warden JSONL matching. */
export function normalizeRepoPath(filePath) {
  if (!filePath) return filePath;
  return filePath.replace(/\\/g, '/').replace(/^\.\//, '').replace(/\/+/g, '/');
}

/** True when a Warden-reported path refers to the expected scenario file. */
export function pathMatchesWardenFile(reportedPath, wardenFile) {
  const reported = normalizeRepoPath(reportedPath);
  const expected = normalizeRepoPath(wardenFile);
  if (reported === expected) return true;
  const base = expected.split('/').pop();
  return base != null && reported.endsWith(`/${base}`);
}

export function findingCountForFile(fileMap, wardenFile) {
  const expected = normalizeRepoPath(wardenFile);
  if (fileMap[expected] != null) return fileMap[expected];
  for (const [key, count] of Object.entries(fileMap)) {
    if (pathMatchesWardenFile(key, wardenFile)) return count;
  }
  return 0;
}

export function findingsForFile(findings, wardenFile) {
  return findings.filter(
    (f) => f.location && pathMatchesWardenFile(f.location.path, wardenFile),
  );
}

export function listMainBatchJava(scenarios, scenariosDir) {
  const isolatedJava = new Set(
    listIsolated(scenarios)
      .map((s) => s.file)
      .filter((file) => ISOLATED_FILE_JAVA.test(file)),
  );
  return fs
    .readdirSync(scenariosDir)
    .filter((name) => name.endsWith('.java') && !isolatedJava.has(name))
    .sort();
}

/**
 * @returns {string[]} validation error messages (empty = ok)
 */
export function validateExpected(scenarios, scenariosDir) {
  const errors = [];

  if (!Array.isArray(scenarios)) {
    return ['EXPECTED.json must be a JSON array'];
  }

  const ids = new Set();
  const expectedJava = new Set();

  for (const [index, s] of scenarios.entries()) {
    const label = `entry ${index}`;
    if (!s || typeof s !== 'object') {
      errors.push(`${label}: must be an object`);
      continue;
    }
    if (typeof s.id !== 'string' || !s.id) {
      errors.push(`${label}: missing or empty "id"`);
    } else {
      if (ids.has(s.id)) errors.push(`duplicate id "${s.id}"`);
      ids.add(s.id);
      if (s.id === 'main') {
        errors.push(`id "main" is reserved for routing.json`);
      }
    }
    if (typeof s.file !== 'string' || !s.file) {
      errors.push(`${label}: missing or empty "file"`);
    } else if (ISOLATED_FILE_JAVA.test(s.file)) {
      expectedJava.add(s.file);
      const onDisk = path.join(scenariosDir, s.file);
      if (!fs.existsSync(onDisk)) {
        errors.push(`${s.id}: scenarios/${s.file} does not exist`);
      }
    } else if (s.file !== ISOLATED_FILE_NOTICES) {
      errors.push(
        `${s.id}: unsupported file "${s.file}" (use *.java or ${ISOLATED_FILE_NOTICES})`,
      );
    }
    if (typeof s.expectFinding !== 'boolean') {
      errors.push(`${s.id ?? label}: "expectFinding" must be a boolean`);
    }
    if (s.isolated) {
      if (
        !ISOLATED_FILE_JAVA.test(s.file) &&
        s.file !== ISOLATED_FILE_NOTICES
      ) {
        errors.push(
          `${s.id}: isolated scenarios must use *.java or ${ISOLATED_FILE_NOTICES}`,
        );
      }
    }
  }

  let diskEntries = [];
  try {
    diskEntries = fs.readdirSync(scenariosDir);
  } catch (e) {
    errors.push(`cannot read scenarios dir ${scenariosDir}: ${e.message}`);
    return errors;
  }

  const diskJava = diskEntries.filter((n) => n.endsWith('.java'));
  for (const name of diskJava) {
    if (!expectedJava.has(name)) {
      errors.push(`scenarios/${name} has no matching entry in EXPECTED.json`);
    }
  }

  for (const name of REQUIRED_SCENARIO_FIXTURES) {
    const onDisk = path.join(scenariosDir, name);
    if (!fs.existsSync(onDisk)) {
      errors.push(`scenarios/${name} is required but missing`);
    }
  }

  const diskNonJava = diskEntries.filter(
    (n) => !n.endsWith('.java') && fs.statSync(path.join(scenariosDir, n)).isFile(),
  );
  for (const name of diskNonJava) {
    if (!REQUIRED_SCENARIO_FIXTURES.includes(name)) {
      errors.push(
        `scenarios/${name} is not listed in REQUIRED_SCENARIO_FIXTURES (update assert-scenarios.mjs)`,
      );
    }
  }

  if (listMainBatchJava(scenarios, scenariosDir).length === 0) {
    errors.push('main Warden batch needs at least one non-isolated .java scenario');
  }

  return errors;
}

export function parseWardenJsonl(jsonlPath) {
  /** @type {Record<string, number>} */
  const fileMap = {};
  const allFindings = [];
  try {
    const raw = fs.readFileSync(jsonlPath, 'utf8').trim();
    if (!raw) return { fileMap, findings: [] };
    const records = raw
      .split('\n')
      .filter((l) => l.trim())
      .map((l) => JSON.parse(l));
    for (const record of records) {
      const file = record.chunk && record.chunk.file;
      if (!file) continue;
      const normalized = normalizeRepoPath(file);
      const recordFindings = record.findings || [];
      fileMap[normalized] = (fileMap[normalized] || 0) + recordFindings.length;
      for (const f of recordFindings) {
        allFindings.push({
          ...f,
          location: f.location || { path: normalized, startLine: 1 },
        });
      }
    }
  } catch (e) {
    console.error(
      'ERROR: Could not parse Warden output from ' + jsonlPath + ':',
      e.message,
    );
    process.exit(2);
  }
  return { fileMap, findings: allFindings };
}

export function routingSet(routingPath, id, jsonlPath) {
  const routing = JSON.parse(fs.readFileSync(routingPath, 'utf8'));
  routing[id] = jsonlPath;
  fs.writeFileSync(routingPath, JSON.stringify(routing));
}

function wardenFileForScenario(destPkg, scenario) {
  return scenario.file === ISOLATED_FILE_NOTICES
    ? ISOLATED_FILE_NOTICES
    : `${destPkg}/${scenario.file}`;
}

function loadRouting(routingPath) {
  /** @type {Record<string, string>} */
  let routing;
  try {
    routing = JSON.parse(fs.readFileSync(routingPath, 'utf8'));
  } catch (e) {
    console.error(`ERROR: Could not read routing file ${routingPath}:`, e.message);
    process.exit(2);
  }

  if (typeof routing.main !== 'string' || !routing.main) {
    console.error('ERROR: routing.json must include a non-empty "main" JSONL path.');
    process.exit(2);
  }
  return routing;
}

function cmdValidate(expectedPath, scenariosDir) {
  if (!expectedPath || !scenariosDir) {
    console.error(
      'Usage: node assert-scenarios.mjs validate <EXPECTED.json> <scenarios-dir>',
    );
    process.exit(2);
  }
  const errors = validateExpected(loadExpected(expectedPath), scenariosDir);
  if (errors.length > 0) {
    console.error('EXPECTED.json validation failed:');
    for (const err of errors) console.error(`  - ${err}`);
    process.exit(1);
  }
  console.log('EXPECTED.json OK');
}

function cmdListIsolated(expectedPath) {
  for (const s of listIsolated(loadExpected(expectedPath))) {
    process.stdout.write(`${s.id}\t${s.file}\n`);
  }
}

function cmdListMainJava(expectedPath, scenariosDir) {
  if (!expectedPath || !scenariosDir) {
    console.error(
      'Usage: node assert-scenarios.mjs list-main-java <EXPECTED.json> <scenarios-dir>',
    );
    process.exit(2);
  }
  for (const name of listMainBatchJava(loadExpected(expectedPath), scenariosDir)) {
    process.stdout.write(`${name}\n`);
  }
}

function cmdRoutingSet(routingPath, id, jsonlPath) {
  if (!routingPath || !id || !jsonlPath) {
    console.error(
      'Usage: node assert-scenarios.mjs routing-set <routing.json> <id> <jsonl-path>',
    );
    process.exit(2);
  }
  routingSet(routingPath, id, jsonlPath);
}

function cmdAssert(expectedPath, destPkg, routingPath) {
  if (!expectedPath || !destPkg || !routingPath) {
    console.error(
      'Usage: node assert-scenarios.mjs assert <EXPECTED.json> <dest-package-path> <routing.json>',
    );
    process.exit(2);
  }

  const routing = loadRouting(routingPath);
  const scenarios = loadExpected(expectedPath);

  /** @type {Record<string, ReturnType<typeof parseWardenJsonl>>} */
  const parsed = {};
  function getSource(id) {
    const jsonlPath = routing[id] ?? routing.main;
    if (!parsed[jsonlPath]) parsed[jsonlPath] = parseWardenJsonl(jsonlPath);
    return parsed[jsonlPath];
  }

  const GREEN = '\x1b[32m';
  const RED = '\x1b[31m';
  const RESET = '\x1b[0m';

  const failures = [];
  let pass = 0;

  for (const s of scenarios) {
    if (s.isolated && !routing[s.id]) {
      console.error(
        `ERROR: isolated scenario "${s.id}" has no routing entry (missing Warden run?)`,
      );
      process.exit(2);
    }

    const wardenFile = wardenFileForScenario(destPkg, s);
    const source = getSource(s.id);
    const count = findingCountForFile(source.fileMap, wardenFile);
    const passed = s.expectFinding ? count > 0 : count === 0;

    if (passed) {
      console.log(`${GREEN}PASS${RESET} ${s.id}`);
      pass++;
    } else {
      const reason = s.expectFinding
        ? 'expected finding (>= medium), got none'
        : `expected no finding (>= medium), got ${count}`;
      console.log(`${RED}FAIL${RESET} ${s.id}  (${reason})`);

      failures.push({
        id: s.id,
        findings: findingsForFile(source.findings, wardenFile),
      });
    }
  }

  const total = scenarios.length;
  console.log('');
  console.log(`${total} scenarios: ${pass} passed, ${total - pass} failed`);

  if (failures.length > 0) {
    console.log('');
    console.log('Warden output');
    console.log('══════════════════════');

    for (const { id, findings } of failures) {
      console.log('');
      console.log(id);
      console.log('-'.repeat(id.length));
      if (findings.length === 0) {
        console.log('(Warden produced no findings for this file)');
      } else {
        for (const f of findings) {
          console.log(f.title);
          if (f.description) console.log(f.description);
          if (f.verification) console.log('\nVerification: ' + f.verification);
          console.log('');
        }
      }
    }

    process.exit(1);
  }
}

function usage() {
  console.error(`Usage:
  node assert-scenarios.mjs validate <EXPECTED.json> <scenarios-dir>
  node assert-scenarios.mjs list-isolated <EXPECTED.json>
  node assert-scenarios.mjs list-main-java <EXPECTED.json> <scenarios-dir>
  node assert-scenarios.mjs routing-set <routing.json> <id> <jsonl-path>
  node assert-scenarios.mjs assert <EXPECTED.json> <dest-package-path> <routing.json>`);
  process.exit(2);
}

function main() {
  const [, , cmd, ...args] = process.argv;
  switch (cmd) {
    case 'validate':
      cmdValidate(args[0], args[1]);
      break;
    case 'list-isolated':
      if (!args[0]) usage();
      cmdListIsolated(args[0]);
      break;
    case 'list-main-java':
      cmdListMainJava(args[0], args[1]);
      break;
    case 'routing-set':
      cmdRoutingSet(args[0], args[1], args[2]);
      break;
    case 'assert':
      cmdAssert(args[0], args[1], args[2]);
      break;
    default:
      usage();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
