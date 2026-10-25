export interface AccessPolicy {
  newWebLogins: boolean;
  openAfterCountdown?: boolean;
  countdownTarget: string;
  websitePreviewPath: string;
}
export function isWebsiteOpen(config: AccessPolicy, now?: number): boolean;
export function areNewWebLoginsEnabled(config: AccessPolicy, now?: number): boolean;
export function launchStatus(config: AccessPolicy, now?: number): {
  websiteOpen: boolean;
  loginEnabled: boolean;
  newWebLogins: boolean;
  openAfterCountdown: boolean;
  countdownTarget: string;
  serverTime: number;
};
