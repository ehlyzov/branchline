import io.github.ehlyzov.branchline.debug.Debug;
import io.github.ehlyzov.branchline.ir.IRNode;
import io.github.ehlyzov.branchline.vm.Bytecode;
import io.github.ehlyzov.branchline.vm.PathWriteMeta;
import io.github.ehlyzov.branchline.vm.TraceMetadata;
import io.github.ehlyzov.branchline.vm.VM;
import io.github.ehlyzov.branchline.vm.VMTransformBenchmark;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts real trace metadata Map.get calls in one native large typicalTransform.
 * This is a lookup-count probe, not a timing or allocation benchmark.
 *
 * Reproduce (substitute the jar to compare another build):
 * javac -proc:none -cp /tmp/branchline-vm-hotpath/before/vm.jar -d /tmp/branchline-trace-probe \
 *   development/evidence/vm-hotpath-optimization/TraceLookupProbe.java
 * java -cp /tmp/branchline-trace-probe:/tmp/branchline-vm-hotpath/before/vm.jar TraceLookupProbe
 */
public final class TraceLookupProbe {
    private static final class CountingMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> delegate;
        private long gets;

        CountingMap(Map<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override public V get(Object key) {
            gets++;
            return delegate.get(key);
        }

        @Override public Set<Entry<K, V>> entrySet() {
            return delegate.entrySet();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void expectNumber(Object actual, int expected) {
        if (actual == null || new BigDecimal(actual.toString()).compareTo(BigDecimal.valueOf(expected)) != 0) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        VMTransformBenchmark benchmark = new VMTransformBenchmark();
        benchmark.setDataset("large");
        benchmark.setup();
        benchmark.resetEnv();
        VM vm = (VM) field(benchmark, "vm");
        if (field(vm, "tracer") != null || Debug.INSTANCE.getTracer() != null) {
            throw new AssertionError("Probe requires no tracer");
        }

        Bytecode original = (Bytecode) field(benchmark, "transformBytecode");
        TraceMetadata metadata = original.getTraceMetadata();
        CountingMap<Integer, List<IRNode>> enters = new CountingMap<>(metadata.getEnterEvents());
        CountingMap<Integer, List<IRNode>> exits = new CountingMap<>(metadata.getExitEvents());
        CountingMap<Integer, PathWriteMeta> writes = new CountingMap<>(metadata.getPathWrites());
        Bytecode counted = original.copy(
            original.getOpcodes(), original.getIntOperands(), original.getStringOperands(),
            original.getObjKeyOperands(), original.getConstOperands(), original.getIntOperandOffsets(),
            original.getStringOperandOffsets(), original.getObjKeyOperandOffsets(),
            original.getConstOperandOffsets(), original.getConstants(), original.getDebugInfo(),
            new TraceMetadata(enters, exits, writes));

        Object result = vm.execute(counted, (Map<String, Object>) field(benchmark, "env"));
        if (!(result instanceof Map<?, ?> output)) {
            throw new AssertionError("Expected output map, got " + result);
        }
        expectNumber(output.get("orderCount"), 500);
        expectNumber(output.get("total"), 87250);
        if (Debug.INSTANCE.getTracer() != null) {
            throw new AssertionError("Tracer changed during execution");
        }

        System.out.println("jar=" + VM.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("workload=VMTransformBenchmark.typicalTransform dataset=large executions=1 tracer=null");
        System.out.println("method=delegating trace metadata maps count Map.get; same arrays/constants/program/input");
        System.out.println("output=" + result);
        System.out.println("outputCheck=PASS expected orderCount=500 total=87250");
        System.out.println("enterEvents.get=" + enters.gets);
        System.out.println("exitEvents.get=" + exits.gets);
        System.out.println("pathWrites.get=" + writes.gets);
        System.out.println("total.get=" + (enters.gets + exits.gets + writes.gets));
        System.out.println("scope=lookup counts only; wrapper overhead excludes timing/allocation conclusions");
    }
}
