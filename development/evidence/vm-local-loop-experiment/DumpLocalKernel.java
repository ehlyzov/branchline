import java.nio.file.*;
import io.github.ehlyzov.branchline.cli.BranchlineProgram;
import io.github.ehlyzov.branchline.vm.*;
public class DumpLocalKernel {
  public static void main(String[] args) throws Exception {
    var fixture = new VMFactorialFixture("holdoutProjection","large");
    var program = new BranchlineProgram(fixture.getSource(),null);
    var plan = JvmLocalLoopCompiler.prepare(program.compileBytecode(program.selectTransform(null)));
    if(plan.getLoweredLoopCount()!=1) throw new AssertionError(plan.getFallbackReason());
    Files.write(Path.of(args[0]),JvmLocalLoopCompiler.generatedClassBytes(plan));
    System.out.println("Generated classes="+plan.getGeneratedClassCount()+" bytes="+plan.getGeneratedBytecodeBytes()+" bodyInstructions="+plan.getBodyInstructionCount());
  }
}
