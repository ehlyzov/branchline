package audit;
import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.ir.*;
import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(org.openjdk.jmh.annotations.Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value=2,jvmArgsAppend={"-Xms256m","-Xmx768m"})
@Warmup(iterations=2,time=200,timeUnit=TimeUnit.MILLISECONDS)
@Measurement(iterations=3,time=200,timeUnit=TimeUnit.MILLISECONDS)
public class VMPerformanceBench {
 @Param({"add:0","add:8192","call:0","call:8192","env:0","env:4096","append:1000","append:4000","set:1000","set:4000"}) public String scenario;
 private VM vm; private Bytecode code; private Map<String,Object> env;
 private Bytecode make(List<Instruction> ins){return Bytecode.Companion.fromInstructions(ins,List.of(),Map.of(),new TraceMetadata());}
 private Bytecode compile(String source){
  var program=new Parser(new Lexer(source).lex(),source).parse();
  var transform=(TransformDecl)program.getDecls().get(0);
  var ir=new ToIR(Map.of(),Map.of()).compile(transform.getBody().getStatements());
  return new io.github.ehlyzov.branchline.vm.Compiler(Map.of(),Map.of(),true).compile(ir);
 }
 @Setup(Level.Trial) public void setup(){
  var parts=scenario.split(":");String kind=parts[0];int n=Integer.parseInt(parts[1]);vm=new VM();env=new HashMap<>();
  if(kind.equals("append")||kind.equals("set")){
   var input=new ArrayList<Object>();for(int i=0;i<n;i++)input.add(kind.equals("set")?"k"+i:i);env.put("input",input);
   code=compile(kind.equals("append")?"TRANSFORM Main {\n LET out = []\n FOR EACH x IN input {\n out += x\n }\n OUTPUT {out:out}\n }":"TRANSFORM Main {\n LET out = {}\n FOR EACH x IN input {\n SET out.[x] = x\n }\n OUTPUT {out:out}\n }");
   var result=(Map<?,?>)vm.execute(code,env);var out=result.get("out");
   if(kind.equals("append")){if(!input.equals(out))throw new AssertionError(out);}
   else {var expected=new LinkedHashMap<Object,Object>();for(var key:input)expected.put(key,key);if(!expected.equals(out))throw new AssertionError(out);}
  }else{
   var ins=new ArrayList<Instruction>();int pad=kind.equals("env")?0:n;ins.add(new Instruction.JUMP(pad+1));for(int i=0;i<pad;i++)ins.add(Instruction.NOP.INSTANCE);
   if(kind.equals("add")){ins.add(new Instruction.PUSH(1));ins.add(new Instruction.PUSH(2));ins.add(Instruction.ADD.INSTANCE);}
   else {var body=make(List.of(new Instruction.PUSH(0),Instruction.RETURN_VALUE.INSTANCE));env.put("f",new LambdaValue(List.of(),body,Map.of()));if(kind.equals("env"))for(int i=0;i<n;i++)env.put("v"+i,i);ins.add(new Instruction.LOAD_VAR("f"));ins.add(new Instruction.CALL_LAMBDA(0));}
   code=make(ins);Object expected=kind.equals("add")?3:0;if(!expected.equals(vm.execute(code,env)))throw new AssertionError();
  }
 }
 @Benchmark public Object execute(){return vm.execute(code,env);}
}
