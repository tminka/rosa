import leon.Real
import Real._

import leon.Utils._

object PathErrorTest {

  def test(x: Real): Real = {
    require(x.in(-5.0, 5.0) && x +/- 1e-10)
    val res = if(x < 0) {
        x*x
      } else {
        2*x
      }
    res
  } ensuring(res => res +/- 1e-8)
  
  def test2(x: Real): Real = {
    require(x.in(-5.0, 5.0) && x +/- 1e-10)
    if(x < 0) {
      x*x
    } else {
      2*x + 0.1
    }
  } ensuring(res => res +/- 1e-8)
  
}