package manfred.bytedepth.app.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {
  @Test
  void factories_returnPermitAndClampRejectedWait() {
    assertTrue(RateLimitDecision.permit().allowed());
    assertEquals(0, RateLimitDecision.rejected(-2).nanosToWaitForRefill());
    assertEquals(3, RateLimitDecision.rejected(3).nanosToWaitForRefill());
  }
}
