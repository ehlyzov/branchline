import java.util.*;
import java.lang.reflect.*;
import io.github.ehlyzov.branchline.vm.*;
import io.github.ehlyzov.branchline.ir.Exec;

public class NativePairValidation {
 static Object field(Object object,String name)throws Exception{
  var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
 }
 static Map<String,Object> expected(String kind,String size,int orders,int items){
  if(kind.equals("path"))return Map.of("customer","Customer-"+size,"firstSku","SKU-0-0","firstQty",1,"secondSku","SKU-0-1");
  if(kind.equals("transform")){int total=0;for(int i=0;i<orders;i++)total+=50+i%250;return Map.of("orderCount",orders,"total",total);}
  var expensive=new ArrayList<Integer>();var skus=new ArrayList<String>();
  for(int i=0;i<orders;i++)if(50+i%250>150)expensive.add(i);
  for(int i=0;i<items;i++)skus.add("SKU-0-"+i);
  return Map.of("expensive",expensive,"skus",skus);
 }
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception{
  var klass=Class.forName("io.github.ehlyzov.branchline.benchmarks.InterpreterTransformBenchmark");
  for(String size:List.of("small","medium","large")){
   var v=new VMTransformBenchmark();v.setDataset(size);v.setup();
   var i=klass.getConstructor().newInstance();klass.getMethod("setDataset",String.class).invoke(i,size);klass.getMethod("setCollectionType",String.class).invoke(i,"hash");klass.getMethod("setup").invoke(i);
   Object input=field(v,"input");if(!input.equals(field(i,"input")))throw new AssertionError("Different input datasets");
   int n=size.equals("small")?10:size.equals("medium")?100:500;
   int m=size.equals("small")?5:size.equals("medium")?10:25;
   for(String kind:List.of("path","array","transform")){
    v.resetEnv();klass.getMethod("resetEnv").invoke(i);
    var ve=(Map<String,Object>)field(v,"env");var ie=(Map<String,Object>)field(i,"env");
    if(!ve.equals(ie))throw new AssertionError("Different initial environments");
    Object a=((VM)field(v,"vm")).execute((Bytecode)field(v,kind+"Bytecode"),ve);
    Object b=((Exec)field(i,kind+"Exec")).run(ie,false);
    Object expected=expected(kind,size,n,m);
    if(!expected.equals(a)||!expected.equals(b))throw new AssertionError(kind+":"+size+" expected="+expected+" vm="+a+" interpreter="+b);
    if(!input.equals(field(i,"input")))throw new AssertionError("Input datasets diverged");
    System.out.println("PASS existing "+kind+":"+size+" same input/env; both outputs match independent expected");
   }
  }
 }
}
