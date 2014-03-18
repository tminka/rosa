import leon.real._
import RealOps._

object Traincar3 {

  // y: <1, 30, 20>     s:<1, 30, 25>
  def out1(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11)
    (-3.7377847506227999E+02) * s0  + 7.0862205220929002E+02 * s1  + (-3.7886044910192999E+02) * s2  + (-4.5413076942315001E+02) * s3  +
     (-8.7386976105502004E+02) * (1.0)*s4  + 3.5911290954487999E+03 * (1.0)*s5  + (-2.2921226288533999E+03) * (1.0)*s6  + 2.9021426298877441E+02    
  }


  def state1(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (0.9999996047)*s0 + (7.191445178E-07)*s1+ (-3.804306415E-07)*s2+ (0.003390948426)*s3+ (-0.007373658011)*s4+
 (0.00251435504)*s5+ (-0.001735963933)*s6+ (-3.2913896479139998E-03)*y0 + (7.2728293625470000E-03)*y1+
 (-2.5109142643930001E-03)*y2+ (1.7337651582750001E-03)*y3+ (9.60292E-10)*2.9021426298877441E+02
  }

  def state2(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (8.311153577E-10)*s0 +(1.000000002)*s1+ (-1.019462999E-08)*s2+ (1.426096894E-04)*s3+ (0.004152887373)*s4+ (-0.002584463613)*s5+ (-0.001280301976)*s6
+ (-1.4262580017300000E-04)*y0 +(-4.0529285568870000E-03)*y1+ (2.4846129842129999E-03)*y2+ (1.2802087276050000E-03)*y3+ (4.0371E-11)*2.9021426298877441E+02
  }

  def state3(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
     (-1.413514624E-07)*s0 +(2.960957207E-07)*s1+ (0.9999997794)*s2+ (0.001259708376)*s3+ (-0.001326863654)*s4+ (0.009741383745)*s5+ (-0.01209814023)*s6
+ (-1.2598715285710000E-03)*y0 + (1.3265590608970001E-03)*y1+ (-9.6401205297600000E-03)*y2+ (1.1997334409343001E-02)*y3+ (3.56771E-10)*2.9021426298877441E+02
  }

  def state4(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (-2.160538622E-04)*s0 +(4.011584145E-04)*s1+ (-2.14484627E-04)*s2+ (0.9992989209)*s3+ (-3.699735601E-04)*s4+ (0.002043378232)*s5+ (-0.00122374133)*s6
+ (4.4334768343400001E-04)*y0 + (-1.2406658684299999E-04)*y1+ (-1.0483935681000001E-05)*y2+ (-7.3810400367999997E-05)*y3+ (5.66097217E-07)*2.9021426298877441E+02
  }

  def state5(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (7.805459445E-06)*s0 +(-7.795254622E-06)*s1+ (-1.164219329E-08)*s2+ (1.029861692E-04)*s3+ (0.9992705039)*s4+ (1.709798451E-04)*s5+ (1.943846519E-05)*s6
+ (-1.0190707901600000E-04)*y0 + (7.2728601855600000E-04)*y1+ (-1.6978291545200001E-04)*y2+ (-1.950523374E-05)*y3+ (2.9166E-11)*2.9021426298877441E+02
  }

  def state6(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (-1.278980488E-09)*s0 +(7.819576188E-06)*s1+ (-7.817496855E-06)*s2+ (1.807543861E-05)*s3+ (1.724117406E-04)*s4+ (0.9992790154)*s5+ (2.725773198E-04)*s6
+ (-1.8077674363999999E-05)*y0 +(-1.7132402225200001E-04)*y1+ (7.1881845635799997E-04)*y2+ (-2.7149682594400002E-04)*y3+ (5.118E-12)*2.9021426298877441E+02

  }

  def state7(s0: Real, s1: Real, s2: Real, s3: Real, s4: Real, s5: Real, s6: Real, y0: Real, y1: Real, y2: Real, y3: Real) = {
    require(-3 <= s0 && s0 <= 5 && -3 <= s1 && s1 <= 5 && -3 <= s2 && s2 <= 5 && -6 <= s3 && s3 <= 11 && -6 <= s4 && s4 <= 11 &&
      -6 <= s5 && s5 <= 11 && -6 <= s6 && s6 <= 11 && -6 <= y0 && y0 <= 10 && -6 <= y1 && y1 <= 10 && -6 <= y2 && y2 <= 10 &&
      -6 <= y3 && y3 <= 10)
    (-8.892911482E-09)*s0 +(1.750282868E-08)*s1+ (7.806737695E-06)*s2+ (8.234669601E-05)*s3+ (1.964473315E-06)*s4+ (2.518123976E-04)*s5+
     (0.999262088)*s6 + (-8.2357312397000003E-05)*y0 + (-1.984691067E-06)*y1+ (-2.5063655913299998E-04)*y2+ (7.3676635226699999E-04)*y3+
      (2.3321E-11)*2.9021426298877441E+02
    
  }
  
}