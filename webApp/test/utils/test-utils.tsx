import { render, RenderResult } from '@testing-library/react'
import { Auth, AuthContext, LocationService } from '@tmtsoftware/esw-ts'
import type { TestUtils } from '@tmtsoftware/esw-ts'
import React from 'react'
import { BrowserRouter } from 'react-router-dom'
import { instance, mock } from 'ts-mockito'
import { LocationServiceProvider } from '../../src/contexts/LocationServiceContext'

class MockedFetch {
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  // eslint-disable-next-line @typescript-eslint/no-empty-function, @typescript-eslint/no-unused-vars
  fetch(input: RequestInfo, init?: RequestInit): Promise<Response> {}
}

export const mockFetch = (): typeof window.fetch => {
  const mockedFetch = mock(MockedFetch)
  window.fetch = instance(mockedFetch).fetch
  return mockedFetch.fetch
}

export const locationServiceMock = mock<LocationService>()

const getMockAuth = (loggedIn: boolean): Auth => {
  let loggedInValue = loggedIn
  return {
    hasRealmRole: () => true,
    hasResourceRole: () => false,
    isAuthenticated: () => loggedInValue,
    logout: () => {
      loggedInValue = false
      return Promise.resolve() as TestUtils.KeycloakPromise<void, void>
    },
    token: () => 'token string',
    tokenParsed: () =>
      ({
        preferred_username: loggedIn ? 'esw-user' : undefined
      } as TestUtils.KeycloakTokenParsed),
    realmAccess: () => [''] as unknown as TestUtils.KeycloakRoles,
    resourceAccess: () => [''] as unknown as TestUtils.KeycloakResourceAccess,
    loadUserProfile: () =>
      Promise.resolve({}) as TestUtils.KeycloakPromise<
        TestUtils.KeycloakProfile,
        void
      >
  }
}

const renderWithAuth = (ui: React.ReactElement): RenderResult => {
  const authContext = (
    <AuthContext.Provider
      value={{
        auth: getMockAuth(true),
        login: () => ({}),
        logout: () => ({})
      }}>
      {ui}
    </AuthContext.Provider>
  )
  return render(authContext)
}

const renderWithLocationServiceContext = (ui: React.ReactElement) => {
  return renderWithAuth(
    <LocationServiceProvider locationService={instance(locationServiceMock)}>
      {ui}
    </LocationServiceProvider>
  )
}

export const renderWithRouter = (ui: React.ReactElement): RenderResult => {
  return renderWithLocationServiceContext(<BrowserRouter>{ui}</BrowserRouter>)
}
