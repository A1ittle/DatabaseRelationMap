from pathlib import Path
import json,copy
r=Path(__file__).resolve().parents[1]
def obj(i,t):return {'id':i,'type':t,'namespace':'demo/prod/core','technicalName':i,'displayName':i,'system':'demo','owner':None,'javaKind':'class' if t=='java' else None}
def edge(i,s,t,rel):return {'id':i,'source':s,'target':t,'rel':rel,'sourceOrder':0,'evidenceIds':['ev-demo']}
nodes=[obj('root','table'),obj('view-a','view'),obj('proc-b','procedure'),obj('table-c','table'),obj('java-j','java'),obj('external-x','table'),obj('proc-s','procedure')]
edges=[edge('e01','root','view-a','reads'),edge('e02','root','view-a','derives'),edge('e03','view-a','proc-b','reads'),edge('e04','proc-b','table-c','writes'),edge('e05','table-c','java-j','reads'),edge('e06','view-a','java-j','reads'),edge('e07','root','java-j','reads'),edge('e08','java-j','external-x','writes'),edge('e09','external-x','view-a','reads'),edge('e10','root','proc-s','reads'),edge('e11','proc-s','table-c','writes')]
batch={'batchKey':'demo-001','scopeId':'demo','sourceVersion':'fixture-v1','capturedAt':'2026-09-14T00:00:00Z','coverage':'complete','mode':'full','objects':nodes,'relations':edges,'evidence':[{'id':'ev-demo','state':'unknown','sourceRef':'fixture:manually-authored','observedAt':'2026-09-14T00:00:00Z','description':'Synthetic design example, not real parsed lineage.'}]}
expected={'seedId':'root','reach':['root','view-a','proc-b','table-c','java-j','proc-s'],'treeIds':['e02','e03','e04','e05','e10'],'crossIds':['e01','e06','e07','e11'],'excludedRelationIds':['e08','e09'],'rank':{'root':0,'view-a':1,'proc-b':2,'table-c':3,'java-j':4,'proc-s':1},'minHops':{'root':0,'view-a':1,'proc-b':2,'table-c':2,'java-j':1,'proc-s':1},'parentEdgeIds':{'view-a':'e02','proc-b':'e03','table-c':'e04','java-j':'e05','proc-s':'e10'},'stats':{'downstream':5,'javaTerminals':1,'leaves':1,'crossEdges':4,'byType':{'table':1,'view':1,'procedure':2,'java':1},'countStatus':'exact'},'pathToJava':{'nodeIds':['root','java-j'],'edgeIds':['e07']}}
meta={'requestId':'request-demo','queryId':'query-demo','snapshotId':'snapshot-demo','coverage':'complete','computationStatus':'complete','treeStatus':'available','algorithmVersion':'lineage-tree-v1','expiresAt':'2026-09-14T01:00:00Z'}
parent=expected['parentEdgeIds'];rank=expected['rank'];hops=expected['minHops']
tree={e['target']:e for e in edges if e['id'] in expected['treeIds']}
def gn(n):
 i=n['id'];return {'object':n,'minHops':hops[i],'layoutRank':rank[i],'parentEdgeId':parent.get(i),'treeChildCount':sum(e['source']==i for e in edges if e['id'] in expected['treeIds']),'crossCount':sum(e['source']==i or e['target']==i for e in edges if e['id'] in expected['crossIds'])}
initial={'root','view-a','proc-s'}
projection={'meta':meta,'clientRevision':0,'nodes':[gn(n) for n in nodes if n['id'] in initial],'edges':[{'relation':e,'kind':'tree' if e['id'] in expected['treeIds'] else 'cross'} for e in edges if e['source'] in initial and e['target'] in initial],'matchedDownstream':5,'renderLimited':False,'reasons':[]}
query={'meta':meta,'seed':nodes[0],'stats':expected['stats'],'projection':projection}
path={'meta':meta,'status':'found','nodes':[nodes[0],nodes[4]],'edges':[{'relation':edges[6],'kind':'cross'}],'reason':None}
for name,v in [('import.json',batch),('expected.json',expected),('query-response.json',query),('path-response.json',path)]:
 (r/'fixtures/v1'/name).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
print('Created 4 manually specified design examples')
