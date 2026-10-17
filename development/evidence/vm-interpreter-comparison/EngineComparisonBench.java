package audit;

import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.ir.*;
import io.github.ehlyzov.branchline.std.StdLib;
import io.github.ehlyzov.branchline.vm.*;
import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(org.openjdk.jmh.annotations.Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value=2,jvmArgsAppend={"-Xms256m","-Xmx768m"})
@Warmup(iterations=3,time=300,timeUnit=TimeUnit.MILLISECONDS)
@Measurement(iterations=4,time=300,timeUnit=TimeUnit.MILLISECONDS)
public class EngineComparisonBench {
    @Param({"paths:1000", "sum:1000", "filter:1000", "project:1000", "lambda:1000", "append:1000", "set:1000",
            "sum:4000", "filter:4000", "project:4000", "lambda:4000", "append:4000", "set:4000"})
    public String scenario;
    @Param({"vm", "interpreter"}) public String engine;
    private VM vm;
    private Bytecode bytecode;
    private Exec interpreter;
    private Map<String,Object> input;

    public static Map<String,Object> makeInput(int n) {
        var rows=new ArrayList<Object>();
        for(int i=0;i<n;i++) {
            var row=new LinkedHashMap<String,Object>();
            row.put("id", i); row.put("key", "k"+i); row.put("amount", 1+i%100);
            row.put("active", i%2==0); row.put("nested", Map.of("tag", "tag"+i%7)); rows.add(row);
        }
        return Map.of("rows",rows);
    }

    public static String source(String kind) {
        String body=switch(kind) {
            case "paths" -> "OUTPUT {result:{first:input.rows[0].nested.tag, second:input.rows[1].id, amount:input.rows[2].amount}}";
            case "sum" -> "LET total = 0\n FOR EACH r IN input.rows {\n SET total = total + r.amount\n }\n OUTPUT {result:total}";
            case "filter" -> "OUTPUT {result:[r.id FOR EACH r IN input.rows WHERE r.active]}";
            case "project" -> "OUTPUT {result:[{id:r.id, value:r.amount * 2, label:r.nested.tag} FOR EACH r IN input.rows]}";
            case "lambda" -> "LET twice = (x) -> x * 2\n OUTPUT {result:[twice(r.amount) FOR EACH r IN input.rows]}";
            case "append" -> "LET out = []\n FOR EACH r IN input.rows {\n out += r.id\n }\n OUTPUT {result:out}";
            case "set" -> "LET out = {}\n FOR EACH r IN input.rows {\n SET out.[r.key] = r.amount\n }\n OUTPUT {result:out}";
            default -> throw new IllegalArgumentException(kind);
        };
        return "TRANSFORM Main {\n"+body+"\n}";
    }

    public static Object expected(String kind,int n) {
        if(kind.equals("paths"))return Map.of("first","tag0","second",1,"amount",3);
        if(kind.equals("sum")){int sum=0;for(int i=0;i<n;i++)sum+=1+i%100;return sum;}
        if(kind.equals("set")){var out=new LinkedHashMap<String,Object>();for(int i=0;i<n;i++)out.put("k"+i,1+i%100);return out;}
        var out=new ArrayList<Object>();
        for(int i=0;i<n;i++)switch(kind){
            case "filter" -> {if(i%2==0)out.add(i);}
            case "project" -> out.add(Map.of("id",i,"value",2*(1+i%100),"label","tag"+i%7));
            case "lambda" -> out.add(2*(1+i%100));
            case "append" -> out.add(i);
            default -> throw new IllegalArgumentException(kind);
        }
        return out;
    }

    @Setup(Level.Trial) public void setup() {
        var parts=scenario.split(":");String kind=parts[0];int n=Integer.parseInt(parts[1]);
        input=makeInput(n);
        String source=source(kind);
        var program=new Parser(new Lexer(source).lex(),source).parse();
        var transform=(TransformDecl)program.getDecls().get(0);
        var hosts=StdLib.INSTANCE.getFns();
        var ir=new ToIR(Map.of(),hosts).compile(transform.getBody().getStatements());
        bytecode=new io.github.ehlyzov.branchline.vm.Compiler(Map.of(),hosts,true).compile(ir);
        vm=new VM(hosts,Map.of(),null);
        interpreter=new Exec(ir,hosts,StdLib.INSTANCE.getMeta(),Map.of(),null,null,
                ExecutionCaps.Companion.getDEFAULT(),null,false,new HashMap<>(),new HashMap<>(),new HashMap<>());
        Object expected=Map.of("result",expected(kind,n));
        for(int i=0;i<3;i++) {
            Object a=runVm(),b=runInterpreter();
            if(!Objects.equals(expected,a)||!Objects.equals(expected,b))
                throw new AssertionError(scenario+" mismatch expected="+expected+" vm="+a+" interpreter="+b);
        }
        if(!input.equals(makeInput(n)))throw new AssertionError("Input mutated: "+scenario);
    }
    private Map<String,Object> freshEnv(){var env=new HashMap<String,Object>();env.put("input",input);return env;}
    private Object runVm(){return vm.execute(bytecode,freshEnv());}
    private Object runInterpreter(){return interpreter.run(freshEnv(),false);}
    @Benchmark public Object execute(){return engine.equals("vm")?runVm():runInterpreter();}

    public static void main(String[] args) {
        for(String kind:List.of("paths","sum","filter","project","lambda","append","set"))
            for(int n:List.of(1000,4000)) {
                if(kind.equals("paths")&&n!=1000)continue;
                var bench=new EngineComparisonBench();bench.scenario=kind+":"+n;bench.engine="vm";bench.setup();
                System.out.println("PASS "+bench.scenario+" both engines, expected output and unchanged input");
            }
    }
}
