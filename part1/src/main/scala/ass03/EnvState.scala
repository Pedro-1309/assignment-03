package ass03


case class CarInfo(id: String, position: Double, speed: Double)

case class EnvState(
                     cars: Map[String, CarInfo] = Map.empty,
                     streetLength: Double = 1000.0,
                     deltaTime: Double = 0.1
                   ) {

  // Computes the new environment state based on the decisions made by the cars
  def update(decisions: Map[String, Double]): EnvState = {

    val updatedCars = cars.map { case (id, carInfo) =>
      // Retrieve the new speed, default to current if missing
      val newSpeed = decisions.getOrElse(id, carInfo.speed)
      var newPosition = carInfo.position + (newSpeed * deltaTime)

      // Wrap around the street
      if (newPosition > streetLength) {
        newPosition = newPosition - streetLength
      }

      // Return the updated CarInfo
      id -> carInfo.copy(position = newPosition, speed = newSpeed)
    }

    // New instance of EnvState with the updated map
    this.copy(cars = updatedCars)
  }
}