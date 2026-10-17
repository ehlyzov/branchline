/** Parent regression: must fail against the pre-guard measured jar and pass final source. */
public class SuspendAcceptance {
  public static void main(String[] args) {
    for(String source:new String[]{"TRANSFORM T { OUTPUT SUSPEND 0; }", "TRANSFORM T { LET saved = SUSPEND 0; OUTPUT saved; }"}) {
      var eager=PipelineAcceptance.prepare(source,true);
      var candidate=PipelineAcceptance.prepare(source,false);
      String a=PipelineAcceptance.result(eager,"{\"a\":1,\"unused\":2}");
      String b=PipelineAcceptance.result(candidate,"{\"a\":1,\"unused\":2}");
      if(!a.equals(b))throw new AssertionError("Suspension snapshot differs; plan="+candidate.getReport().getEffectiveMode()+" eager="+a+" candidate="+b);
      if(!candidate.getReport().getEffectiveMode().name().equals("EAGER")||!b.contains("__suspended")||!b.contains("unused"))throw new AssertionError("Incomplete suspension acceptance: "+b);
    }
    System.out.println("PASS parent suspension acceptance: 2 sources, exact snapshot parity, full environment, EAGER plan");
  }
}
