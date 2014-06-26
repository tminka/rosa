import leon.real._
import RealOps._

object NBody {


  // TODO: Einheiten?
  def step(x: Real, y: Real, z: Real, vx: Real, vy: Real,
    vz: Real, i: LoopCounter): (Real, Real, Real, Real, Real, Real) = {

    require(-6 <= x && x <= 6 && -6 <= y && y <= 6 && -0.2 <= z && z <= 0.2 &&
        -3 <= vx && vx <= 3 && -3 <= vy && vy <= 3 && -0.1 <= vz && vz <= 0.1 &&
        x*x + y*y + z*z >= 20.0 &&
        -6 <= ~x && ~x <= 6 && -6 <= ~y && ~y <= 6 && -0.2 <= ~z && ~z <= 0.2 &&
        -3 <= ~vx && ~vx <= 3 && -3 <= ~vy && ~vy <= 3 && -0.1 <= ~vz && ~vz <= 0.1 &&
         (~x)*(~x) + (~y)*(~y)+ (~z)*(~z) >= 20.0)

    if (i < 100) {
      val dt = 0.1
      val solarMass = 39.47841760435743

      val distance = sqrt(x*x + y*y + z*z)
      val mag = dt / (distance * distance * distance)

      val vxNew = vx - x * solarMass * mag
      val vyNew = vy - y * solarMass * mag
      val vzNew = vz - z * solarMass * mag

      val x1 = x + dt * vxNew
      val y1 = y + dt * vyNew
      val z1 = z + dt * vzNew
      step(x1, y1, z1, vxNew, vyNew, vzNew, i++)
    } else {
      (x, y, z, vx, vy, vz)
    }
  } ensuring (_ match {
    case (xP, yP, zP, vxP, vyP, vzP) =>
      -6 <= xP && xP <= 6 && -6 <= yP && yP <= 6
  })




  /*def nBodySimulation(x: Real, y: Real, z: Real, vx: Real, vy: Real,
    vz: Real): (Real, Real, Real, Real, Real, Real) = {

    require(-6 <= x && x <= 6 && -6 <= y && y <= 6 && -6 <= z && z <= 6 &&
      // check this:
        -6 <= vx && vx <= 6 && -6 <= vy && vy <= 6 && -6 <= vz && vz <= 6)

    iterate(x, y, z, vx, vy, vz) {
      val dt = 0.1
      val solarMass = 39.47841760435743

      val distance = sqrt(x*x + y*y + z*z)
      val mag = dt / (distance * distance * distance)

      val vxNew = vx - x * solarMass * mag
      val vyNew = vy - y * solarMass * mag
      val vzNew = vz - z * solarMass * mag

      x <== x + dt * vxNew
      y <== y + dt * vyNew
      z <== z + dt * vzNew
      vx <== vxNew
      vy <== vyNew
      vz <== vzNew
    }
  } ensuring (_ match {
    case (a, b, c, d, e, f) => -10 <= a && a <= 10 && -10 <= b && b <= 10
  })*/

}