import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { createCollaborationRuntime } from '../collaboration/runtime.ts';
import type { DemoSession } from '../collaboration/types.ts';
import type { CurrentUser } from './current-user.ts';
import LoginPage from '../collaboration/login-page.tsx';

const sessionKey = 'linguan:demo-auth:session:v1';

type CurrentUserContextValue = {
  currentUser: CurrentUser;
  session: DemoSession;
  login(username: string, password: string): void;
  logout(): void;
  switchUser(username: string, password: string): void;
};

const CurrentUserContext = createContext<CurrentUserContextValue | null>(null);

function readSession(): DemoSession | null {
  try {
    const raw = sessionStorage.getItem(sessionKey);
    if (!raw) return null;
    const value = JSON.parse(raw) as DemoSession;
    return value.schemaVersion === 1 && value.userId ? value : null;
  } catch { return null; }
}

export function CurrentUserProvider({ children }: { children: ReactNode }) {
  const runtime = useMemo(() => createCollaborationRuntime(localStorage), []);
  const [session, setSession] = useState<DemoSession | null>(() => readSession());
  let currentUser = null;
  if (session) {
    try { currentUser = runtime.getUser(session.userId); }
    catch { sessionStorage.removeItem(sessionKey); }
  }

  const authenticate = (username: string, password: string) => {
    const next = runtime.createSession(username, password);
    sessionStorage.setItem(sessionKey, JSON.stringify(next));
    setSession(next);
  };
  const logout = () => { sessionStorage.removeItem(sessionKey); setSession(null); };

  if (!session || !currentUser) return <LoginPage onLogin={authenticate} />;
  const value: CurrentUserContextValue = {
    currentUser: { userId: currentUser.userId, displayName: currentUser.displayName, initials: currentUser.initials },
    session, login: authenticate, switchUser: authenticate, logout,
  };
  return <CurrentUserContext.Provider value={value}>{children}</CurrentUserContext.Provider>;
}

export function useCurrentUser() {
  const context = useContext(CurrentUserContext);
  if (!context) throw new Error('CurrentUserProvider 未初始化');
  return context;
}
