import java.util.*;
import io.github.ehlyzov.branchline.cli.*;
import io.github.ehlyzov.branchline.json.*;
import io.github.ehlyzov.branchline.std.StdLib;
import io.github.ehlyzov.branchline.vm.*;

/** Parent-held semantic checks, separate from implementation-agent fixtures. */
public class FactorialAcceptance {
  static int checks;
  static Object json(Object v) {return JsonInputKt.parseJsonValue(JsonInteropKt.formatJson(v,false,JsonNumberMode.EXTENDED),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));}
  static Map<String,Object> env(Map<String,Object> input) {var e=new LinkedHashMap<String,Object>();e.put("input",input);e.putAll(input);e.put("row",input);return e;}
  static String outcome(VM vm,Bytecode bc,VMExperimentalPlan plan,Map<String,Object> e) {try {return "OK:"+JsonInteropKt.formatJson(plan==null?vm.execute(bc,e):vm.executeExperimental(plan,e),false,JsonNumberMode.EXTENDED);}catch(Exception x){return "ERR:"+x.getClass().getName()+":"+x.getMessage();}}
  static boolean equalEnv(Map<String,Object> a,Map<String,Object> b){ if(!a.keySet().equals(b.keySet()))return false; for(String k:a.keySet()){Object x=a.get(k),y=b.get(k); if(x instanceof LambdaValue l){if(!(y instanceof LambdaValue r)||l.getCaptured()!=a||r.getCaptured()!=b||!l.getParams().equals(r.getParams()))return false;}else if(!Objects.equals(x,y))return false;}return true;}
  static void check(String source,Map<String,Object> input,Object expected,boolean optimized,boolean snapshot) {
    var runtime=new BranchlineProgram(source,null);var transform=runtime.selectTransform(null);var bc=runtime.compileBytecode(transform);
    var e=env(input);var ref=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);String want=outcome(ref,bc,null,e);
    if(expected!=null){if(!want.startsWith("OK:"))throw new AssertionError(want);Object actual=JsonInputKt.parseJsonValue(want.substring(3),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));if(!Objects.equals(actual,json(expected)))throw new AssertionError("Independent expected mismatch "+want+" expected="+expected);if(!Objects.equals(json(runtime.execute(transform,input)),json(expected)))throw new AssertionError("Interpreter expected mismatch");}
    Object snap=snapshot?JsonInputKt.parseJsonValue(ref.snapshotJson(),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING)):null;
    for(boolean diagnostics:new boolean[]{false,true}) for(var flags:new boolean[][]{{false,false},{true,false},{false,true},{true,true}}){
      var vm=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);var opts=new VMExperimentalOptions(flags[0],flags[1],diagnostics);var plan=vm.prepareExperimental(bc,opts);var actualEnv=env(input);String actual=outcome(vm,bc,plan,actualEnv);
      if(!Objects.equals(want,actual))throw new AssertionError("Outcome "+opts+" source="+source+" expected="+want+" actual="+actual);
      if(!equalEnv(e,actualEnv)||!new ArrayList<>(e.keySet()).equals(new ArrayList<>(actualEnv.keySet())))throw new AssertionError("Environment/order "+opts+" "+source);
      if(snapshot&&!Objects.equals(snap,JsonInputKt.parseJsonValue(vm.snapshotJson(),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING))))throw new AssertionError("Snapshot "+opts+" "+source);
      if(optimized&&(flags[0]||flags[1])&&vm.getExperimentalReport().getFallbackReason()!=null)throw new AssertionError("Unexpected fallback "+vm.getExperimentalReport());
      if(!diagnostics&&(vm.getExperimentalReport().getSlotReads()!=0||vm.getExperimentalReport().getFusedDispatches()!=0))throw new AssertionError("Disabled diagnostics counted");
      checks++;
    }
  }
  public static void main(String[] args){
    Random random=new Random(61017);
    for(int trial=0;trial<40;trial++){
      var rows=new ArrayList<Object>();long total=0;var filtered=new ArrayList<Object>();int n=random.nextInt(35);
      for(int i=0;i<n;i++){long v=random.nextInt(201)-100;total+=v;rows.add(new LinkedHashMap<String,Object>(Map.of("v",v,"id",(long)i)));if(v>17)filtered.add(new LinkedHashMap<String,Object>(Map.of("id",(long)i,"score",v*3-i)));}
      var input=new LinkedHashMap<String,Object>();input.put("rows",rows);input.put("meta",new LinkedHashMap<String,Object>(Map.of("name","case-"+trial)));
      check("TRANSFORM T { LET acc = 0; FOR EACH r IN input.rows { SET acc = acc + r.v; } OUTPUT {value: acc} }",input,Map.of("value",total),true,true);
      check("TRANSFORM T { OUTPUT {value: [{id: r.id, score: r.v * 3 - r.id} FOR EACH r IN input.rows WHERE r.v > 17]} }",input,Map.of("value",filtered),true,true);
      check("TRANSFORM T { LET name = input.meta.name; OUTPUT {value: name} }",input,Map.of("value","case-"+trial),true,true);
    }
    var nums=new LinkedHashMap<String,Object>();nums.put("x",Long.MAX_VALUE);nums.put("rows",new ArrayList<>(List.of(1L,2L,3L)));
    for(String body:List.of("LET a = input.x + 1; OUTPUT {value: a}","LET a = 7; LET b = 1 / 0; OUTPUT b","LET a = input.missing.deep; OUTPUT {value: a}","LET a = null; OUTPUT {value: a}","LET a = 2; FOR EACH a IN input.rows { LET seen = a; } OUTPUT {value: a}"))check("TRANSFORM T { "+body+" }",nums,null,false,true);
    check("TRANSFORM T { LET twice = (x) -> x * 2; OUTPUT {value: twice(9)} }",new LinkedHashMap<>(),Map.of("value",18),false,false);
    check("TRANSFORM T { OUTPUT SUSPEND 0 }",new LinkedHashMap<>(Map.of("unused",3L)),null,false,true);
    System.out.println("PASS parent factorial checks="+checks+"; 40 seeded input sets, independent interpreter/reference results, all 4 flags x diagnostics, exact errors/final env/order/snapshot, fallback cases");
  }
}
