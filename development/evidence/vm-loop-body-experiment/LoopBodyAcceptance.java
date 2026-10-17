import java.util.*;
import io.github.ehlyzov.branchline.cli.*;
import io.github.ehlyzov.branchline.json.*;
import io.github.ehlyzov.branchline.std.StdLib;
import io.github.ehlyzov.branchline.vm.*;

/** Parent-held semantic checks, separate from implementation-agent fixtures. */
public class LoopBodyAcceptance {
  static int checks;
  static Object json(Object v) {return JsonInputKt.parseJsonValue(JsonInteropKt.formatJson(v,false,JsonNumberMode.EXTENDED),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));}
  static Map<String,Object> env(Map<String,Object> input) {var e=new LinkedHashMap<String,Object>();e.put("input",input);e.putAll(input);e.put("row",input);return e;}
  static String outcome(VM vm,Bytecode bc,LoopBodyPlan plan,Map<String,Object> e) {try {return "OK:"+JsonInteropKt.formatJson(plan==null?vm.execute(bc,e):vm.executeLoopBodies(plan,e),false,JsonNumberMode.EXTENDED);}catch(Exception x){return "ERR:"+x.getClass().getName()+":"+x.getMessage();}}
  static boolean equalEnv(Map<String,Object> a,Map<String,Object> b){ if(!a.keySet().equals(b.keySet()))return false; for(String k:a.keySet()){Object x=a.get(k),y=b.get(k); if(x instanceof LambdaValue l){if(!(y instanceof LambdaValue r)||l.getCaptured()!=a||r.getCaptured()!=b||!l.getParams().equals(r.getParams()))return false;}else if(!Objects.equals(x,y))return false;}return true;}
  static Object jsonText(String s) {return JsonInputKt.parseJsonValue(s,new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));}
  static void check(String source,Map<String,Object> input,Object expected,boolean mustCompile,boolean mustEnter,boolean snapshot) {
    var runtime=new BranchlineProgram(source,null);var transform=runtime.selectTransform(null);var bc=runtime.compileBytecode(transform);
    Object pristine=json(input);var e=env(input);var ref=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);String want=outcome(ref,bc,null,e);
    if(expected!=null){if(!want.startsWith("OK:"))throw new AssertionError(want);if(!Objects.equals(jsonText(want.substring(3)),json(expected)))throw new AssertionError("Independent expected mismatch "+want+" expected="+expected);if(!Objects.equals(json(runtime.execute(transform,input)),json(expected)))throw new AssertionError("Interpreter expected mismatch "+source);}
    var vm=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);var plan=JvmLoopCompiler.prepare(bc);var actualEnv=env(input);String actual=outcome(vm,bc,plan,actualEnv);
    if(!want.equals(actual))throw new AssertionError("Outcome source="+source+" expected="+want+" actual="+actual);
    if(!equalEnv(e,actualEnv)||!new ArrayList<>(e.keySet()).equals(new ArrayList<>(actualEnv.keySet())))throw new AssertionError("Environment/order "+source);
    if(snapshot&&!Objects.equals(jsonText(ref.snapshotJson()),jsonText(vm.snapshotJson())))throw new AssertionError("Snapshot "+source+" expected="+ref.snapshotJson()+" actual="+vm.snapshotJson());
    if(mustCompile&&plan.getLoweredLoopCount()!=1)throw new AssertionError("Required shape not compiled: "+plan.getFallbackReason()+" source="+source);
    if(mustEnter&&(!vm.getLoopBodyExecuted()||vm.getLoopBodyEntries()<1||vm.getLoopBodyFallbackReason()!=null))throw new AssertionError("Required kernel not executed "+vm.getLoopBodyFallbackReason());
    if(!Objects.equals(pristine,json(input)))throw new AssertionError("Input mutated");
    checks++;
  }
  static LinkedHashMap<String,Object> obj(Object... entries){var m=new LinkedHashMap<String,Object>();for(int i=0;i<entries.length;i+=2)m.put((String)entries[i],entries[i+1]);return m;}
  static void reuseChecks() {
    String source="TRANSFORM T { OUTPUT {rows: [{id: r.id, v: r.meta.value * 2} FOR EACH r IN input.rows WHERE r.id >= 0]} }";
    var runtime=new BranchlineProgram(source,null);var bc=runtime.compileBytecode(runtime.selectTransform(null));var vm=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);var plan=JvmLoopCompiler.prepare(bc);var ref=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);
    if(plan.getLoweredLoopCount()!=1)throw new AssertionError("Reuse kernel not compiled");
    for(int trial=0;trial<30;trial++) {
      var rows=new ArrayList<Object>();rows.add(obj("id",1L,"meta",obj("value",4L)));rows.add(obj("id",2L,"meta",trial%3==0?null:obj("value",(long)trial)));
      var input=obj("rows",rows);var e=env(input);var a=env(input);e.put("r",trial%2==0?null:"outer");a.put("r",trial%2==0?null:"outer");
      String want=outcome(ref,bc,null,e),actual=outcome(vm,bc,plan,a);
      if(want.startsWith("ERR:")!=(trial%3==0)||!want.equals(actual)||!equalEnv(e,a)||!new ArrayList<>(e.keySet()).equals(new ArrayList<>(a.keySet()))||!jsonText(ref.snapshotJson()).equals(jsonText(vm.snapshotJson())))throw new AssertionError("Recovery "+trial+" expected="+want+" actual="+actual);
      if(!vm.getLoopBodyExecuted())throw new AssertionError("Reuse silently bypassed kernel");checks++;
    }
  }
  public static void main(String[] args) {
    Random random=new Random(61019);
    for(int trial=0;trial<80;trial++) {
      var rows=new ArrayList<Object>();var filtered=new ArrayList<Object>();var scalars=new ArrayList<Object>();var unfiltered=new ArrayList<Object>();int n=random.nextInt(35);long cut=trial%11-5;
      for(int i=0;i<n;i++) {long v=random.nextInt(201)-100;rows.add(obj("v",v,"id",(long)i,"meta",obj("tag","r"+i)));if(v>cut){filtered.add(obj("key",(long)i,"amount",v*4-i,"tag","r"+i));scalars.add(v+7);}unfiltered.add(obj("value",v,"key",(long)i));}
      var input=obj("rows",rows,"meta",obj("name","case-"+trial));
      check("TRANSFORM T { OUTPUT {rows: [{key: entry.id, amount: entry.v * 4 - entry.id, tag: entry.meta.tag} FOR EACH entry IN input.rows WHERE entry.v > "+cut+"]} }",input,obj("rows",filtered),true,n>0,true);
      check("TRANSFORM T { OUTPUT {values: [item.v + 7 FOR EACH item IN input.rows WHERE item.v > "+cut+"]} }",input,obj("values",scalars),true,n>0,true);
      check("TRANSFORM T { OUTPUT {rows: [{value: x.v, key: x.id} FOR EACH x IN input.rows]} }",input,obj("rows",unfiltered),true,n>0,true);
    }
    for(Object v:Arrays.asList(null,"bad",Long.MAX_VALUE,Long.MIN_VALUE,1.25,0L,-7L)) {
      var input=obj("rows",new ArrayList<>(List.of(obj("v",3L),obj("v",v))));
      for(String expr:List.of("r.v + 1","r.v * 3","7 / r.v"))check("TRANSFORM T { OUTPUT {rows: ["+expr+" FOR EACH r IN input.rows]} }",input,null,true,true,true);
    }
    for(Object rows:Arrays.asList(null,"bad",7L,new ArrayList<>()))check("TRANSFORM T { OUTPUT {rows: [r.v FOR EACH r IN input.rows]} }",obj("rows",rows),null,true,false,true);
    for(String source:List.of("TRANSFORM T { OUTPUT {value: input.x} }","TRANSFORM T { LET acc = 0; FOR EACH r IN input.rows { SET acc = acc + r; } OUTPUT {value: acc} }","TRANSFORM T { OUTPUT {rows: [[v FOR EACH v IN r] FOR EACH r IN input.nested]} }"))check(source,obj("x",7L,"rows",new ArrayList<>(List.of(1L,2L)),"nested",new ArrayList<>(List.of(new ArrayList<>(List.of(1L,2L))))),null,false,false,true);
    check("TRANSFORM T { LET twice = (x) -> x * 2; OUTPUT {value: twice(9)} }",new LinkedHashMap<>(),obj("value",18),false,false,false);
    reuseChecks();
    System.out.println("PASS parent loop-body checks="+checks+"; seeded independent outputs/interpreter, varied loop expressions, exact VM errors/env/order/snapshots, overflow/null and recovery/fallback");
  }
}
