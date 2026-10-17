import java.util.*;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import io.github.ehlyzov.branchline.vm.*;

public class OverlayProbe {
  static class CountMap extends LinkedHashMap<String,Object> {
    long sizes, entries;
    @Override public int size(){sizes++; return super.size();}
    @Override public Set<Map.Entry<String,Object>> entrySet(){entries++;return super.entrySet();}
    void reset(){sizes=entries=0;}
  }
  static final com.sun.management.ThreadMXBean MEM=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  static long allocated(){return MEM.getThreadAllocatedBytes(Thread.currentThread().threadId());}
  static Bytecode bc(Instruction... ins){return Bytecode.Companion.fromInstructions(Arrays.asList(ins),List.of(),Map.of(),new TraceMetadata(Map.of(),Map.of(),Map.of()));}
  static Bytecode nested(int depth, boolean suspend){
    Bytecode leaf=suspend?bc(Instruction.SUSPEND.INSTANCE,new Instruction.LOAD_SCOPE("answer"),Instruction.RETURN_VALUE.INSTANCE):bc(new Instruction.LOAD_SCOPE("answer"),Instruction.RETURN_VALUE.INSTANCE);
    for(int i=0;i<depth;i++) leaf=bc(new Instruction.PUSH(new LambdaTemplate(List.of(),leaf)),new Instruction.CALL_LAMBDA(0),Instruction.RETURN_VALUE.INSTANCE);
    return leaf;
  }
  @SuppressWarnings("unchecked")
  static Map<String,Object> overlay(CountMap root,int depth) throws Exception {
    Constructor<?> constructor=Class.forName("io.github.ehlyzov.branchline.vm.VM$OverlayEnv").getDeclaredConstructor(Map.class);constructor.setAccessible(true);
    Map<String,Object> env=root;
    for(int i=0;i<depth;i++) env=(Map<String,Object>)constructor.newInstance(env);
    return env;
  }
  static void direct(int depth)throws Exception{
    CountMap root=new CountMap();root.put("answer",7);
    Map<String,Object> env=overlay(root,depth);root.reset();
    long a=allocated(),t=System.nanoTime();Set<?> entries=env.entrySet();long ns=System.nanoTime()-t,bytes=allocated()-a;
    if(entries.size()!=1 || !Objects.equals(env.get("answer"),7))throw new AssertionError();
    System.out.printf(Locale.ROOT,"entries,%d,%d,%d,%d,%.6f,1,7%n",depth,root.sizes,root.entries,bytes,ns/1e6);
  }
  static void vm(int depth,boolean suspend){
    CountMap root=new CountMap();root.put("answer",7);Bytecode program=nested(depth,suspend);VM vm=new VM();root.reset();
    long a=allocated(),t=System.nanoTime();Object out=vm.execute(program,root);long ns=System.nanoTime()-t,bytes=allocated()-a;
    int length=0;
    if(suspend){
      String snapshot=(String)((Map<?,?>)out).get("snapshot");length=snapshot.length();
      VM restored=VM.Companion.restoreFromSnapshot(snapshot,Map.of(),Map.of(),null);
      if(!restored.resume())throw new AssertionError("not finished");out=restored.resultOrNull();
    }
    if(!(out instanceof Number) || ((Number)out).intValue()!=7)throw new AssertionError("bad result "+out);
    System.out.printf(Locale.ROOT,"%s,%d,%d,%d,%d,%.6f,%d,%s%n",suspend?"suspend":"lookup",depth,root.sizes,root.entries,bytes,ns/1e6,length,out);
  }
  public static void main(String[] args)throws Exception {
    for(int i=0;i<10;i++){direct(3);vm(2,true);vm(2,false);}
    System.out.println("mode,depth,root_size_calls,root_entries_calls,allocated_bytes,elapsed_ms,snapshot_chars,result");
    for(int d:new int[]{0,1,2,3,4,6,8,10,12})direct(d);
    for(int d:new int[]{0,1,2,3,4,6,8,10,12})vm(d,true);
    for(int d:new int[]{0,4,8,12})vm(d,false);
  }
}
