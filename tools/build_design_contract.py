"""Build design artifacts only: OpenAPI and import JSON Schema, no application runtime."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
S={}
def ref(n): return {'$ref':'#/components/schemas/'+n}
def arr(v,**kw): return dict(type='array',items=v,**kw)
def obj(p,required=None): return {'type':'object','additionalProperties':False,'properties':p,'required':list(p) if required is None else required}
def en(*v): return {'type':'string','enum':list(v)}
def st(**kw): return dict(type='string',**kw)
def num(**kw): return dict(type='integer',minimum=0,**kw)
def nullable(v):return {'anyOf':[v,{'type':'null'}]}
idtype=st(minLength=1,maxLength=200)
S['ObjectType']=en('table','view','procedure','java')
S['RelationType']=en('reads','writes','calls','derives')
S['EvidenceState']=en('observed','parsed','inferred','confirmed','unknown')
S['Object']=obj({'id':idtype,'type':ref('ObjectType'),'namespace':st(minLength=1,maxLength=300),'technicalName':st(minLength=1,maxLength=1000),'displayName':st(maxLength=1000),'system':st(minLength=1,maxLength=200),'owner':nullable(st(maxLength=200)),'javaKind':nullable(en('class','job','service'))})
S['Evidence']=obj({'id':idtype,'state':ref('EvidenceState'),'sourceRef':st(maxLength=2000),'observedAt':st(format='date-time'),'description':st(maxLength=4000)})
S['Relation']=obj({'id':idtype,'source':idtype,'target':idtype,'rel':ref('RelationType'),'sourceOrder':num(maximum=2147483647),'evidenceIds':arr(idtype,uniqueItems=True,minItems=1,maxItems=100)})
S['Coverage']=en('complete','partial','unknown')
S['ImportBatch']=obj({'batchKey':st(minLength=1,maxLength=200),'scopeId':idtype,'sourceVersion':st(minLength=1,maxLength=200),'capturedAt':st(format='date-time'),'coverage':ref('Coverage'),'mode':en('full'),'objects':arr(ref('Object'),minItems=1,maxItems=100000),'relations':arr(ref('Relation'),maxItems=500000),'evidence':arr(ref('Evidence'),maxItems=500000)})
S['Stats']=obj({'downstream':nullable(num()),'javaTerminals':nullable(num()),'leaves':nullable(num()),'crossEdges':nullable(num()),'byType':obj({k:nullable(num()) for k in ['table','view','procedure','java']}),'countStatus':en('exact','lower_bound','unknown')})
S['Meta']=obj({'requestId':idtype,'queryId':idtype,'snapshotId':idtype,'coverage':ref('Coverage'),'computationStatus':en('complete','incomplete'),'treeStatus':en('available','unavailable_cycle','unavailable_incomplete'),'algorithmVersion':st(),'expiresAt':st(format='date-time')})
S['QueryRequest']=obj({'seedId':idtype,'snapshotId':idtype},['seedId'])
S['QueryResponse']=obj({'meta':ref('Meta'),'seed':ref('Object'),'stats':ref('Stats'),'projection':nullable(ref('ProjectionResponse'))})
S['GraphNode']=obj({'object':ref('Object'),'minHops':num(),'layoutRank':nullable(num()),'parentEdgeId':nullable(idtype),'treeChildCount':nullable(num()),'crossCount':nullable(num())})
S['GraphEdge']=obj({'relation':ref('Relation'),'kind':en('tree','cross','unclassified')})
S['ProjectionRequest']=obj({'candidateIds':arr(idtype,minItems=1,maxItems=200,uniqueItems=True),'selectedId':idtype,'types':arr(ref('ObjectType'),maxItems=4,uniqueItems=True),'revealSelectedPath':{'type':'boolean'},'clientRevision':num()},['candidateIds','selectedId','types','revealSelectedPath','clientRevision'])
S['ProjectionResponse']=obj({'meta':ref('Meta'),'clientRevision':num(),'nodes':arr(ref('GraphNode'),maxItems=200),'edges':arr(ref('GraphEdge'),maxItems=2000),'matchedDownstream':nullable(num()),'renderLimited':{'type':'boolean'},'reasons':arr(en('TYPE_FILTER','NODE_BUDGET','EDGE_BUDGET','LAYOUT_UNAVAILABLE','PATH_REQUIRES_HIDDEN_TYPES'))})
S['PageInfo']=obj({'nextCursor':nullable(st(maxLength=4096)),'hasMore':{'type':'boolean'},'total':nullable(num())})
S['NodePage']=obj({'meta':ref('Meta'),'items':arr(ref('GraphNode'),maxItems=100),'page':ref('PageInfo')})
S['SearchHit']=obj({'object':ref('Object'),'action':en('reveal','recenter')})
S['SearchResponse']=obj({'requestId':idtype,'items':arr(ref('SearchHit'),maxItems=20),'page':ref('PageInfo')})
S['Cluster']=obj({'id':idtype,'layoutRank':nullable(num()),'type':ref('ObjectType'),'count':num()})
S['ClusterPage']=obj({'meta':ref('Meta'),'items':arr(ref('Cluster'),maxItems=100),'page':ref('PageInfo')})
S['NodeDetail']=obj({'meta':ref('Meta'),'node':ref('GraphNode'),'directDownstreamCount':num(),'canSetAsRoot':{'type':'boolean'}})
S['RelationPage']=obj({'meta':ref('Meta'),'items':arr(ref('GraphEdge'),maxItems=100),'endpointObjects':arr(ref('Object'),maxItems=200),'page':ref('PageInfo')})
S['PathResponse']=obj({'meta':ref('Meta'),'status':en('found','not_found','unknown'),'nodes':arr(ref('Object'),maxItems=256),'edges':arr(ref('GraphEdge'),maxItems=255),'reason':nullable(en('QUERY_INCOMPLETE','PATH_LENGTH_LIMIT'))})
S['EvidenceResponse']=obj({'meta':ref('Meta'),'relationId':idtype,'items':arr(ref('Evidence'),maxItems=100)})
S['Error']=obj({'code':en('INVALID_ARGUMENT','INVALID_SEED','UNAUTHENTICATED','FORBIDDEN','NOT_FOUND','LINEAGE_NOT_COLLECTED','QUERY_EXPIRED','POLICY_CHANGED','CURSOR_MISMATCH','PROJECTION_LIMIT','RATE_LIMITED','TEMPORARILY_UNAVAILABLE','IMPORT_INVALID','PUBLISH_CONFLICT','PAYLOAD_TOO_LARGE'),'message':st(),'requestId':idtype,'retryable':{'type':'boolean'}})
S['ImportResponse']=obj({'runId':idtype,'status':en('received','validating','ready','failed','published'),'snapshotId':nullable(idtype),'errorCount':num(),'warningCount':num()})
S['PublishRequest']=obj({'expectedActiveSnapshotId':nullable(idtype)})
S['PublishResponse']=obj({'snapshotId':idtype,'publishedAt':st(format='date-time')})
S['ErrorItem']=obj({'code':st(),'path':st(),'message':st(),'severity':en('error','warning')})
S['ImportDetail']=obj({'run':ref('ImportResponse'),'issues':arr(ref('ErrorItem'),maxItems=100),'page':ref('PageInfo')})
paths={}
def param(n,schema,where='query',required=False):return {'name':n,'in':where,'required':required,'schema':schema}
page=[param('cursor',st(maxLength=4096)),param('limit',{'type':'integer','minimum':1,'maximum':100,'default':50})]
types=param('types',arr(ref('ObjectType'),uniqueItems=True,maxItems=4))
def endpoint(path,method,op,summary,response,body=None,parameters=None,status='200',errors=None):
 pars=[param(n,idtype,'path',True) for n in __import__('re').findall(r'\{([^}]+)\}',path)]+(parameters or [])
 o={'operationId':op,'summary':summary,'responses':{status:{'description':summary+'成功','content':{'application/json':{'schema':ref(response)}}}}}
 if pars:o['parameters']=pars
 if body:o['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
 for c in errors or ['400','401','403','404','409','410','413','429','503']:o['responses'][c]={'description':'结构化错误；按错误码处理','content':{'application/json':{'schema':ref('Error')}}}
 paths.setdefault(path,{})[method]=o
endpoint('/lineage/search','get','searchObjects','检索合法表入口或当前查询下游','SearchResponse',parameters=[param('q',st(minLength=1,maxLength=200),required=True),param('queryId',idtype),param('cursor',st(maxLength=4096)),param('limit',{'type':'integer','minimum':1,'maximum':20,'default':10})])
endpoint('/lineage/queries','post','createQuery','创建固定快照查询','QueryResponse','QueryRequest',status='201')
endpoint('/lineage/queries/{qid}/projection','post','projectGraph','计算有界画布完整投影','ProjectionResponse','ProjectionRequest')
endpoint('/lineage/queries/{qid}/children','get','listTreeChildren','主树子对象分页，不返回画布增量','NodePage',parameters=[param('parentId',idtype,required=True),*page,types])
endpoint('/lineage/queries/{qid}/overview','get','listClusters','层级和类型聚合分页','ClusterPage',parameters=[*page,types])
endpoint('/lineage/queries/{qid}/clusters/{cid}/members','get','listClusterMembers','簇成员分页','NodePage',parameters=page)
endpoint('/lineage/queries/{qid}/impact','get','listImpact','授权可达下游清单','NodePage',parameters=[*page,types,param('system',st())])
endpoint('/lineage/queries/{qid}/nodes/{id}','get','getNode','对象详情','NodeDetail')
endpoint('/lineage/queries/{qid}/nodes/{id}/relations','get','listRelations','对象关系与端点分页','RelationPage',parameters=[*page,param('kind',en('all','tree','cross'),required=True)])
endpoint('/lineage/queries/{qid}/path','get','getPath','一条最短下游路径','PathResponse',parameters=[param('targetId',idtype,required=True)])
endpoint('/lineage/queries/{qid}/relations/{rid}/evidence','get','getEvidence','关系证据','EvidenceResponse')
endpoint('/imports','post','createImport','提交全量来源批次','ImportResponse','ImportBatch',status='202')
endpoint('/imports/{runId}','get','getImport','导入状态与问题分页','ImportDetail',parameters=page)
endpoint('/imports/{runId}/publish','post','publishImport','原子发布快照','PublishResponse','PublishRequest')
api={'openapi':'3.1.0','info':{'title':'程序血缘地图 V1 API 设计契约','version':'1.0.0','description':'设计契约，尚无服务实现。应用约束见 docs/07-implementation-design.md；对象授权、ID 唯一、边端点、分页闭包需语义验收。'},'servers':[{'url':'/api'}],'security':[{'sessionCookie':[]}],'paths':paths,'components':{'securitySchemes':{'sessionCookie':{'type':'apiKey','in':'cookie','name':'LINEAGE_SESSION','description':'服务端 OIDC 会话。写入 imports/publish 另需 CSRF token 和 INGESTOR 权限；POST 查询端点仅创建只读临时上下文。'}},'schemas':S}}
for path in ['/imports','/imports/{runId}/publish']:
 paths[path]['post'].setdefault('parameters',[]).append(param('X-CSRF-Token',st(minLength=1),'header',True))
(ROOT/'spec/v1/openapi.json').write_text(json.dumps(api,ensure_ascii=False,indent=2)+'\n')
def replace(v):
 if isinstance(v,dict):return {k:(val.replace('#/components/schemas/','#/$defs/') if k=='$ref' else replace(val)) for k,val in v.items()}
 if isinstance(v,list):return [replace(x) for x in v]
 return v
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','$id':'urn:program-lineage:import:v1','$ref':'#/$defs/ImportBatch','$defs':replace(S)}
(ROOT/'spec/v1/import.schema.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n')
print('Generated',len(paths),'paths,',len(S),'schemas')
