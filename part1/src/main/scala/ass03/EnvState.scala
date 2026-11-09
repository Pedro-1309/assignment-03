package ass03

case class RoadInfo(id: String, startX: Double, startY: Double, endX: Double, endY: Double, length: Double)

case class CarInfo(id: String, roadId: String, position: Double, speed: Double)

// tickOffset for starting traffic lights at different states
case class SemInfo(id: String, roadId: String, position: Double, state: String, x: Double, y: Double, tickOffset: Int = 0) {
  def updateColor(tick: Int): SemInfo = {
    val greenDuration = 75
    val yellowDuration = 25
    val redDuration = 100
    val cycle = greenDuration + yellowDuration + redDuration

    val t = (tick + tickOffset) % cycle
    val newState = if (t < greenDuration) "GREEN"
    else if (t < greenDuration + yellowDuration) "YELLOW"
    else "RED"

    this.copy(state = newState)
  }
}

case class EnvState(
                     roads: Map[String, RoadInfo] = Map.empty,
                     cars: Map[String, CarInfo] = Map.empty,
                     trafficLights: Map[String, SemInfo] = Map.empty,
                     deltaTime: Double = 1.0
                   ) {

  // Computes the new environment state based on the decisions made by the cars
  def update(tick: Int, decisions: Map[String, Double]): EnvState = {

    val updatedSems = trafficLights.map { case (id, sem) =>
      id -> sem.updateColor(tick)
    }

    val updatedCars = cars.map { case (id, car) =>
      val newSpeed = decisions.getOrElse(id, car.speed)
      var newPos = car.position + newSpeed
      val roadLength = roads(car.roadId).length
      // Wrap around the street
      if (newPos > roadLength) newPos = newPos - roadLength
      id -> car.copy(position = newPos, speed = newSpeed)
    }

    this.copy(cars = updatedCars, trafficLights = updatedSems)
  }
}