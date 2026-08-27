export function presentLoginFailure(error: unknown): string {
  const detail = error instanceof Error ? error.message : '';
  if (/用户名|密码|账号|登录/iu.test(detail)) return '用户名或密码不正确，请重新输入。';
  return '暂时无法登录，请稍后重试。';
}
