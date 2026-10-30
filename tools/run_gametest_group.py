#!/usr/bin/env python3
"""Run and verify one group in an isolated world. No source manifest edits."""
from pathlib import Path
import argparse,json,re,subprocess,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--group',required=True);p.add_argument('--seed',type=int,default=17)
a=p.parse_args();groups=json.loads((ROOT/'tools/gametest-groups.json').read_text());classes=groups[a.group]
run=ROOT/'build/gametest'/f'{a.group}-{a.seed}'
if (run/'world').exists():raise SystemExit(f'Refusing to reuse test world {run}')
run.mkdir(parents=True,exist_ok=True)
expected=set()
for name in classes:
    source=(ROOT/'src/testmod/java/dev/aiplayer/test'/f'{name}.java').read_text()
    expected.update((name+'.'+method).lower() for method in re.findall(r'@GameTest\b[\s\S]*?public void (\w+)\(TestContext',source))
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
(run/'manifest.json').write_text(json.dumps({'group':a.group,'seed':a.seed,'revision':revision,'expected':sorted(expected)},indent=2)+'\n')
result=subprocess.run(['bash','gradlew','runGametest','--no-daemon',f'-PgameTestClasses={",".join(classes)}',f'-PscenarioSeed={a.seed}',f'-PgametestRunDir={run.relative_to(ROOT)}'],cwd=ROOT)
report=run/'reports/gametest-results.xml'
if not report.exists():raise SystemExit('Missing GameTest XML report')
cases=list(ET.parse(report).iter('testcase'));names=[c.attrib['name'].lower() for c in cases]
failures=[c.attrib['name'] for c in cases if c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None]
if set(names)!=expected or len(names)!=len(expected) or failures or result.returncode:
    raise SystemExit(f'Invalid group: missing={expected-set(names)}, unexpected={set(names)-expected}, failed={failures}, exit={result.returncode}')
print(f'{a.group}, seed {a.seed}: {len(names)} unique tests passed')
