import java.util.*;
import io.github.ehlyzov.branchline.cli.*;
import io.github.ehlyzov.branchline.json.*;
import io.github.ehlyzov.branchline.std.StdLib;
import io.github.ehlyzov.branchline.vm.*;

/** Parent-held semantic checks, separate from implementation-agent fixtures. */
public class PreparedBlockAcceptance {
  static int checks;
  static Object json(Object v) {return JsonInputKt.parseJsonValue(JsonInteropKt.formatJson(v,false,JsonNumberMode.EXTENDED),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));}
  static Map<String,Object> env(Map<String,Object> input) {var e=new LinkedHashMap<String,Object>();e.put("input",input);e.putAll(input);e.put("row",input);return e;}
  static String outcome(VM vm,Bytecode bc,PreparedBlockPlan plan,Map<String,Object> e) {try {return "OK:"+JsonInteropKt.formatJson(plan==null?vm.execute(bc,e):vm.executeBlocks(plan,e),false,JsonNumberMode.EXTENDED);}catch(Exception x){return "ERR:"+x.getClass().getName()+":"+x.getMessage();}}
  static boolean equalEnv(Map<String,Object> a,Map<String,Object> b){ if(!a.keySet().equals(b.keySet()))return false; for(String k:a.keySet()){Object x=a.get(k),y=b.get(k); if(x instanceof LambdaValue l){if(!(y instanceof LambdaValue r)||l.getCaptured()!=a||r.getCaptured()!=b||!l.getParams().equals(r.getParams()))return false;}else if(!Objects.equals(x,y))return false;}return true;}
  static void check(String source,Map<String,Object> input,Object expected,boolean optimized,boolean snapshot) {
    var runtime=new BranchlineProgram(source,null);var transform=runtime.selectTransform(null);var bc=runtime.compileBytecode(transform);
    Object pristine=json(input);var e=env(input);var ref=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);String want=outcome(ref,bc,null,e);
    if(expected!=null){if(!want.startsWith("OK:"))throw new AssertionError(want);Object actual=JsonInputKt.parseJsonValue(want.substring(3),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));if(!Objects.equals(actual,json(expected)))throw new AssertionError("Independent expected mismatch "+want+" expected="+expected);if(!Objects.equals(json(runtime.execute(transform,input)),json(expected)))throw new AssertionError("Interpreter expected mismatch");}
    Object snap=snapshot?JsonInputKt.parseJsonValue(ref.snapshotJson(),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING)):null;
    for(boolean optimize:new boolean[]{false,true}) {
      var vm=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);var opts=optimize;var plan=vm.prepareBlocks(bc,optimize);var actualEnv=env(input);String actual=outcome(vm,bc,plan,actualEnv);
      if(!Objects.equals(want,actual))throw new AssertionError("Outcome "+opts+" source="+source+" expected="+want+" actual="+actual);
      if(!equalEnv(e,actualEnv)||!new ArrayList<>(e.keySet()).equals(new ArrayList<>(actualEnv.keySet())))throw new AssertionError("Environment/order "+opts+" "+source);
      if(snapshot&&!Objects.equals(snap,JsonInputKt.parseJsonValue(vm.snapshotJson(),new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING))))throw new AssertionError("Snapshot "+opts+" "+source);
      if(optimized&&(!vm.getPreparedBlockExecuted()||vm.getPreparedBlockOptimized()!=optimize||vm.getPreparedBlockFallbackReason()!=null))throw new AssertionError("Unexpected mode/fallback "+vm.getPreparedBlockFallbackReason());
      if(!Objects.equals(pristine,json(input)))throw new AssertionError("Input mutated");
      checks++;
    }
  }

  static void reuseChecks() {
    String source="TRANSFORM T { LET acc = 0; FOR EACH r IN input.rows { SET acc = acc + r.v; } IF input.fail THEN { LET bad = input.missing.deep; } OUTPUT {value: acc} }";
    var runtime=new BranchlineProgram(source,null);var bc=runtime.compileBytecode(runtime.selectTransform(null));
    for(boolean optimize:new boolean[]{false,true}) {
      var vm=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);var plan=vm.prepareBlocks(bc,optimize);var ref=new VM(StdLib.INSTANCE.getFns(),Map.of(),null);
      for(int i=0;i<20;i++) {
        var rows=new ArrayList<Object>();for(int j=0;j<i%7;j++)rows.add(new LinkedHashMap<>(Map.of("v",(long)(i-j))));
        var input=new LinkedHashMap<String,Object>();input.put("rows",rows);input.put("fail",i%3==0);
        var wantEnv=env(input);var actualEnv=env(input);String want=outcome(ref,bc,null,wantEnv),actual=outcome(vm,bc,plan,actualEnv);
        if(want.startsWith("ERR:")!=(i%3==0))throw new AssertionError("Bad recovery fixture "+want);
        if(!want.equals(actual)||!equalEnv(wantEnv,actualEnv)||!new ArrayList<>(wantEnv.keySet()).equals(new ArrayList<>(actualEnv.keySet()))||!jsonText(ref.snapshotJson()).equals(jsonText(vm.snapshotJson())))throw new AssertionError("Reuse/error recovery "+i+" optimize="+optimize+" expected="+want+" actual="+actual);
        if(!vm.getPreparedBlockExecuted()||vm.getPreparedBlockFallbackReason()!=null)throw new AssertionError("Reuse fallback");
        checks++;
      }
    }
  }
  static Object jsonText(String s) {return JsonInputKt.parseJsonValue(s,new JsonParseOptions(JsonNumberMode.EXTENDED,JsonKeyMode.STRING));}
  static void hostChecks() {
    String source="TRANSFORM T { LET total = 0; FOR EACH r IN input.rows { SET total = total + r.v; } LET observed = LENGTH(input.rows); SET total = total + 1; OUTPUT {value: total, observed: observed} }";
    var runtime=new BranchlineProgram(source,null);var bc=runtime.compileBytecode(runtime.selectTransform(null));
    for(boolean throwsHost:new boolean[]{false,true}) for(boolean optimize:new boolean[]{false,true}) {
      var results=new ArrayList<String>();var envs=new ArrayList<Map<String,Object>>();var snapshots=new ArrayList<Object>();var hostSnapshots=new ArrayList<Object>();var seen=new ArrayList<Object>();
      for(int variant=0;variant<2;variant++) {
        var input=new LinkedHashMap<String,Object>();input.put("rows",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("v",3L)),new LinkedHashMap<>(Map.of("v",4L)))));
        var e=env(input);var holder=new VM[1];var hosts=new HashMap<>(StdLib.INSTANCE.getFns());
        hosts.put("LENGTH",args->{seen.add(new LinkedHashMap<>(e));hostSnapshots.add(jsonText(holder[0].snapshotJson()));e.remove("total");e.put("hostAdded",null);e.put("total",100L);if(throwsHost)throw new IllegalArgumentException("parent host failure");return 2;});
        var vm=new VM(hosts,Map.of(),null);holder[0]=vm;var plan=variant==0?null:vm.prepareBlocks(bc,optimize);
        results.add(outcome(vm,bc,plan,e));envs.add(e);snapshots.add(jsonText(vm.snapshotJson()));
      }
      if(!results.get(0).equals(results.get(1))||!equalEnv(envs.get(0),envs.get(1))||!new ArrayList<>(envs.get(0).keySet()).equals(new ArrayList<>(envs.get(1).keySet()))||!snapshots.get(0).equals(snapshots.get(1))||!hostSnapshots.get(0).equals(hostSnapshots.get(1))||!seen.get(0).equals(seen.get(1)))throw new AssertionError("Host boundary optimize="+optimize+" throws="+throwsHost+" results="+results);
      checks++;
    }
  }
  public static void main(String[] args){
    Random random=new Random(61018);
    for(int trial=0;trial<80;trial++){
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
    reuseChecks();
    hostChecks();
    System.out.println("PASS parent prepared-block checks="+checks+"; 80 seeded input sets, independent interpreter/reference results, both modes, exact errors/final env/order/snapshot, reuse/error recovery/host observation/fallback cases");
  }
}
