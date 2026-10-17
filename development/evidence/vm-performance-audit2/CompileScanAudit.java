import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.ir.*;
import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import java.lang.management.ManagementFactory;
public class CompileScanAudit {
 static volatile Object sink;
 static Token token=new Token(TokenType.IDENTIFIER,"audit",1,1);
 static long reads;
 static class CountedList extends AbstractList<IRNode> { List<IRNode> nodes;CountedList(List<IRNode> nodes){this.nodes=nodes;}public int size(){return nodes.size();} public IRNode get(int i){reads++;return nodes.get(i);} }
 static List<IRNode> make(int depth,int bodyWidth) {
  List<IRNode> body=new ArrayList<>();
  for(int i=0;i<bodyWidth;i++)body.add(new IRLet("x"+i,new StringExpr("v"+i,token)));
  for(int i=0;i<depth;i++)body=List.of(new IRIf(new BoolExpr(true,token),new CountedList(body),null));
  return body;
 }
 static void measure(int d,int width) {
  var body=make(d,width);reads=0;sink=new io.github.ehlyzov.branchline.vm.Compiler().compile(body);long scans=reads;
  for(int i=0;i<25;i++)sink=new io.github.ehlyzov.branchline.vm.Compiler().compile(body);
  long[] timings=new long[5];var alloc=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long bytes=0;
  for(int r=0;r<5;r++) {long before=alloc.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();for(int i=0;i<15;i++)sink=new io.github.ehlyzov.branchline.vm.Compiler().compile(body);timings[r]=(System.nanoTime()-start)/15;bytes=(alloc.getThreadAllocatedBytes(Thread.currentThread().threadId())-before)/15;}
  Arrays.sort(timings);System.out.printf("%d,%d,%d,%d,%d,%d%n",d,width,((Bytecode)sink).size(),scans,timings[2],bytes);
 }
 public static void main(String[]args){System.out.println("depth,leaf_statements,instructions,body_list_reads,median_ns_compile,bytes_compile");for(int d:new int[]{1,8,32,128})measure(d,4096);}
}
