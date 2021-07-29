import { HttpConnection, LocationService, Prefix } from '@tmtsoftware/esw-ts'
import { errorMessage } from './message'

const backendServicePrefix = Prefix.fromString('ESW.vbds')
export const BACKEND_CONNECTION = HttpConnection(
  backendServicePrefix,
  'Service'
)

export const getBackendUrl = (
  locationService: LocationService
): Promise<string | undefined> =>
  getBackendLocation(locationService).then((location) => location?.uri)

const getBackendLocation = async (locationService: LocationService) => {
  try {
    const backendLocation = await locationService.find(BACKEND_CONNECTION)
    if (backendLocation === undefined) {
      errorMessage(
        `Backend Server connection ${BACKEND_CONNECTION.prefix.toJSON()} not available`
      )
    }
    return backendLocation
  } catch (e) {
    errorMessage('Failed to resolve backend Url', e)
    return
  }
}
