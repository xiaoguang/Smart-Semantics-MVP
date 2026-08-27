import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { forwardRef, type ComponentPropsWithoutRef } from 'react';
import './back-action.css';

export type BackActionProps = Omit<ComponentPropsWithoutRef<typeof Button>, 'children' | 'icon' | 'onClick'> & {
  destination: string;
  onBack(): void;
};

const BackAction = forwardRef<HTMLAnchorElement | HTMLButtonElement, BackActionProps>(function BackAction({
  destination,
  onBack,
  className,
  ...buttonProps
}, ref) {
  const label = `返回${destination}`;
  return <Tooltip title={label}>
    <Button
      {...buttonProps}
      ref={ref}
      type="text"
      className={['back-action', className].filter(Boolean).join(' ')}
      icon={<ArrowLeftOutlined aria-hidden="true" />}
      aria-label={label}
      data-back-action="true"
      onClick={onBack}
    />
  </Tooltip>;
});

export default BackAction;
