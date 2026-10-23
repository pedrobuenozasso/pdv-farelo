#!/usr/bin/env bash
set -euo pipefail

docker run --rm \
  -v /opt/farelo-os/current/apps/web:/work:ro \
  -w /work node:24-alpine \
  npm audit --omit=dev --json > /root/farelo-web-npm-audit.json || true

python3 - <<'PY'
import json

with open('/root/farelo-web-npm-audit.json', encoding='utf-8') as report:
    audit = json.load(report)

if audit.get('error'):
    print('Audit error:', audit['error'].get('message'))
else:
    print('Runtime vulnerability counts:', audit.get('metadata', {}).get('vulnerabilities'))
    for name, finding in audit.get('vulnerabilities', {}).items():
        print(name, finding.get('severity'), 'fix available:', finding.get('fixAvailable'))
PY
