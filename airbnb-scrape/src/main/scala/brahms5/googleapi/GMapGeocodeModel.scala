package brahms5.googleapi

import brahms5.util.SnakifiedSprayJsonSupport

object GMapGeocodeModel {
    case class GeocodeAddressComponent(longName: String, shortName: String, types: Seq[String])
    case class Location(lat: Double, lng: Double)
    case class Geometry(location: Location, locationType: String)
    case class GeocodeResult( addressComponents: Seq[GeocodeAddressComponent],
                              formattedAddress: String,
                              geometry: Option[Geometry],
                              types: Seq[String])
    case class GeocodeResponse(results: Seq[GeocodeResult],
                               status: String)
    object GeocodeProtocol extends SnakifiedSprayJsonSupport {
      implicit val locationFormat = jsonFormat2(Location)
      implicit val geometryFormat = jsonFormat2(Geometry)
      implicit val addressFormat = jsonFormat3(GeocodeAddressComponent)
      implicit val resultFormat = jsonFormat4(GeocodeResult)
      implicit val responseFormat = jsonFormat2(GeocodeResponse)
    }
}