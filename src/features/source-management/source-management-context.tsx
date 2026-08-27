import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useCollaboration } from '../collaboration/collaboration-context.tsx';
import { useCurrentUser } from '../ai-modeling/current-user-context.tsx';
import { createSourceManagementRuntime } from './runtime.ts';
import type { ExportArtifact, SourceManagementCommand, SourceManagementResult, SourceManagementRuntime, SourceManagementSnapshot } from './types.ts';

type CommandInput = SourceManagementCommand extends infer Command
  ? Command extends SourceManagementCommand ? Omit<Command, 'workspaceId' | 'expectedRevision' | 'actor'> : never
  : never;

type SourceManagementContextValue = {
  runtime: SourceManagementRuntime;
  snapshot: SourceManagementSnapshot | null;
  loading: boolean;
  error?: string;
  role: 'VIEWER' | 'EDITOR' | 'REVIEWER' | 'PUBLISHER' | 'ADMIN';
  run(command: CommandInput): Promise<SourceManagementResult>;
  refresh(): Promise<void>;
  exportYaml(): Promise<ExportArtifact>;
  resetDemo(): Promise<void>;
};

const SourceManagementContext = createContext<SourceManagementContextValue | null>(null);

export function SourceManagementProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useCurrentUser();
  const collaboration = useCollaboration();
  const runtime = useMemo(() => createSourceManagementRuntime({ storage: localStorage }), []);
  const [snapshot, setSnapshot] = useState<SourceManagementSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const projectId = collaboration.modelSpaceIds[0];
  const role = projectId ? collaboration.accessFor(projectId).role : 'VIEWER';
  const refresh = async () => {
    setLoading(true);
    try {
      setSnapshot(await runtime.read(collaboration.activeWorkspaceId));
      setError(undefined);
    } catch {
      setSnapshot(null);
      setError('运行来源暂时无法读取');
    }
    finally { setLoading(false); }
  };
  useEffect(() => { void refresh(); }, [collaboration.activeWorkspaceId, currentUser.userId, collaboration.revision]);
  const value: SourceManagementContextValue = {
    runtime, snapshot, loading, ...(error ? { error } : {}), role,
    async run(command) {
      if (!snapshot) throw new Error('来源中心尚未加载');
      const result = await runtime.execute({
        ...command, workspaceId: snapshot.workspaceId, expectedRevision: snapshot.revision,
        actor: { userId: currentUser.userId, role },
      } as SourceManagementCommand);
      setSnapshot(result.snapshot);
      return result;
    },
    refresh,
    exportYaml() {
      if (!snapshot) throw new Error('来源中心尚未加载');
      return runtime.exportYaml(snapshot.workspaceId);
    },
    async resetDemo() {
      for (const workspace of collaboration.workspaces) {
        const projectId = workspace.modelProjectIds[0];
        if (!projectId || collaboration.accessFor(projectId).role !== 'ADMIN') continue;
        const current = await runtime.read(workspace.workspaceId);
        await runtime.execute({
          type: 'RESET_DEMO', workspaceId: workspace.workspaceId, expectedRevision: current.revision,
          actor: { userId: currentUser.userId, role: 'ADMIN' },
        });
      }
      await refresh();
    },
  };
  return <SourceManagementContext.Provider value={value}>{children}</SourceManagementContext.Provider>;
}

export function useSourceManagement() {
  const context = useContext(SourceManagementContext);
  if (!context) throw new Error('SourceManagementProvider 未初始化');
  return context;
}
