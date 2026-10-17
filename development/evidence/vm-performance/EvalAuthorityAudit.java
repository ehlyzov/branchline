import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.vm.*;
import java.util.*;

public class EvalAuthorityAudit {
 static volatile Object sink;
 static Token token = new Token(TokenType.IDENTIFIER,"test",1,1);
 static Expr expr = new StringExpr("ok",token);
 static Map<String,FuncDecl> authority(int count,int width) {
  var map = new LinkedHashMap<String,FuncDecl>();
  for(int i=0;i<count;i++) {
   var fields = new ArrayList<Expr>();
   for(int j=0;j<width;j++) fields.add(new StringExpr("field"+j,token));
   map.put("unused"+i,new FuncDecl("unused"+i,List.of(),new ExprBody(new ArrayExpr(fields,token),token),token));
  }
  return map;
 }
 static void measure(int n,int width,boolean useVM,boolean prepared) {
  var eval = new VMEval(authority(n,width),Map.of(),Map.of(),null,VMFallbackPolicy.NONE,useVM,null);
  var env = new HashMap<String,Object>();
  var ready = prepared?eval.prepare(expr):null;
  for(int i=0;i<300;i++) sink=prepared?ready.eval(env):eval.eval(expr,env);
  long[] timings = new long[5];
  for(int r=0;r<5;r++) {
   long start=System.nanoTime();
   for(int i=0;i<500;i++)sink=prepared?ready.eval(env):eval.eval(expr,env);
   timings[r]=(System.nanoTime()-start)/500;
  }
  Arrays.sort(timings);
  if(!sink.equals("ok"))throw new AssertionError(sink);
  System.out.printf("%s,%s,%d,%d,%d%n",useVM?"vm":"disabled",prepared?"prepared":"implicit",n,width,timings[2]);
 }
 public static void main(String[] args) {
  System.out.println("mode,adapter,unused_functions,function_ast_width,median_ns_per_eval");
  for(boolean vm:new boolean[]{true,false})for(int n:new int[]{0,128,1024})measure(n,256,vm,false);
  if(args.length>0)for(boolean vm:new boolean[]{true,false})for(int n:new int[]{0,128,1024})measure(n,256,vm,true);
 }
}
