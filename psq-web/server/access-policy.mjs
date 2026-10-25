export function isWebsiteOpen(config, now = Date.now()) {
  if (!config.openAfterCountdown) return config.newWebLogins === true;
  const target = Date.parse(config.countdownTarget);
  return Number.isFinite(target) && now >= target;
}

export function areNewWebLoginsEnabled(config, now = Date.now()) {
  return config.newWebLogins === true && isWebsiteOpen(config, now);
}

export function launchStatus(config, now = Date.now()) {
  return {
    websiteOpen: isWebsiteOpen(config, now),
    loginEnabled: areNewWebLoginsEnabled(config, now),
    newWebLogins: config.newWebLogins === true,
    openAfterCountdown: config.openAfterCountdown === true,
    countdownTarget: config.countdownTarget,
    serverTime: now,
  };
}
