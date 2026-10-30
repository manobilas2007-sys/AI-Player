#!/usr/bin/env python3
"""Aggregate exact named results; repeated seeds never inflate coverage."""
from pathlib import Path
import argparse,collections,json,re,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('reports',type=Path);a=p.parse_args()
groups=json.loads((ROOT/'tools/gametest-groups.json').read_text())
expected={}
for group,classes in groups.items():
    expected[group]={name.lower()+'.'+method.lower() for name in classes for method in re.findall(r'@GameTest\b[\s\S]*?public void (\w+)\(TestContext',(ROOT/'src/testmod/java/dev/aiplayer/test'/f'{name}.java').read_text())}
all_names=set().union(*(v for k,v in expected.items() if k!='critical'))
baseline=set(json.loads((ROOT/'tools/gametest-baseline-v063.json').read_text()))
assert len(baseline)==185 and baseline<=all_names,'Original 185 names must be preserved'
assert len(all_names)>=370 and len(all_names-baseline)>=185,'Need at least 185 distinct new cases'
seen=set();primary=[];revisions=set()
for manifest in a.reports.rglob('manifest.json'):
    m=json.loads(manifest.read_text());key=(m['group'],m['seed']);assert key not in seen,('Duplicate group',key);seen.add(key);revisions.add(m['revision'])
    report=manifest.parent/'reports/gametest-results.xml';assert report.exists(),('Missing report',report)
    cases=list(ET.parse(report).iter('testcase'));names=[c.attrib['name'].lower() for c in cases]
    assert set(names)==expected[m['group']] and len(names)==len(set(names)),('Missing/duplicate/unexpected tests',key)
    assert not any(c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None for c in cases),('Failed or skipped test',key)
    if m['group']!='critical':primary+=names
required={(g,17) for g in groups if g!='critical'}|{('critical',s) for s in (17,104729,8675309)}
assert seen==required,('Missing/unexpected groups',required-seen,seen-required)
assert len(revisions)==1,'Reports came from different source revisions'
assert set(primary)==all_names and len(primary)==len(all_names),'Coverage counted twice or omitted'
summary={'revision':next(iter(revisions)),'unique_tests':len(all_names),'preserved':len(baseline),'new':len(all_names-baseline),'critical_per_seed':len(expected['critical']),'seeds':[17,104729,8675309],'failures':0}
(a.reports/'VERIFIED.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
