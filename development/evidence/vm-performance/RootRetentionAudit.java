import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
public class RootRetentionAudit {
 static Bytecode bc(List<Instruction> ins) {return Bytecode.Companion.fromInstructions(ins,List.of(),Map.of(),new TraceMetadata());}
 public static void main(String[] args) throws Exception {
  var ins=new ArrayList<Instruction>();
  ins.add(new Instruction.JUMP(8193));
  for(int i=0;i<8192;i++)ins.add(Instruction.NOP.INSTANCE);
  ins.add(new Instruction.LOAD_VAR("fn")); ins.add(new Instruction.CALL_LAMBDA(0));
  var root=bc(ins); var vm=new VM();
  var field=VM.class.getDeclaredField("preparedProgram"); field.setAccessible(true);
  Object previous=null; int preparations=0;
  for(int i=0;i<1000;i++) {
   var body=bc(List.of(new Instruction.PUSH(i),Instruction.RETURN_VALUE.INSTANCE));
   var env=new HashMap<String,Object>(); env.put("fn",new LambdaValue(List.of(),body,Map.of()));
   if(!vm.execute(root,env).equals(i))throw new AssertionError();
   var prepared=field.get(vm); if(previous!=prepared)preparations++; previous=prepared;
  }
  System.out.println("same_root_preparations_for_1000_transient_callees="+preparations);
 }
}
