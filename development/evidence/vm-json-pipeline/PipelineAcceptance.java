import io.github.ehlyzov.branchline.cli.*;
import io.github.ehlyzov.branchline.json.*;
import io.github.ehlyzov.branchline.contract.ContractValidationMode;
import java.util.*;
import java.lang.ref.WeakReference;

/** Parent-owned differential/independent acceptance, separate from implementation tests. */
public class PipelineAcceptance {
  static int checks;
  static ExperimentalJsonPipeline prepare(String source, boolean eager, JsonNumberMode numbers, JsonKeyMode keys, ContractValidationMode contracts) {
    return ExperimentalJsonPipeline.Companion.prepare(source, null,
      new ExperimentalJsonPipelineOptions(eager,new JsonParseOptions(numbers,keys),contracts));
  }
  static ExperimentalJsonPipeline prepare(String source,boolean eager) {
    return prepare(source,eager,JsonNumberMode.SAFE,JsonKeyMode.STRING,ContractValidationMode.OFF);
  }
  static String result(ExperimentalJsonPipeline p,String input) {
    try{return "OK:"+p.execute(input);}catch(Exception e){return "ERROR:"+e.getClass().getName()+":"+e.getMessage();}
  }
  static void check(String source,String input,String mode,String expected) {
    var eager=prepare(source,true);var selected=prepare(source,false);
    if(mode!=null&&!selected.getReport().getEffectiveMode().name().equals(mode))throw new AssertionError("Wrong plan: "+source+" "+selected.getReport());
    var a=result(eager,input);var b=result(selected,input);
    if(!a.equals(b))throw new AssertionError("Differential mismatch: "+source+" input="+input+" eager="+a+" selective="+b);
    if(expected!=null){
      if(!b.startsWith("OK:"))throw new AssertionError(b);
      var opts=new JsonParseOptions(JsonNumberMode.SAFE,JsonKeyMode.STRING);
      if(!Objects.equals(JsonInputKt.parseJsonValue(expected,opts),JsonInputKt.parseJsonValue(b.substring(3),opts)))throw new AssertionError("Expected "+expected+", actual "+b);
    }
    checks++;
  }
  static final String SIMPLE="TRANSFORM T { OUTPUT {value: input.keep} }";
  static Object[] detach(ExperimentalJsonPipeline p){
    var raw="{\"keep\":\"short\",\"unused\":\""+"x".repeat(1024*1024)+"\"}";
    return new Object[]{p.materialize(raw),new WeakReference<>(raw)};
  }
  @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
    check("TRANSFORM T { OUTPUT {a: input.x, b: row.x} }","{\"x\":1,\"input\":{\"x\":7},\"row\":{\"x\":9}}","SELECTIVE","{\"a\":7,\"b\":1}");
    check(SIMPLE,"{\"keep\":1,\"inp\\u0075t\":{\"keep\":7}}","SELECTIVE","{\"value\":7}");
    check(SIMPLE,"{\"keep\":1,\"input\":null}","SELECTIVE",null);
    check(SIMPLE,"{\"keep\":1,\"input\":5}","SELECTIVE",null);
    check("TRANSFORM T { LET input = {keep: 9}; OUTPUT {value: input.keep} }","{\"keep\":1}","EAGER","{\"value\":9}");
    check("TRANSFORM T { LET a = row; LET b = a; OUTPUT {value: b} }","{\"keep\":1,\"extra\":2}","EAGER","{\"value\":{\"keep\":1,\"extra\":2}}");
    check("TRANSFORM T { LET a = input.keep; LET b = a; OUTPUT {value: b} }","{\"keep\":3,\"unused\":[4,5]}","SELECTIVE","{\"value\":3}");
    check("TRANSFORM T { LET k = \"keep\"; OUTPUT {value: input[k]} }","{\"keep\":3,\"extra\":2}","EAGER","{\"value\":3}");
    check("TRANSFORM T { OUTPUT {value: LENGTH(row)} }","{\"keep\":3,\"extra\":2}","EAGER","{\"value\":2}");
    for(var value:List.of("null","[]","{}","[1,2]","{\"x\":1}","\"A\\uD83D\\uDE00\"","12"))
      check("TRANSFORM T { OUTPUT {value: LENGTH(input.keep)} }","{\"keep\":"+value+",\"unused\":{\"x\":[1,2]}}","SELECTIVE",null);
    for(var input:List.of("{}","{\"keep\":null}","{\"keep\":[]}","{\"keep\":{}}",""," \t\n","\u2003"))check(SIMPLE,input,"SELECTIVE",null);
    var bad=List.of(
      "{\"keep\":1,\"unused\":{\"a\":0,\"\\u0061\":1}}",
      "{\"keep\":null,\"keep\":1}","{\"keep\":1,\"unused\":[1,]}",
      "{\"keep\":1,\"unused\":{\"x\":1,}}","{\"keep\":1,\"unused\":\"\\q\"}",
      "{\"keep\":1,\"unused\":\"\\uD800\"}","{\"keep\":1,\"unused\":\"\\uD800\\u0041\"}",
      "{\"keep\":1,\"unused\":tru}","{\"keep\":1} trailing", "{\"keep\":1,\"unused\":01}",
      "{\"keep\":1,\"unused\":1e}","{\"keep\":1,\"unused\":1.}",
      "{\"keep\":1,\"unused\":{\"x\":1,\"x\":tru}}","[]","false","null");
    for(var input:bad){check(SIMPLE,input,"SELECTIVE",null);if(!result(prepare(SIMPLE,false),input).startsWith("ERROR:"))throw new AssertionError("Invalid accepted: "+input);}
    for(var mode:JsonNumberMode.values())for(var value:List.of("9007199254740993","9223372036854775808","1.2345678901234567","1e400","-9007199254740993")){
      var input="{\"keep\":1,\"unused\":"+value+"}";
      var a=result(prepare(SIMPLE,true,mode,JsonKeyMode.STRING,ContractValidationMode.OFF),input);
      var b=result(prepare(SIMPLE,false,mode,JsonKeyMode.STRING,ContractValidationMode.OFF),input);
      if(!a.equals(b))throw new AssertionError("Number mismatch "+mode+" "+value+" "+a+" "+b);checks++;
    }
    for(var value:List.of("\"\\uDC00\"","\"\\uD83D\\uDE00\"","\"a\\nb\\t\\/\\\\\""))check(SIMPLE,"{\"keep\":"+value+",\"unused\":"+value+"}","SELECTIVE",null);
    var keyInput="{\"keep\":{\"0\":1,\"01\":2,\"9223372036854775808\":3},\"unused\":1}";
    var a=result(prepare(SIMPLE,true,JsonNumberMode.SAFE,JsonKeyMode.NUMERIC,ContractValidationMode.OFF),keyInput);
    var b=result(prepare(SIMPLE,false,JsonNumberMode.SAFE,JsonKeyMode.NUMERIC,ContractValidationMode.OFF),keyInput);
    if(!a.equals(b))throw new AssertionError("Numeric-key mismatch");checks++;
    for(var mode:List.of(ContractValidationMode.STRICT,ContractValidationMode.WARN)){
      var e=prepare(SIMPLE,true,JsonNumberMode.SAFE,JsonKeyMode.STRING,mode);
      var s=prepare(SIMPLE,false,JsonNumberMode.SAFE,JsonKeyMode.STRING,mode);
      if(!s.getReport().getEffectiveMode().name().equals("EAGER"))throw new AssertionError("Contracts must fallback");
      if(!result(e,"{\"keep\":1,\"extra\":2}").equals(result(s,"{\"keep\":1,\"extra\":2}")))throw new AssertionError("Contract mismatch");checks++;
    }
    var selected=prepare(SIMPLE,false);
    try{selected.getReport().getSelectedRootKeys().clear();}catch(UnsupportedOperationException ignored){}
    if(!selected.execute("{\"keep\":4,\"unused\":2}").contains("4"))throw new AssertionError("Report mutation changed plan");checks++;
    var retained=detach(selected);for(int n=0;n<6&&((WeakReference<?>)retained[1]).get()!=null;n++){System.gc();Thread.sleep(20);}
    if(((WeakReference<?>)retained[1]).get()!=null)throw new AssertionError("Materialized result retained source text");
    if(!Objects.equals(((Map<?,?>)retained[0]).get("keep"),"short"))throw new AssertionError("Detached value mismatch");checks++;
    var first=selected.materialize("{\"keep\":{\"v\":1},\"extra\":2}");selected.execute("{\"keep\":9}");
    if(!Objects.equals(((Map<?,?>)first.get("keep")).get("v"),1L))throw new AssertionError("Reuse mutated earlier result");checks++;
    System.out.println("PASS parent acceptance checks="+checks+"; exact eager/selected outcomes, independent expected values, plan/fallback metadata, report isolation, detached input lifetime");
  }
}
