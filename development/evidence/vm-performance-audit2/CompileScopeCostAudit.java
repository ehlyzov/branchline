import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.ir.*;
import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import java.lang.management.ManagementFactory;
public class CompileScopeCostAudit {
 static volatile Object sink;
 static Token token=new Token(TokenType.IDENTIFIER,"audit",1,1);
 static List<IRNode> make(int n) {
  List<IRNode> body=new ArrayList<>();List<Expr> result=new ArrayList<>();
  for(int i=0;i<n;i++) {body.add(new IRLet("field"+i,new ArrayExpr(List.of(),token)));result.add(new IdentifierExpr("field"+i,token));}
  for(int i=0;i<n;i++)body.add(new IRIf(new BoolExpr(true,token),List.of(new IRPlusAssignVar("field"+i,new StringExpr("ok",token))),null));
  body.add(new IRExprOutput(new ArrayExpr(result,token)));return body;
 }
 static void measure(int n) {
  var body=make(n);for(int i=0;i<5;i++)sink=new io.github.ehlyzov.branchline.vm.Compiler().compile(body);
  long[] timings=new long[5];var alloc=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long bytes=0;
  for(int r=0;r<5;r++) {long before=alloc.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();for(int i=0;i<3;i++)sink=new io.github.ehlyzov.branchline.vm.Compiler().compile(body);timings[r]=(System.nanoTime()-start)/3;bytes=(alloc.getThreadAllocatedBytes(Thread.currentThread().threadId())-before)/3;}
  var bc=(Bytecode)sink;var result=(List<?>)new VM().execute(bc,new HashMap<>());if(result.size()!=n||!result.stream().allMatch(List.of("ok")::equals))throw new AssertionError(result);
  Arrays.sort(timings);System.out.printf("%d,%d,%d,%d%n",n,bc.size(),timings[2],bytes);
 }
 public static void main(String[]args){System.out.println("conditional_array_accumulators,instructions,median_ns_compile,bytes_compile");for(int n:new int[]{128,512,1024})measure(n);}
}
