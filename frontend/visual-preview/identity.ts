import { demoAdministrator } from './fixtures';

const unavailable = async () => { throw new Error('Authentication is not part of this independent read-only preview.'); };

const fixtureIdentity = Object.freeze({
  user: demoAdministrator,
  isAuthenticated: false,
  loading: false,
  login: unavailable,
  logout: unavailable,
  refreshUser: async () => {},
});

export const useAuth = () => fixtureIdentity;
