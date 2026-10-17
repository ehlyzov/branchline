"""Read VM method Code lengths directly from a classfile, without starting a JVM."""
import sys,zipfile,json,pathlib
jar=sys.argv[1] if len(sys.argv)>1 else 'vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar'
data=zipfile.ZipFile(jar).read('io/github/ehlyzov/branchline/vm/VM.class');pos=8
def take(n):
    global pos
    value=int.from_bytes(data[pos:pos+n],'big');pos+=n;return value
def skip(n):
    global pos
    pos+=n
pool=[None]*take(2);index=1
while index<len(pool):
    tag=take(1)
    if tag==1:
        length=take(2)
        # Method/attribute names are ASCII; unused strings may use modified UTF-8.
        pool[index]=data[pos:pos+length].decode('utf8',errors='replace');skip(length)
    elif tag in (3,4):skip(4)
    elif tag in (5,6):skip(8);index+=1
    elif tag in (7,8,16,19,20):skip(2)
    elif tag in (9,10,11,12,17,18):skip(4)
    elif tag==15:skip(3)
    else:raise ValueError(tag)
    index+=1
skip(6);skip(2*take(2))
for _ in range(take(2)):
    skip(6)
    for _ in range(take(2)):take(2);skip(take(4))
rows=[]
for _ in range(take(2)):
    take(2);name=pool[take(2)];take(2)
    for _ in range(take(2)):
        attribute=pool[take(2)];length=take(4);end=pos+length
        if attribute=='Code':
            take(2);take(2);rows.append(dict(method=name,codeLength=take(4)))
        pos=end
rows.sort(key=lambda x:x['codeLength'],reverse=True)
print(json.dumps(rows,indent=2))
