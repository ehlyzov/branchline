#!/usr/bin/env python3
"""Independent language-neutral authoring experiment tasks and oracle.
Only stdlib. No engine, compiler, product code, or candidate solution is imported.
Each reference has a deliberately hand-written assertion as a cross-check.
"""
import hashlib
import json
from pathlib import Path

PRIVATE = Path('/tmp/branchline-authoring-private')
PUBLIC = Path('/tmp/branchline-authoring-public')
TASKS = []
HIDDEN = {}
REFERENCES = {}
HAND_CHECKED = set()
COMMON = (
    'Input and output are JSON. Produce exactly the stated output keys; ignore all unmentioned input keys. '
    'Object key order is irrelevant; array order and empty arrays are significant. '
    'Unless this task explicitly validates arbitrary JSON kinds, assume input obeys its stated domain. '
    'A string is preserved exactly unless a normalization rule is stated; comparisons are case-sensitive. '
    'Missing means a key is absent, distinct from an explicit JSON null. '
    'All integers and all intermediate arithmetic in permitted inputs have absolute value below 2^53; '
    'money is integer cents. No clock, randomness, external state, or external lookup is used. '
)


def integer(x):
    # JSON numeric lexical spelling is not a type distinction: 1.0 is integral.
    return isinstance(x, (int, float)) and not isinstance(x, bool) and x == int(x)


def nonblank(x):
    return isinstance(x, str) and x.strip(' ') != ''


def add(n, category, title, spec, ref, public, hidden, hand_input, hand_expected):
    ident = f'A{n:02}'
    assert ident not in REFERENCES
    actual = ref(hand_input)
    assert actual == hand_expected, (ident, 'hand assertion', actual, hand_expected)
    HAND_CHECKED.add(ident)
    REFERENCES[ident] = ref
    TASKS.append(dict(id=ident, category=category, title=title,
                      spec=COMMON + spec,
                      public=[dict(input=x, expected=ref(x)) for x in public]))
    HIDDEN[ident] = [dict(input=x, expected=ref(x)) for x in hidden]


# A01: a direct field map, with explicit nullable email.
def a01(x):
    return {'customer': {'customerId': x['id'],
                         'name': {'first': x['given'], 'last': x['family']},
                         'email': x.get('email')}}
add(1, 'API mapping', 'Rename and nest customer fields',
    'Input has required string id, given, family; email may be absent, null, or a string. '
    'Return {customer:{customerId:id,name:{first:given,last:family},email:E}}, '
    'where E is null for absent/null email, otherwise the original email string. Empty strings are valid.', a01,
    [{'id':'c1','given':'Ada','family':'Lovelace','email':'ada@example.test'},
     {'id':'c2','given':'Li','family':'','email':None}],
    [{'id':'c0','given':'','family':''},
     {'id':'c3','given':' A ','family':' B ','email':''},
     {'id':'c4','given':'N','family':'M','email':None,'phone':'ignored'},
     {'id':'c5','given':'é','family':'李','email':'e@example.test'},
     {'id':'c6','given':'A','family':'B','name':{'first':'wrong'}},
     {'id':'','given':'G','family':'F','email':' x '}],
    {'id':'H','given':'One','family':'Two'},
    {'customer':{'customerId':'H','name':{'first':'One','last':'Two'},'email':None}})


# A02: flatten while preserving both source orders.
def a02(x):
    rows = []
    for order in x['orders']:
        for i, line in enumerate(order['lines']):
            rows.append({'orderId':order['id'], 'lineNo':i+1,
                         'sku':line['sku'], 'quantity':line['quantity']})
    return {'rows':rows}
add(2, 'API mapping', 'Flatten order lines',
    'Input {orders:[{id:string,lines:[{sku:string,quantity:integer >=0}]}]}. '
    'Return {rows:[{orderId,lineNo,sku,quantity}]}, one row per line in order order, then line order. '
    'lineNo is 1-based within each order, restarting for every order. Orders with no lines produce no rows. '
    'Repeated order IDs and SKUs are retained.', a02,
    [{'orders':[{'id':'o1','lines':[{'sku':'a','quantity':2},{'sku':'b','quantity':0}]}]},
     {'orders':[]}],
    [{'orders':[{'id':'empty','lines':[]}]},
     {'orders':[{'id':'o','lines':[{'sku':'a','quantity':1}]}]},
     {'orders':[{'id':'o','lines':[]},{'id':'p','lines':[{'sku':'z','quantity':3}]}]},
     {'orders':[{'id':'same','lines':[{'sku':'a','quantity':1}]},{'id':'same','lines':[{'sku':'a','quantity':2}]}]},
     {'orders':[{'id':'o','lines':[{'sku':'a','quantity':0},{'sku':'a','quantity':1},{'sku':'a','quantity':2}]}]},
     {'orders':[{'id':'o','lines':[{'sku':'','quantity':1000000,'extra':True}],'extra':42}]}],
    {'orders':[{'id':'x','lines':[{'sku':'s','quantity':4}]},{'id':'y','lines':[{'sku':'t','quantity':5}]}]},
    {'rows':[{'orderId':'x','lineNo':1,'sku':'s','quantity':4},{'orderId':'y','lineNo':1,'sku':'t','quantity':5}]})


def a03(x):
    for key in ('primary','backup'):
        if x.get(key) is not None:
            a = x[key]
            return {'selected':key,'address':{'city':a['city'],'postalCode':a.get('postalCode')}}
    return {'selected':'none','address':None}
add(3, 'API mapping', 'Select an optional address',
    'Input primary and backup are each absent, null, or an address object with required string city '
    'and optional null/string postalCode. Choose primary if it is an object; otherwise backup if it is '
    'an object; otherwise no address. An empty city or postalCode does not disqualify an address. '
    'Return {selected:"primary"|"backup"|"none",address:null|{city,postalCode}}, always including '
    'postalCode (null if absent/null).', a03,
    [{'primary':{'city':'Paris','postalCode':'75001'},'backup':{'city':'Lyon'}},
     {'primary':None,'backup':{'city':'Lyon'}}],
    [{},{'primary':None,'backup':None},
     {'primary':{'city':''},'backup':{'city':'B','postalCode':'1'}},
     {'backup':{'city':'B','postalCode':''}},
     {'primary':{'city':'A','postalCode':None,'street':'ignored'}},
     {'backup':None,'unused':{'city':'wrong'}}],
    {'primary':None,'backup':{'city':'X','postalCode':' 0 '}},
    {'selected':'backup','address':{'city':'X','postalCode':' 0 '}})


def a04(x):
    mapping = {'NEW':'queued','PENDING':'queued','SENT':'complete','DELIVERED':'complete',
               'CANCELED':'cancelled','CANCELLED':'cancelled'}
    return {'statuses':[{'original':s,'label':mapping.get(s.strip(' ').upper(),'unknown')} for s in x['statuses']]}
add(4, 'API mapping', 'Normalize enum labels',
    'Input {statuses:[ASCII strings]}. For each string, remove only leading/trailing ASCII spaces '
    '(U+0020), then uppercase ASCII letters. NEW/PENDING map to queued; SENT/DELIVERED to complete; '
    'CANCELED/CANCELLED to cancelled; everything else, including empty text and strings containing '
    'tabs, maps to unknown. Return {statuses:[{original:unmodified input string,label}]} in input order.', a04,
    [{'statuses':[' new ','SENT','cancelled','???']},{'statuses':[]}],
    [{'statuses':['']},{'statuses':['   ',' pending  ']},
     {'statuses':['DeLiVeReD','Canceled','CANCELLED']},
     {'statuses':['new\t','\tnew','NE W']},
     {'statuses':['new','new',' New ']},
     {'statuses':['queued','complete','cancelled ','unknown']}],
    {'statuses':['pending',' sent ','No']},
    {'statuses':[{'original':'pending','label':'queued'},{'original':' sent ','label':'complete'},{'original':'No','label':'unknown'}]})


def a05(x):
    mode = x.get('ackMode') or 'all'
    send = mode == 'all' or (mode == 'errors' and not x['accepted'])
    ack = None
    if send:
        ack = {'eventId':x['eventId'],'status':'ok' if x['accepted'] else 'error',
               'reason':None if x['accepted'] else (x.get('reason') if x.get('reason') is not None else 'unspecified')}
    return {'ack':ack}
add(5, 'API mapping', 'Build an outbound acknowledgement',
    'Input has eventId:string, accepted:boolean, optional ackMode:null|"all"|"errors"|"none", '
    'and optional reason:null|string. Missing/null ackMode defaults to all. all emits an ack; none '
    'emits none; errors emits only when accepted=false. Return {ack:null} if none, otherwise '
    '{ack:{eventId,status:"ok" when accepted else "error",reason:R}}. R is always null when '
    'accepted; otherwise the provided non-null reason (including empty string), or "unspecified".', a05,
    [{'eventId':'e1','accepted':True},{'eventId':'e2','accepted':False,'ackMode':'errors','reason':'bad'}],
    [{'eventId':'a','accepted':True,'ackMode':'errors'},
     {'eventId':'b','accepted':False,'ackMode':'none','reason':'bad'},
     {'eventId':'c','accepted':False,'ackMode':None,'reason':None},
     {'eventId':'d','accepted':False,'reason':''},
     {'eventId':'e','accepted':True,'ackMode':'all','reason':'ignored'},
     {'eventId':'f','accepted':False,'ackMode':'all'}],
    {'eventId':'h','accepted':False},
    {'ack':{'eventId':'h','status':'error','reason':'unspecified'}})


# Validation errors are ordered strings, with no coercion of JSON kinds.
def price_error(x, key='priceCents'):
    if key not in x or x[key] is None:
        return 'price.required'
    v = x[key]
    if not integer(v):
        return 'price.integer'
    if v < 0 or v > 1000000000:
        return 'price.range'
    return None


def a06(x):
    e = price_error(x)
    return {'valid':e is None,'errors':[] if e is None else [e]}
add(6, 'Validation', 'Validate integer price cents',
    'Input is any JSON object; priceCents may have any JSON kind or be absent. Validate without '
    'coercion. Absent/null yields price.required; otherwise a value that is not a mathematically '
    'integral JSON number yields price.integer (booleans are not numbers); otherwise outside '
    'inclusive [0,1000000000] yields price.range. Numeric 1.0 is integral. Apply only the first '
    'applicable error. Return {valid:boolean,errors:[error string]}, with [] on success.', a06,
    [{'priceCents':0},{'priceCents':'100'}],
    [{},{'priceCents':None},{'priceCents':-1},{'priceCents':1000000000},
     {'priceCents':1000000001},{'priceCents':True},
     {'priceCents':1.5},{'priceCents':1.0},{'priceCents':[]},{'priceCents':{}}],
    {'priceCents':-0.5}, {'valid':False,'errors':['price.integer']})


def required_string(x, key, path):
    if key not in x or x[key] is None:
        return path+'.required'
    if not isinstance(x[key],str):
        return path+'.type'
    if x[key].strip(' ') == '':
        return path+'.blank'
    return None


def a07(x):
    errors = []
    for k in ('customerId','email'):
        e=required_string(x,k,k)
        if e: errors.append(e)
    if 'shipTo' not in x or x['shipTo'] is None:
        errors.append('shipTo.required')
    elif not isinstance(x['shipTo'],dict):
        errors.append('shipTo.type')
    else:
        e=required_string(x['shipTo'],'city','shipTo.city')
        if e: errors.append(e)
    return {'errors':errors}
add(7, 'Validation', 'Collect all required-field errors',
    'Input is any JSON object. Validate customerId, email, then shipTo.city in this order. For '
    'customerId/email, absent/null -> <field>.required; other non-string -> <field>.type; string '
    'empty after trimming only ASCII spaces -> <field>.blank; otherwise valid. No email syntax '
    'check. For shipTo: absent/null -> shipTo.required; non-object -> shipTo.type; otherwise '
    'validate city with the same string rules and path shipTo.city. Do not emit child errors '
    'when the shipTo object itself fails. Return {errors:[all errors in the stated order]}.', a07,
    [{'customerId':'c','email':'a@b','shipTo':{'city':'X'}},{}],
    [{'customerId':None,'email':False,'shipTo':[]},
     {'customerId':' ','email':'','shipTo':{}},
     {'customerId':1,'email':'x','shipTo':{'city':None}},
     {'customerId':'c','email':'x','shipTo':None},
     {'customerId':'\t','email':'nonsyntax','shipTo':{'city':'\t'}},
     {'customerId':'c','email':{},'shipTo':{'city':3,'postal':'ignored'}}],
    {'customerId':'  ','email':None,'shipTo':{'city':False}},
    {'errors':['customerId.blank','email.required','shipTo.city.type']})


def a08(x):
    errors=[]
    if 'lines' not in x or x['lines'] is None:
        errors=['lines.required']
    elif not isinstance(x['lines'],list):
        errors=['lines.array']
    elif not x['lines']:
        errors=['lines.nonempty']
    else:
        for i, row in enumerate(x['lines']):
            p=f'lines.{i}'
            if not isinstance(row,dict): errors.append(p+'.object')
            elif 'quantity' not in row or row['quantity'] is None: errors.append(p+'.quantity.required')
            elif not integer(row['quantity']): errors.append(p+'.quantity.integer')
            elif not 1 <= row['quantity'] <= 1000: errors.append(p+'.quantity.range')
    return {'valid':not errors,'errors':errors}
add(8, 'Validation', 'Constrain line quantity bounds',
    'Input is any JSON object. lines absent/null -> lines.required; non-array -> lines.array; '
    'empty array -> lines.nonempty. In these cases stop child validation. Otherwise inspect '
    'each element by zero-based index i in order: non-object -> lines.i.object; else quantity '
    'absent/null -> lines.i.quantity.required; else not mathematically integral JSON number '
    '(including booleans) -> lines.i.quantity.integer; else outside inclusive [1,1000] -> '
    'lines.i.quantity.range. Emit at most one error per element, in array order. Numeric 1.0 '
    'is integral. Return {valid:errors is empty,errors:[strings]}.', a08,
    [{'lines':[{'quantity':1},{'quantity':1000}]},{'lines':[{'quantity':0},{'quantity':'2'}]}],
    [{},{'lines':None},{'lines':{}},{'lines':[]},
     {'lines':[None,{}, {'quantity':None},{'quantity':True},{'quantity':1001}]},
     {'lines':[{'quantity':-1},{'quantity':1.5},{'quantity':1.0},{'quantity':999}]}],
    {'lines':[{'quantity':2},False,{'quantity':1001}]},
    {'valid':False,'errors':['lines.1.object','lines.2.quantity.range']})


def a09(x):
    m=x.get('method')
    if not isinstance(m,str) or m not in ('pickup','delivery'):
        errors=['method.invalid']
    elif m == 'pickup':
        errors=[]
        if not nonblank(x.get('pickupPoint')): errors.append('pickupPoint.required')
        if x.get('address') is not None: errors.append('address.forbidden')
    else:
        errors=[]
        a=x.get('address')
        if not isinstance(a,dict) or not nonblank(a.get('city')): errors.append('address.required')
        if x.get('pickupPoint') is not None: errors.append('pickupPoint.forbidden')
    return {'valid':not errors,'errors':errors}
add(9, 'Validation', 'Validate dependent delivery fields',
    'Input is any JSON object. method must be exactly string pickup or delivery; otherwise emit '
    'only method.invalid. For pickup, pickupPoint must be a string nonempty after trimming ASCII '
    'spaces (otherwise pickupPoint.required), and address must be absent/null (otherwise '
    'address.forbidden). For delivery, address must be an object whose city is a string nonempty '
    'after trimming ASCII spaces (otherwise one address.required), and pickupPoint must be '
    'absent/null (otherwise pickupPoint.forbidden). Required errors precede forbidden errors. '
    'An empty string is a present non-null value for the forbidden-field checks. Return '
    '{valid:errors is empty,errors:[strings]}.', a09,
    [{'method':'pickup','pickupPoint':'P','address':None},
     {'method':'delivery','address':{'city':'X'},'pickupPoint':'P'}],
    [{},{'method':'PICKUP','address':{}},
     {'method':'pickup','pickupPoint':' ','address':{}},
     {'method':'pickup','pickupPoint':False,'address':None},
     {'method':'delivery','address':{'city':''},'pickupPoint':''},
     {'method':'delivery','address':{'city':' X '},'pickupPoint':None},
     {'method':[],'pickupPoint':'P'}],
    {'method':'delivery','address':False,'pickupPoint':4},
    {'valid':False,'errors':['address.required','pickupPoint.forbidden']})


def a10(x):
    states=('draft','active','paused','closed')
    errors=[]
    for key in ('from','to'):
        if not isinstance(x.get(key),str) or x.get(key) not in states: errors.append(key+'.invalid')
    if not errors:
        f,t=x['from'],x['to']
        if f != t and (f,t) not in {('draft','active'),('active','paused'),('active','closed'),('paused','active'),('paused','closed')}:
            errors=['transition.forbidden']
    return {'allowed':not errors,'errors':errors}
add(10, 'Validation', 'Reject invalid enum transitions',
    'Input is any JSON object. from and to must each be exactly one of draft, active, paused, '
    'closed. Emit from.invalid then to.invalid for invalid/missing/null fields and skip transition '
    'checking if either fails. Valid transitions are every self-transition plus draft->active, '
    'active->paused, active->closed, paused->active, paused->closed. All other pairs yield the '
    'single error transition.forbidden. Return {allowed:errors is empty,errors:[strings]}.', a10,
    [{'from':'draft','to':'active'},{'from':'closed','to':'active'}],
    [{},{'from':None,'to':False},{'from':'draft','to':'closed'},
     {'from':'paused','to':'closed'},{'from':'closed','to':'closed'},
     {'from':'active','to':'draft'},{'from':'ACTIVE','to':'active'},
     {'from':'paused','to':'active'},{'from':'draft','to':'draft'},
     {'from':'draft','to':'paused'},
     {'from':'active','to':'active'},
     {'from':'active','to':'paused'},
     {'from':'active','to':'closed'},
     {'from':'paused','to':'draft'},
     {'from':'paused','to':'paused'},
     {'from':'closed','to':'draft'},
     {'from':'closed','to':'paused'}],
    {'from':'active','to':'paused'}, {'allowed':True,'errors':[]})


# Lookup/grouping; list scans deliberately prioritize clarity over efficiency.
def a11(x):
    lines=[]
    for row in x['lines']:
        match=next((c for c in x['catalogue'] if c['sku']==row['sku']),None)
        lines.append({'sku':row['sku'],'quantity':row['quantity'],
                      'name':None if match is None else match['name'],
                      'unitCents':None if match is None else match['unitCents']})
    return {'lines':lines}
add(11, 'Lookup and grouping', 'Enrich SKU from a catalogue',
    'Input {lines:[{sku:string,quantity:integer >=0}],catalogue:[{sku:string,name:string,unitCents:integer >=0}]}. '
    'For each line find the FIRST catalogue entry with equal sku, case-sensitive. Return '
    '{lines:[{sku,quantity,name,unitCents}]}; a missing match has explicit null name and unitCents. '
    'Preserve all lines, their order and repetitions; ignore unused catalogue entries.', a11,
    [{'lines':[{'sku':'a','quantity':2}],'catalogue':[{'sku':'a','name':'Apple','unitCents':25}]},
     {'lines':[{'sku':'missing','quantity':1}],'catalogue':[]}],
    [{'lines':[],'catalogue':[]},
     {'lines':[{'sku':'a','quantity':0}],'catalogue':[{'sku':'a','name':'first','unitCents':0},{'sku':'a','name':'second','unitCents':9}]},
     {'lines':[{'sku':'A','quantity':1}],'catalogue':[{'sku':'a','name':'lower','unitCents':1}]},
     {'lines':[{'sku':'x','quantity':1},{'sku':'x','quantity':2}],'catalogue':[{'sku':'x','name':'X','unitCents':3}]},
     {'lines':[{'sku':'b','quantity':2},{'sku':'a','quantity':1}],'catalogue':[{'sku':'a','name':'A','unitCents':1},{'sku':'b','name':'B','unitCents':2}]},
     {'lines':[{'sku':'','quantity':1}],'catalogue':[{'sku':'','name':'','unitCents':1000000000}]}],
    {'lines':[{'sku':'s','quantity':3},{'sku':'z','quantity':0}],'catalogue':[{'sku':'s','name':'N','unitCents':4}]},
    {'lines':[{'sku':'s','quantity':3,'name':'N','unitCents':4},{'sku':'z','quantity':0,'name':None,'unitCents':None}]})


def a12(x):
    matched=[]; missing=[]
    for order in x['orders']:
        matches=[c for c in x['customers'] if c['id']==order['customerId']]
        if matches:
            matched.append({'orderId':order['id'],'customerName':matches[-1]['name']})
        else:
            missing.append({'orderId':order['id'],'customerId':order['customerId']})
    return {'matched':matched,'missing':missing}
add(12, 'Lookup and grouping', 'Retain missing customer lookups explicitly',
    'Input {orders:[{id:string,customerId:string}],customers:[{id:string,name:string}]}. '
    'For each order use the LAST customer entry with matching id, case-sensitive. Return '
    '{matched:[{orderId,customerName}],missing:[{orderId,customerId}]}. Every order appears '
    'exactly once, in its matching partition; preserve relative input order within each partition '
    'and retain duplicate orders. Empty customer names are successful matches.', a12,
    [{'orders':[{'id':'o1','customerId':'c'},{'id':'o2','customerId':'z'}],'customers':[{'id':'c','name':'C'}]},
     {'orders':[],'customers':[]}],
    [{'orders':[{'id':'o','customerId':'c'}],'customers':[]},
     {'orders':[{'id':'o','customerId':'c'}],'customers':[{'id':'c','name':'old'},{'id':'c','name':'new'}]},
     {'orders':[{'id':'o','customerId':'c'}],'customers':[{'id':'c','name':''}]},
     {'orders':[{'id':'o','customerId':'C'}],'customers':[{'id':'c','name':'C'}]},
     {'orders':[{'id':'a','customerId':'x'},{'id':'b','customerId':'c'},{'id':'a','customerId':'x'},{'id':'d','customerId':'c'}],'customers':[{'id':'c','name':'C'}]},
     {'orders':[],'customers':[{'id':'u','name':'Unused'}]}],
    {'orders':[{'id':'1','customerId':'x'},{'id':'2','customerId':'y'}],'customers':[{'id':'y','name':'Y'}]},
    {'matched':[{'orderId':'2','customerName':'Y'}],'missing':[{'orderId':'1','customerId':'x'}]})


def a13(x):
    groups=[]
    for row in x['orders']:
        group=next((g for g in groups if g['customerId']==row['customerId']),None)
        if group is None:
            group={'customerId':row['customerId'],'orderIds':[]}; groups.append(group)
        group['orderIds'].append(row['id'])
    return {'groups':groups}
add(13, 'Lookup and grouping', 'Group orders by customer',
    'Input {orders:[{id:string,customerId:string}]}. Return {groups:[{customerId,orderIds:[strings]}]}. '
    'Group by exact customerId. Groups occur in order of first appearance of that customerId; '
    'orderIds retain input order within the group, including duplicates. Produce no groups '
    'for customers absent from orders; an empty orders list gives groups:[]. Empty IDs are valid.', a13,
    [{'orders':[{'id':'o1','customerId':'b'},{'id':'o2','customerId':'a'},{'id':'o3','customerId':'b'}]},
     {'orders':[]}],
    [{'orders':[{'id':'o','customerId':'c'}]},
     {'orders':[{'id':'o','customerId':'c'},{'id':'o','customerId':'c'}]},
     {'orders':[{'id':'a','customerId':''},{'id':'b','customerId':''}]},
     {'orders':[{'id':'1','customerId':'C'},{'id':'2','customerId':'c'}]},
     {'orders':[{'id':'1','customerId':'z'},{'id':'2','customerId':'y'},{'id':'3','customerId':'x'}]},
     {'orders':[{'id':'1','customerId':'b'},{'id':'2','customerId':'a'},{'id':'3','customerId':'a'},{'id':'4','customerId':'b'}]}],
    {'orders':[{'id':'2','customerId':'x'},{'id':'1','customerId':'x'}]},
    {'groups':[{'customerId':'x','orderIds':['2','1']}]})


def a14(x):
    ids=[]
    for row in x['records']:
        if row['id'] not in ids: ids.append(row['id'])
    output=[]
    for ident in ids:
        winner=None
        for row in x['records']:
            if row['id']==ident and (winner is None or row['revision'] >= winner['revision']): winner=row
        output.append({'id':winner['id'],'revision':winner['revision'],'value':winner['value']})
    return {'records':output}
add(14, 'Lookup and grouping', 'Deduplicate by supplied revision',
    'Input {records:[{id:string,revision:integer >=0,value:any JSON value}]}. For each exact id '
    'keep the record with greatest revision; tied greatest revisions choose the LAST occurrence '
    'in the input. Return {records:[{id,revision,value}]} in order of FIRST appearance of each id '
    '(not winner position or revision order). Copy the winning value including null/arrays/objects. '
    'Ignore other record keys.', a14,
    [{'records':[{'id':'a','revision':1,'value':'old'},{'id':'b','revision':3,'value':0},{'id':'a','revision':2,'value':'new'}]},
     {'records':[]}],
    [{'records':[{'id':'a','revision':0,'value':None}]},
     {'records':[{'id':'a','revision':2,'value':'first'},{'id':'a','revision':2,'value':'last'}]},
     {'records':[{'id':'a','revision':4,'value':[1]},{'id':'a','revision':0,'value':[]}]},
     {'records':[{'id':'b','revision':0,'value':False},{'id':'a','revision':9,'value':True},{'id':'b','revision':8,'value':{'x':1}}]},
     {'records':[{'id':'','revision':1,'value':0},{'id':'','revision':2,'value':''}]},
     {'records':[{'id':'A','revision':1,'value':'upper'},{'id':'a','revision':1,'value':'lower'}]}],
    {'records':[{'id':'x','revision':3,'value':1},{'id':'y','revision':9,'value':2},{'id':'x','revision':3,'value':None}]},
    {'records':[{'id':'x','revision':3,'value':None},{'id':'y','revision':9,'value':2}]})


def a15(x):
    rows=[]
    for r in x['requests']:
        available=sum(s['available'] for s in x['stock'] if s['sku']==r['sku'] and s['warehouse']==r['warehouse'])
        rows.append({'sku':r['sku'],'warehouse':r['warehouse'],'requested':r['quantity'],
                     'available':available,'fulfilled':min(r['quantity'],available),
                     'shortfall':max(0,r['quantity']-available)})
    return {'requests':rows}
add(15, 'Lookup and grouping', 'Join warehouse availability',
    'Input {requests:[{sku:string,warehouse:string,quantity:integer >=0}],stock:[{sku:string,warehouse:string,available:integer >=0}]}. '
    'For each request independently sum available from ALL stock entries matching BOTH sku and '
    'warehouse exactly. Missing matches sum to 0. Return {requests:[{sku,warehouse,requested:quantity,'
    'available:sum,fulfilled:min(quantity,sum),shortfall:max(0,quantity-sum)}]} in request order. '
    'Requests do not consume stock; repeated requests get the same availability.', a15,
    [{'requests':[{'sku':'s','warehouse':'w','quantity':5}],'stock':[{'sku':'s','warehouse':'w','available':3}]},
     {'requests':[],'stock':[]}],
    [{'requests':[{'sku':'s','warehouse':'w','quantity':2}],'stock':[]},
     {'requests':[{'sku':'s','warehouse':'w','quantity':5}],'stock':[{'sku':'s','warehouse':'w','available':2},{'sku':'s','warehouse':'w','available':4}]},
     {'requests':[{'sku':'s','warehouse':'w','quantity':2}],'stock':[{'sku':'s','warehouse':'other','available':100},{'sku':'other','warehouse':'w','available':100}]},
     {'requests':[{'sku':'s','warehouse':'w','quantity':0}],'stock':[{'sku':'s','warehouse':'w','available':9}]},
     {'requests':[{'sku':'s','warehouse':'w','quantity':3},{'sku':'s','warehouse':'w','quantity':3}],'stock':[{'sku':'s','warehouse':'w','available':4}]},
     {'requests':[{'sku':'a|b','warehouse':'c','quantity':1},{'sku':'a','warehouse':'b|c','quantity':1}],'stock':[{'sku':'a|b','warehouse':'c','available':2}]}],
    {'requests':[{'sku':'x','warehouse':'w','quantity':7}],'stock':[{'sku':'x','warehouse':'w','available':7}]},
    {'requests':[{'sku':'x','warehouse':'w','requested':7,'available':7,'fulfilled':7,'shortfall':0}]})


# Exact integer arithmetic, including explicit per-operation rounding.
def a16(x):
    rows=[{'sku':r['sku'],'totalCents':r['quantity']*r['unitCents']} for r in x['lines']]
    return {'lines':rows,'totalCents':sum(r['totalCents'] for r in rows)}
add(16, 'Aggregation', 'Compute line totals in cents',
    'Input {lines:[{sku:string,quantity:integer >=0,unitCents:integer >=0}]}. Each line product '
    'and the sum of all products are <=1000000000000. Return {lines:[{sku,totalCents:quantity*unitCents}],'
    'totalCents:sum of line totals}. Preserve input order and repeated SKUs; perform exact integer '
    'arithmetic with no rounding. Empty input has lines:[] and totalCents:0.', a16,
    [{'lines':[{'sku':'a','quantity':3,'unitCents':125},{'sku':'b','quantity':2,'unitCents':50}]},
     {'lines':[]}],
    [{'lines':[{'sku':'a','quantity':0,'unitCents':999}]},
     {'lines':[{'sku':'a','quantity':999,'unitCents':0}]},
     {'lines':[{'sku':'a','quantity':1000000,'unitCents':1000000}]},
     {'lines':[{'sku':'x','quantity':2,'unitCents':3},{'sku':'x','quantity':4,'unitCents':5}]},
     {'lines':[{'sku':'a','quantity':1,'unitCents':2147483648},{'sku':'b','quantity':1,'unitCents':1}]},
     {'lines':[{'sku':'','quantity':1,'unitCents':1}]}],
    {'lines':[{'sku':'s','quantity':7,'unitCents':9}]},
    {'lines':[{'sku':'s','totalCents':63}],'totalCents':63})


def a17(x):
    discount=(x['subtotalCents']*x['discountBps']+5000)//10000
    return {'discountCents':discount,'payableCents':x['subtotalCents']-discount}
add(17, 'Aggregation', 'Apply a discount with half-up rounding',
    'Input {subtotalCents:integer in [0,1000000000],discountBps:integer in [0,10000]}. '
    'Compute exact rational discount=subtotalCents*discountBps/10000 and round it to the nearest '
    'integer cent; an exact half cent rounds upward (half-up for these nonnegative values). '
    'Return {discountCents:rounded discount,payableCents:subtotalCents-discountCents}. Round '
    'the discount once, not the payable amount separately.', a17,
    [{'subtotalCents':999,'discountBps':1500},{'subtotalCents':5,'discountBps':1000}],
    [{'subtotalCents':0,'discountBps':10000},
     {'subtotalCents':123,'discountBps':0},
     {'subtotalCents':123,'discountBps':10000},
     {'subtotalCents':1,'discountBps':4999},
     {'subtotalCents':1,'discountBps':5000},
     {'subtotalCents':1,'discountBps':5001},
     {'subtotalCents':1000000000,'discountBps':9999},
     {'subtotalCents':25,'discountBps':1000}],
    {'subtotalCents':15,'discountBps':1000}, {'discountCents':2,'payableCents':13})


def a18(x):
    cats=[]
    for row in x['records']:
        if row['category'] not in cats: cats.append(row['category'])
    by=[]
    for c in cats:
        rows=[r for r in x['records'] if r['category']==c]
        by.append({'category':c,'accepted':sum(1 for r in rows if r['accepted']),
                   'rejected':sum(1 for r in rows if not r['accepted'])})
    return {'accepted':sum(1 for r in x['records'] if r['accepted']),
            'rejected':sum(1 for r in x['records'] if not r['accepted']),'byCategory':by}
add(18, 'Aggregation', 'Count accepted and rejected records',
    'Input {records:[{category:string,accepted:boolean}]}. Return {accepted:total true count,'
    'rejected:total false count,byCategory:[{category,accepted:true count,rejected:false count}]}. '
    'Categories use exact string equality and appear in order of first occurrence. Include both '
    'counts even when zero. Count every record, including duplicates. Empty input produces zero '
    'totals and byCategory:[].', a18,
    [{'records':[{'category':'b','accepted':False},{'category':'a','accepted':True},{'category':'b','accepted':True}]},
     {'records':[]}],
    [{'records':[{'category':'c','accepted':False}]},
     {'records':[{'category':'c','accepted':False},{'category':'c','accepted':False}]},
     {'records':[{'category':'c','accepted':True},{'category':'c','accepted':True}]},
     {'records':[{'category':'','accepted':False},{'category':'','accepted':True}]},
     {'records':[{'category':'A','accepted':True},{'category':'a','accepted':False}]},
     {'records':[{'category':'z','accepted':False},{'category':'y','accepted':False},{'category':'x','accepted':False}]}],
    {'records':[{'category':'x','accepted':True},{'category':'y','accepted':False}]},
    {'accepted':1,'rejected':1,'byCategory':[{'category':'x','accepted':1,'rejected':0},{'category':'y','accepted':0,'rejected':1}]})


def a19(x):
    best=None
    for row in x['events']:
        if best is None or row['timestamp']>=best['timestamp']: best=row
    return {'latest':None if best is None else {'id':best['id'],'timestamp':best['timestamp']}}
add(19, 'Aggregation', 'Choose the latest supplied timestamp',
    'Input {events:[{id:string,timestamp:integer in [0,1000000000000]}]}; timestamp is a supplied '
    'epoch-second number, not a date string. Choose the greatest timestamp, breaking ties by '
    'LAST input occurrence. Return {latest:{id,timestamp}} containing only these two winner '
    'fields, or {latest:null} for no events. IDs do not influence selection.', a19,
    [{'events':[{'id':'a','timestamp':20},{'id':'b','timestamp':10}]},{'events':[]}],
    [{'events':[{'id':'a','timestamp':0}]},
     {'events':[{'id':'a','timestamp':10},{'id':'b','timestamp':10}]},
     {'events':[{'id':'a','timestamp':5},{'id':'b','timestamp':8},{'id':'c','timestamp':2}]},
     {'events':[{'id':'same','timestamp':1},{'id':'same','timestamp':1000000000000}]},
     {'events':[{'id':'z','timestamp':2147483647},{'id':'a','timestamp':2147483648}]},
     {'events':[{'id':'a','timestamp':8,'payload':'ignore'},{'id':'b','timestamp':1},{'id':'c','timestamp':8}]}],
    {'events':[{'id':'first','timestamp':0},{'id':'last','timestamp':0}]},
    {'latest':{'id':'last','timestamp':0}})


def a20(x):
    keys=[]
    for row in x['stock']:
        if row['sku'] not in keys: keys.append(row['sku'])
    items=[]
    for sku in keys:
        on=sum(r['onHand'] for r in x['stock'] if r['sku']==sku)
        reserved=sum(r['reserved'] for r in x['stock'] if r['sku']==sku)
        items.append({'sku':sku,'onHand':on,'reserved':reserved,'available':max(0,on-reserved)})
    return {'items':items,'totals':{k:sum(r[k] for r in items) for k in ('onHand','reserved','available')}}
add(20, 'Aggregation', 'Build stock summaries',
    'Input {stock:[{sku:string,onHand:integer >=0,reserved:integer >=0}]}; all aggregate counts '
    'are <=1000000000000. Group by exact sku in first-occurrence order. For each group SUM '
    'onHand and reserved separately, then available=max(0,summed onHand-summed reserved). '
    'Return {items:[{sku,onHand,reserved,available}],totals:{onHand,reserved,available}}, where '
    'each total is the sum of that field across items. Clamp after grouping; total available '
    'is the sum of per-SKU clamped values. Empty input has [] and all totals 0.', a20,
    [{'stock':[{'sku':'a','onHand':5,'reserved':2},{'sku':'a','onHand':3,'reserved':1},{'sku':'b','onHand':1,'reserved':4}]},
     {'stock':[]}],
    [{'stock':[{'sku':'a','onHand':0,'reserved':0}]},
     {'stock':[{'sku':'a','onHand':1,'reserved':5},{'sku':'a','onHand':8,'reserved':0}]},
     {'stock':[{'sku':'a','onHand':2,'reserved':8},{'sku':'b','onHand':10,'reserved':1}]},
     {'stock':[{'sku':'z','onHand':3,'reserved':3},{'sku':'a','onHand':1,'reserved':0}]},
     {'stock':[{'sku':'','onHand':2147483648,'reserved':1},{'sku':'','onHand':1,'reserved':0}]},
     {'stock':[{'sku':'A','onHand':1,'reserved':0},{'sku':'a','onHand':0,'reserved':1}]}],
    {'stock':[{'sku':'s','onHand':2,'reserved':5},{'sku':'s','onHand':4,'reserved':0}]},
    {'items':[{'sku':'s','onHand':6,'reserved':5,'available':1}],'totals':{'onHand':6,'reserved':5,'available':1}})


# Contract evolution tasks specify business migration only, never unavailable tooling.
def a21(x):
    source=x.get('source')
    return {'version':2,'id':x['id'],'amountCents':x['amountCents'],
            'source':None if source is None else {'system':source['system'],'reference':source.get('reference')},
            'compatibility':'legacy' if source is None else 'extended'}
add(21, 'Contract changes', 'Add optional source metadata',
    'Old business input/output contained {id:string,amountCents:integer >=0}. New input keeps '
    'those required fields and adds optional source:null|{system:string,reference?:null|string}. '
    'Accept both schemas. Return the NEW guaranteed shape {version:2,id,amountCents,source,'
    'compatibility}. Missing/null source normalizes to source:null and compatibility:"legacy". '
    'An object source normalizes to {system,reference}, with explicit null for missing/null '
    'reference, and compatibility:"extended". Empty strings are valid. No output key may be omitted.', a21,
    [{'id':'e1','amountCents':100},{'id':'e2','amountCents':0,'source':{'system':'erp','reference':'r1'}}],
    [{'id':'a','amountCents':0,'source':None},
     {'id':'b','amountCents':1,'source':{'system':'s'}},
     {'id':'c','amountCents':2,'source':{'system':'s','reference':None}},
     {'id':'d','amountCents':3,'source':{'system':'','reference':''}},
     {'id':'e','amountCents':2147483648,'unused':'ignore'},
     {'id':'f','amountCents':5,'source':{'system':'x','reference':' y ','extra':1}}],
    {'id':'h','amountCents':9,'source':{'system':'S'}},
    {'version':2,'id':'h','amountCents':9,'source':{'system':'S','reference':None},'compatibility':'extended'})


def a22(x):
    old=x.get('customerId'); new=x.get('accountId')
    if old is None and new is None:
        return {'ok':False,'compatibility':'incompatible','record':None,'errors':['accountId.required']}
    if old is not None and new is not None and old!=new:
        return {'ok':False,'compatibility':'conflict','record':None,'errors':['accountId.conflict']}
    return {'ok':True,'compatibility':'dual' if old is not None and new is not None else ('legacy' if new is None else 'current'),
            'record':{'accountId':new if new is not None else old,'amountCents':x['amountCents']},'errors':[]}
add(22, 'Contract changes', 'Rename a required identifier with compatibility',
    'Old business records required customerId; new records require accountId. Input has required '
    'amountCents:integer >=0 and each identifier is absent, null, or a string nonempty after '
    'trimming ASCII spaces. Do not trim valid identifiers. Treat null identifiers as absent. '
    'Neither non-null -> {ok:false,compatibility:"incompatible",record:null,errors:["accountId.required"]}. '
    'Both non-null unequal -> same rejection shape with compatibility:"conflict" and '
    'errors:["accountId.conflict"]. Otherwise return {ok:true,compatibility:C,record:{accountId:I,'
    'amountCents},errors:[]}. I is accountId if non-null, otherwise customerId. C is dual if both '
    'non-null and equal, current if only accountId, legacy if only customerId. The new output '
    'record never contains customerId.', a22,
    [{'customerId':'c','amountCents':1},{'accountId':'a','amountCents':2}],
    [{'amountCents':0},{'customerId':None,'accountId':None,'amountCents':1},
     {'customerId':'x','accountId':'x','amountCents':2},
     {'customerId':'x','accountId':'y','amountCents':3},
     {'customerId':'x','accountId':None,'amountCents':4},
     {'customerId':None,'accountId':'y','amountCents':5},
     {'customerId':' x ','accountId':'x','amountCents':6}],
    {'customerId':'h','accountId':'h','amountCents':0},
    {'ok':True,'compatibility':'dual','record':{'accountId':'h','amountCents':0},'errors':[]})


def a23(x):
    email=x.get('email')
    if email is None or email.strip(' ')== '':
        return {'ok':False,'compatibility':'needs-enrichment','record':None,'errors':['email.required']}
    return {'ok':True,'compatibility':'current','record':{'id':x['id'],'email':email.strip(' ')},'errors':[]}
add(23, 'Contract changes', 'Make optional email required',
    'Old schema accepted {id:string,email?:null|string}. New schema requires a nonblank email '
    'string, and migration MUST NOT invent one. Input follows the old schema. An absent/null '
    'email, or an email empty after trimming only ASCII spaces, returns {ok:false,'
    'compatibility:"needs-enrichment",record:null,errors:["email.required"]}. Otherwise return '
    '{ok:true,compatibility:"current",record:{id,email:email with leading/trailing ASCII spaces '
    'removed},errors:[]}. No email syntax check, case conversion, or internal-space removal. '
    'This returned record is the new downstream schema guarantee.', a23,
    [{'id':'a','email':' x@example.test '},{'id':'b'}],
    [{'id':'a','email':None},{'id':'b','email':''},{'id':'c','email':'   '},
     {'id':'d','email':'A@B'},{'id':'e','email':' a b '},{'id':'f','email':'\t'},
     {'id':'g','email':'z','extra':'ignore'}],
    {'id':'h','email':'  a  '},
    {'ok':True,'compatibility':'current','record':{'id':'h','email':'a'},'errors':[]})


def a24(x):
    decision=x['decision']
    if decision=='accept': status='accepted'; reason=None
    elif decision=='reject': status='rejected'; reason=x.get('reason') if x.get('reason') is not None else 'rejected'
    else: status='deferred'; reason=x.get('reason') if x.get('reason') is not None else 'awaiting-review'
    return {'id':x['id'],'status':status,'terminal':decision!='hold','reason':reason,
            'compatibility':'extended' if decision=='hold' else 'legacy'}
add(24, 'Contract changes', 'Extend an output enum with deferred',
    'Old input had {id:string,decision:"accept"|"reject",reason?:null|string}; old output status '
    'was accepted/rejected. New input also permits decision:"hold", and the new downstream '
    'shape is {id,status,terminal,reason,compatibility}. accept -> status accepted, terminal true, '
    'reason null regardless of input; reject -> status rejected, terminal true, reason supplied '
    'non-null string or default "rejected"; hold -> status deferred, terminal false, reason '
    'supplied non-null string or default "awaiting-review". Empty reason strings are retained. '
    'compatibility is extended for hold, legacy otherwise. Accept both old and new decisions.', a24,
    [{'id':'a','decision':'accept'},{'id':'b','decision':'hold','reason':'manual'}],
    [{'id':'a','decision':'reject'},
     {'id':'b','decision':'hold'},
     {'id':'c','decision':'hold','reason':None},
     {'id':'d','decision':'hold','reason':''},
     {'id':'e','decision':'reject','reason':''},
     {'id':'f','decision':'accept','reason':'ignored'},
     {'id':'g','decision':'reject','reason':None}],
    {'id':'h','decision':'reject','reason':'bad'},
    {'id':'h','status':'rejected','terminal':True,'reason':'bad','compatibility':'legacy'})


def a25(x):
    items=x['items']
    string_count=sum(isinstance(i,str) for i in items)
    compatibility='empty' if not items else ('legacy' if string_count==len(items) else ('current' if string_count==0 else 'mixed'))
    return {'orderId':x['orderId'],'lines':[{'sku':i,'quantity':1} if isinstance(i,str) else {'sku':i['sku'],'quantity':i['quantity']} for i in items],
            'compatibility':compatibility}
add(25, 'Contract changes', 'Change nested list element shape',
    'Old input {orderId:string,items:[SKU strings]}; new items use {sku:string,quantity:integer >=0}. '
    'The migration accepts old, new, or mixed element arrays; items is always an array and each '
    'element is either a string or the specified object. Return {orderId,lines:[{sku,quantity}],'
    'compatibility}. A legacy string becomes {sku:that string,quantity:1}; an object copies '
    'only sku and quantity, including zero. Preserve order/repetitions and do not merge SKUs. '
    'compatibility is empty for no items, legacy if all elements are strings, current if all '
    'are objects, mixed otherwise. lines is always an array, including zero/one elements.', a25,
    [{'orderId':'o1','items':['a','b']},{'orderId':'o2','items':[{'sku':'x','quantity':2}]}],
    [{'orderId':'a','items':[]},
     {'orderId':'b','items':['x']},
     {'orderId':'c','items':[{'sku':'x','quantity':0}]},
     {'orderId':'d','items':['x',{'sku':'x','quantity':3},'y']},
     {'orderId':'e','items':['','']},
     {'orderId':'f','items':[{'sku':'a','quantity':1,'oldQuantity':99},{'sku':'a','quantity':2}]}],
    {'orderId':'h','items':[{'sku':'s','quantity':0},'t']},
    {'orderId':'h','lines':[{'sku':'s','quantity':0},{'sku':'t','quantity':1}],'compatibility':'mixed'})


# Repair tasks describe flawed algorithms in prose, not language-specific solutions.
def a26(x):
    fallback='displayName' not in x
    return {'name':x['legalName'] if fallback else x['displayName'],'usedFallback':fallback}
add(26, 'Maintenance and repair', 'Repair absent versus null fallback',
    'Input {legalName:string,displayName?:null|string}. Existing flawed algorithm: choose legalName '
    'whenever displayName is absent OR null, and report usedFallback=true in either case. '
    'Requested correction: fallback ONLY when the displayName key is absent. An explicit null '
    'must remain null, and an empty string must remain empty. Return {name:chosen value,'
    'usedFallback:boolean}. Preserve all other original behavior: strings copied exactly, no '
    'trimming and no other output keys.', a26,
    [{'legalName':'Legal'},{'legalName':'Legal','displayName':None}],
    [{'legalName':'L','displayName':''},
     {'legalName':'L','displayName':' D '},
     {'legalName':'','displayName':None},
     {'legalName':'','otherName':'ignored'},
     {'legalName':'same','displayName':'same'},
     {'legalName':'L','displayName':'0'}],
    {'legalName':'Fallback','displayName':None}, {'name':None,'usedFallback':False})


def a27(x):
    return {'batches':[{'batchId':b['batchId'],'codes':[e['code'] for e in b['entries']]} for b in x['batches']]}
add(27, 'Maintenance and repair', 'Repair singleton list collapse',
    'Input {batches:[{batchId:string,entries:[{code:string}]}]}. Existing flawed algorithm maps '
    'entries to codes, but returns a scalar string for one entry, omits codes for no entries, '
    'and collapses batches to a single object when there is one batch. Requested correction: '
    'return {batches:[{batchId,codes:[strings]}]} with BOTH batches and codes always arrays, '
    'including empty/singleton cases. Preserve batch order and entry order, duplicate IDs/codes '
    'and string contents. Every input batch appears even if entries is empty; ignore extra keys.', a27,
    [{'batches':[{'batchId':'b','entries':[{'code':'x'}]}]},{'batches':[]}],
    [{'batches':[{'batchId':'empty','entries':[]}]},
     {'batches':[{'batchId':'b','entries':[{'code':'a'},{'code':'b'}]}]},
     {'batches':[{'batchId':'a','entries':[]},{'batchId':'b','entries':[{'code':'x'}]}]},
     {'batches':[{'batchId':'same','entries':[{'code':'x'},{'code':'x'}]},{'batchId':'same','entries':[]}]},
     {'batches':[{'batchId':'','entries':[{'code':''}]}]},
     {'batches':[{'batchId':'b','entries':[{'code':'x','other':'ignored'}],'other':1}]}],
    {'batches':[{'batchId':'h','entries':[]}]}, {'batches':[{'batchId':'h','codes':[]}]})


def a28(x):
    rows=[]
    for line in x['lines']:
        tax=(line['netCents']*line['rateBps']+5000)//10000
        rows.append({'id':line['id'],'taxCents':tax,'grossCents':line['netCents']+tax})
    net=sum(r['netCents'] for r in x['lines']); tax=sum(r['taxCents'] for r in rows)
    return {'lines':rows,'netCents':net,'taxCents':tax,'grossCents':net+tax}
add(28, 'Maintenance and repair', 'Repair tax rounding per line',
    'Input {lines:[{id:string,netCents:integer in [0,1000000000],rateBps:integer in [0,10000]}]}; '
    'sum of net amounts is <=1000000000000. Existing flawed algorithm truncates each exact '
    'line tax netCents*rateBps/10000 downward to whole cents before adding it to net. Requested '
    'correction: round EACH line tax to nearest integer cent, exact half upward, then sum the '
    'already rounded line taxes. Do not round an invoice-wide exact tax sum. Return '
    '{lines:[{id,taxCents,grossCents:netCents+rounded tax}],netCents:sum of nets,taxCents:sum of '
    'rounded line taxes,grossCents:sum of nets+sum of rounded taxes}. Preserve line order and '
    'duplicate IDs, with zero totals and [] for no lines.', a28,
    [{'lines':[{'id':'a','netCents':5,'rateBps':1000},{'id':'b','netCents':5,'rateBps':1000}]},
     {'lines':[]}],
    [{'lines':[{'id':'a','netCents':1,'rateBps':4999}]},
     {'lines':[{'id':'a','netCents':1,'rateBps':5000}]},
     {'lines':[{'id':'a','netCents':1,'rateBps':5001}]},
     {'lines':[{'id':'same','netCents':1,'rateBps':4000},{'id':'same','netCents':1,'rateBps':4000}]},
     {'lines':[{'id':'a','netCents':0,'rateBps':10000},{'id':'b','netCents':123,'rateBps':0}]},
     {'lines':[{'id':'a','netCents':1000000000,'rateBps':9999}]},
     {'lines':[{'id':'a','netCents':25,'rateBps':1000}]}],
    {'lines':[{'id':'h','netCents':15,'rateBps':1000}]},
    {'lines':[{'id':'h','taxCents':2,'grossCents':17}],'netCents':15,'taxCents':2,'grossCents':17})


def a29(x):
    return {'event':{'id':x['eventId'],'revision':x['revision']},
            'ack':None if x.get('notify') is False else {'eventId':x['eventId'],'state':'stored'}}
add(29, 'Maintenance and repair', 'Add an acknowledgement opt-out',
    'Input {eventId:string,revision:integer >=0,notify?:null|boolean}. Baseline always emits '
    'both a stored event and an acknowledgement. Existing attempted change is flawed: it '
    'defaults notify to true using a rule that treats false as missing, so false still emits '
    'an ack. Requested correction: ONLY explicit notify:false suppresses the ack; absent, null '
    'and true retain the baseline ack. Always return {event:{id:eventId,revision},ack:A}, '
    'where A is null when suppressed, otherwise {eventId,state:"stored"}. The event is always '
    'emitted, with revision preserved including zero. No validation/coercion or event suppression.', a29,
    [{'eventId':'e','revision':1},{'eventId':'e','revision':1,'notify':False}],
    [{'eventId':'a','revision':0,'notify':True},
     {'eventId':'b','revision':0,'notify':None},
     {'eventId':'c','revision':0,'notify':False},
     {'eventId':'d','revision':2147483648,'notify':False},
     {'eventId':'','revision':0},
     {'eventId':'e','revision':9,'notify':True,'ack':False}],
    {'eventId':'h','revision':0,'notify':False},
    {'event':{'id':'h','revision':0},'ack':None})


def a30(x):
    errors=[]
    e=required_string(x,'eventId','eventId')
    if e: errors.append(e)
    e=price_error(x)
    if e: errors.append(e)
    return {'valid':not errors,'errors':errors,
            'event':None if errors else {'type':'price.changed','id':x['eventId'],'priceCents':x['priceCents']}}
add(30, 'Maintenance and repair', 'Validate before composing an event',
    'Input is any JSON object. Existing flawed algorithm first builds an event, substituting '
    'empty ID and zero price for absent/null values, then validates that event and may return '
    'the event even on validation failure. Requested correction: validate ORIGINAL input first '
    'and compose only if all checks pass. Validate eventId then priceCents, collecting up to '
    'one error for each in that order. eventId absent/null -> eventId.required; non-string -> '
    'eventId.type; empty after trimming ASCII spaces -> eventId.blank. Valid eventId is copied '
    'untrimmed. priceCents absent/null -> price.required; not mathematically integral JSON '
    'number (booleans excluded) -> price.integer; outside inclusive [0,1000000000] -> price.range. '
    'Numeric 1.0 is integral. Return {valid:boolean,errors:[strings],event:null} on ANY error, '
    'otherwise {valid:true,errors:[],event:{type:"price.changed",id:eventId,priceCents}}. '
    'No default values, partial events, or other output keys.', a30,
    [{'eventId':'e','priceCents':0},{}],
    [{'eventId':None,'priceCents':None},
     {'eventId':' ','priceCents':-1},
     {'eventId':False,'priceCents':True},
     {'eventId':'e','priceCents':'1'},
     {'eventId':' e ','priceCents':1000000000},
     {'eventId':'e','priceCents':1000000001},
     {'eventId':[],'priceCents':0},
     {'eventId':'e','priceCents':1.5},
     {'eventId':'e','priceCents':1.0}],
    {'eventId':'h','priceCents':None}, {'valid':False,'errors':['price.required'],'event':None})


def audit():
    assert [t['id'] for t in TASKS] == [f'A{i:02}' for i in range(1,31)]
    assert HAND_CHECKED == set(REFERENCES) == set(HIDDEN)
    categories = {}
    total_public = total_hidden = 0
    for t in TASKS:
        ident=t['id']; categories[t['category']]=categories.get(t['category'],0)+1
        assert set(t)=={'id','category','title','spec','public'}
        assert len(t['public'])>=2 and len(HIDDEN[ident])>=5
        assert len(t['spec'])>500
        pub_inputs={json.dumps(c['input'],sort_keys=True,ensure_ascii=False) for c in t['public']}
        hid_inputs={json.dumps(c['input'],sort_keys=True,ensure_ascii=False) for c in HIDDEN[ident]}
        assert len(pub_inputs)==len(t['public']), (ident,'duplicate public input')
        assert len(hid_inputs)==len(HIDDEN[ident]), (ident,'duplicate hidden input')
        assert not pub_inputs & hid_inputs, (ident,'public/hidden overlap')
        for c in t['public']+HIDDEN[ident]:
            assert set(c)=={'input','expected'}
            assert isinstance(c['input'],dict)
            assert isinstance(c['expected'],dict), (ident,'non-object output')
            assert c['expected']==REFERENCES[ident](c['input']), ident
            encoded=json.dumps(c,ensure_ascii=False,allow_nan=False)
            assert json.loads(encoded)==c, (ident,'JSON roundtrip')
        total_public+=len(t['public']); total_hidden+=len(HIDDEN[ident])
    a10_task=next(t for t in TASKS if t['id']=='A10')
    transitions={(c['input'].get('from'),c['input'].get('to'))
                 for c in a10_task['public']+HIDDEN['A10']
                 if isinstance(c['input'].get('from'),str) and isinstance(c['input'].get('to'),str)}
    states=('draft','active','paused','closed')
    assert {(f,t) for f in states for t in states} <= transitions, 'A10 state-pair coverage'
    assert len(categories)==6 and all(n==5 for n in categories.values()), categories
    return {'tasks':len(TASKS),'categories':categories,'public_cases':total_public,
            'hidden_cases':total_hidden,'hand_assertions':len(HAND_CHECKED),
            'scalar_outputs':0,'duplicate_or_shared_inputs_within_task':0}


def main():
    results=audit()
    for path,value in ((PUBLIC/'tasks.json',TASKS),(PRIVATE/'hidden.json',HIDDEN)):
        path.write_text(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    for path in (Path(__file__),PUBLIC/'tasks.json',PRIVATE/'hidden.json'):
        results.setdefault('sha256',{})[str(path)]=hashlib.sha256(path.read_bytes()).hexdigest()
    print(json.dumps(results,ensure_ascii=False,indent=2))

if __name__=='__main__':
    main()
