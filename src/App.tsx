import { ConfigProvider, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AppLayout from './layout/AppLayout';
import { LinguanWorkspaceProvider } from './features/ai-modeling/workspace-context';
import { CurrentUserProvider } from './features/ai-modeling/current-user-context';
import { CollaborationProvider } from './features/collaboration/collaboration-context';
import { SourceManagementProvider } from './features/source-management/source-management-context';
import AiWorkspaceErrorBoundary from './features/ai-modeling/ai-workspace-error-boundary';
import { useCurrentUser } from './features/ai-modeling/current-user-context';
import { useCollaboration } from './features/collaboration/collaboration-context';
import './index.css';
import './App.css';

message.config({ maxCount: 1, duration: 3 });

function WorkspaceShell() {
  const { currentUser } = useCurrentUser();
  const collaboration = useCollaboration();
  return <AiWorkspaceErrorBoundary
    resetKey={`${currentUser.userId}:${collaboration.activeWorkspaceId}:${collaboration.viewMode}:${collaboration.revision}`}
    onReturnFormal={() => collaboration.setViewMode('FORMAL')}
  >
    <SourceManagementProvider>
      <LinguanWorkspaceProvider>
        <AppLayout />
      </LinguanWorkspaceProvider>
    </SourceManagementProvider>
  </AiWorkspaceErrorBoundary>;
}

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#0078ad',
          colorLink: '#0078ad',
          colorSuccess: '#0d7543',
          colorWarning: '#c77100',
          colorError: '#c4360b',
          colorText: '#374066',
          colorTextSecondary: '#5f6685',
          colorTextTertiary: '#8088a3',
          colorBorder: '#d9d9d9',
          colorBorderSecondary: '#ebecf0',
          colorBgLayout: '#f5f6fa',
          colorBgContainer: '#ffffff',
          borderRadius: 8,
          borderRadiusLG: 12,
          controlHeight: 36,
          fontSize: 14,
          fontFamily:
            'Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", Arial, sans-serif',
          boxShadowSecondary: '0 4px 28px rgba(9, 1, 61, 0.14)',
        },
        components: {
          Button: {
            primaryShadow: 'none',
            defaultShadow: 'none',
          },
          Card: {
            headerHeight: 52,
          },
          Menu: {
            itemBorderRadius: 8,
            itemHeight: 42,
            itemMarginInline: 10,
            itemSelectedBg: '#e6f7fc',
            itemSelectedColor: '#0078ad',
          },
          Table: {
            headerBg: '#f9fafc',
            headerColor: '#5f6685',
            rowHoverBg: '#f9fafc',
            borderColor: '#ebecf0',
            cellPaddingBlockSM: 10,
            cellPaddingInlineSM: 12,
          },
          Tabs: {
            itemActiveColor: '#0078ad',
            itemHoverColor: '#006292',
            itemSelectedColor: '#0078ad',
            inkBarColor: '#0078ad',
          },
        },
      }}
    >
      <CurrentUserProvider>
        <CollaborationProvider>
          <WorkspaceShell />
        </CollaborationProvider>
      </CurrentUserProvider>
    </ConfigProvider>
  );
}

export default App;
