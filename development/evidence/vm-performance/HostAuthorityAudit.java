import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import kotlin.jvm.functions.Function1;

public class HostAuthorityAudit {
 static Bytecode bc(Instruction... ins) {return Bytecode.Companion.fromInstructions(Arrays.asList(ins),List.of(),Map.of(),new TraceMetadata());}
 public static void main(String[] args) {
  var hosts = new HashMap<String,Function1<List<? extends Object>,Object>>();
  hosts.put("target", values -> "old");
  var root = bc(new Instruction.CALL_HOST(0,"target",0));
  var vm = new VM(hosts,Map.of(),null);
  Object before=vm.execute(root,new HashMap<>());
  hosts.put("target", values -> "new");
  Object after=vm.execute(root,new HashMap<>());
  System.out.println("same_root_replacement="+before+","+after);
  hosts.put("target", values -> "old");
  hosts.put("change", values -> { hosts.put("target", unused -> "new"); return null; });
  var child = bc(new Instruction.CALL_HOST(0,"change",0),Instruction.RETURN.INSTANCE);
  var nested = bc(new Instruction.PUSH(new LambdaTemplate(List.of(),child)),new Instruction.CALL_LAMBDA(0),Instruction.POP.INSTANCE,new Instruction.CALL_HOST(0,"target",0));
  System.out.println("replacement_during_child="+new VM(hosts,Map.of(),null).execute(nested,new HashMap<>()));
 }
}
