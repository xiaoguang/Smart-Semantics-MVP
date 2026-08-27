export type CurrentUser = {
  userId: string;
  displayName: string;
  initials: string;
};

export const localCurrentUser: CurrentUser = {
  userId: 'linguan-local-user',
  displayName: '灵光用户',
  initials: 'LG',
};
