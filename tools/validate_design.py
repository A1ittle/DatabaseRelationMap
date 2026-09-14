"""Validate the design package, not the future application. Requires jsonschema."""
from pathlib import Path
import json,re,hashlib,copy,sys
from collections import deque
from jsonschema import Draft202012Validator,FormatChecker
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads((ROOT/p).read_text())
api=read('spec/v1/openapi.json');defs=api['components']['schemas']
def schema_for(n):return {'$schema':'https://json-schema.org/draft/2020-12/schema','$ref':'#/components/schemas/'+n,'components':{'schemas':defs}}
def validate(n,value):Draft202012Validator(schema_for(n),format_checker=FormatChecker()).validate(value)
checks=[]
official=read('evidence/openapi-3.1-official-schema.json')
Draft202012Validator(official,format_checker=FormatChecker()).validate(api)
checks.append('Official OpenAPI 3.1 document structure')
for name in defs:Draft202012Validator.check_schema(defs[name])
Draft202012Validator.check_schema(read('spec/v1/import.schema.json'))
checks.append('32 component schemas and import schema meta-validation')
for name,fn in [('ImportBatch','import.json'),('QueryResponse','query-response.json'),('PathResponse','path-response.json')]:validate(name,read('fixtures/v1/'+fn))
checks.append('3 contract examples validate')
batch=read('fixtures/v1/import.json');expected=read('fixtures/v1/expected.json')
def semantic(b):
 ids=[n['id'] for n in b['objects']];es=[e['id'] for e in b['relations']];ev=[x['id'] for x in b['evidence']]
 assert len(ids)==len(set(ids)) and len(es)==len(set(es)) and len(ev)==len(set(ev)),'Duplicate ID'
 assert len({(e['source'],e['target'],e['rel']) for e in b['relations']})==len(es),'Duplicate logical relation'
 for n in b['objects']:assert (n['javaKind'] is not None)==(n['type']=='java'),'javaKind condition'
 for e in b['relations']:
  assert e['source'] in ids and e['target'] in ids,'Dangling endpoint'
  assert set(e['evidenceIds'])<=set(ev),'Dangling evidence'
semantic(batch)
nodes={n['id']:n for n in batch['objects']};seed=expected['seedId']
usable=[e for e in batch['relations'] if nodes[e['source']]['type']!='java' and e['source']!=e['target']]
q=deque([seed]);hops={seed:0}
while q:
 u=q.popleft()
 for e in usable:
  if e['source']==u and e['target'] not in hops:hops[e['target']]=hops[u]+1;q.append(e['target'])
assert set(hops)==set(expected['reach']) and hops==expected['minHops']
reachable=[e for e in usable if e['source'] in hops and e['target'] in hops]
by_edge={e['id']:e for e in reachable};tree=set(expected['treeIds']);cross=set(expected['crossIds'])
assert not tree&cross and tree|cross==set(by_edge)
assert len(tree)==len(hops)-1
assert set(expected['parentEdgeIds'])==set(hops)-{seed}
for n,eid in expected['parentEdgeIds'].items():
 e=by_edge[eid];assert e['target']==n and eid in tree
 assert expected['rank'][n]==expected['rank'][e['source']]+1
 strength={'writes':3,'derives':2,'calls':1,'reads':0}
 candidates=[e for e in reachable if e['target']==n]
 best=sorted(candidates,key=lambda e:(-(expected['rank'][e['source']]+1),-strength[e['rel']],e['sourceOrder'],e['id']))[0]
 assert best['id']==eid
stats=expected['stats'];down=set(hops)-{seed}
assert stats['downstream']==len(down) and stats['crossEdges']==len(cross)
assert stats['byType']=={t:sum(nodes[n]['type']==t for n in down) for t in ['table','view','procedure','java']}
assert stats['javaTerminals']==sum(nodes[n]['type']=='java' for n in down)
assert stats['leaves']==sum(not any(e['source']==n for e in reachable) for n in down)
projection=read('fixtures/v1/query-response.json')['projection'];visible={n['object']['id'] for n in projection['nodes']}
assert {e['relation']['id'] for e in projection['edges']}=={e['id'] for e in reachable if e['source'] in visible and e['target'] in visible}
for edge in projection['edges']:assert edge['kind']==('tree' if edge['relation']['id'] in tree else 'cross')
path=read('fixtures/v1/path-response.json');assert len(path['edges'])==len(path['nodes'])-1
assert [n['id'] for n in path['nodes']]==expected['pathToJava']['nodeIds']
assert [e['relation']['id'] for e in path['edges']]==expected['pathToJava']['edgeIds']
for i,e in enumerate(path['edges']):assert (e['relation']['source'],e['relation']['target'])==(path['nodes'][i]['id'],path['nodes'][i+1]['id'])
checks.append('Manual fixture reach/rank/parent/partition/count/path/projection invariants')
negative=0
for mutation in ['bad_rel','duplicate_object','missing_endpoint']:
 b=copy.deepcopy(batch)
 if mutation=='bad_rel':b['relations'][0]['rel']='guessed'
 elif mutation=='duplicate_object':b['objects'].append(b['objects'][0])
 else:b['relations'][0]['target']='MISSING'
 try:validate('ImportBatch',b);semantic(b)
 except Exception:negative+=1
 else:raise AssertionError('Negative case not rejected: '+mutation)
checks.append(f'{negative} malformed examples rejected')
for base in ['reference/open-design','reference/offline-style-update']:
 for row in read(base+'/source-manifest.json')['files']:
  assert hashlib.sha256((ROOT/base/row['path']).read_bytes()).hexdigest()==row['sha256'],row['path']
checks.append('Preserved prototype file hashes')
for p in [ROOT/'README.md',ROOT/'HANDOFF.md',*sorted((ROOT/'docs').glob('*.md'))]:
 s=p.read_text();assert s.count('```')%2==0,('code fence',p)
 for target in re.findall(r'\]\(([^)]+)\)',s):
  if '://' in target or target.startswith('#'):continue
  assert (p.parent/target.split('#')[0]).exists(),(p,target)
 refs=dict(re.findall(r'^\[([^\]]+)\]:\s*(\S+)',s,re.M))
 for refid in re.findall(r'(?<!\!)\[([A-Z]+\d+)\](?![:(])',s):assert refid in refs,(p,refid)
checks.append('Design links, references and code fences')
print(json.dumps({'status':'PASS','openapiPaths':len(api['paths']),'schemas':len(defs),'checks':checks,'notValidated':['Application implementation','SQL migration execution','Real input lineage correctness','UI runtime and performance','Production OIDC and deployment']},ensure_ascii=False,indent=2))
