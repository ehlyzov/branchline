import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import java.lang.management.ManagementFactory;
public class RootRetentionCostAudit {
 static volatile Object sink;
 static Bytecode bc(List<Instruction> ins) {return Bytecode.Companion.fromInstructions(ins,List.of(),Map.of(),new TraceMetadata());}
 static void measure(int padding) {
  var ins=new ArrayList<Instruction>(); ins.add(new Instruction.JUMP(padding+1));
  for(int i=0;i<padding;i++)ins.add(Instruction.NOP.INSTANCE);
  ins.add(new Instruction.LOAD_VAR("fn"));ins.add(new Instruction.CALL_LAMBDA(0));
  var root=bc(ins);var vm=new VM();var envs=new ArrayList<HashMap<String,Object>>();
  for(int i=0;i<1000;i++) {var env=new HashMap<String,Object>(); env.put("fn",new LambdaValue(List.of(),bc(List.of(new Instruction.PUSH(i),Instruction.RETURN_VALUE.INSTANCE)),Map.of()));envs.add(env);}
  for(int i=0;i<2000;i++)sink=vm.execute(root,envs.get(i%1000));
  var alloc=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  long b=alloc.getThreadAllocatedBytes(Thread.currentThread().threadId()),t=System.nanoTime();
  for(int i=0;i<10000;i++)sink=vm.execute(root,envs.get(i%1000));
  System.out.printf("%d,%d,%d%n",padding,(System.nanoTime()-t)/10000,(alloc.getThreadAllocatedBytes(Thread.currentThread().threadId())-b)/10000);
 }
 public static void main(String[] args) {System.out.println("unexecuted_root_padding,ns_per_execution,allocated_bytes_per_execution");for(int n:new int[]{0,8192,32768,65536})measure(n);}
}
