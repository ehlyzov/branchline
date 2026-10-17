import java.math.BigInteger;
import java.util.*;
import io.github.ehlyzov.branchline.vm.*;

/** Runs against a built candidate, independently of Kotlin opt-in compilation. */
public class LegacyNumericObservationProbe {
    static Object run(boolean experimental) {
        var env = new LinkedHashMap<String, Object>();
        var observations = new ArrayList<Object>();
        env.put("input", new BigInteger("1") {
            @Override public BigInteger add(BigInteger value) {
                observations.add(env.get("x"));
                env.put("x", 11);
                return super.add(value);
            }
        });
        var code = Bytecode.Companion.fromInstructions(List.of(
            new Instruction.PUSH(7), Instruction.DUP.INSTANCE,
            new Instruction.STORE_VAR("x"), new Instruction.STORE_LOCAL(0),
            new Instruction.LOAD_VAR("input"), new Instruction.PUSH(1),
            Instruction.ADD.INSTANCE, Instruction.POP.INSTANCE,
            new Instruction.LOAD_LOCAL(0), new Instruction.LOAD_VAR("x"),
            new Instruction.MAKE_ARRAY(2)
        ), List.of(), Map.of(), new TraceMetadata(Map.of(), Map.of(), Map.of()));
        var vm = new VM(Map.of(), Map.of(), null);
        var result = experimental
            ? vm.executeExperimental(vm.prepareExperimental(code, new VMExperimentalOptions(true, true, true)), env)
            : vm.execute(code, env);
        return "result=" + result + ", observed=" + observations + ", x=" + env.get("x");
    }
    public static void main(String[] args) {
        Object expected = run(false), actual = run(true);
        System.out.println("ordinary: " + expected);
        System.out.println("experimental: " + actual);
        if (!Objects.equals(expected, actual)) throw new AssertionError("Legacy numeric observer mismatch");
    }
}
