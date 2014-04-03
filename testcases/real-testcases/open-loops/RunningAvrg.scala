import leon.real._
import RealOps._
import annotations._


object RunningAvrg {

  
  

  def loopInvariant(m: Real, x: Real, c: Real): Real = {
    require(-1200 <= m && m <= 1200 && -1200 <= x && x <= 1200 && x +/- 1e-5 &&
        1 <= c && c <= 1000)

    ((c - 1)*m + x) / c

  } ensuring ( res =>
      -1200 <= res && res <= 1200 //&& -1200 <= ~res && ~res <= 1200
    )


  // Can be extended to also compute the population variance, whatever that is...
  def variance(n: Real, m: Real, s: Real, x: Real): Real = {
    require(-1200 <= m && m <= 1200 && -1200 <= x && x <= 1200 &&
        0 <= s && s <= 1440000 && 2 <= n && n <= 1000)

    val m_new = ((n - 1) * m + x) / n

    val s_new = ((n - 2)/(n - 1)) * s + ((m_new - m) * (m_new - m))/n

    s_new
  } ensuring ( res =>
    0 <= res && res <= 1440000.00
    )

/*  @modelInput                                 // this roundoff thing is probably not going to compile
  def nextValue: Real = { ??? } ensuring (res => -1200 <= next && next <= 1200 && roundoff(res))

  /**
    The challenge in this example is that it uses the loop counter
    in the computation. We may still be able to prove something,
    since this computation has to hold for any counter.
  */
  def mean(currMean: Real, counter: Int): Real = {
    require(-1200 <= currMean && currMean <= 1200 && loopCounter(counter))

    if (counter < 100) {
      val nextVal = nextValue
      val newMean = ((counter.toReal - 1) * currMean + nextValue) / counter.toReal
      mean(newMean, counter + 1)
    } else {
      currMean
    }

  } ensuring ( res => res <= 1200.0 && -1200.0 <= res)

*/
  // standard deviation and variance as a running computation
  // weighted mean

  // inner product (this we'd have to compare against the specialized work)


  // online kurtosis algorithm
  //http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance

  // a possible challenge: computing a two-pass covariance
  //http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
  // the first sum will have already some error
}