import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from 'antd';
import { WarningOutlined } from '@ant-design/icons';

type Props = {
  children: ReactNode;
  resetKey: string;
  onReturnFormal(): void;
};

type State = { error: Error | null };

export default class AiWorkspaceErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('AI workspace rendering failed', error, info.componentStack);
  }

  componentDidUpdate(previous: Props) {
    if (previous.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return <section className="ai-workspace-error" role="alert">
      <WarningOutlined aria-hidden="true" />
      <div><strong>草稿内容暂时无法显示</strong><p>请返回正式模型后重新打开；若问题持续存在，请恢复演示后重试。</p></div>
      <Button type="primary" onClick={this.props.onReturnFormal}>打开正式模型</Button>
    </section>;
  }
}
