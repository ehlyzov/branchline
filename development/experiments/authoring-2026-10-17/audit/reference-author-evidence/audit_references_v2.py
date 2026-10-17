from pathlib import Path
import concurrent.futures
import importlib.util
import json
import re
import sys

repo=Path('/Users/eugene/repo/research/branchline-public')
base=Path('/tmp/branchline-reference-audit-v2');base.mkdir(exist_ok=True)
evaluator=repo/'development/experiments/authoring-2026-10-17/evaluator/evaluate.py'
spec=importlib.util.spec_from_file_location('reference_evaluator',evaluator)
e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
refs=repo/'development/experiments/authoring-2026-10-17/references'
blocks={lang:re.findall('```'+lang+r'\n(.*?)```',(refs/(lang+'.md')).read_text(),re.S) for lang in ('branchline','jsonata','kotlin')}
tests=[]
def add(lang,name,source,cases):
 tests.append((lang,name,source,[{'input':i,'expected':o} for i,o in cases]))
def bl(name,body,output,cases,prelude=''):
 add('branchline',name,prelude+'\nTRANSFORM Main {\n'+body+'\nOUTPUT '+output+'\n}',cases)
def kt(name,body,output,cases,prelude=''):
 add('kotlin',name,prelude+'\nfun transform(input: Any?): Any? {\n'+body+'\nreturn '+output+'\n}',cases)
def ja(name,expression,cases):add('jsonata',name,expression,cases)

add('branchline','fence-1',blocks['branchline'][0],[({'label':' Ab C '},{'label':'ab c'}),({}, {'label':''})])
bl('fence-2',blocks['branchline'][1],'{code: code, first: first}',[({}, {'code':'unknown','first':None}),({'child':{'code':'q'},'values':[4]},{'code':'q','first':4})])
bl('fence-3',blocks['branchline'][2],'{text: isText, list: isList, number: isNumeric}',[({'value':v},{'text':t,'list':l,'number':n}) for v,t,l,n in [(None,False,False,False),(True,False,False,False),(3,False,False,True),('a',True,False,False),([],False,True,False),({},False,False,False)]])
bl('fence-4',blocks['branchline'][3],'{category: category}',[({'score':n},{'category':s}) for n,s in [(9,'low'),(10,'equal'),(11,'high')]])
bl('fence-5-corrected-object-lambda',blocks['branchline'][4],'{doubled: doubled, positive: positive, total: total, positions: positions, squares: squares}',[({'values':[-2,0,3]},{'doubled':[-4,0,6],'positive':[3],'total':1,'positions':[{'index':0,'value':-2},{'index':1,'value':0},{'index':2,'value':3}],'squares':[9]}),({}, {'doubled':[],'positive':[],'total':0,'positions':[],'squares':[]})])
add('branchline','fence-6','TRANSFORM Main {\n'+blocks['branchline'][5]+'\n}',[({'values':[2,5]},{'entries':[{'value':2},{'value':5}]}),({}, {'entries':[]})])
rows=[{'kind':'u','code':'a','rank':2},{'kind':'v','code':'b','rank':1},{'kind':'u','code':'c','rank':2}]
bl('fence-7',blocks['branchline'][6],'{grouped: grouped}',[({'rows':rows},{'grouped':{'u':[rows[0],rows[2]],'v':[rows[1]]}}),({}, {'grouped':{}})])
bl('fence-8',blocks['branchline'][7],'{ordered: ordered}',[({'rows':rows},{'ordered':[rows[1],rows[0],rows[2]]}),({}, {'ordered':[]})])
bl('inline-if-object-lambda-helper', '''LET choice = ""
IF input.enabled THEN { SET choice = "on" } ELSE { SET choice = "off" }
LET objects = MAP([{field: "q"}], (x) -> ({field: x.field}))
LET blockObjects = MAP([{field: "q"}], (x) -> { RETURN {field: x.field} })''','{choice: choice, objects: objects, blockObjects: blockObjects, next: Next(4), blockNext: BlockNext(4), recovered: TRY INT("x") CATCH(err) => 7}',[({'enabled':True},{'choice':'on','objects':[{'field':'q'}],'blockObjects':[{'field':'q'}],'next':5,'blockNext':5,'recovered':7}),({'enabled':False},{'choice':'off','objects':[{'field':'q'}],'blockObjects':[{'field':'q'}],'next':5,'blockNext':5,'recovered':7})], 'FUNC Next(x) = x + 1\nFUNC BlockNext(x) { RETURN x + 1 }')
bl('inline-core-collections','''LET obj = {k: NULL, code: "q", amount: 3}
LET xs = [2, 5]
LET rows = [{code: "q", value: 1}, {code: "q", value: 2}]
LET table = REDUCE(rows, {}, (acc, row) -> PUT(acc, row.code, row))
LET wanted = "q"''','''{literal: {code: "q", amount: 3}, path: input.child.code, quoted: input["some-key"], position: xs[0], missing: GET(obj,"absent",8), presentNull: GET(obj,"k",8), nullDefault: GET(obj,"k",8) ?? 8, present: SOME(KEYS(obj),(k) -> k == "k"), obj: IS_OBJECT(obj), objectFunction: IS_FUNCTION((x) -> x + 1), nullList: LISTIFY(NULL), objectList: LISTIFY({code:"q"}), listList: LISTIFY([7]), wrap: [7], keys: KEYS(xs), values: VALUES({code:"q"}), entries: ENTRIES({code:"q"}), put: PUT([2],1,5), deleted: DELETE({code:"q",extra:4},"extra"), appended: APPEND([2],[5]), prepended: PREPEND([2],5), flattened: FLATTEN([[2],[5],8]), lookup: GET(table,"q",NULL), found: FIND(rows,(row) -> row.code == wanted), distinct: DISTINCT([2,5,2]), sorted: SORT([5,2]), reverse: REVERSE([2,5]), zip: ZIP([2,5],[7]), range: RANGE(1,3), length: LENGTH(NULL), count: COUNT({code:"q"}), emptySum: SUM([]), emptyAvg: AVG([]), emptyMin: MIN([]), emptyMax: MAX([]), any: SOME([], (x) -> TRUE), all: EVERY([], (x) -> FALSE)}''',[({'child':{'code':'q'},'some-key':'r'},{'literal':{'code':'q','amount':3},'path':'q','quoted':'r','position':2,'missing':8,'presentNull':None,'nullDefault':8,'present':True,'obj':True,'objectFunction':True,'nullList':[],'objectList':[{'code':'q'}],'listList':[7],'wrap':[7],'keys':[0,1],'values':['q'],'entries':[{'key':'code','value':'q'}],'put':[2,5],'deleted':{'code':'q'},'appended':[2,[5]],'prepended':[5,2],'flattened':[2,5,8],'lookup':{'code':'q','value':2},'found':{'code':'q','value':1},'distinct':[2,5],'sorted':[2,5],'reverse':[5,2],'zip':[[2,7]],'range':[1,2,3],'length':0,'count':1,'emptySum':0,'emptyAvg':None,'emptyMin':None,'emptyMax':None,'any':False,'all':True})], 'FUNC Next(x) = x + 1')
bl('inline-text-number-operators','', '''{lower: LOWER("Ab"), upper: UPPER(TRIM(input.label ?? "")), trim: TRIM(" a  b "), substring: SUBSTRING("abc",1,1), contains: CONTAINS("abc","b"), split: SPLIT("a:b",":"), joined: JOIN(["a","b"],":"), replaced: REPLACE("a1b2","[0-9]","x"), match: MATCH("a1b2","[0-9]"), stringNull: STRING(NULL), number: NUMBER("3"), integer: INT("3"), digits: PARSE_INT("x3y"), precise: DEC("1.25") + DEC("1.25"), big: DEC("17"), roundedEven: ROUND(6.5,0), roundedOdd: ROUND(7.5,0), quotient: -7 // 3, remainder: -7 % 3, division: 7 / 2, abs: ABS(-3), floor: FLOOR(-1.2), ceil: CEIL(-1.2), zero: 0 ?? 9, bool: FALSE ?? TRUE, empty: "" ?? "x", arithmetic: (2 + 3) * 4 - 1, comparison: (2 < 3) && !(2 == 3), textConcat: "a" + "b", nested: [[7]]}''',[({'label':' ab '},{'lower':'ab','upper':'AB','trim':'a  b','substring':'b','contains':True,'split':['a','b'],'joined':'a:b','replaced':'axbx','match':['1','2'],'stringNull':None,'number':3,'integer':3,'digits':3,'precise':2.5,'big':17,'roundedEven':6,'roundedOdd':8,'quotient':-2,'remainder':-1,'division':3.5,'abs':3,'floor':-2,'ceil':-1,'zero':0,'bool':False,'empty':'','arithmetic':19,'comparison':True,'textConcat':'ab','nested':[[7]]})])

ja('fence-1',blocks['jsonata'][0],[({'label':' Ab C '},{'label':'ab c'})])
ja('fence-2',blocks['jsonata'][1],[({}, {'missingOnly':9,'missingOrNull':9}),({'settings':{'level':None}},{'missingOnly':None,'missingOrNull':9}),({'settings':{'level':0}},{'missingOnly':0,'missingOrNull':0})])
ja('fence-3','('+blocks['jsonata'][2]+'; {"values":$xs})',[({}, {'values':[]}),({'values':None},{'values':[]}),({'values':[4]},{'values':[4]})])
ja('fence-4','($rows := rows; '+blocks['jsonata'][3]+')',[({'rows':rows},{'u':[rows[0],rows[2]],'v':[rows[1]]}),({'rows':[]},{})])
ja('fence-5','($rows := rows; '+blocks['jsonata'][4]+')',[({'rows':rows},[rows[1],rows[0],rows[2]]),({'rows':[]},[]),({'rows':[rows[0]]},[rows[0]])])
ja('inline-collections-and-kinds','''($xs := [4]; $rows := rows; $acc := []; $x := 4; $key := "q";
{"missing": {"value": missing}, "type":$type(null), "types":[$map([null,true,3,"a",[],{}],$type)], "existsNull":$exists(null), "booleanEmpty":$boolean([]), "mapEmpty":[$map([],function($x){$x+1})], "mapSingle":[$map($xs,function($x){$x+1})], "filter":[$filter([1,2],function($x){$x>1})], "nested":[$map([4,8],function($x){[$x,$x+1]})], "extraNested":[[4]], "reduce":$reduce([2,5],function($a,$x){$a+$x},0), "range":[0..3], "emptyRange":[1..0], "append":$append($acc,[[$x,$x+1]]), "dynamic":{$key:4}, "index":$merge([$map($rows,function($r){{$r.code:$r}})]), "group":rows{kind:$sum(rank)}, "sort":$sort([2,1]), "order":rows^(<rank, >code), "keys":[$keys({"q":4})], "lookup":$lookup({"q":4},"q"), "each":[$each({"q":4},function($v,$k){{$k:$v}})], "sift":$sift({"q":4,"r":0},function($v){$v>0}), "spread":[$spread({"q":4})], "distinct":$distinct([1,2,1]), "reverse":$reverse([1,2]), "zip":$zip([1,2],[4]), "join":$join(["a","b"],":"), "sum":$sum([]), "count":$count([]), "first":rows[code="a"][0], "position":values[0], "negative":values[-1], "quoted":settings.`some-key`})''',[({'rows':rows,'values':[4,8],'settings':{'some-key':'z'}},{'missing':{},'type':'null','types':['null','boolean','number','string','array','object'],'existsNull':True,'booleanEmpty':False,'mapEmpty':[],'mapSingle':[5],'filter':[2],'nested':[[4,5],[8,9]],'extraNested':[[4]],'reduce':7,'range':[0,1,2,3],'emptyRange':[],'append':[[4,5]],'dynamic':{'q':4},'index':{'a':rows[0],'b':rows[1],'c':rows[2]},'group':{'u':4,'v':1},'sort':[1,2],'order':[rows[1],rows[2],rows[0]],'keys':['q'],'lookup':4,'each':[{'q':4}],'sift':{'q':4},'spread':[{'q':4}],'distinct':[1,2],'reverse':[2,1],'zip':[[1,4]],'join':'a:b','sum':0,'count':0,'first':rows[0],'position':4,'negative':8,'quoted':'z'})])
ja('inline-text-numbers','''($a := -7; $b := 3;
{"lower":$lowercase("Ab"),"upper":$uppercase("Ab"),"trim":$trim(" a  b "),"substring":$substring("abc",1,1),"contains":$contains("abc","b"),"split":$split("a:b",":"),"replace":$replace("aba","a","x"),"string":$string({"q":4}),"number":$number("3"),"floorDivision":$floor($a/$b),"truncDivision":$a/$b<0?$ceil($a/$b):$floor($a/$b),"remainder":$a%$b,"roundEven":$round(6.5),"roundOdd":$round(7.5),"abs":$abs(-3),"floor":$floor(-1.2),"ceil":$ceil(-1.2),"power":$power(2,3),"sqrt":$sqrt(4),"root":$$.value,"concat":"a"&"b","condition":(3>2 and $not(false))?"on":"off"})''',[({'value':4},{'lower':'ab','upper':'AB','trim':'a b','substring':'b','contains':True,'split':['a','b'],'replace':'xbx','string':'{"q":4}','number':3,'floorDivision':-3,'truncDivision':-2,'remainder':-1,'roundEven':6,'roundOdd':8,'abs':3,'floor':-2,'ceil':-1,'power':8,'sqrt':2,'root':4,'concat':'ab','condition':'on'})])

add('kotlin','fence-1',blocks['kotlin'][0],[({'label':' Ab C '},{'label':'ab c'}),({}, {'label':''})])
kt('fence-2',blocks['kotlin'][1],'mapOf("code" to code,"first" to first)',[({}, {'code':'unknown','first':None}),({'child':{'code':'q'},'values':[4]},{'code':'q','first':4})])
kt('fence-3','val root = input as Map<*, *>\n'+blocks['kotlin'][2],'out',[({}, {'code':'q'}),({'note':None},{'code':'q','note':None})])
kt('fence-4','val root=input as Map<*, *>\nval score=(root["score"] as Number).toLong()\nval enabled=root["enabled"] as Boolean\n'+blocks['kotlin'][3],'mapOf("category" to category,"label" to label)',[({'score':n,'enabled':enabled},{'category':cat,'label':'on' if enabled else 'off'}) for n,cat,enabled in [(9,'low',False),(10,'equal',True),(11,'high',True)]])
kt('fence-5',blocks['kotlin'][4],'mapOf("doubled" to doubled,"positive" to positive,"total" to total,"indexed" to indexed)',[({}, {'doubled':[4,10,16],'positive':[2,5,8],'total':15,'indexed':[{'index':0,'value':2},{'index':1,'value':5},{'index':2,'value':8}]})])
kt('fence-6','val root=input as Map<*, *>\n'+blocks['kotlin'][5],'mapOf("values" to accumulated)',[({'rows':[4,8]},{'values':[{'index':0,'value':4},{'index':1,'value':8}]}),({}, {'values':[]})])
kt('fence-7','val root=input as Map<*, *>\n'+blocks['kotlin'][6],'mapOf("groups" to groups,"index" to index,"wanted" to wanted)',[({'rows':rows},{'groups':{'u':[rows[0],rows[2]],'v':[rows[1]]},'index':{'a':rows[0],'b':rows[1],'c':rows[2]},'wanted':None}),({'rows':[]},{'groups':{},'index':{},'wanted':None})])
rows_kt=[dict(r,label='x') for r in rows]
kt('fence-8','val rows=(input as Map<*, *>)["rows"] as List<Map<*, *>>\n'+blocks['kotlin'][7],'mapOf("ordered" to ordered,"descending" to descending)',[({'rows':rows_kt},{'ordered':[rows_kt[1],rows_kt[0],rows_kt[2]],'descending':[rows_kt[0],rows_kt[2],rows_kt[1]]}),({'rows':[]},{'ordered':[],'descending':[]})])
kt('inline-values-kinds-null','''val obj=mapOf<String,Any?>("k" to null)
val fallback=8
val xs=listOf(2L,5L)
val accumulated=mutableListOf<Any?>()
accumulated.add(listOf(7L))
accumulated.addAll(listOf(8L))
val index=3
val original=input
''','''mapOf("contains" to obj.containsKey("k"),"missingDefault" to obj.getOrDefault("absent",fallback),"presentNull" to obj.getOrDefault("k",fallback),"zero" to (0L?:9L),"empty" to "".isNotEmpty(),"literal" to mapOf("code" to "q","amount" to 3L),"single" to listOf(7L),"nested" to listOf(listOf(7L)),"emptyList" to emptyList<Any?>(),"emptyMap" to emptyMap<String,Any?>(),"mappedEmpty" to emptyList<Long>().map{it+1},"mappedSingle" to listOf(4L).map{it+1},"mapNotNull" to listOf(null,4).mapNotNull{it},"flatten" to listOf(listOf(2),listOf(5)).flatten(),"flatMap" to listOf(2,5).flatMap{listOf(it,it+1)},"accumulated" to accumulated,"range" to (0 until 0).toList(),"inclusive" to (0..2).toList(),"fold" to emptyList<Long>().fold(0L){a,x->a+x},"sum" to xs.sum(),"sumOf" to xs.sumOf{it},"count" to xs.count(),"any" to emptyList<Long>().any{true},"all" to emptyList<Long>().all{false},"none" to xs.none{it<0},"first" to xs.firstOrNull(),"find" to xs.find{it>3},"min" to xs.minOrNull(),"max" to xs.maxOrNull(),"interpolation" to "item-$index","isMap" to (original is Map<*, *>),"safeCast" to (original as? List<*>),"helper" to Next(4L))''',[({}, {'contains':True,'missingDefault':8,'presentNull':None,'zero':0,'empty':False,'literal':{'code':'q','amount':3},'single':[7],'nested':[[7]],'emptyList':[],'emptyMap':{},'mappedEmpty':[],'mappedSingle':[5],'mapNotNull':[4],'flatten':[2,5],'flatMap':[2,3,5,6],'accumulated':[[7],8],'range':[],'inclusive':[0,1,2],'fold':0,'sum':7,'sumOf':7,'count':2,'any':False,'all':True,'none':True,'first':2,'find':5,'min':2,'max':5,'interpolation':'item-3','isMap':True,'safeCast':None,'helper':5})],'fun Next(x: Long): Long = x + 1')
kt('inline-object-operations','''val obj=mapOf("q" to 4L,"r" to 0L)
val m=obj.toMutableMap()
m["z"]=3L
m.put("w",2L)
m.remove("r")
val groups=mutableMapOf<String,MutableList<Long>?>()
groups["q"]=null
groups.getOrPut("q"){mutableListOf()}!!.add(4L)
''','''mapOf("keys" to obj.keys.toList(),"values" to obj.values.toList(),"entries" to obj.entries.map{mapOf("key" to it.key,"value" to it.value)},"mapped" to obj.mapValues{(key,value)->value+1},"filterKeys" to obj.filterKeys{it=="q"},"filterValues" to obj.filterValues{it>0},"filter" to obj.filter{it.value>0},"mutable" to m,"groups" to groups,"distinct" to listOf(1,2,1).distinct(),"distinctBy" to listOf("a","ab","b").distinctBy{it.length},"sorted" to listOf(2,1).sorted(),"sortedDesc" to listOf(1,2).sortedDescending(),"reversed" to listOf(1,2).reversed())''',[({}, {'keys':['q','r'],'values':[4,0],'entries':[{'key':'q','value':4},{'key':'r','value':0}],'mapped':{'q':5,'r':1},'filterKeys':{'q':4},'filterValues':{'q':4},'filter':{'q':4},'mutable':{'q':4,'z':3,'w':2},'groups':{'q':[4]},'distinct':[1,2],'distinctBy':['a','ab'],'sorted':[1,2],'sortedDesc':[2,1],'reversed':[2,1]})])
kt('inline-text-numbers','val text=" a  b "\nval value:Any?=null','''mapOf("trim" to text.trim(),"lower" to "Ab".lowercase(),"upper" to "Ab".uppercase(),"contains" to text.contains("a"),"starts" to text.startsWith(" "),"ends" to text.endsWith(" "),"substring" to "abc".substring(1,2),"split" to "a:b".split(":"),"join" to listOf("a","b").joinToString(":"),"literalReplace" to "aba".replace("a","x"),"regexReplace" to "a1b2".replace(Regex("[0-9]"),"x"),"nullString" to value.toString(),"longParse" to "3".toLongOrNull(),"badParse" to "x".toLongOrNull(),"doubleParse" to "3.5".toDoubleOrNull(),"quotient" to (-7L/3L),"remainder" to (-7L%3L),"division" to (7.0/3.0),"floor" to floor(-1.2),"ceil" to ceil(-1.2),"abs" to abs(-3L),"roundEven" to round(6.5),"roundOdd" to round(7.5),"roundToLong" to 6.5.roundToLong(),"decimal" to BigDecimal("6.5").setScale(0,RoundingMode.HALF_EVEN),"bigInteger" to BigInteger("17"),"compareDecimal" to BigDecimal("1.0").compareTo(BigDecimal("1.00")),"decimalEquals" to BigDecimal("1.0").equals(BigDecimal("1.00")),"power" to 2.0.pow(3),"sqrt" to sqrt(4.0))''',[({}, {'trim':'a  b','lower':'ab','upper':'AB','contains':True,'starts':True,'ends':True,'substring':'b','split':['a','b'],'join':'a:b','literalReplace':'xbx','regexReplace':'axbx','nullString':'null','longParse':3,'badParse':None,'doubleParse':3.5,'quotient':-2,'remainder':-1,'division':7/3,'floor':-2,'ceil':-1,'abs':3,'roundEven':6,'roundOdd':8,'roundToLong':7,'decimal':6,'bigInteger':17,'compareDecimal':0,'decimalEquals':False,'power':8,'sqrt':2})])

# Keep a separately executed negative control for each language's failure feedback.
add('branchline','negative-original-if','TRANSFORM Main {\nIF input.enabled { OUTPUT {value: 1} } ELSE { OUTPUT {value: 0} }\n}',[({'enabled':True},{'value':1})])
add('branchline','negative-original-object-lambda','TRANSFORM Main {\nOUTPUT {rows: MAP([{field: "q"}], (x) -> {field: x.field})}\n}',[({}, {'rows':[{'field':'q'}]})])
add('jsonata','negative-wrong-type','$number("x")',[({},0)])
add('kotlin','negative-wrong-type','fun transform(input: Any?): Any? = "x".toLong()',[({},0)])

def run(test):
 lang,name,source,cases=test
 folder=base/lang/name;folder.mkdir(parents=True,exist_ok=True)
 (folder/'source').write_text(source+'\n');(folder/'cases.json').write_text(json.dumps(cases))
 code=e.evaluate(lang,folder/'source',folder/'cases.json',folder/'result.json')
 result=json.loads((folder/'result.json').read_text())
 ok=code==0 and result.get('compile_ok') and bool(result.get('cases')) and all(x.get('ok') for x in result['cases'])
 negative=name.startswith('negative-')
 row={'language':lang,'example':name,'cases':len(cases),'passed':bool(ok),'expected_failure':negative,'audit_ok':bool(not ok if negative else ok),'compile_ok':result.get('compile_ok'),'error':result.get('error'),'failed_cases':[x for x in result.get('cases',[]) if not x.get('ok')], 'source':str(folder/'source'),'source_sha256':__import__('hashlib').sha256((source+'\n').encode()).hexdigest()}
 print(json.dumps(row),flush=True)
 return row
for lang,name,source,cases in tests:
 if name.startswith('fence-'):
  index=int(name.split('-')[1])-1
  assert blocks[lang][index] in source, (lang,name,'snippet changed by wrapper')
if len(sys.argv)>1:
 tests=[t for t in tests if t[1] in sys.argv[1:]]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 results=list(pool.map(run,tests))
old=json.loads((base/'audit-results.json').read_text()) if (base/'audit-results.json').exists() else []
merged={(r['language'],r['example']):r for r in old}
merged.update({(r['language'],r['example']):r for r in results})
(base/'audit-results.json').write_text(json.dumps(list(merged.values()),indent=2))
print('SUMMARY',len(results),'examples',sum(r['cases'] for r in results),'cases',sum(r['audit_ok'] for r in results),'audit passes')
sys.exit(0 if all(r['audit_ok'] for r in results) else 1)
