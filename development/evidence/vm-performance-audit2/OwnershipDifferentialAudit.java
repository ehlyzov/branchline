import io.github.ehlyzov.branchline.*;
import io.github.ehlyzov.branchline.ir.*;
import io.github.ehlyzov.branchline.vm.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
public final class OwnershipDifferentialAudit {
  static final Token T = new Token(TokenType.IDENTIFIER,"audit",1,1);
  static final Random R = new Random(170126);
  static IdentifierExpr id(String s) { return new IdentifierExpr(s,T); }
  static Expr expr(int depth) {
    switch (R.nextInt(depth == 0 ? 4 : 6)) {
      case 0: return new ArrayExpr(List.of(),T);
      case 1: return new ObjectExpr(List.of(),T);
      case 2: return id("x"+R.nextInt(8));
      case 3: return new StringExpr("value",T);
      case 4: return new ArrayExpr(List.of(expr(depth-1),expr(depth-1)),T);
      default: return new CallExpr(id("host"),List.of(expr(depth-1)),T);
    }
  }
  static List<IRNode> nodes(int depth) {
    List<IRNode> out=new ArrayList<>();
    for(int i=0,n=2+R.nextInt(9);i<n;i++) {
      String name="x"+R.nextInt(8);
      switch(R.nextInt(depth==0?4:6)) {
        case 0: out.add(new IRLet(name,expr(2)));break;
        case 1: out.add(new IRPlusAssignVar(name,expr(2)));break;
        case 2: out.add(new IRExprStmt(expr(2)));break;
        case 3: out.add(new IRSetVar(name,expr(2)));break;
        case 4: out.add(new IRIf(new BoolExpr(R.nextBoolean(),T),nodes(depth-1),R.nextBoolean()?nodes(depth-1):null));break;
        default: out.add(new IRForEach(name,new ArrayExpr(List.of(new StringExpr("row",T)),T),R.nextBoolean()?id("x"+R.nextInt(8)):null,nodes(depth-1)));break;
      }
    }
    return out;
  }
  public static void main(String[] args) throws Exception {
    var reused=new io.github.ehlyzov.branchline.vm.Compiler();
    for(int i=0;i<500;i++) {
      List<IRNode> ir=new ArrayList<>();
      for(int n=0;n<8;n++)ir.add(new IRLet("x"+n,n%2==0?new ArrayExpr(List.of(),T):new ObjectExpr(List.of(),T)));
      ir.addAll(nodes(3));
      var compiler=i%2==0?reused:new io.github.ehlyzov.branchline.vm.Compiler();
      var bc=compiler.compile(ir);
      String encoded=BytecodeIO.INSTANCE.encodeToString(bc);
      String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));
      System.out.println(i+","+bc.size()+","+digest);
    }
  }
}
